package com.airportbus.ticket.api.dto;

public class CorrectionDtos {
    /** 公开上报请求。 */
    public record SubmitCorrectionRequest(String sourceId, String description, String contact) {}
    /** 管理员改状态。 */
    public record UpdateCorrectionRequest(String status, String resolutionNote) {}
}
