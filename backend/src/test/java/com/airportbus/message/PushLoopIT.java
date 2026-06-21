package com.airportbus.message;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.service.FavoriteService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "airportbus.message.reconcile-delay-ms=3600000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class PushLoopIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired BusCommandService busCmd;
    @Autowired MessageService messages;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER")); favorites.favorite(sourceId); CurrentUser.clear();
        return u.id;
    }
    private BusInput input(String price) {
        return new BusInput("VAB 1", "西站", "ÖBB", null, "40min", price, "03:00-24:00", null,
                List.of("A"), List.of(new BusDetailDto.Schedule("all day","30min",null)), List.of(), List.of(), List.of());
    }

    @Test void adminSave_pushesToSubscriber() {
        long u1 = subscribe("push1", "vie-vab1");
        int v = busWrite.selectVersionHash("vie-vab1").version();
        busCmd.save("vie-vab1", busWrite.findAirportId("VIE"), input("€" + System.currentTimeMillis() % 1000), v, "admin:1", false);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(messages.unreadCount(u1)).isEqualTo(1));
        assertThat(messages.list(u1, 20, 0).get(0).templateCode()).isEqualTo("BUS_UPDATED");
    }

    @Test void adminDelete_pushesOffline_andClearsFavorite() {
        long u1 = subscribe("push2", "vie-vab2");
        long busId = busWrite.findBusId("vie-vab2");
        busCmd.delete("vie-vab2", "admin:1");
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(messages.list(u1, 20, 0)).anyMatch(m -> m.templateCode().equals("BUS_OFFLINE")));
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty();
    }
}
