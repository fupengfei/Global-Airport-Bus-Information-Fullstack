package com.airportbus.ticket.api.dto;

public class TicketDtos {
    /** 用户建单:关联线路可选 + 问题/建议正文。 */
    public record CreateTicketRequest(String sourceId, String body) {}
    /** 回复(用户/管理员通用):仅正文,author 服务端从主体取(E10)。 */
    public record ReplyRequest(String body) {}
}
