package com.airportbus.bus.mapper;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusSummaryDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BusQueryMapper {
    List<TreeRow> selectTreeRows();

    Long selectAirportIdByCode(@Param("code") String airportCode);
    List<BusSummaryDto> selectBusesByAirport(@Param("code") String airportCode);

    BusDetailDto.HeadRow selectBusHead(@Param("sourceId") String sourceId);
    RouteAirport selectRouteAirportCity(@Param("sourceId") String sourceId);
    List<String> selectStops(@Param("busId") Long busId);
    List<BusDetailDto.Schedule> selectSchedules(@Param("busId") Long busId);
    List<BusDetailDto.Image> selectImages(@Param("busId") Long busId);
    List<BusDetailDto.FileRef> selectFiles(@Param("busId") Long busId);
    List<BusDetailDto.Alert> selectAlerts(@Param("busId") Long busId);

    List<com.airportbus.bus.api.dto.SearchResultDto.AirportHit> searchAirports(@Param("q") String q);
    List<com.airportbus.bus.api.dto.SearchResultDto.RouteHit> searchRoutesByStop(@Param("q") String q);

    record TreeRow(String countryCode, String countryName, String cityName,
                   String airportCode, String airportName) {}

    record RouteAirport(String airportName, String cityName) {}
}
