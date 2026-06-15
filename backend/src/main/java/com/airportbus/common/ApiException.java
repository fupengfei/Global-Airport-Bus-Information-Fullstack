package com.airportbus.common;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
