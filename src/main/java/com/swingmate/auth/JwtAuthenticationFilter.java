package com.swingmate.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰을 읽어 인증 주체(userId)를 설정한다.
 * 액세스 토큰은 TOKEN_ACCESS, 온보딩 토큰은 TOKEN_ONBOARDING 권한을 받는다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_AUTHORITY = "TOKEN_ACCESS";
    public static final String ONBOARDING_AUTHORITY = "TOKEN_ONBOARDING";

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository) {
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            tokenProvider.parse(token, TokenType.ACCESS)
                    .ifPresentOrElse(
                            parsed -> {
                                boolean active = refreshTokenRepository.findById(parsed.sessionId())
                                        .filter(row -> row.belongsTo(parsed.userId()))
                                        .filter(row -> row.getExpiresAt().isAfter(java.time.Instant.now()))
                                        .isPresent();
                                if (active) authenticate(parsed, ACCESS_AUTHORITY);
                            },
                            () -> tokenProvider.parse(token, TokenType.ONBOARDING)
                                    .ifPresent(parsed -> authenticate(parsed, ONBOARDING_AUTHORITY)));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(JwtTokenProvider.ParsedToken parsed, String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(
                parsed.userId(), null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
