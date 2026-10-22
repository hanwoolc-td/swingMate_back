package com.swingmate.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** oauth.mock-enabled=true 상태에서 idToken 문자열이 곧 공급자 사용자 ID가 된다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void newUserGetsOnboardingTokenAndCanSelectRoleOnce() throws Exception {
        String kakaoId = "kakao-" + UUID.randomUUID();

        JsonNode first = json(login("KAKAO", kakaoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.onboardingStatus").value("PENDING"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()));
        String onboardingToken = first.path("onboardingToken").asText();

        // 온보딩 토큰으로 역할 선택은 되지만, 가입 완료 전용 API는 막힌다.
        mvc.perform(patch("/api/v1/onboarding/role").header("Authorization", "Bearer " + onboardingToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRO"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + onboardingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("PENDING"));
        mvc.perform(get("/api/v1/something-else").header("Authorization", "Bearer " + onboardingToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/phone-verifications/not-a-uuid/confirm")
                        .header("Authorization", "Bearer " + onboardingToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 같은 계정으로 다시 로그인하면 기존 사용자다.
        login("KAKAO", kakaoId).andExpect(jsonPath("$.isNewUser").value(false));

        // 가입 완료 후에는 역할을 바꿀 수 없다.
        completeOnboarding(first);
        mvc.perform(patch("/api/v1/onboarding/role").header("Authorization", "Bearer " + onboardingToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"));
    }

    @Test
    void completedUserGetsTokensAndRefreshRotates() throws Exception {
        String googleId = "google-" + UUID.randomUUID();
        completeOnboarding(json(login("GOOGLE", googleId)));

        JsonNode loggedIn = json(login("GOOGLE", googleId)
                .andExpect(jsonPath("$.onboardingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.onboardingToken").doesNotExist())
                .andExpect(jsonPath("$.user.id").exists()));
        String accessToken = loggedIn.path("accessToken").asText();
        String refreshToken = loggedIn.path("refreshToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/no-such-api").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // 액세스 토큰으로 온보딩 API는 호출할 수 없다.
        mvc.perform(patch("/api/v1/onboarding/role").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());

        JsonNode rotated = json(refresh(refreshToken).andExpect(status().isOk()));
        // 이미 쓴 리프레시 토큰은 재사용할 수 없다.
        refresh(refreshToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        String newRefresh = rotated.path("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", newRefresh)))
                .andExpect(status().isNoContent());
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingOrBadTokens() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").exists());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        // Spring MVC 예외는 500이 아니라 알맞은 4xx로 내려간다.
        mvc.perform(get("/api/v1/auth/social/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/auth/social/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"NAVER\",\"idToken\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deviceSessionSurvivesRefreshAndRevocationImmediatelyBlocksAccess() throws Exception {
        String googleId = "session-" + UUID.randomUUID();
        completeOnboarding(json(login("GOOGLE", googleId)));
        String deviceId = UUID.randomUUID().toString();
        JsonNode firstLogin = loginWithDevice(googleId, deviceId);
        JsonNode loggedIn = loginWithDevice(googleId, deviceId);
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + firstLogin.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        String accessToken = loggedIn.path("accessToken").asText();
        String refreshToken = loggedIn.path("refreshToken").asText();

        JsonNode sessions = json(mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(2, sessions.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from refresh_tokens where device_id = ?",
                Integer.class, UUID.fromString(deviceId)));
        JsonNode currentSession = null;
        for (JsonNode item : sessions) if (item.path("current").asBoolean()) currentSession = item;
        org.junit.jupiter.api.Assertions.assertNotNull(currentSession);
        org.junit.jupiter.api.Assertions.assertEquals("Pixel 9", currentSession.path("deviceName").asText());
        String sessionId = currentSession.path("id").asText();

        JsonNode rotated = json(refresh(refreshToken).andExpect(status().isOk()));
        JsonNode afterRefresh = json(mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk()));
        JsonNode currentAfterRefresh = null;
        for (JsonNode item : afterRefresh) if (item.path("current").asBoolean()) currentAfterRefresh = item;
        org.junit.jupiter.api.Assertions.assertNotNull(currentAfterRefresh);
        org.junit.jupiter.api.Assertions.assertEquals(sessionId, currentAfterRefresh.path("id").asText());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/auth/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        refresh(rotated.path("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentLoginOnSameDeviceLeavesOneSession() throws Exception {
        String googleId = "concurrent-session-" + UUID.randomUUID();
        completeOnboarding(json(login("GOOGLE", googleId)));
        String deviceId = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> {
                start.await();
                return loginWithDevice(googleId, deviceId);
            });
            var second = executor.submit(() -> {
                start.await();
                return loginWithDevice(googleId, deviceId);
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "select count(*) from refresh_tokens where device_id = ?",
                    Integer.class, UUID.fromString(deviceId)));
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode loginWithDevice(String googleId, String deviceId) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/social/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GOOGLE\",\"idToken\":\"" + googleId
                                + "\",\"deviceId\":\"" + deviceId
                                + "\",\"deviceName\":\"Pixel 9\",\"platform\":\"ANDROID\",\"appVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk()));
    }

    private ResultActions login(String provider, String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/social/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"" + provider + "\",\"idToken\":\"" + token + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body("refreshToken", refreshToken)));
    }

    /** 토큰 흐름만 검증하려고 SMS·온보딩 절차 대신 DB를 직접 갱신한다. */
    private void completeOnboarding(JsonNode loginResponse) throws Exception {
        String token = loginResponse.path("onboardingToken").asText();
        String userId = json(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)))
                .path("id").asText();
        jdbc.update("update users set onboarding_status = 'COMPLETED', role = 'MEMBER' where id = ?",
                UUID.fromString(userId));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String body(String key, String value) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(key, value));
    }
}
