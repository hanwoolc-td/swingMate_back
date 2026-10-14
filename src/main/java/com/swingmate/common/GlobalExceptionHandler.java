package com.swingmate.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.errorCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleInvalid(Exception e) {
        return respond(ErrorCode.INVALID_REQUEST);
    }

    /** 동시 요청으로 유니크 제약(휴대폰 번호, 프로 코드 등)에 걸린 경우. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException e) {
        return respond(ErrorCode.DUPLICATE_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 없는 경로, 지원하지 않는 메서드 등 Spring MVC가 상태 코드를 정해 둔 예외는 500으로 숨기지 않는다.
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return respond(switch (springError.getStatusCode().value()) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                default -> ErrorCode.INVALID_REQUEST;
            });
        }
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_ERROR);
        log.error("Unhandled error traceId={}", body.traceId(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }
}
