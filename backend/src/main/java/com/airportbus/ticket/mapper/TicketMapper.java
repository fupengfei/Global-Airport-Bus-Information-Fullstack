package com.airportbus.ticket.mapper;

import com.airportbus.ticket.Ticket;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface TicketMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: userId,relatedSourceId,createdBy。 */
    int insert(Map<String, Object> row);

    Ticket selectById(@Param("id") long id);

    /** 我的工单(按 user 过滤,status 可空)。 */
    List<Ticket> selectByUser(@Param("userId") long userId, @Param("status") String status,
                              @Param("limit") int limit, @Param("offset") int offset);

    /** 管理员队列(status 可空)。 */
    List<Ticket> selectPage(@Param("status") String status,
                            @Param("limit") int limit, @Param("offset") int offset);

    /** 回复后:置状态 + last_reply_at=NOW()。 */
    int updateStatusAndLastReply(@Param("id") long id, @Param("status") String status);

    /** 关闭:仅置状态(不动 last_reply_at)。 */
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
