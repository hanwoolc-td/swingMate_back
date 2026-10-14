package com.swingmate.auth;

import com.swingmate.auth.AuthDtos.RefreshRequest;
import com.swingmate.auth.AuthDtos.SocialLoginRequest;
import com.swingmate.auth.AuthDtos.SocialLoginResponse;
import com.swingmate.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/social/login")
    public SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.socialLogin(request.provider(), request.idToken());
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
}
