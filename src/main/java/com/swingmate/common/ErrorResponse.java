package com.swingmate.common;

import java.util.UUID;

/** 명세서 6장: 모든 오류 응답은 code, message, traceId를 포함한다. */
public record ErrorResponse(String code, String message, String traceId) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.message(), UUID.randomUUID().toString());
    }
}
