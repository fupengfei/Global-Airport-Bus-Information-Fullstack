package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.SearchResultDto;
import com.airportbus.bus.seed.SeedImporter;
import com.airportbus.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=false",
        "spring.cache.type=none",
        "spring.data.redis.host=localhost",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class BusQueryServiceIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired SeedImporter importer;
    @Autowired BusQueryService service;

    @BeforeEach
    void seed() { importer.importFromClasspath("data/data.json"); }

    @Test
    void treeHasTwoCountries() {
        assertThat(service.tree().countries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void detailLoadsStopsInOrder() {
        BusDetailDto d = service.detail("vie-vab1");
        assertThat(d.route()).isEqualTo("VAB 1");
        assertThat(d.stops()).containsExactly(
                "维也纳机场", "维也纳中央车站 Hauptbahnhof (南入口)", "维也纳西站 Westbahnhof");
    }

    @Test
    void searchByStopNameMatchesRoute() {
        var r = service.search("中央车站");
        assertThat(r.routes()).extracting(SearchResultDto.RouteHit::sourceId).contains("vie-vab1");
    }

    @Test
    void blankSearchReturnsEmpty() {
        var r = service.search("   ");
        assertThat(r.airports()).isEmpty();
        assertThat(r.routes()).isEmpty();
    }

    @Test
    void unknownBusThrowsNotFound() {
        assertThatThrownBy(() -> service.detail("nope-xxx")).isInstanceOf(ApiException.class);
    }
}
