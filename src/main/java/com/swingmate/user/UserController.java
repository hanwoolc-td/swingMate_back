package com.swingmate.user;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.onboarding.ProProfile;
import com.swingmate.onboarding.ProProfileRepository;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final ProProfileRepository proProfileRepository;

    public UserController(UserRepository userRepository, ProProfileRepository proProfileRepository) {
        this.userRepository = userRepository;
        this.proProfileRepository = proProfileRepository;
    }

    /** 앱 재실행 시 사용자·역할·온보딩 상태 확인. 프로는 앱을 다시 깔아도 자기 프로 코드를 다시 볼 수 있다. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        String proCode = user.getRole() == Role.PRO
                ? proProfileRepository.findById(userId).map(ProProfile::getProCode).orElse(null)
                : null;
        return UserResponse.withProCode(user, proCode);
    }
}
