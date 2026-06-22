package com.airportbus.common;

import org.springframework.http.HttpStatus;

/** 业务错误码:body.code 带业务原因,status 带 HTTP 类别(D2)。 */
public enum ErrorCode {
    BUS_NOT_FOUND(HttpStatus.NOT_FOUND),
    AIRPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_INPUT(HttpStatus.BAD_REQUEST),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    INVALID_CODE(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN),
    BUS_VERSION_CONFLICT(HttpStatus.CONFLICT),
    CORRECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    TICKET_FORBIDDEN(HttpStatus.FORBIDDEN),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
}
