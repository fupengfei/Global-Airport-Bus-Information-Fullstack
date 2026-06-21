package com.airportbus.bus.service;

/** 线路下线事件;切片 B 监听 → 通知订阅者 + 清理收藏。 */
public record BusDeletedEvent(long busRouteId, String sourceId, String route) {}
