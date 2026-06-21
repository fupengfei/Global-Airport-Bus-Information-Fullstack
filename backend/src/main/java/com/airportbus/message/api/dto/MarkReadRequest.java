package com.airportbus.message.api.dto;
import java.util.List;
public record MarkReadRequest(List<Long> ids) {}
