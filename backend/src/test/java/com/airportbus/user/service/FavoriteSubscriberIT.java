package com.airportbus.user.service;

import com.airportbus.user.mapper.FavoriteMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
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
class FavoriteSubscriberIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired FavoriteService favorites;
    @Autowired FavoriteMapper favoriteMapper;
    @Autowired UserMapper users;
    @Autowired com.airportbus.bus.mapper.BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId);
        return u.id;
    }

    @Test void activeSubscriberUserIds_listsSubscribers() {
        long busId = busWrite.findBusId("vie-vab1");
        long u1 = subscribe("fsub1", "vie-vab1");
        long u2 = subscribe("fsub2", "vie-vab1");
        List<Long> ids = favorites.activeSubscriberUserIds(busId);
        assertThat(ids).contains(u1, u2);
    }

    @Test void softDeleteByBusRouteId_removesAll() {
        long busId = busWrite.findBusId("vie-vab1");
        subscribe("fsub3", "vie-vab1");
        favorites.softDeleteByBusRouteId(busId, "admin");
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty();
    }
}
