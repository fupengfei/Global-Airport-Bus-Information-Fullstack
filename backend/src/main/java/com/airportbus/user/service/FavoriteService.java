package com.airportbus.user.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.service.BusQueryService;
import com.airportbus.user.api.dto.FavoriteStatusDto;
import com.airportbus.user.mapper.FavoriteMapper;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 收藏 = 订阅(订阅侧)。userId 一律取自 CurrentUser(JWT 上下文)。 */
@Service
public class FavoriteService {

    private final FavoriteMapper mapper;
    private final BusQueryService busQuery;

    public FavoriteService(FavoriteMapper mapper, BusQueryService busQuery) {
        this.mapper = mapper;
        this.busQuery = busQuery;
    }

    public FavoriteStatusDto favorite(String sourceId) {
        JwtPrincipal me = CurrentUser.require();
        long busRouteId = busQuery.requireBusRouteId(sourceId); // 不存在 → 404
        mapper.upsertFavorite(me.userId(), busRouteId, actor(me));
        return new FavoriteStatusDto(true);
    }

    public FavoriteStatusDto unfavorite(String sourceId) {
        JwtPrincipal me = CurrentUser.require();
        long busRouteId = busQuery.requireBusRouteId(sourceId); // 不存在 → 404
        mapper.softDeleteFavorite(me.userId(), busRouteId, actor(me));
        return new FavoriteStatusDto(false);
    }

    public List<String> myIds() {
        return mapper.selectFavoritedSourceIds(CurrentUser.require().userId());
    }

    public List<BusDetailDto> myFavorites() {
        List<BusDetailDto> out = new ArrayList<>();
        for (String sourceId : myIds()) {
            out.add(busQuery.detail(sourceId)); // 复用查询主线装配(命中缓存时不重复计热度)
        }
        return out;
    }

    private static String actor(JwtPrincipal me) {
        return "user:" + me.userId();
    }
}
