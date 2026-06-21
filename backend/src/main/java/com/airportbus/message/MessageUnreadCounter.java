package com.airportbus.message;

import com.airportbus.message.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 未读计数:Redis 加速、DB 权威。写时 invalidate(DEL),读时 miss → DB COUNT 重建 + TTL。Redis 异常吞掉、回退 DB。 */
@Component
public class MessageUnreadCounter {
    private static final Logger log = LoggerFactory.getLogger(MessageUnreadCounter.class);
    private static final String PREFIX = "msg:unread:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final MessageMapper mapper;

    public MessageUnreadCounter(StringRedisTemplate redis, MessageMapper mapper) {
        this.redis = redis; this.mapper = mapper;
    }

    public long unread(long userId) {
        String key = PREFIX + userId;
        try {
            String v = redis.opsForValue().get(key);
            if (v != null) return Long.parseLong(v);
        } catch (Exception e) { log.warn("unread redis get failed {}: {}", userId, e.toString()); }
        long count = mapper.countUnread(userId);
        try { redis.opsForValue().set(key, Long.toString(count), TTL); }
        catch (Exception e) { log.warn("unread redis set failed {}: {}", userId, e.toString()); }
        return count;
    }

    public void invalidate(long userId) {
        try { redis.delete(PREFIX + userId); }
        catch (Exception e) { log.warn("unread redis del failed {}: {}", userId, e.toString()); }
    }
}
