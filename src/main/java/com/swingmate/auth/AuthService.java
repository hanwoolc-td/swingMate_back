package com.swingmate.auth;

import com.swingmate.auth.AuthDtos.SocialLoginResponse;
import com.swingmate.auth.AuthDtos.TokenResponse;
import com.swingmate.auth.AuthDtos.DeviceSessionResponse;
import com.swingmate.auth.social.SocialTokenVerifier;
import com.swingmate.auth.social.SocialUserInfo;
import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.SocialAccount;
import com.swingmate.user.SocialAccountRepository;
import com.swingmate.user.SocialProvider;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import com.swingmate.user.UserResponse;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SocialTokenVerifier socialTokenVerifier;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;

    public AuthService(SocialTokenVerifier socialTokenVerifier, UserRepository userRepository,
                       SocialAccountRepository socialAccountRepository,
                       RefreshTokenRepository refreshTokenRepository, JwtTokenProvider tokenProvider) {
        this.socialTokenVerifier = socialTokenVerifier;
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 가입 완료 사용자는 액세스·리프레시 토큰을, 신규 또는 가입 미완료 사용자는 온보딩 토큰을 받는다.
     */
    @Transactional
    public SocialLoginResponse socialLogin(SocialProvider provider, String token,
                                           String deviceId, String deviceName, String platform, String appVersion) {
        SocialUserInfo info = socialTokenVerifier.verify(provider, token);

        var existing = socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
        boolean isNewUser = existing.isEmpty();
        // ponytail: 같은 계정의 동시 첫 로그인은 유니크 제약으로 한쪽이 실패한다. 앱이 재시도하면 기존 사용자로 로그인된다.
        User user = existing
                .map(account -> userRepository.findById(account.getUserId())
                        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND)))
                .orElseGet(() -> register(provider, info));

        if (!user.isOnboardingCompleted()) {
            user.recordOnboardingActivity();
            return new SocialLoginResponse(isNewUser, user.getOnboardingStatus(), null, null,
                    tokenProvider.createOnboardingToken(user.getId()), null);
        }
        TokenResponse tokens = issueTokens(user.getId(), deviceId, deviceName, platform, appVersion);
        return new SocialLoginResponse(isNewUser, user.getOnboardingStatus(), tokens.accessToken(),
                tokens.refreshToken(), null, UserResponse.from(user));
    }

    /** 리프레시 토큰을 회전한다. 한 번 사용한 리프레시 토큰은 다시 쓸 수 없다. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        var parsed = tokenProvider.parse(refreshToken, TokenType.REFRESH)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));
        RefreshToken session = refreshTokenRepository.findByIdForUpdate(parsed.sessionId())
                .filter(row -> row.matches(parsed.userId(), parsed.tokenId()))
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));
        UUID nextTokenId = UUID.randomUUID();
        session.rotate(nextTokenId, tokenProvider.refreshTokenExpiresAt());
        refreshTokenRepository.save(session);
        return new TokenResponse(tokenProvider.createAccessToken(parsed.userId(), parsed.sessionId()),
                tokenProvider.createRefreshToken(parsed.userId(), parsed.sessionId(), nextTokenId));
    }

    @Transactional
    public List<DeviceSessionResponse> sessions(UUID userId, String accessToken) {
        UUID currentSessionId = tokenProvider.parse(accessToken, TokenType.ACCESS)
                .map(JwtTokenProvider.ParsedToken::sessionId).orElse(null);
        return refreshTokenRepository.findAllByUserIdOrderByLastUsedAtDesc(userId).stream()
                .map(row -> new DeviceSessionResponse(row.getId().toString(), row.getDeviceName(),
                        row.getPlatform(), row.getAppVersion(), row.getCreatedAt(), row.getLastUsedAt(),
                        row.getId().equals(currentSessionId)))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        refreshTokenRepository.deleteByIdAndUserId(sessionId, userId);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    /** 가입 완료 사용자에게 로그인 세션을 발급한다. 기기 정보는 클라이언트가 제공한다. */
    @Transactional
    public TokenResponse issueTokens(UUID userId) {
        return issueTokens(userId, UUID.randomUUID().toString(), "알 수 없는 기기", "UNKNOWN", null);
    }

    @Transactional
    public TokenResponse issueTokens(UUID userId, String deviceName, String platform, String appVersion) {
        return issueTokens(userId, UUID.randomUUID().toString(), deviceName, platform, appVersion);
    }

    @Transactional
    public TokenResponse issueTokens(UUID userId, String deviceIdValue, String deviceName,
                                     String platform, String appVersion) {
        // Serialize session replacement for a user. Concurrent login requests from the same
        // installation must not both observe the old row and insert a duplicate user/device pair.
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        UUID sessionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID deviceId;
        try {
            deviceId = deviceIdValue == null ? UUID.randomUUID() : UUID.fromString(deviceIdValue);
        } catch (IllegalArgumentException invalidDeviceId) {
            deviceId = UUID.randomUUID();
        }
        refreshTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId);
        String safeName = deviceName == null || deviceName.isBlank() ? "알 수 없는 기기" : deviceName.trim();
        safeName = safeName.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (safeName.isEmpty()) safeName = "알 수 없는 기기";
        String requestedPlatform = platform == null ? "UNKNOWN" : platform.trim().toUpperCase(Locale.ROOT);
        String safePlatform = List.of("IOS", "ANDROID", "WEB", "UNKNOWN").contains(requestedPlatform)
                ? requestedPlatform : "UNKNOWN";
        if (safeName.length() > 120) safeName = safeName.substring(0, 120);
        String safeVersion = appVersion == null || appVersion.isBlank() ? null : appVersion.trim();
        if (safeVersion != null && safeVersion.length() > 40) safeVersion = safeVersion.substring(0, 40);
        refreshTokenRepository.save(new RefreshToken(sessionId, tokenId, userId, deviceId,
                tokenProvider.refreshTokenExpiresAt(), safeName, safePlatform,
                safeVersion));
        return new TokenResponse(tokenProvider.createAccessToken(userId, sessionId),
                tokenProvider.createRefreshToken(userId, sessionId, tokenId));
    }

    /** 이미 폐기됐거나 형식이 잘못된 토큰도 성공으로 처리한다(멱등). */
    @Transactional
    public void logout(String refreshToken) {
        tokenProvider.parse(refreshToken, TokenType.REFRESH)
                .ifPresent(parsed -> refreshTokenRepository.deleteByIdAndTokenIdReturningCount(
                        parsed.sessionId(), parsed.tokenId()));
    }

    private User register(SocialProvider provider, SocialUserInfo info) {
        User user = userRepository.save(User.pending());
        socialAccountRepository.save(new SocialAccount(user.getId(), provider, info.providerUserId(), info.email()));
        return user;
    }

}
