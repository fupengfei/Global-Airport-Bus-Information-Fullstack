package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.*;
import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BusQueryService {

    private final BusQueryMapper mapper;

    public BusQueryService(BusQueryMapper mapper) {
        this.mapper = mapper;
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
        return mapper.selectBusesByAirport(airportCode); // 空机场返回空数组
    }

    @Cacheable(cacheNames = "busDetail", key = "#sourceId")
    public BusDetailDto detail(String sourceId) {
        BusDetailDto.HeadRow h = mapper.selectBusHead(sourceId);
        if (h == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: " + sourceId);
        return new BusDetailDto(
                h.sourceId(), h.route(), h.destination(), h.operator(), h.officialUrl(),
                h.duration(), h.price(), h.operatingHours(), h.lastUpdated(), h.fetchFailed(),
                mapper.selectStops(h.id()),
                mapper.selectSchedules(h.id()),
                mapper.selectImages(h.id()),
                mapper.selectFiles(h.id()),
                mapper.selectAlerts(h.id()));
    }

    private static final class TreeAcc {
        final String name;
        final Map<String, List<TreeDto.Airport>> cities = new LinkedHashMap<>();
        TreeAcc(String name) { this.name = name; }
    }
}
