package com.swingmate.phone;

import com.swingmate.phone.PhoneVerificationService.ConfirmResult;
import com.swingmate.phone.PhoneVerificationService.SendResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩 토큰으로만 호출할 수 있다. 재발송은 previousVerificationId를 함께 보낸다. */
@RestController
@RequestMapping("/api/v1/phone-verifications")
public class PhoneVerificationController {

    private final PhoneVerificationService service;

    public PhoneVerificationController(PhoneVerificationService service) {
        this.service = service;
    }

    @PostMapping
    public SendResult send(@AuthenticationPrincipal UUID userId, @Valid @RequestBody SendRequest request) {
        return service.send(userId, request.phoneNumber(), request.purpose(), request.previousVerificationId());
    }

    @PostMapping("/{verificationId}/confirm")
    public ConfirmResult confirm(@AuthenticationPrincipal UUID userId, @PathVariable UUID verificationId,
                                 @Valid @RequestBody ConfirmRequest request) {
        return service.confirm(userId, verificationId, request.code());
    }

    public record SendRequest(
            @NotBlank String phoneNumber,
            @Pattern(regexp = "SIGNUP_PHONE_VERIFICATION") @NotBlank String purpose,
            UUID previousVerificationId
    ) {
    }

    public record ConfirmRequest(@Pattern(regexp = "\\d{6}") @NotBlank String code) {
    }
}
