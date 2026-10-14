package com.swingmate.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.swingmate.common.ApiException;
import com.swingmate.common.ErrorCode;
import com.swingmate.user.SocialProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 카카오·Google 응답 형태로 앱 ID·aud·iss 검증이 동작하는지 확인한다. */
class SocialTokenVerifierTest {

    private static final String KAKAO_INFO = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String KAKAO_ME = "https://kapi.kakao.com/v2/user/me";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SocialTokenVerifier verifier = new SocialTokenVerifier(
            new OAuthProperties(false, new OAuthProperties.Kakao("1234"),
                    new OAuthProperties.Google(List.of("my-client-id"))),
            builder.build());

    @Test
    void kakaoTokenFromOurAppIsAccepted() {
        server.expect(requestTo(KAKAO_INFO)).andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(json("{\"id\":987,\"app_id\":1234}"));
        server.expect(requestTo(KAKAO_ME))
                .andRespond(json("{\"id\":987,\"kakao_account\":{\"email\":\"a@b.com\"}}"));

        assertThat(verifier.verify(SocialProvider.KAKAO, "kakao-token"))
                .isEqualTo(new SocialUserInfo("987", "a@b.com"));
    }

    @Test
    void kakaoTokenFromAnotherAppIsRejected() {
        server.expect(requestTo(KAKAO_INFO)).andRespond(json("{\"id\":987,\"app_id\":9999}"));
        assertError(SocialProvider.KAKAO, "kakao-token", ErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void expiredKakaoTokenIsRejected() {
        server.expect(requestTo(KAKAO_INFO)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertError(SocialProvider.KAKAO, "kakao-token", ErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void providerOutageIsNotReportedAsBadToken() {
        server.expect(requestTo(KAKAO_INFO)).andRespond(withServerError());
        assertError(SocialProvider.KAKAO, "kakao-token", ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void googleTokenChecksAudienceAndIssuer() {
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=g-token";
        server.expect(requestTo(url)).andRespond(json(
                "{\"aud\":\"my-client-id\",\"iss\":\"https://accounts.google.com\",\"sub\":\"g-1\","
                        + "\"email\":\"g@b.com\",\"email_verified\":\"true\"}"));
        assertThat(verifier.verify(SocialProvider.GOOGLE, "g-token")).isEqualTo(new SocialUserInfo("g-1", "g@b.com"));

        server.reset();
        server.expect(requestTo(url)).andRespond(json(
                "{\"aud\":\"someone-else\",\"iss\":\"https://accounts.google.com\",\"sub\":\"g-1\"}"));
        assertError(SocialProvider.GOOGLE, "g-token", ErrorCode.SOCIAL_TOKEN_INVALID);

        server.reset();
        server.expect(requestTo(url)).andRespond(json(
                "{\"aud\":\"my-client-id\",\"iss\":\"evil.example.com\",\"sub\":\"g-1\"}"));
        assertError(SocialProvider.GOOGLE, "g-token", ErrorCode.SOCIAL_TOKEN_INVALID);
    }

    @Test
    void unverifiedGoogleEmailIsNotStored() {
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=g-token")).andRespond(json(
                "{\"aud\":\"my-client-id\",\"iss\":\"accounts.google.com\",\"sub\":\"g-1\","
                        + "\"email\":\"g@b.com\",\"email_verified\":\"false\"}"));
        assertThat(verifier.verify(SocialProvider.GOOGLE, "g-token").email()).isNull();
    }

    @Test
    void missingConfigurationRejectsInsteadOfTrustingAnyApp() {
        var unconfigured = new SocialTokenVerifier(new OAuthProperties(false, null, null), builder.build());
        assertThatThrownBy(() -> unconfigured.verify(SocialProvider.KAKAO, "t"))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID);
        assertThatThrownBy(() -> unconfigured.verify(SocialProvider.GOOGLE, "t"))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID);
    }

    private void assertError(SocialProvider provider, String token, ErrorCode expected) {
        assertThatThrownBy(() -> verifier.verify(provider, token))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(expected);
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
