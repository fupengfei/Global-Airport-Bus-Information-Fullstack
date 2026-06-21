package com.airportbus.bus.service;

/** 线路内容变化事件;切片 B 用 @TransactionalEventListener(AFTER_COMMIT) 监听。 */
public record BusUpdatedEvent(long busRouteId, String sourceId, String route, int version,
                              String oldHash, String newHash, ChangedSummary summary) {}
