package com.swingmate.onboarding;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 테스트 인증번호는 sms.fake-code 기본값 123456이다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingFlowTest {

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(10000000);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void memberSignsUpAndConnectsToPro() throws Exception {
        // 프로 가입
        String proToken = onboardingToken();
        String proPhone = newPhone();
        JsonNode pro = json(put("/api/v1/onboarding/pro", proToken, Map.of(
                "verificationToken", verify(proToken, proPhone),
                "name", "김프로",
                "phoneNumber", proPhone,
                "introduction", "KPGA 투어 프로입니다.",
                "careers", List.of("KPGA 투어 프로", " "),
                "availability", List.of(Map.of("dayOfWeek", "MONDAY", "timeRanges", List.of(
                        Map.of("startTime", "09:00", "endTime", "13:00"),
                        Map.of("startTime", "18:00", "endTime", "22:00"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PRO"))
                .andExpect(jsonPath("$.user.onboardingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.proCode").isString()));
        String proCode = pro.path("proCode").asText();
        // 프로는 나중에 내 정보에서 코드를 다시 볼 수 있다.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + pro.path("accessToken").asText()))
                .andExpect(jsonPath("$.proCode").value(proCode));

        // 회원 가입 (하이픈 포함 번호도 같은 번호로 취급)
        String memberToken = onboardingToken();
        String memberPhone = newPhone();
        Map<String, Object> memberBody = Map.of(
                "verificationToken", verify(memberToken, memberPhone),
                "name", "홍길동",
                "phoneNumber", memberPhone.substring(0, 3) + "-" + memberPhone.substring(3, 7) + "-"
                        + memberPhone.substring(7),
                "lessonInterests", List.of("BEGINNER", "PUTTING"),
                "golfExperience", "UNDER_6_MONTHS");
        JsonNode member = json(put("/api/v1/onboarding/member", memberToken, memberBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("MEMBER"))
                .andExpect(jsonPath("$.user.phoneNumber").value(memberPhone))
                .andExpect(jsonPath("$.proCode").doesNotExist())
                .andExpect(jsonPath("$.user.proCode").doesNotExist()));
        // 같은 요청 재시도는 성공하고 토큰만 새로 나온다.
        put("/api/v1/onboarding/member", memberToken, memberBody).andExpect(status().isOk());

        String access = member.path("accessToken").asText();
        for (int i = 0; i < 2; i++) { // 두 번 연결해도 결과는 같다(멱등).
            post("/api/v1/members/me/pro-connection", access, Map.of("proCode", proCode.toLowerCase()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pro.name").value("김프로"));
        }
        post("/api/v1/members/me/pro-connection", access, Map.of("proCode", "ZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRO_CODE_NOT_FOUND"));
        // 프로는 다른 프로와 연결할 수 없다.
        post("/api/v1/members/me/pro-connection", pro.path("accessToken").asText(), Map.of("proCode", proCode))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRO_CONNECTION_NOT_ALLOWED"));

        // 이미 가입에 쓰인 번호는 다른 계정이 인증받을 수 없다.
        post("/api/v1/phone-verifications", onboardingToken(), sendBody(memberPhone))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_IN_USE"));
    }

    @Test
    void smsVerificationRules() throws Exception {
        String token = onboardingToken();
        String phone = newPhone();
        String id = json(post("/api/v1/phone-verifications", token, sendBody(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(180))
                .andExpect(jsonPath("$.resendAvailableAt").exists()))
                .path("verificationId").asText();

        // 1분 안에 같은 번호로 다시 요청하면 제한된다.
        post("/api/v1/phone-verifications", token, sendBody(phone))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_SEND_LIMIT_EXCEEDED"));

        // 다른 사용자는 남의 인증 요청을 확인할 수 없다.
        confirm(onboardingToken(), id, "123456").andExpect(status().isNotFound());

        for (int i = 1; i < 5; i++) {
            confirm(token, id, "000000").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
        }
        confirm(token, id, "000000").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
        // 횟수를 넘긴 뒤에는 맞는 번호도 거절된다.
        confirm(token, id, "123456").andExpect(status().isTooManyRequests());

        // 인증 성공 후에는 같은 요청을 다시 쓸 수 없다.
        String token2 = onboardingToken();
        String phone2 = newPhone();
        String id2 = json(post("/api/v1/phone-verifications", token2, sendBody(phone2))).path("verificationId").asText();
        confirm(token2, id2, "123456").andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").exists())
                .andExpect(jsonPath("$.phoneNumber").value(phone2));
        confirm(token2, id2, "123456").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_VERIFIED"));

        post("/api/v1/phone-verifications", token2, sendBody("010-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NUMBER_INVALID"));
    }

    @Test
    void onboardingValidationErrors() throws Exception {
        String token = onboardingToken();
        String phone = newPhone();
        String verification = verify(token, phone);

        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "홍길동",
                "phoneNumber", phone, "lessonInterests", List.of(), "golfExperience", "UNDER_6_MONTHS"))
                .andExpect(jsonPath("$.code").value("LESSON_INTEREST_REQUIRED"));
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "홍길동",
                "phoneNumber", phone, "lessonInterests", List.of("IRON")))
                .andExpect(jsonPath("$.code").value("GOLF_EXPERIENCE_REQUIRED"));
        // 관심 분야 필드를 아예 빼도 500이 아니라 400이다.
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "홍길동",
                "phoneNumber", phone, "golfExperience", "UNDER_6_MONTHS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LESSON_INTEREST_REQUIRED"));
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "가".repeat(51),
                "phoneNumber", phone))
                .andExpect(jsonPath("$.code").value("NAME_INVALID"));
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "홍1",
                "phoneNumber", phone))
                .andExpect(jsonPath("$.code").value("NAME_INVALID"));
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", verification, "name", "홍길동",
                "phoneNumber", newPhone()))
                .andExpect(jsonPath("$.code").value("PHONE_NUMBER_MISMATCH"));
        put("/api/v1/onboarding/member", token, Map.of("verificationToken", "bad", "name", "홍길동",
                "phoneNumber", phone))
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
        // 다른 사용자의 인증 결과는 쓸 수 없다.
        put("/api/v1/onboarding/member", onboardingToken(), Map.of("verificationToken", verification,
                "name", "홍길동", "phoneNumber", phone))
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        Map<String, Object> pro = Map.of("verificationToken", verification, "name", "Kim Pro",
                "phoneNumber", phone, "introduction", "소개", "careers", List.of("코치"));
        put("/api/v1/onboarding/pro", token, with(pro, "introduction", "가".repeat(201)))
                .andExpect(jsonPath("$.code").value("PRO_INTRODUCTION_REQUIRED"));
        put("/api/v1/onboarding/pro", token, with(pro, "careers", List.of()))
                .andExpect(jsonPath("$.code").value("PRO_CAREER_REQUIRED"));
        put("/api/v1/onboarding/pro", token, with(pro, "careers", List.of("가".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRO_CAREER_REQUIRED"));
        for (Object availability : List.of(
                List.of(),
                List.of(Map.of("dayOfWeek", "MONDAY", "timeRanges", List.of())),
                List.of(Map.of("dayOfWeek", "MONDAY", "timeRanges",
                        List.of(Map.of("startTime", "13:00", "endTime", "09:00")))),
                List.of(Map.of("dayOfWeek", "MONDAY", "timeRanges", List.of(
                        Map.of("startTime", "09:00", "endTime", "13:00"),
                        Map.of("startTime", "12:00", "endTime", "15:00")))),
                List.of(Map.of("dayOfWeek", "FUNDAY", "timeRanges",
                        List.of(Map.of("startTime", "09:00", "endTime", "13:00")))),
                List.of(Map.of("dayOfWeek", "MONDAY", "timeRanges",
                        List.of(Map.of("startTime", "9시", "endTime", "13:00")))))) {
            put("/api/v1/onboarding/pro", token, with(pro, "availability", availability))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AVAILABILITY_INVALID"));
        }
        // 맞닿은 시간대(13:00 종료, 13:00 시작)는 겹침이 아니다.
        put("/api/v1/onboarding/pro", token, with(pro, "availability", List.of(Map.of("dayOfWeek", "MONDAY",
                "timeRanges", List.of(Map.of("startTime", "09:00", "endTime", "13:00"),
                        Map.of("startTime", "13:00", "endTime", "15:00"))))))
                .andExpect(status().isOk());
    }

    private String onboardingToken() throws Exception {
        return json(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/social/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("provider", "KAKAO", "idToken", "kakao-" + UUID.randomUUID())))))
                .path("onboardingToken").asText();
    }

    private String verify(String token, String phone) throws Exception {
        String id = json(post("/api/v1/phone-verifications", token, sendBody(phone))).path("verificationId").asText();
        return json(confirm(token, id, "123456")).path("verificationToken").asText();
    }

    private ResultActions confirm(String token, String id, String code) throws Exception {
        return post("/api/v1/phone-verifications/" + id + "/confirm", token, Map.of("code", code));
    }

    private static Map<String, Object> sendBody(String phone) {
        return Map.of("phoneNumber", phone, "purpose", "SIGNUP_PHONE_VERIFICATION");
    }

    private static String newPhone() {
        return "010" + PHONE_SEQ.incrementAndGet();
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        var copy = new java.util.HashMap<>(base);
        copy.put(key, value);
        return copy;
    }

    private ResultActions post(String url, String token, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions put(String url, String token, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
