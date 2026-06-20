package com.airportbus.admin.api.dto;

import com.airportbus.user.mapper.FavoriteMapper;

import java.util.List;

public record SubscriptionStatsDto(List<FavoriteMapper.RouteSub> topRoutes,
                                   List<FavoriteMapper.AirportSub> topAirports,
                                   List<FavoriteMapper.CitySub> topCities) {}
