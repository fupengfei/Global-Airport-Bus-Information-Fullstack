package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.*;
import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BusQueryService {

    private final BusQueryMapper mapper;
    private final SearchHotnessMapper hotnessMapper;
    private final SearchHotnessService hotness;

    public BusQueryService(BusQueryMapper mapper, SearchHotnessMapper hotnessMapper,
                           SearchHotnessService hotness) {
        this.mapper = mapper;
        this.hotnessMapper = hotnessMapper;
        this.hotness = hotness;
    }

    @Cacheable(cacheNames = "tree")
    public TreeDto tree() {
        Map<String, TreeAcc> countries = new LinkedHashMap<>();
        for (BusQueryMapper.TreeRow row : mapper.selectTreeRows()) {
            TreeAcc c = countries.computeIfAbsent(row.countryCode(),
                    k -> new TreeAcc(row.countryName()));
            c.cities.computeIfAbsent(row.cityName(), k -> new ArrayList<>())
                    .add(new TreeDto.Airport(row.airportCode(), row.airportName()));
        }
        List<TreeDto.Country> result = new ArrayList<>();
        countries.forEach((code, acc) -> {
            List<TreeDto.City> cityList = new ArrayList<>();
            acc.cities.forEach((cityName, airports) ->
                    cityList.add(new TreeDto.City(cityName, airports)));
            result.add(new TreeDto.Country(code, acc.name, cityList));
        });
        return new TreeDto(result);
    }

    @Cacheable(cacheNames = "airportBuses", key = "#airportCode")
    public List<BusSummaryDto> busesByAirport(String airportCode) {
        if (mapper.selectAirportIdByCode(airportCode) == null) {
            throw new ApiException(ErrorCode.AIRPORT_NOT_FOUND, "no airport: " + airportCode);
        }
        List<BusSummaryDto> buses = mapper.selectBusesByAirport(airportCode); // 空机场返回空数组
        // 命中成功后异步 +1。cache hit 时不进入本方法体,计数只在 cache miss 触发(见 SearchHotnessService 注释)。
        hotness.record(airportCode);
        return buses;
    }

    public SearchResultDto search(String q) {
        String t = q == null ? "" : q.trim();
        if (t.isEmpty()) return new SearchResultDto(List.of(), List.of());
        return new SearchResultDto(mapper.searchAirports(t), mapper.searchRoutesByStop(t));
    }

    @Cacheable(cacheNames = "busDetail", key = "#sourceId")
    public BusDetailDto detail(String sourceId) {
        BusDetailDto.HeadRow h = mapper.selectBusHead(sourceId);
        if (h == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: " + sourceId);
        // detail 命中后解析其所属机场 code 再异步计数(BusDetail 不含 airport code,额外一次 mapper 查询)。
        hotness.record(hotnessMapper.selectAirportCodeByBusSourceId(sourceId));
        BusQueryMapper.RouteAirport ra = mapper.selectRouteAirportCity(sourceId);
        List<String> stops = AirportStopOrderer.airportFirst(
                mapper.selectStops(h.id()),
                ra == null ? null : ra.airportName(),
                ra == null ? null : ra.cityName());
        return new BusDetailDto(
                h.sourceId(), h.route(), h.destination(), h.operator(), h.officialUrl(),
                h.duration(), h.price(), h.operatingHours(), h.lastUpdated(), h.fetchFailed(),
                ra == null ? null : ra.countryName(),
                ra == null ? null : ra.cityName(),
                ra == null ? null : ra.airportName(),
                ra == null ? null : ra.airportCode(),
                stops,
                mapper.selectSchedules(h.id()),
                mapper.selectImages(h.id()),
                mapper.selectFiles(h.id()),
                mapper.selectAlerts(h.id()));
    }

    /** source_id → 内部 bus_route_id;不存在或已删除抛 BUS_NOT_FOUND(404)。收藏模块跨模块复用。 */
    public long requireBusRouteId(String sourceId) {
        Long id = mapper.selectIdBySourceId(sourceId);
        if (id == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: " + sourceId);
        return id;
    }

    private static final class TreeAcc {
        final String name;
        final Map<String, List<TreeDto.Airport>> cities = new LinkedHashMap<>();
        TreeAcc(String name) { this.name = name; }
    }
}
