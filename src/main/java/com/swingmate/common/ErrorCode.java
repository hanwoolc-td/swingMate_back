package com.swingmate.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 유효하지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 가입이 완료된 사용자입니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청과 충돌합니다."),
    NAME_INVALID(HttpStatus.BAD_REQUEST, "이름은 한글 또는 영문 2자 이상이어야 합니다."),
    PHONE_NUMBER_INVALID(HttpStatus.BAD_REQUEST, "휴대폰 번호 형식이 올바르지 않습니다."),
    PHONE_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 번호입니다."),
    PHONE_ALREADY_IN_USE(HttpStatus.CONFLICT, "다른 계정에서 사용 중인 번호입니다."),
    SMS_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "인증 요청을 찾을 수 없습니다."),
    VERIFICATION_EXPIRED(HttpStatus.GONE, "인증번호가 만료되었습니다."),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다."),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "휴대폰 인증 정보가 유효하지 않습니다."),
    PHONE_NUMBER_MISMATCH(HttpStatus.BAD_REQUEST, "인증한 번호와 입력한 번호가 다릅니다."),
    LESSON_INTEREST_REQUIRED(HttpStatus.BAD_REQUEST, "관심 레슨 분야를 한 개 이상 선택해 주세요."),
    GOLF_EXPERIENCE_REQUIRED(HttpStatus.BAD_REQUEST, "골프 경력을 선택해 주세요."),
    PRO_INTRODUCTION_REQUIRED(HttpStatus.BAD_REQUEST, "프로 소개를 1~200자로 입력해 주세요."),
    PRO_CAREER_REQUIRED(HttpStatus.BAD_REQUEST, "경력을 한 개 이상 입력해 주세요."),
    AVAILABILITY_INVALID(HttpStatus.BAD_REQUEST, "레슨 가능 시간이 올바르지 않습니다."),
    PRO_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로 코드를 찾을 수 없습니다."),
    PRO_CONNECTION_NOT_ALLOWED(HttpStatus.CONFLICT, "프로와 연결할 수 없는 상태입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
