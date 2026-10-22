package com.swingmate.phone;

import com.swingmate.auth.JwtTokenProvider;
import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneVerificationService {

    static final Duration CODE_VALIDITY = Duration.ofMinutes(3);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int DAILY_SEND_LIMIT = 5;

    private final PhoneVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final String fakeCode;

    public PhoneVerificationService(PhoneVerificationRepository verificationRepository,
                                    UserRepository userRepository, JwtTokenProvider tokenProvider,
                                    @Value("${sms.fake-code}") String fakeCode) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.fakeCode = fakeCode;
    }

    @Transactional
    public SendResult send(UUID userId, String rawPhoneNumber, String purpose, UUID previousVerificationId) {
        String phoneNumber = PhoneNumbers.normalize(rawPhoneNumber);
        Instant now = Instant.now();

        int resendCount = 0;
        if (previousVerificationId != null) {
            PhoneVerification previous = findOwned(userId, previousVerificationId);
            if (previous.isVerified()) {
                throw new ApiException(ErrorCode.PHONE_ALREADY_VERIFIED);
            }
            previous.expire(now);
            resendCount = previous.getResendCount() + 1;
        }

        userRepository.findByPhoneNumber(phoneNumber)
                .filter(owner -> !owner.getId().equals(userId))
                .ifPresent(owner -> {
                    throw new ApiException(ErrorCode.PHONE_ALREADY_IN_USE);
                });

        verificationRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(last -> last.getCreatedAt().plus(RESEND_INTERVAL).isAfter(now))
                .ifPresent(last -> {
                    throw new ApiException(ErrorCode.SMS_SEND_LIMIT_EXCEEDED);
                });
        if (verificationRepository.countByPhoneNumberAndCreatedAtAfter(phoneNumber, now.minus(Duration.ofDays(1)))
                >= DAILY_SEND_LIMIT) {
            throw new ApiException(ErrorCode.SMS_SEND_LIMIT_EXCEEDED);
        }

        UUID id = UUID.randomUUID();
        // ponytail: SMS 사업자 미정이라 실제 발송 없이 고정 인증번호(sms.fake-code)를 쓴다. 사업자 연동 시 난수 생성 + 발송으로 교체.
        String code = fakeCode;
        PhoneVerification verification = verificationRepository.save(new PhoneVerification(
                id, userId, phoneNumber, purpose, hash(id, code), now, now.plus(CODE_VALIDITY), resendCount));
        recordOnboardingActivity(userId);

        return new SendResult(verification.getId(), verification.getExpiresAt(),
                CODE_VALIDITY.toSeconds(), now.plus(RESEND_INTERVAL));
    }

    /** 실패해도 시도 횟수가 저장되도록 ApiException은 롤백하지 않는다. */
    @Transactional(noRollbackFor = ApiException.class)
    public ConfirmResult confirm(UUID userId, UUID verificationId, String code) {
        PhoneVerification verification = findOwned(userId, verificationId);
        Instant now = Instant.now();
        recordOnboardingActivity(userId);

        if (verification.isVerified()) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_VERIFIED);
        }
        if (verification.isAttemptsExceeded()) {
            throw new ApiException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (verification.isExpired(now)) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (code == null || !MessageDigest.isEqual(
                hash(verificationId, code).getBytes(StandardCharsets.UTF_8),
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            verification.recordFailedAttempt();
            throw new ApiException(verification.isAttemptsExceeded()
                    ? ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED : ErrorCode.VERIFICATION_CODE_INVALID);
        }

        verification.markVerified(now);
        return new ConfirmResult(tokenProvider.createVerificationToken(userId, verification.getPhoneNumber()),
                verification.getPhoneNumber(), now);
    }

    private PhoneVerification findOwned(UUID userId, UUID verificationId) {
        return verificationRepository.findById(verificationId)
                .filter(v -> v.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.VERIFICATION_NOT_FOUND));
    }

    private void recordOnboardingActivity(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> user.recordOnboardingActivity());
    }

    /** 인증번호 원문은 저장하지 않는다. 인증 ID를 섞어 같은 번호라도 해시가 달라지게 한다. */
    private static String hash(UUID verificationId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((verificationId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record SendResult(UUID verificationId, Instant expiresAt, long remainingSeconds,
                             Instant resendAvailableAt) {
    }

    public record ConfirmResult(String verificationToken, String phoneNumber, Instant verifiedAt) {
    }
}
