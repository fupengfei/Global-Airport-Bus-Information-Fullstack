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
        "airportbus.message.reconcile-delay-ms=3600000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class ReconcilerIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MessageReconciler reconciler;
    @Autowired MessageService messages;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;
    @Autowired BusCommandService busCmd;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    @Test void reconcile_backfillsMissedSubscriber() {
        // 触发一次内容变更(产生新 version),用 suppressEvents=true 模拟「漏发」(事件未送达/进程崩)
        int v = busWrite.selectVersionHash("vie-vab3").version();
        busCmd.save("vie-vab3", busWrite.findAirportId("VIE"),
                new BusInput("VAB 3","x","y",null,"d","€"+(System.currentTimeMillis()%1000),"oh",null,
                        List.of("A"), List.of(new BusDetailDto.Schedule("t","i",null)), List.of(), List.of(), List.of()),
                v, "admin:1", true);
        // 用户随后订阅(收藏)
        AppUser u = new AppUser(); u.username="rec1"; u.email="rec1@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id,"USER")); favorites.favorite("vie-vab3"); CurrentUser.clear();
        assertThat(messages.unreadCount(u.id)).isEqualTo(0); // 还没消息

        reconciler.reconcile(); // 对账回填

        assertThat(messages.unreadCount(u.id)).isEqualTo(1);
        assertThat(messages.list(u.id, 20, 0).get(0).templateCode()).isEqualTo("BUS_UPDATED");
    }
}
