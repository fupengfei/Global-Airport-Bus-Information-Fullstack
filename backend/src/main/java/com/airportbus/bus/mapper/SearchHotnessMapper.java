package com.airportbus.bus.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 机场搜索热度:detail 路径解析 airport code、落库时 code→id 映射、按天 upsert 累加。 */
public interface SearchHotnessMapper {

    /** 由 bus 的 source_id 解析其所属机场 code(detail 命中后用于计数,BusDetail 不含 airport code)。 */
    String selectAirportCodeByBusSourceId(@Param("sourceId") String sourceId);

    /** 落库时把机场 code 映射成 airport_id;未知机场返回 null。 */
    Long selectAirportIdByCode(@Param("code") String code);

    /** 按 (airport_id, day) upsert 累加 delta(唯一键命中则 cnt = cnt + delta)。 */
    void upsertStat(@Param("airportId") long airportId,
                    @Param("day") LocalDate day,
                    @Param("delta") long delta);
}
