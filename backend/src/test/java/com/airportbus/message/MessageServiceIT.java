package com.airportbus.message;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.bus.service.ChangedSummary;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.service.FavoriteService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class MessageServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MessageService service;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId); CurrentUser.clear();
        return u.id;
    }
    private BusUpdatedEvent updEvent(int version) {
        long busId = busWrite.findBusId("vie-vab1");
        var sum = new ChangedSummary(List.of(new ChangedSummary.FieldChange("price", "€11", "€13")), List.of());
        return new BusUpdatedEvent(busId, "vie-vab1", "VAB 1", version, "h0", "h1", sum);
    }

    @Test void fanOutUpdated_deliversToSubscribers_withDiff_unreadCounts() {
        long u1 = subscribe("msvc1", "vie-vab1");
        service.fanOutUpdated(updEvent(5));
        assertThat(service.unreadCount(u1)).isEqualTo(1);
        List<Message> page = service.list(u1, 20, 0);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).templateCode()).isEqualTo("BUS_UPDATED");
        assertThat(page.get(0).paramsJson()).contains("price").contains("€13");
    }

    @Test void fanOutUpdated_isIdempotent_onSameVersion() {
        long u1 = subscribe("msvc2", "vie-vab1");
        service.fanOutUpdated(updEvent(7));
        service.fanOutUpdated(updEvent(7));
        assertThat(service.unreadCount(u1)).isEqualTo(1);
    }

    @Test void fanOutOffline_notifies_andSoftDeletesFavorites() {
        long u1 = subscribe("msvc3", "vie-vab1");
        long busId = busWrite.findBusId("vie-vab1");
        service.fanOutOffline(new BusDeletedEvent(busId, "vie-vab1", "VAB 1"));
        assertThat(service.unreadCount(u1)).isEqualTo(1);
        assertThat(service.list(u1, 20, 0).get(0).templateCode()).isEqualTo("BUS_OFFLINE");
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty();
    }

    @Test void markRead_and_delete_updateUnread() {
        long u1 = subscribe("msvc4", "vie-vab1");
        service.fanOutUpdated(updEvent(9));
        long id = service.list(u1, 20, 0).get(0).id();
        service.markRead(u1, List.of(id));
        assertThat(service.unreadCount(u1)).isEqualTo(0);
        service.delete(u1, id);
        assertThat(service.list(u1, 20, 0)).isEmpty();
    }
}
