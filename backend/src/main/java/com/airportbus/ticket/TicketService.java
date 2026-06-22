package com.airportbus.ticket;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.message.MessageService;
import com.airportbus.ticket.mapper.TicketMapper;
import com.airportbus.ticket.mapper.TicketReplyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 用户建议工单:建单/线程查询 + 状态机回复/关闭。author 一律服务端从主体取(E10)。 */
@Service
public class TicketService {
    private final TicketMapper tickets;
    private final TicketReplyMapper replies;
    private final BusWriteMapper busWrite;
    private final MessageService messages;

    public TicketService(TicketMapper tickets, TicketReplyMapper replies,
                         BusWriteMapper busWrite, MessageService messages) {
        this.tickets = tickets; this.replies = replies; this.busWrite = busWrite; this.messages = messages;
    }

    @Transactional
    public TicketThread create(long userId, String sourceId, String body) {
        requireBody(body);
        String src = normalizeSource(sourceId);
        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("relatedSourceId", src);
        row.put("createdBy", "user:" + userId);
        tickets.insert(row);
        long ticketId = ((Number) row.get("id")).longValue();
        insertReply(ticketId, "USER", userId, body.trim());
        return thread(ticketId);
    }

    public List<Ticket> listMine(long userId, String status, int limit, int offset) {
        return tickets.selectByUser(userId, status, page(limit), Math.max(offset, 0));
    }

    public TicketThread getMine(long userId, long ticketId) {
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        return new TicketThread(t, replies.selectByTicket(ticketId));
    }

    @Transactional
    public TicketThread replyAsUser(long userId, long ticketId, String body) {
        requireBody(body);
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        insertReply(ticketId, "USER", userId, body.trim());
        tickets.updateStatusAndLastReply(ticketId, "OPEN"); // 用户回复永远重开
        return thread(ticketId);
    }

    @Transactional
    public Ticket closeAsUser(long userId, long ticketId) {
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        tickets.updateStatus(ticketId, "CLOSED");
        return tickets.selectById(ticketId);
    }

    // ---- 内部 helper ----
    private TicketThread thread(long ticketId) {
        return new TicketThread(tickets.selectById(ticketId), replies.selectByTicket(ticketId));
    }

    /** 返回新回复的 id(admin 回复发站内信需要)。 */
    private long insertReply(long ticketId, String authorType, long authorId, String body) {
        Map<String, Object> row = new HashMap<>();
        row.put("ticketId", ticketId);
        row.put("authorType", authorType);
        row.put("authorId", authorId);
        row.put("body", body);
        row.put("createdBy", authorType.toLowerCase() + ":" + authorId);
        replies.insert(row);
        return ((Number) row.get("id")).longValue();
    }

    private Ticket requireTicket(long ticketId) {
        Ticket t = tickets.selectById(ticketId);
        if (t == null) throw new ApiException(ErrorCode.TICKET_NOT_FOUND, String.valueOf(ticketId));
        return t;
    }

    private void requireBody(String body) {
        if (body == null || body.isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "body required");
    }

    private String normalizeSource(String sourceId) {
        String src = (sourceId == null || sourceId.isBlank()) ? null : sourceId.trim();
        if (src != null && busWrite.selectVersionHash(src) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, src);
        return src;
    }

    private static int page(int limit) { return limit < 1 ? 20 : Math.min(limit, 100); }
}
