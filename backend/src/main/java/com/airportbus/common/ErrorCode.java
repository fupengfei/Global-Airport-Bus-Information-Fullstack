package com.airportbus.common;

import org.springframework.http.HttpStatus;

/** 业务错误码:body.code 带业务原因,status 带 HTTP 类别(D2)。 */
public enum ErrorCode {
    BUS_NOT_FOUND(HttpStatus.NOT_FOUND),
    AIRPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
}
