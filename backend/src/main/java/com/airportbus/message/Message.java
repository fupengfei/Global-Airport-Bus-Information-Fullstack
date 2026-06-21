package com.airportbus.message;

import java.time.LocalDateTime;

/** message 行 + 列表视图(relatedSourceId 由 join 得)。 */
public record Message(long id, long userId, String templateCode, String paramsJson,
                      String relatedSourceId, boolean isRead, LocalDateTime createdAt) {}
