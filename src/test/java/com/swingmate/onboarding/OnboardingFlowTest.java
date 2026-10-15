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
    void memberAndProSignUpThenConnectWithMemberCode() throws Exception {
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
                .andExpect(jsonPath("$.accessToken").exists()));
        String proAccess = pro.path("accessToken").asText();

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
                .andExpect(jsonPath("$.user.phoneNumber").value(memberPhone)));
        // 같은 요청 재시도는 성공하고 토큰만 새로 나온다.
        put("/api/v1/onboarding/member", memberToken, memberBody).andExpect(status().isOk());

        String access = member.path("accessToken").asText();

        // CONNECT-01: 회원이 일회용 코드 발급 → 프로가 입력 → 회원 승인
        String code = json(post("/api/v1/members/me/connection-code", access, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(300)))
                .path("code").asText();
        // 승인 전에는 프로에게 회원 이름만 보인다.
        String connectionId = json(post("/api/v1/pros/me/connections", proAccess, Map.of("code", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.member.name").value("홍길동"))
                .andExpect(jsonPath("$.member.phoneNumber").doesNotExist()))
                .path("id").asText();
        // 코드는 한 번만 쓸 수 있다.
        post("/api/v1/pros/me/connections", proAccess, Map.of("code", code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONNECTION_CODE_INVALID"));

        // 회원은 승인 전에 프로 정보를 확인한다.
        get("/api/v1/members/me/connections", access)
                .andExpect(jsonPath("$[0].id").value(connectionId))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].pro.name").value("김프로"))
                .andExpect(jsonPath("$[0].pro.introduction").value("KPGA 투어 프로입니다."))
                .andExpect(jsonPath("$[0].pro.careers[0]").value("KPGA 투어 프로"));
        post("/api/v1/members/me/connections/" + connectionId + "/approve", access, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        post("/api/v1/members/me/connections/" + connectionId + "/approve", access, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONNECTION_NOT_PENDING"));
        get("/api/v1/pros/me/connections", proAccess)
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].member.name").value("홍길동"));

        // 이미 연결된 회원의 새 코드를 다시 입력하면 막힌다.
        String code2 = json(post("/api/v1/members/me/connection-code", access, Map.of())).path("code").asText();
        post("/api/v1/pros/me/connections", proAccess, Map.of("code", code2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CONNECTED"));

        // 역할이 맞지 않으면 403.
        post("/api/v1/members/me/connection-code", proAccess, Map.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
        post("/api/v1/pros/me/connections", access, Map.of("code", code2))
                .andExpect(status().isForbidden());

        // 다른 프로의 요청은 거절할 수 있고, 거절하면 목록에서 빠진다.
        String otherPro = signUpPro("이프로");
        String code3 = json(post("/api/v1/members/me/connection-code", access, Map.of())).path("code").asText();
        // 새 코드를 받으면 이전 코드는 무효다.
        post("/api/v1/pros/me/connections", otherPro, Map.of("code", code2))
                .andExpect(status().isNotFound());
        String rejectId = json(post("/api/v1/pros/me/connections", otherPro, Map.of("code", code3)))
                .path("id").asText();
        // 다른 회원은 남의 요청을 처리할 수 없다.
        post("/api/v1/members/me/connections/" + rejectId + "/approve", signUpMember(), Map.of())
                .andExpect(status().isNotFound());
        post("/api/v1/members/me/connections/" + rejectId + "/reject", access, Map.of())
                .andExpect(jsonPath("$.status").value("ENDED"));
        get("/api/v1/members/me/connections", access)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pro.name").value("김프로"));
        get("/api/v1/pros/me/connections", otherPro).andExpect(jsonPath("$.length()").value(0));

        // 거절된 프로도 새 코드로 다시 요청할 수 있다.
        String code4 = json(post("/api/v1/members/me/connection-code", access, Map.of())).path("code").asText();
        post("/api/v1/pros/me/connections", otherPro, Map.of("code", code4))
                .andExpect(jsonPath("$.id").value(rejectId))
                .andExpect(jsonPath("$.status").value("PENDING"));

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

    /** 가입을 마친 프로의 액세스 토큰. */
    private String signUpPro(String name) throws Exception {
        String token = onboardingToken();
        String phone = newPhone();
        return json(put("/api/v1/onboarding/pro", token, Map.of(
                "verificationToken", verify(token, phone), "name", name, "phoneNumber", phone,
                "introduction", "소개", "careers", List.of("코치"),
                "availability", List.of(Map.of("dayOfWeek", "TUESDAY", "timeRanges",
                        List.of(Map.of("startTime", "09:00", "endTime", "10:00")))))))
                .path("accessToken").asText();
    }

    /** 가입을 마친 일반 회원의 액세스 토큰. */
    private String signUpMember() throws Exception {
        String token = onboardingToken();
        String phone = newPhone();
        return json(put("/api/v1/onboarding/member", token, Map.of(
                "verificationToken", verify(token, phone), "name", "다른회원", "phoneNumber", phone,
                "lessonInterests", List.of("IRON"), "golfExperience", "OVER_7_YEARS")))
                .path("accessToken").asText();
    }

    private ResultActions get(String url, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", "Bearer " + token));
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
