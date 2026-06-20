package com.airportbus.user.service;

import com.airportbus.user.mapper.FavoriteMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 订阅(收藏)统计(只读)。供 admin 概览编排。 */
@Service
public class FavoriteStatsService {

    private final FavoriteMapper favorites;

    public FavoriteStatsService(FavoriteMapper favorites) { this.favorites = favorites; }

    public long totalFavorites() { return favorites.countFavorites(); }

    public long newFavoritesInLastDays(int days) {
        int n = days < 1 ? 7 : Math.min(days, 90);
        return favorites.countFavoritesSince(LocalDate.now().minusDays(n - 1L));
    }

    public List<FavoriteMapper.RouteSub> topRoutes(int limit)    { return favorites.topRoutes(cap(limit)); }
    public List<FavoriteMapper.AirportSub> topAirports(int limit) { return favorites.topAirports(cap(limit)); }
    public List<FavoriteMapper.CitySub> topCities(int limit)      { return favorites.topCities(cap(limit)); }

    private static int cap(int limit) { return limit < 1 ? 20 : Math.min(limit, 100); }
}
