package com.airportbus.bus.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface BusWriteMapper {
    Long findCountryId(@Param("code") String code);
    void insertCountry(@Param("code") String code, @Param("name") String name);

    Long findCityId(@Param("countryId") Long countryId, @Param("name") String name);
    void insertCity(Map<String, Object> row);    // keys: countryId, name -> 回填 id

    Long findAirportId(@Param("code") String code);
    void insertAirport(Map<String, Object> row);  // keys: cityId, code, name, officialUrl -> 回填 id
    void updateAirport(Map<String, Object> row);  // keys: id, cityId, name, officialUrl

    Long findBusId(@Param("sourceId") String sourceId);
    void insertBus(Map<String, Object> row);      // 见 XML 列;回填 id
    void updateBus(Map<String, Object> row);

    void deleteStops(@Param("busId") Long busId);
    void deleteSchedules(@Param("busId") Long busId);
    void deleteImages(@Param("busId") Long busId);
    void deleteFiles(@Param("busId") Long busId);
    void deleteAlerts(@Param("busId") Long busId);

    void insertStop(@Param("busId") Long busId, @Param("seq") int seq, @Param("name") String name);
    void insertSchedule(Map<String, Object> row);  // busId, timeRange, intervalText, note
    void insertImage(@Param("busId") Long busId, @Param("url") String url, @Param("caption") String caption);
    void insertFile(@Param("busId") Long busId, @Param("name") String name, @Param("url") String url);
    void insertAlert(Map<String, Object> row);     // busId, type, message, startDate, endDate
}
