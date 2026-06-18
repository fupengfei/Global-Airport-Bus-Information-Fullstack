package com.airportbus.bus.api.dto;

import java.util.List;

public record SearchResultDto(List<AirportHit> airports, List<RouteHit> routes) {
    public record AirportHit(String code, String name, String cityName, String countryCode) {}
    public record RouteHit(String sourceId, String route, String destination, String airportCode, String matchedStop) {}
}
