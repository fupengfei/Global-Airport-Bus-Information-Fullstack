package com.airportbus.bus.api.dto;

import java.time.LocalDate;
import java.util.List;

public record BusDetailDto(
        String sourceId, String route, String destination, String operator, String officialUrl,
        String duration, String price, String operatingHours,
        LocalDate lastUpdated, boolean fetchFailed,
        String countryName, String cityName, String airportName, String airportCode,
        List<String> stops,
        List<Schedule> schedules,
        List<Image> images,
        List<FileRef> files,
        List<Alert> alerts) {

    public record Schedule(String timeRange, String intervalText, String note) {}
    public record Image(String url, String caption) {}
    public record FileRef(String name, String url) {}
    public record Alert(String type, String message, LocalDate startDate, LocalDate endDate) {}

    /** MyBatis 查 bus_route 头部行用;Service 再拼子表。 */
    public record HeadRow(Long id, String sourceId, String route, String destination, String operator,
                          String officialUrl, String duration, String price, String operatingHours,
                          LocalDate lastUpdated, boolean fetchFailed) {}
}
