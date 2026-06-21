package com.airportbus.admin.api.dto;

import com.airportbus.bus.api.dto.BusInput;

public record UpdateBusRequest(String airportCode, int version, BusInput data) {}
