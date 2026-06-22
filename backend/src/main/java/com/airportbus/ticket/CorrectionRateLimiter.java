package com.airportbus.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 公开纠错上报按 IP 限流:窗口内超上限拒绝。Redis 故障放行(可用性优先)。 */
@Component
public class CorrectionRateLimiter {
    private final StringRedisTemplate redis;
    private final int windowSec;
    private final int max;

    public CorrectionRateLimiter(StringRedisTemplate redis,
                                 @Value("${airportbus.correction.rate-limit-window-sec:300}") int windowSec,
                                 @Value("${airportbus.correction.rate-limit-max:5}") int max) {
        this.redis = redis; this.windowSec = windowSec; this.max = max;
    }

    /** true=放行。ip 空或 Redis 异常一律放行。 */
    public boolean allow(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            String key = "corr:rl:" + ip;
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) redis.expire(key, Duration.ofSeconds(windowSec));
            return n == null || n <= max;
        } catch (Exception e) {
            return true; // 缓存故障不阻断公开上报
        }
    }
}
