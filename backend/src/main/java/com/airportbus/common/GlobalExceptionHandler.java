package com.airportbus.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.code, ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(ErrorCode.INTERNAL_ERROR, "internal error", List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.Detail> details) {
        ApiError body = new ApiError(code.name(), message, details, UUID.randomUUID().toString());
        return ResponseEntity.status(code.status).body(body);
    }
}
