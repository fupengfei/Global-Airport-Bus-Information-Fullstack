package com.airportbus.bus.api.dto;

import java.time.LocalDate;

public record BusSummaryDto(
        String sourceId, String route, String destination, String operator,
        String duration, String price, LocalDate lastUpdated, boolean fetchFailed) {}
