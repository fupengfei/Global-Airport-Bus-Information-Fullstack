package com.airportbus.user.service;

import com.airportbus.user.mapper.FavoriteMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class FavoriteStatsServiceIT {
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

    @Autowired FavoriteStatsService stats;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFavorites() { jdbc.execute("DELETE FROM favorite"); }

    @AfterEach
    void cleanup() { CurrentUser.clear(); }

    /** 直接插用户(insertUser 回填 id)→ 设进 CurrentUser → 走 FavoriteService 收藏种子线路。 */
    private void insertUserAndFavorite(String name, String sourceId) {
        AppUser u = new AppUser();
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId);
    }

    @Test
    void topRoutes_rankByFavoriteCount_notifyEqualsFavorite() {
        insertUserAndFavorite("fst1", "vie-vab1");
        insertUserAndFavorite("fst2", "vie-vab1");
        insertUserAndFavorite("fst3", "pvg-line4");

        List<FavoriteMapper.RouteSub> rows = stats.topRoutes(20);
        assertThat(rows).isNotEmpty();
        FavoriteMapper.RouteSub top = rows.get(0);
        assertThat(top.busSourceId()).isEqualTo("vie-vab1");
        assertThat(top.favoriteCount()).isEqualTo(2);
        assertThat(top.notifyCount()).isEqualTo(top.favoriteCount());
        assertThat(top.airportCode()).isEqualTo("VIE");
    }

    @Test
    void totalFavorites_countsActiveOnly() {
        long before = stats.totalFavorites();
        insertUserAndFavorite("fst4", "vie-vab1");
        assertThat(stats.totalFavorites()).isEqualTo(before + 1);
    }

    @Test
    void topAirports_and_topCities_aggregate() {
        insertUserAndFavorite("fst5", "vie-vab1");
        assertThat(stats.topAirports(20)).anyMatch(a -> a.airportCode().equals("VIE"));
        assertThat(stats.topCities(20)).anyMatch(c -> c.favoriteCount() >= 1);
    }
}
