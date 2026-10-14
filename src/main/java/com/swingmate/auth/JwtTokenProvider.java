package com.swingmate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "typ";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId) {
        return create(userId, TokenType.ACCESS, UUID.randomUUID(), properties.accessTokenValidity());
    }

    public String createOnboardingToken(UUID userId) {
        return create(userId, TokenType.ONBOARDING, UUID.randomUUID(), properties.onboardingTokenValidity());
    }

    /** 리프레시 토큰은 tokenId(jti)를 DB에 저장해 폐기·회전 여부를 판단한다. */
    public String createRefreshToken(UUID userId, UUID tokenId) {
        return create(userId, TokenType.REFRESH, tokenId, properties.refreshTokenValidity());
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now().plus(properties.refreshTokenValidity());
    }

    /** 서명·만료·토큰 종류가 모두 맞을 때만 값을 반환한다. */
    public Optional<ParsedToken> parse(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.name().equals(claims.get(TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId())));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String create(UUID userId, TokenType type, UUID tokenId, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(TYPE_CLAIM, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    public record ParsedToken(UUID userId, UUID tokenId) {
    }
}
