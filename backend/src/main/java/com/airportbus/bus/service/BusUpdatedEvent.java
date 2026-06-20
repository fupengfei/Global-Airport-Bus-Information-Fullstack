package com.airportbus.bus.service;

/** 线路内容变化事件;切片 B 用 @TransactionalEventListener(AFTER_COMMIT) 监听。本期无监听者。 */
public record BusUpdatedEvent(long busRouteId, String sourceId,
                              String oldHash, String newHash, ChangedSummary summary) {}
