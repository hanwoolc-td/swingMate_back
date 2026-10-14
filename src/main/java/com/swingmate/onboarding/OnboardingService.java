package com.swingmate.onboarding;

import com.swingmate.auth.AuthDtos.TokenResponse;
import com.swingmate.auth.AuthService;
import com.swingmate.auth.JwtTokenProvider;
import com.swingmate.auth.JwtTokenProvider.VerifiedPhone;
import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.onboarding.OnboardingDtos.DayAvailability;
import com.swingmate.onboarding.OnboardingDtos.MemberOnboardingRequest;
import com.swingmate.onboarding.OnboardingDtos.OnboardingResponse;
import com.swingmate.onboarding.OnboardingDtos.ProOnboardingRequest;
import com.swingmate.onboarding.OnboardingDtos.TimeRange;
import com.swingmate.onboarding.ProProfile.AvailabilitySlot;
import com.swingmate.phone.PhoneNumbers;
import com.swingmate.user.Role;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import com.swingmate.user.UserResponse;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    /** 한글 또는 영문 단어(공백 구분), 글자 수 2자 이상 */
    private static final Pattern NAME = Pattern.compile("[가-힣a-zA-Z]+( [가-힣a-zA-Z]+)*");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** 헷갈리는 0/O, 1/I 제외 */
    private static final String PRO_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PRO_CODE_LENGTH = 6;
    private static final int INTRODUCTION_MAX = 200;
    private static final int CAREER_MAX = 200;
    private static final int NAME_MAX = 50;

    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ProProfileRepository proProfileRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    public OnboardingService(UserRepository userRepository, MemberProfileRepository memberProfileRepository,
                             ProProfileRepository proProfileRepository, JwtTokenProvider tokenProvider,
                             AuthService authService) {
        this.userRepository = userRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.proProfileRepository = proProfileRepository;
        this.tokenProvider = tokenProvider;
        this.authService = authService;
    }

    @Transactional
    public UserResponse selectRole(UUID userId, Role role) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            throw new ApiException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        user.selectRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public OnboardingResponse completeMember(UUID userId, MemberOnboardingRequest request) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            return alreadyCompleted(user, Role.MEMBER);
        }
        String name = validateName(request.name());
        VerifiedPhone phone = verifiedPhone(userId, request.verificationToken(), request.phoneNumber());
        Set<LessonInterest> interests = new HashSet<>();
        if (request.lessonInterests() != null) {
            request.lessonInterests().stream().filter(Objects::nonNull).forEach(interests::add);
        }
        if (interests.isEmpty()) {
            throw new ApiException(ErrorCode.LESSON_INTEREST_REQUIRED);
        }
        if (request.golfExperience() == null) {
            throw new ApiException(ErrorCode.GOLF_EXPERIENCE_REQUIRED);
        }

        memberProfileRepository.save(new MemberProfile(userId, interests, request.golfExperience()));
        user.completeOnboarding(Role.MEMBER, name, phone.phoneNumber(), phone.verifiedAt());
        return completed(user, null);
    }

    @Transactional
    public OnboardingResponse completePro(UUID userId, ProOnboardingRequest request) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            return alreadyCompleted(user, Role.PRO);
        }
        String name = validateName(request.name());
        VerifiedPhone phone = verifiedPhone(userId, request.verificationToken(), request.phoneNumber());
        String introduction = request.introduction() == null ? "" : request.introduction().trim();
        if (introduction.isEmpty() || introduction.length() > INTRODUCTION_MAX) {
            throw new ApiException(ErrorCode.PRO_INTRODUCTION_REQUIRED);
        }
        List<String> careers = request.careers() == null ? List.of() : request.careers().stream()
                .filter(Objects::nonNull).map(String::trim).filter(c -> !c.isEmpty()).toList();
        if (careers.isEmpty() || careers.stream().anyMatch(c -> c.length() > CAREER_MAX)) {
            throw new ApiException(ErrorCode.PRO_CAREER_REQUIRED);
        }
        List<AvailabilitySlot> availability = validateAvailability(request.availability());

        ProProfile profile = proProfileRepository.save(
                new ProProfile(userId, introduction, newProCode(), careers, availability));
        user.completeOnboarding(Role.PRO, name, phone.phoneNumber(), phone.verifiedAt());
        return completed(user, profile.getProCode());
    }

    /** 가입 완료 요청 재시도는 같은 역할이면 새 토큰만 다시 발급한다(데이터 중복 생성 없음). */
    private OnboardingResponse alreadyCompleted(User user, Role role) {
        if (user.getRole() != role) {
            throw new ApiException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        String proCode = role == Role.PRO
                ? proProfileRepository.findById(user.getId()).map(ProProfile::getProCode).orElse(null)
                : null;
        return completed(user, proCode);
    }

    private OnboardingResponse completed(User user, String proCode) {
        TokenResponse tokens = authService.issueTokens(user.getId());
        return new OnboardingResponse(UserResponse.from(user), tokens.accessToken(), tokens.refreshToken(), proCode);
    }

    private VerifiedPhone verifiedPhone(UUID userId, String verificationToken, String rawPhoneNumber) {
        VerifiedPhone phone = verificationToken == null ? null
                : tokenProvider.parseVerificationToken(verificationToken)
                        .filter(p -> p.userId().equals(userId))
                        .orElse(null);
        if (phone == null) {
            throw new ApiException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }
        if (!phone.phoneNumber().equals(PhoneNumbers.normalize(rawPhoneNumber))) {
            throw new ApiException(ErrorCode.PHONE_NUMBER_MISMATCH);
        }
        userRepository.findByPhoneNumber(phone.phoneNumber())
                .filter(owner -> !owner.getId().equals(userId))
                .ifPresent(owner -> {
                    throw new ApiException(ErrorCode.PHONE_ALREADY_IN_USE);
                });
        return phone;
    }

    private static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!NAME.matcher(name).matches() || name.replace(" ", "").length() < 2 || name.length() > NAME_MAX) {
            throw new ApiException(ErrorCode.NAME_INVALID);
        }
        return name;
    }

    /** 요일 중복 없음, 요일마다 시간대 1개 이상, start < end, 같은 요일 시간대끼리 겹치지 않음. */
    private static List<AvailabilitySlot> validateAvailability(List<DayAvailability> days) {
        if (days == null || days.isEmpty()) {
            throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
        }
        Set<DayOfWeek> seenDays = new HashSet<>();
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (DayAvailability day : days) {
            if (day == null || day.timeRanges() == null || day.timeRanges().isEmpty()) {
                throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
            }
            DayOfWeek dayOfWeek = parseDay(day.dayOfWeek());
            if (!seenDays.add(dayOfWeek)) {
                throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
            }
            List<LocalTime[]> ranges = new ArrayList<>();
            for (TimeRange range : day.timeRanges()) {
                if (range == null) {
                    throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
                }
                LocalTime start = parseTime(range.startTime());
                LocalTime end = parseTime(range.endTime());
                if (!start.isBefore(end)) {
                    throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
                }
                ranges.add(new LocalTime[]{start, end});
            }
            ranges.sort(Comparator.comparing(r -> r[0]));
            for (int i = 1; i < ranges.size(); i++) {
                if (ranges.get(i)[0].isBefore(ranges.get(i - 1)[1])) {
                    throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
                }
            }
            ranges.forEach(r -> slots.add(new AvailabilitySlot(dayOfWeek, r[0], r[1])));
        }
        return slots;
    }

    private static DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ApiException(ErrorCode.AVAILABILITY_INVALID);
        }
    }

    private String newProCode() {
        // 32^6 ≈ 10억 조합이라 충돌은 드물다. 동시 생성 충돌은 DB 유니크 제약이 막는다.
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(PRO_CODE_LENGTH);
            for (int i = 0; i < PRO_CODE_LENGTH; i++) {
                code.append(PRO_CODE_CHARS.charAt(random.nextInt(PRO_CODE_CHARS.length())));
            }
            if (!proProfileRepository.existsByProCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("프로 코드 생성 실패");
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
