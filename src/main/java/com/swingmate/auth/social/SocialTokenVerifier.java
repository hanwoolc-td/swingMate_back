package com.swingmate.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.SocialProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 앱이 받은 소셜 토큰을 공급자 서버에 확인한다.
 * 다른 앱에서 발급된 토큰을 막기 위해 카카오 앱 ID와 Google 클라이언트 ID(aud)를 반드시 비교한다.
 */
@Component
public class SocialTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(SocialTokenVerifier.class);
    private static final List<String> GOOGLE_ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final OAuthProperties properties;
    private final RestClient restClient;

    public SocialTokenVerifier(OAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public SocialUserInfo verify(SocialProvider provider, String token) {
        if (properties.mockEnabled()) {
            // 로컬 개발 전용: 토큰 문자열을 그대로 공급자 사용자 ID로 쓴다.
            return new SocialUserInfo(token, null);
        }
        try {
            return switch (provider) {
                case KAKAO -> verifyKakao(token);
                case GOOGLE -> verifyGoogle(token);
            };
        } catch (HttpClientErrorException e) {
            throw invalid();
        }
    }

    private SocialUserInfo verifyKakao(String accessToken) {
        String appId = properties.kakao() == null ? null : properties.kakao().appId();
        if (appId == null || appId.isBlank()) {
            log.warn("oauth.kakao.app-id가 설정되지 않아 카카오 로그인을 거부합니다.");
            throw invalid();
        }
        JsonNode tokenInfo = restClient.get()
                .uri("https://kapi.kakao.com/v1/user/access_token_info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        if (tokenInfo == null || !appId.equals(tokenInfo.path("app_id").asText())) {
            throw invalid();
        }
        JsonNode me = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        String id = me == null ? "" : me.path("id").asText();
        if (id.isEmpty()) {
            throw invalid();
        }
        return new SocialUserInfo(id, textOrNull(me.path("kakao_account").path("email")));
    }

    private SocialUserInfo verifyGoogle(String idToken) {
        List<String> clientIds = properties.google() == null ? null : properties.google().clientIds();
        if (clientIds == null || clientIds.isEmpty()) {
            log.warn("oauth.google.client-ids가 설정되지 않아 Google 로그인을 거부합니다.");
            throw invalid();
        }
        // ponytail: tokeninfo 엔드포인트로 서명·만료 검증을 Google에 맡긴다. 호출량이 많아지면 JWKS 로컬 검증으로 교체.
        JsonNode info = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve()
                .body(JsonNode.class);
        if (info == null
                || !clientIds.contains(info.path("aud").asText())
                || !GOOGLE_ISSUERS.contains(info.path("iss").asText())
                || info.path("sub").asText().isEmpty()) {
            throw invalid();
        }
        String email = "true".equals(info.path("email_verified").asText()) ? textOrNull(info.path("email")) : null;
        return new SocialUserInfo(info.path("sub").asText(), email);
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isEmpty() ? null : node.asText();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.SOCIAL_TOKEN_INVALID);
    }
}
