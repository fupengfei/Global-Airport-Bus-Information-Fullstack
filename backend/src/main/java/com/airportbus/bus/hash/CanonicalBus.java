package com.airportbus.bus.hash;

import java.util.List;

/** 规范化值对象:导入器(从 JSON)与运行时(从 DB)都构造它来算 hash。 */
public record CanonicalBus(
        String route,
        String destination,
        String operator,
        String duration,
        String price,
        String operatingHours,
        List<String> stops,                 // 保序(seq 升序)
        List<Schedule> schedules,
        List<Alert> alerts,
        List<Media> images,
        List<Media> files
) {
    public record Schedule(String timeRange, String intervalText, String note) {}
    public record Alert(String type, String message, String startDate, String endDate) {}
    public record Media(String url, String label) {}
}
