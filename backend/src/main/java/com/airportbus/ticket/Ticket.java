package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 工单(对外资源)。 */
public record Ticket(long id, long userId, String relatedSourceId, String status,
                     LocalDateTime lastReplyAt, LocalDateTime createdAt) {}
