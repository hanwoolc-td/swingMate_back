package com.swingmate.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swingmate.user.OnboardingStatus;
import com.swingmate.user.SocialProvider;
import com.swingmate.user.UserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** 카카오는 Access Token, Google은 ID Token을 idToken에 담아 보낸다. */
    public record SocialLoginRequest(@NotNull SocialProvider provider, @NotBlank String idToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SocialLoginResponse(
            boolean isNewUser,
            OnboardingStatus onboardingStatus,
            String accessToken,
            String refreshToken,
            String onboardingToken,
            UserResponse user
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken) {
    }
}
