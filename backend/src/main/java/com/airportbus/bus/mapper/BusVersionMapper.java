package com.airportbus.bus.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface BusVersionMapper {
    void insertSnapshot(java.util.Map<String, Object> row); // busRouteId, version, snapshotJson, contentHash, changedSummary, actor

    /** 某线路的版本列表(新→旧),不含快照大字段。 */
    List<Meta> listVersions(@Param("busRouteId") long busRouteId);

    /** 取某版本完整快照 JSON。 */
    String selectSnapshotJson(@Param("busRouteId") long busRouteId, @Param("version") int version);

    record Meta(int version, String contentHash, String changedSummary, String actor,
                java.time.LocalDateTime createdAt) {}
}
