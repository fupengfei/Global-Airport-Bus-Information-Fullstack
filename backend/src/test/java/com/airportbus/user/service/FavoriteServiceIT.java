package com.airportbus.user.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.common.ApiException;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class FavoriteServiceIT {
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

    @Autowired FavoriteService service;
    @Autowired UserMapper userMapper;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long newUserContext(String name) {
        AppUser u = new AppUser(); // 字段为 public,直接赋值;insertUser 用 useGeneratedKeys 回填 u.id
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        return u.id;
    }

    @Test
    void favoriteThenIdsContainsIt_idempotent() {
        newUserContext("favu1");
        assertTrue(service.favorite("vie-vab1").favorited());
        assertTrue(service.favorite("vie-vab1").favorited()); // 重复幂等
        assertEquals(List.of("vie-vab1"), service.myIds());
    }

    @Test
    void unfavoriteRemoves_idempotent() {
        newUserContext("favu2");
        service.favorite("vie-vab1");
        assertFalse(service.unfavorite("vie-vab1").favorited());
        assertFalse(service.unfavorite("vie-vab1").favorited()); // 重复幂等
        assertTrue(service.myIds().isEmpty());
    }

    @Test
    void refavoriteResurrectsSameRow() {
        newUserContext("favu3");
        service.favorite("vie-vab1");
        service.unfavorite("vie-vab1");
        service.favorite("vie-vab1"); // deleted 1→0,不新增行
        assertEquals(List.of("vie-vab1"), service.myIds());
    }

    @Test
    void favoriteUnknownSource_throwsBusNotFound() {
        newUserContext("favu4");
        assertThrows(ApiException.class, () -> service.favorite("no-such-bus"));
    }

    @Test
    void myFavorites_returnsFullBusDetail() {
        newUserContext("favu5");
        service.favorite("vie-vab1");
        List<BusDetailDto> list = service.myFavorites();
        assertEquals(1, list.size());
        assertEquals("vie-vab1", list.get(0).sourceId());
        assertFalse(list.get(0).stops().isEmpty()); // 含子表
    }
}
