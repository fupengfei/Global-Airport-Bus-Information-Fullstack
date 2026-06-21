package com.airportbus.admin.api.dto;

import com.airportbus.bus.api.dto.BusInput;

public record CreateBusRequest(String sourceId, String airportCode, BusInput data) {}
