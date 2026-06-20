package com.airportbus.admin.api.dto;

/** 概览统计卡(工单卡是纯前端占位,不在此)。 */
public record OverviewDto(long totalUsers, long newUsersThisWeek,
                          long totalFavorites, long newFavoritesThisWeek) {}
