package com.airportbus.bus.api.dto;

import java.time.LocalDateTime;

/** 编辑视图:业务键 + 机场 + 乐观锁版本 + 核对时间 + 可编辑数据。 */
public record BusView(String sourceId, String airportCode, int version,
                      LocalDateTime lastVerifiedAt, BusInput data) {}
