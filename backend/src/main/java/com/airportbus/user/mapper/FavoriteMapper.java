package com.airportbus.user.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FavoriteMapper {
    /** upsert:不存在则插入(deleted=0),存在则把 deleted 置回 0 并刷新 updated_*。 */
    int upsertFavorite(@Param("userId") long userId,
                       @Param("busRouteId") long busRouteId,
                       @Param("actor") String actor);

    /** 软删:把 deleted 置 1(仅作用于当前 deleted=0 的行)。 */
    int softDeleteFavorite(@Param("userId") long userId,
                           @Param("busRouteId") long busRouteId,
                           @Param("actor") String actor);

    /** 当前用户已收藏(deleted=0)且线路未删除的 source_id,按收藏动作时间倒序。 */
    List<String> selectFavoritedSourceIds(@Param("userId") long userId);

    long countFavorites();

    long countFavoritesSince(@Param("since") java.time.LocalDate since);

    java.util.List<RouteSub> topRoutes(@Param("limit") int limit);

    java.util.List<AirportSub> topAirports(@Param("limit") int limit);

    java.util.List<CitySub> topCities(@Param("limit") int limit);

    /** 按线路聚合的订阅数;notifyCount == favoriteCount(收藏=订阅,无独立通知开关)。 */
    record RouteSub(String busSourceId, String route, String destination,
                    String airportCode, String cityName, long favoriteCount, long notifyCount) {}

    record AirportSub(String airportCode, String airportName, String cityName, long favoriteCount) {}

    record CitySub(String cityName, String countryName, long favoriteCount) {}
}
