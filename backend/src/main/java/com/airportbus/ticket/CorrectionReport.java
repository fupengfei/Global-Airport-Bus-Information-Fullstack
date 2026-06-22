package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 匿名纠错上报(对外资源)。 */
public class CorrectionReport {
    public long id;
    public String relatedSourceId;
    public String description;
    public String contact;
    public String status;
    public String resolutionNote;
    public String reporterIp;
    public LocalDateTime createdAt;
}
