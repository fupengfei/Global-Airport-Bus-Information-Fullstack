package com.airportbus.bus.api.dto;

import java.time.LocalDate;
import java.util.List;

/** 巴士编辑入参(子表对齐现有写路径,不含 direction)。 */
public record BusInput(
        String route, String destination, String operator, String officialUrl,
        String duration, String price, String operatingHours, LocalDate lastUpdated,
        List<String> stops,
        List<BusDetailDto.Schedule> schedules,
        List<BusDetailDto.Alert> alerts,
        List<BusDetailDto.Image> images,
        List<BusDetailDto.FileRef> files) {}
