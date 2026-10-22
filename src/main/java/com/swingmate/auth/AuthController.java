package com.swingmate.auth;

import com.swingmate.auth.AuthDtos.RefreshRequest;
import com.swingmate.auth.AuthDtos.SocialLoginRequest;
import com.swingmate.auth.AuthDtos.SocialLoginResponse;
import com.swingmate.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import java.util.UUID;
import com.swingmate.auth.AuthDtos.DeviceSessionResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/social/login")
    public SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.socialLogin(request.provider(), request.idToken(), request.deviceId(), request.deviceName(),
                request.platform(), request.appVersion());
    }

    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public List<DeviceSessionResponse> sessions(@AuthenticationPrincipal UUID userId,
                                                @RequestHeader("Authorization") String authorization) {
        return authService.sessions(userId, authorization.substring("Bearer ".length()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal UUID userId,
                                              @PathVariable UUID sessionId) {
        authService.revokeSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal UUID userId) {
        authService.revokeAllSessions(userId);
        return ResponseEntity.noContent().build();
    }
}
