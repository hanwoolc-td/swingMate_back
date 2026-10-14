package com.swingmate.auth;

public enum TokenType {
    /** 가입 완료 사용자의 API 인증 토큰 */
    ACCESS,
    /** 액세스 토큰 재발급 전용 토큰 */
    REFRESH,
    /** 가입 미완료 사용자의 온보딩 API 전용 토큰 */
    ONBOARDING,
    /** SMS 인증 성공 증명. 온보딩 완료 요청에만 사용한다. */
    PHONE_VERIFICATION
}
