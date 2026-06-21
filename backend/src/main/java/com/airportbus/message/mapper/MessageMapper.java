package com.airportbus.message.mapper;

import com.airportbus.message.Message;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface MessageMapper {
    /** 批量插(幂等:命中 uk_msg_dedup 则忽略)。rows 每个 map: userId,templateCode,paramsJson,relatedBusRouteId,dedupKey,actor。 */
    int batchInsert(@Param("rows") List<java.util.Map<String, Object>> rows);

    long countUnread(@Param("userId") long userId);

    List<Message> selectPage(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    int markRead(@Param("userId") long userId, @Param("ids") List<Long> ids);

    int softDelete(@Param("userId") long userId, @Param("id") long id);

    /** 对账:有活跃订阅者但缺「当前 version」消息的 (userId, busRouteId, version, route, sourceId)。 */
    List<Backfill> selectMissingForCurrentVersion();

    record Backfill(long userId, long busRouteId, int version, String route, String sourceId) {}
}
