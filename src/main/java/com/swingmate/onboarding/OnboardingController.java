package com.swingmate.onboarding;

import com.swingmate.onboarding.OnboardingDtos.MemberOnboardingRequest;
import com.swingmate.onboarding.OnboardingDtos.OnboardingResponse;
import com.swingmate.onboarding.OnboardingDtos.ProOnboardingRequest;
import com.swingmate.user.Role;
import com.swingmate.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩 토큰으로만 호출할 수 있다. 가입 완료 요청은 SMS 인증 후 받은 verificationToken을 본문에 담는다. */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PatchMapping("/role")
    public UserResponse selectRole(@AuthenticationPrincipal UUID userId, @Valid @RequestBody RoleRequest request) {
        return onboardingService.selectRole(userId, request.role());
    }

    @PutMapping("/member")
    public OnboardingResponse completeMember(@AuthenticationPrincipal UUID userId,
                                             @RequestBody MemberOnboardingRequest request) {
        return onboardingService.completeMember(userId, request);
    }

    @PutMapping("/pro")
    public OnboardingResponse completePro(@AuthenticationPrincipal UUID userId,
                                          @RequestBody ProOnboardingRequest request) {
        return onboardingService.completePro(userId, request);
    }

    public record RoleRequest(@NotNull Role role) {
    }
}
