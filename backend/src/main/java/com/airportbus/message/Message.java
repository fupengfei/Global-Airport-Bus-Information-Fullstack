package com.airportbus.message;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;

/** message 行 + 列表视图(relatedSourceId 由 join 得)。params 为存储的 JSON,@JsonRawValue 让序列化为对象而非转义串。 */
public record Message(long id, long userId, String templateCode,
                      @JsonRawValue String params,
                      String relatedSourceId, boolean isRead, LocalDateTime createdAt) {}
