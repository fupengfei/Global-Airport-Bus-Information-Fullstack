package com.airportbus.admin.service;

import com.airportbus.admin.api.dto.OverviewDto;
import com.airportbus.admin.api.dto.SubscriptionStatsDto;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.bus.service.SearchHotnessService;
import com.airportbus.user.service.FavoriteStatsService;
import com.airportbus.user.service.UserStatsService;
import org.springframework.stereotype.Service;

import java.util.List;

/** admin 概览编排:只读、聚合属主模块的统计服务。RBAC 在 Controller 强制。 */
@Service
public class AdminStatsService {

    private static final int WEEK = 7;
    private static final int TOP_N = 20;

    private final UserStatsService userStats;
    private final FavoriteStatsService favoriteStats;
    private final SearchHotnessService hotness;

    public AdminStatsService(UserStatsService userStats, FavoriteStatsService favoriteStats,
                             SearchHotnessService hotness) {
        this.userStats = userStats;
        this.favoriteStats = favoriteStats;
        this.hotness = hotness;
    }

    public OverviewDto overview() {
        return new OverviewDto(
                userStats.totalUsers(),
                userStats.newUsersInLastDays(WEEK),
                favoriteStats.totalFavorites(),
                favoriteStats.newFavoritesInLastDays(WEEK));
    }

    public List<UserStatsService.DailyRegistration> registrations(int days) {
        return userStats.registrations(days);
    }

    public SubscriptionStatsDto subscriptions() {
        return new SubscriptionStatsDto(
                favoriteStats.topRoutes(TOP_N),
                favoriteStats.topAirports(TOP_N),
                favoriteStats.topCities(TOP_N));
    }

    public List<SearchHotnessMapper.HotnessRow> hotnessRanking(String window) {
        return hotness.ranking(window, TOP_N);
    }
}
