package com.airportbus.bus.api.dto;

import java.util.List;

public record TreeDto(List<Country> countries) {
    public record Country(String code, String name, List<City> cities) {}
    public record City(String name, List<Airport> airports) {}
    public record Airport(String code, String name) {}
}
