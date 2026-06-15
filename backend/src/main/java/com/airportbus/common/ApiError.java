package com.airportbus.common;

import java.util.List;

public record ApiError(String code, String message, List<Detail> details, String traceId) {
    public record Detail(String field, String issue) {}
}
