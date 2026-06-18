package com.airportbus.bus.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=false",
        "spring.cache.type=none",
        "spring.data.redis.host=localhost",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class SeedImporterIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @MockBean RedisConnectionFactory redisConnectionFactory; // 不连真实 Redis

    @Autowired SeedImporter importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importIsIdempotent_andHashStableOnReimport() {
        importer.importFromClasspath("data/data.json");
        Integer busCount1 = jdbc.queryForObject("SELECT COUNT(*) FROM bus_route", Integer.class);
        String hash1 = jdbc.queryForObject(
                "SELECT content_hash FROM bus_route WHERE source_id = 'vie-vab1'", String.class);

        importer.importFromClasspath("data/data.json"); // 重跑

        Integer busCount2 = jdbc.queryForObject("SELECT COUNT(*) FROM bus_route", Integer.class);
        String hash2 = jdbc.queryForObject(
                "SELECT content_hash FROM bus_route WHERE source_id = 'vie-vab1'", String.class);

        assertThat(busCount2).isEqualTo(busCount1);        // 不重复插入
        assertThat(hash2).isEqualTo(hash1);                // hash 稳定(无幻象变更)
        assertThat(busCount1).isGreaterThan(0);
        Integer stops = jdbc.queryForObject("SELECT COUNT(*) FROM bus_stop bs " +
                "JOIN bus_route br ON br.id = bs.bus_route_id WHERE br.source_id='vie-vab1'", Integer.class);
        assertThat(stops).isEqualTo(3); // vie-vab1 有 3 个停靠站
    }
}
