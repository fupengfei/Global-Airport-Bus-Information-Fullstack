package com.airportbus.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.code, ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // 兜底必须打栈:否则未知异常被压成 "internal error",根因无从定位
        // (Task 10 的 Redis 缓存命中 500 就因此一度被掩盖)。
        log.error("Unhandled exception, returning INTERNAL_ERROR envelope", ex);
        return build(ErrorCode.INTERNAL_ERROR, "internal error", List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.Detail> details) {
        ApiError body = new ApiError(code.name(), message, details, UUID.randomUUID().toString());
        return ResponseEntity.status(code.status).body(body);
    }
}
