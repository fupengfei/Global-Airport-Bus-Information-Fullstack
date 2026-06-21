package com.airportbus.audit;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuditMapper {
    void insert(java.util.Map<String, Object> row); // actorId,actorType,action,targetType,targetId,summary,ip

    List<Row> list(@Param("actor") Long actorId, @Param("action") String action, @Param("limit") int limit);

    record Row(long id, long actorId, String actorType, String action, String targetType,
               String targetId, String summary, String ip, java.time.LocalDateTime createdAt) {}
}
