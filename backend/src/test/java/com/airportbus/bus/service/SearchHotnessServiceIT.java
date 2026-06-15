package com.airportbus.bus.service;

import com.airportbus.bus.seed.SeedImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.Executor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 机场搜索热度记录侧集成测试:真实 MySQL + 真实 Redis(均 Testcontainers,不 mock RedisConnectionFactory)。
 *
 * <p>验证:命中查询 → Redis 计数 +1;@Scheduled 落库逻辑 → airport_search_stat 落行并清零 Redis。
 * 把 flush-delay 调到极大(不让定时任务在测试期间自动跑),改为手动调 flushToDb() 断言稳定。
 * 直接调 record() 验证 INCR(@Async 行为不影响 Redis 写入结果断言)。</p>
 */
@SpringBootTest(properties = {
        "airportbus.seed.enabled=false",
        "spring.cache.type=none",
        "airportbus.hotness.flush-delay-ms=3600000", // 1h:测试期间定时任务不自动触发,手动调用断言
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Import(SearchHotnessServiceIT.SyncAsyncConfig.class)
@Testcontainers
class SearchHotnessServiceIT {

    /** 测试里把 @Async 设为同步执行(调用方线程内跑),让 record() 写入 Redis 后断言稳定。 */
    @TestConfiguration
    static class SyncAsyncConfig implements AsyncConfigurer {
        @Override public Executor getAsyncExecutor() { return new SyncTaskExecutor(); }
    }

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired SeedImporter importer;
    @Autowired BusQueryService busQuery;
    @Autowired SearchHotnessService hotness;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        importer.importFromClasspath("data/data.json");
        redisTemplate.delete(SearchHotnessService.KEY_PREFIX + "VIE");
    }

    @Test
    void recordIncrementsRedisCounter() {
        hotness.record("VIE");
        hotness.record("VIE");
        String v = redisTemplate.opsForValue().get(SearchHotnessService.KEY_PREFIX + "VIE");
        assertThat(v).isEqualTo("2");
    }

    @Test
    void busesByAirportHitTriggersCount() {
        // cache.type=none → 必进方法体 → record() 同步 INCR(record 内部直接写 Redis)
        busQuery.busesByAirport("VIE");
        String v = redisTemplate.opsForValue().get(SearchHotnessService.KEY_PREFIX + "VIE");
        assertThat(v).isEqualTo("1");
    }

    @Test
    void flushToDbUpsertsStatAndClearsRedis() {
        hotness.record("VIE");
        hotness.record("VIE");
        hotness.record("VIE");
        assertThat(redisTemplate.opsForValue().get(SearchHotnessService.KEY_PREFIX + "VIE")).isEqualTo("3");

        hotness.flushToDb();

        // Redis 已被 GETDEL 清零
        assertThat(redisTemplate.opsForValue().get(SearchHotnessService.KEY_PREFIX + "VIE")).isNull();

        Long airportId = jdbc.queryForObject(
                "SELECT id FROM airport WHERE code = 'VIE' AND deleted = 0", Long.class);
        Long cnt = jdbc.queryForObject(
                "SELECT cnt FROM airport_search_stat WHERE airport_id = ? AND day = ?",
                Long.class, airportId, LocalDate.now());
        assertThat(cnt).isEqualTo(3L);
    }
}
