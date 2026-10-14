package com.swingmate.member;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.onboarding.ProProfile;
import com.swingmate.onboarding.ProProfileRepository;
import com.swingmate.user.Role;
import com.swingmate.user.User;
import com.swingmate.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가입 완료 일반 회원이 프로 코드로 프로와 연결한다. 같은 프로에 다시 요청해도 결과는 같다(멱등). */
@RestController
@RequestMapping("/api/v1/members/me")
public class ProConnectionController {

    private final UserRepository userRepository;
    private final ProProfileRepository proProfileRepository;
    private final ProConnectionRepository connectionRepository;

    public ProConnectionController(UserRepository userRepository, ProProfileRepository proProfileRepository,
                                   ProConnectionRepository connectionRepository) {
        this.userRepository = userRepository;
        this.proProfileRepository = proProfileRepository;
        this.connectionRepository = connectionRepository;
    }

    @PostMapping("/pro-connection")
    @Transactional
    public ConnectionResponse connect(@AuthenticationPrincipal UUID memberId,
                                      @Valid @RequestBody ConnectionRequest request) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (member.getRole() != Role.MEMBER) {
            throw new ApiException(ErrorCode.PRO_CONNECTION_NOT_ALLOWED);
        }
        ProProfile profile = proProfileRepository.findByProCode(request.proCode().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorCode.PRO_CODE_NOT_FOUND));
        User pro = userRepository.findById(profile.getUserId())
                .filter(User::isOnboardingCompleted)
                .orElseThrow(() -> new ApiException(ErrorCode.PRO_CODE_NOT_FOUND));

        if (!connectionRepository.existsById(new ProConnection.Key(memberId, pro.getId()))) {
            connectionRepository.save(new ProConnection(memberId, pro.getId()));
        }
        return new ConnectionResponse(new ProSummary(pro.getId(), pro.getName(), pro.getProfileImageUrl()));
    }

    public record ConnectionRequest(@NotBlank String proCode) {
    }

    public record ConnectionResponse(ProSummary pro) {
    }

    public record ProSummary(UUID id, String name, String profileImageUrl) {
    }
}
