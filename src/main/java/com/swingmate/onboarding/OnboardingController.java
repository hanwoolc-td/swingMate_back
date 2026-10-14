package com.swingmate.onboarding;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.Role;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import com.swingmate.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final UserRepository userRepository;

    public OnboardingController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PatchMapping("/role")
    @Transactional
    public UserResponse selectRole(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody RoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (user.isOnboardingCompleted()) {
            throw new ApiException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        user.selectRole(request.role());
        return UserResponse.from(user);
    }

    public record RoleRequest(@NotNull Role role) {
    }
}
