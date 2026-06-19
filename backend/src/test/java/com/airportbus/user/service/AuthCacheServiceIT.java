package com.airportbus.user.service;

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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=false",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class AuthCacheServiceIT {
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

    @Autowired AuthCacheService cache;

    @Test
    void registerCodeVerifyOnce() {
        String code = cache.issueRegisterCode("a@b.com");
        assertThat(cache.verifyAndConsumeRegisterCode("a@b.com", code)).isTrue();
        assertThat(cache.verifyAndConsumeRegisterCode("a@b.com", code)).isFalse();
    }

    @Test
    void wrongCodeFails() {
        cache.issueRegisterCode("c@d.com");
        assertThat(cache.verifyAndConsumeRegisterCode("c@d.com", "000000")).isFalse();
    }

    @Test
    void resendRateLimited() {
        cache.issueRegisterCode("e@f.com");
        assertThat(cache.canSendRegisterCode("e@f.com")).isFalse();
    }

    @Test
    void resetTokenRoundTrip() {
        String t = cache.issueResetToken(7L);
        assertThat(cache.consumeResetToken(t)).isEqualTo(7L);
        assertThat(cache.consumeResetToken(t)).isNull();
    }

    @Test
    void loginFailLockout() {
        for (int i = 0; i < 10; i++) cache.recordLoginFail("u1");
        assertThat(cache.isLoginLocked("u1")).isTrue();
        cache.clearLoginFail("u1");
        assertThat(cache.isLoginLocked("u1")).isFalse();
    }
}
