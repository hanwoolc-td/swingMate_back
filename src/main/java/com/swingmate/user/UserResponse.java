package com.swingmate.user;

import java.util.UUID;

public record UserResponse(
        UUID id,
        Role role,
        OnboardingStatus onboardingStatus,
        String name,
        String phoneNumber,
        String profileImageUrl
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getRole(), user.getOnboardingStatus(),
                user.getName(), user.getPhoneNumber(), user.getProfileImageUrl());
    }
}
