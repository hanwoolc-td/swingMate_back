package com.swingmate.onboarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swingmate.user.UserResponse;
import java.util.List;
import java.util.Set;

/** 필드별 오류 코드(명세서 5.5, 5.6)를 돌려주기 위해 값 검증은 OnboardingService에서 한다. */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record MemberOnboardingRequest(
            String verificationToken,
            String name,
            String phoneNumber,
            Set<LessonInterest> lessonInterests,
            GolfExperience golfExperience
    ) {
    }

    public record ProOnboardingRequest(
            String verificationToken,
            String name,
            String phoneNumber,
            String introduction,
            List<String> careers,
            List<DayAvailability> availability
    ) {
    }

    /** dayOfWeek: MONDAY~SUNDAY, 시간: HH:mm */
    public record DayAvailability(String dayOfWeek, List<TimeRange> timeRanges) {
    }

    public record TimeRange(String startTime, String endTime) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OnboardingResponse(UserResponse user, String accessToken, String refreshToken) {
    }
}
