package com.airportbus.bus.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class SeedDtos {
    public record Root(List<Country> countries) {}
    public record Country(String code, String name, List<City> cities) {}
    public record City(String name, List<Airport> airports) {}
    public record Airport(String code, String name, String officialUrl, List<Bus> buses) {}
    public record Bus(String id, String route, String destination, String operator, String officialUrl,
                      String duration, String price, String operatingHours,
                      List<String> stops, List<Schedule> schedules, List<Image> images,
                      List<FileRef> files, List<Alert> alerts, String lastUpdated, boolean fetchFailed) {}
    public record Schedule(String timeRange, String interval, String note) {}
    public record Image(String url, String caption) {}
    public record FileRef(String name, String url) {}
    public record Alert(String type, String message, String startDate, String endDate) {}

    private SeedDtos() {}
}
