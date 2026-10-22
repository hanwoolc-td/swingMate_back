package com.swingmate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
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
    private static final String PHONE_CLAIM = "phone";
    private static final String SESSION_CLAIM = "sid";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, UUID sessionId) {
        return create(userId, TokenType.ACCESS, UUID.randomUUID(), sessionId, properties.accessTokenValidity());
    }

    public String createOnboardingToken(UUID userId) {
        return create(userId, TokenType.ONBOARDING, UUID.randomUUID(), UUID.randomUUID(), properties.onboardingTokenValidity());
    }

    /** 리프레시 토큰은 tokenId(jti)를 DB에 저장해 폐기·회전 여부를 판단한다. */
    public String createRefreshToken(UUID userId, UUID sessionId, UUID tokenId) {
        return create(userId, TokenType.REFRESH, tokenId, sessionId, properties.refreshTokenValidity());
    }

    public String createVerificationToken(UUID userId, String phoneNumber) {
        return builder(userId, TokenType.PHONE_VERIFICATION, UUID.randomUUID(), UUID.randomUUID(), properties.verificationTokenValidity())
                .claim(PHONE_CLAIM, phoneNumber)
                .compact();
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now().plus(properties.refreshTokenValidity());
    }

    /** 서명·만료·토큰 종류가 모두 맞을 때만 값을 반환한다. */
    public Optional<ParsedToken> parse(String token, TokenType expectedType) {
        return claims(token, expectedType)
                .map(c -> {
                    UUID tokenId = UUID.fromString(c.getId());
                    String session = c.get(SESSION_CLAIM, String.class);
                    return new ParsedToken(UUID.fromString(c.getSubject()), tokenId,
                            session == null ? tokenId : UUID.fromString(session));
                });
    }

    public Optional<VerifiedPhone> parseVerificationToken(String token) {
        return claims(token, TokenType.PHONE_VERIFICATION)
                .map(c -> new VerifiedPhone(UUID.fromString(c.getSubject()), c.get(PHONE_CLAIM, String.class),
                        c.getIssuedAt().toInstant()));
    }

    private Optional<Claims> claims(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return expectedType.name().equals(claims.get(TYPE_CLAIM, String.class))
                    ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String create(UUID userId, TokenType type, UUID tokenId, UUID sessionId, Duration validity) {
        return builder(userId, type, tokenId, sessionId, validity).compact();
    }

    private JwtBuilder builder(UUID userId, TokenType type, UUID tokenId, UUID sessionId, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(TYPE_CLAIM, type.name())
                .claim(SESSION_CLAIM, sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key);
    }

    public record ParsedToken(UUID userId, UUID tokenId, UUID sessionId) {
    }

    /** verifiedAt은 토큰 발급 시각(= SMS 인증 성공 시각)이다. */
    public record VerifiedPhone(UUID userId, String phoneNumber, Instant verifiedAt) {
    }
}
