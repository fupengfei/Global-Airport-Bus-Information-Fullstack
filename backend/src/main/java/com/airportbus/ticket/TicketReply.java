package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 工单回复(气泡线程一条)。 */
public record TicketReply(long id, long ticketId, String authorType, long authorId,
                          String body, LocalDateTime createdAt) {}
