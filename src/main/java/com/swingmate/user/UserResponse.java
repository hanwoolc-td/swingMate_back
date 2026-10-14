package com.swingmate.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        Role role,
        OnboardingStatus onboardingStatus,
        String name,
        String phoneNumber,
        String profileImageUrl,
        /** 프로 본인에게만 내려간다. 회원에게 공유할 연결 코드. */
        String proCode
) {

    public static UserResponse from(User user) {
        return withProCode(user, null);
    }

    public static UserResponse withProCode(User user, String proCode) {
        return new UserResponse(user.getId(), user.getRole(), user.getOnboardingStatus(),
                user.getName(), user.getPhoneNumber(), user.getProfileImageUrl(), proCode);
    }
}
