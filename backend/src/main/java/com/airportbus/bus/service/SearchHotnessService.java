package com.airportbus.bus.service;

import com.airportbus.bus.mapper.SearchHotnessMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

/**
 * 机场搜索热度(记录侧)。
 *
 * <p>查询命中时对所属机场 +1:走 Redis {@code INCR}、{@code @Async} 触发,查询主流程不等待、
 * 计数失败不影响查询响应(异常被吞)。{@code @Scheduled} 每 5 分钟把 Redis 增量刷入
 * {@code airport_search_stat} 表(按天累加)—— Redis 为加速、MySQL 为权威。</p>
 *
 * <p>取舍:查询结果被 {@code @Cacheable} 缓存,cache hit 时不会进入 BusQueryService 方法体,
 * 故 {@code record} 只在 cache miss(真实未缓存查询)时触发。这是可接受的近似:热度 ≈ 未缓存的真实查询量。</p>
 *
 * <p>隐私:只计数,不记录 user/IP。</p>
 */
@Service
public class SearchHotnessService {

    private static final Logger log = LoggerFactory.getLogger(SearchHotnessService.class);

    /** Redis 计数 key 前缀:airport:hot:{airportCode}。 */
    static final String KEY_PREFIX = "airport:hot:";

    private final StringRedisTemplate redis;
    private final SearchHotnessMapper mapper;

    public SearchHotnessService(StringRedisTemplate redis, SearchHotnessMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** 命中机场计数 +1。@Async + 吞异常:不阻塞、不影响查询正确性。 */
    @Async
    public void record(String airportCode) {
        if (airportCode == null || airportCode.isBlank()) return;
        try {
            redis.opsForValue().increment(KEY_PREFIX + airportCode);
        } catch (Exception e) {
            // 计数失败不影响查询响应;仅记日志。
            log.warn("airport hotness INCR failed for {}: {}", airportCode, e.toString());
        }
    }

    /** 后台榜单:window ∈ {"7d","30d","all"},其余按 7d 兜底。 */
    public java.util.List<SearchHotnessMapper.HotnessRow> ranking(String window, int limit) {
        int cap = limit < 1 ? 20 : Math.min(limit, 100);
        return mapper.ranking(windowSince(window), cap);
    }

    private static java.time.LocalDate windowSince(String window) {
        if ("all".equals(window)) return null;
        if ("30d".equals(window)) return java.time.LocalDate.now().minusDays(29);
        return java.time.LocalDate.now().minusDays(6); // "7d" 及兜底
    }

    /**
     * 周期落库:把 Redis 中每个机场的增量按天 upsert 进 airport_search_stat。
     *
     * <p>原子性:用 {@code GETDEL}(getAndDelete)原子地「取值 + 清零」,落库期间/之后的并发
     * {@code INCR} 会重建 key 从而不丢计数;若落库后 DB upsert 抛错,则把已取走的增量用
     * {@code INCRBY} 加回,避免计数丢失。</p>
     */
    @Scheduled(fixedDelayString = "${airportbus.hotness.flush-delay-ms:300000}")
    public void flushToDb() {
        Set<String> keys;
        try {
            keys = redis.keys(KEY_PREFIX + "*");
        } catch (Exception e) {
            log.warn("airport hotness flush: scan keys failed: {}", e.toString());
            return;
        }
        if (keys == null || keys.isEmpty()) return;

        LocalDate today = LocalDate.now();
        for (String key : keys) {
            String code = key.substring(KEY_PREFIX.length());
            String taken = null;
            try {
                taken = redis.opsForValue().getAndDelete(key); // GETDEL:原子取走增量并清零
                if (taken == null || taken.isBlank()) continue;
                long delta = Long.parseLong(taken.trim());
                if (delta <= 0) continue;

                Long airportId = mapper.selectAirportIdByCode(code);
                if (airportId == null) {
                    // 未知机场:增量加回,等机场出现后再落库(也避免静默丢弃)。
                    redis.opsForValue().increment(key, delta);
                    log.warn("airport hotness flush: unknown airport code {}, delta {} restored", code, delta);
                    continue;
                }
                mapper.upsertStat(airportId, today, delta);
            } catch (Exception e) {
                // 落库失败:把已取走的增量加回 Redis,下个周期重试,避免计数丢失。
                if (taken != null && !taken.isBlank()) {
                    try {
                        redis.opsForValue().increment(key, Long.parseLong(taken.trim()));
                    } catch (Exception ignore) {
                        log.error("airport hotness flush: failed to restore delta for {}", code, ignore);
                    }
                }
                log.warn("airport hotness flush failed for {}: {}", code, e.toString());
            }
        }
    }
}
