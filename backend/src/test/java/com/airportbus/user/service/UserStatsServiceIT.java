package com.airportbus.user.service;

import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
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
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class UserStatsServiceIT {
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

    @Autowired UserStatsService stats;
    @Autowired UserMapper userMapper;

    /** 直接插用户(沿用 FavoriteServiceIT 的造数法;insertUser 用 useGeneratedKeys 回填 id,
     *  created_at 默认 CURRENT_TIMESTAMP=今天)。 */
    private void insertUser(String name) {
        AppUser u = new AppUser();
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
    }

    @Test
    void totalUsers_includesAdminAndInserted() {
        insertUser("ust1");
        insertUser("ust2");
        assertThat(stats.totalUsers()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void newUsersInLastDays_countsRecentRegistrations() {
        long before = stats.newUsersInLastDays(7);
        insertUser("ust3");
        assertThat(stats.newUsersInLastDays(7)).isEqualTo(before + 1);
    }

    @Test
    void registrations_returnsContinuousDaysWithZeroFill() {
        insertUser("ust4");
        List<UserStatsService.DailyRegistration> pts = stats.registrations(7);
        assertThat(pts).hasSize(7);
        assertThat(pts.get(6).count()).isGreaterThanOrEqualTo(1);
        assertThat(pts.get(0).date()).isLessThan(pts.get(6).date());
    }
}
