package com.airportbus.ticket;

import java.util.List;

/** 工单详情:工单本体 + 时间正序回复线程。 */
public record TicketThread(Ticket ticket, List<TicketReply> replies) {}
