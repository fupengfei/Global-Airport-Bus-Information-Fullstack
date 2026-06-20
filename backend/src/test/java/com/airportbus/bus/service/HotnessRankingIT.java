package com.airportbus.bus.service;

import com.airportbus.bus.mapper.SearchHotnessMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class HotnessRankingIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired SearchHotnessService service;
    @Autowired SearchHotnessMapper mapper;

    @Test
    void ranking_sumsCntDescByWindow() {
        Long vie = mapper.selectAirportIdByCode("VIE");
        Long pvg = mapper.selectAirportIdByCode("PVG");
        assertThat(vie).isNotNull();
        assertThat(pvg).isNotNull();
        LocalDate today = LocalDate.now();
        mapper.upsertStat(vie, today, 10);
        mapper.upsertStat(vie, today.minusDays(3), 5);
        mapper.upsertStat(pvg, today, 4);
        mapper.upsertStat(vie, today.minusDays(40), 100);

        List<SearchHotnessMapper.HotnessRow> r7 = service.ranking("7d", 20);
        assertThat(r7.get(0).airportCode()).isEqualTo("VIE");
        assertThat(r7.get(0).views()).isEqualTo(15);
        assertThat(r7).anyMatch(x -> x.airportCode().equals("PVG") && x.views() == 4);

        List<SearchHotnessMapper.HotnessRow> rAll = service.ranking("all", 20);
        assertThat(rAll.get(0).views()).isEqualTo(115);
    }

    @Test
    void ranking_emptyWhenNoStats_isHandled() {
        List<SearchHotnessMapper.HotnessRow> rows = service.ranking("7d", 20);
        assertThat(rows).isNotNull();
    }
}
