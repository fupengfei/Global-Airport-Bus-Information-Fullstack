package com.airportbus.ticket.mapper;

import com.airportbus.ticket.TicketReply;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface TicketReplyMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: ticketId,authorType,authorId,body,createdBy。 */
    int insert(Map<String, Object> row);

    /** 按工单取线程(时间正序)。 */
    List<TicketReply> selectByTicket(@Param("ticketId") long ticketId);
}
