package com.airportbus.user.service;

import com.airportbus.common.ApiException;
import com.airportbus.user.api.dto.AuthDtos.*;
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
class AuthServiceIT {
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

    @Autowired AuthService auth;
    @Autowired AuthCacheService cache;

    private TokenPair register(String u, String e) {
        String code = cache.issueRegisterCode(e);
        return auth.register(new RegisterReq(u, e, code, "password123"));
    }

    @Test
    void registerThenLoginThenMe() {
        TokenPair t = register("alice", "alice@x.com");
        assertThat(t.accessToken()).isNotBlank();
        TokenPair l = auth.login(new LoginReq("alice", "password123"));
        assertThat(l.refreshToken()).isNotBlank();
        MeView me = auth.me(auth.parseAccess(l.accessToken()).userId());
        assertThat(me.username()).isEqualTo("alice");
    }

    @Test
    void duplicateUsernameConflicts() {
        register("bob", "bob@x.com");
        String code = cache.issueRegisterCode("bob2@x.com");
        assertThatThrownBy(() -> auth.register(new RegisterReq("bob", "bob2@x.com", code, "password123")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void wrongCodeRejected() {
        assertThatThrownBy(() -> auth.register(new RegisterReq("carol", "carol@x.com", "000000", "password123")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void wrongPasswordRejected() {
        register("dave", "dave@x.com");
        assertThatThrownBy(() -> auth.login(new LoginReq("dave", "nope")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refreshRotatesAndOldRevoked() {
        TokenPair t = register("erin", "erin@x.com");
        TokenPair n = auth.refresh(new RefreshReq(t.refreshToken()));
        assertThat(n.refreshToken()).isNotEqualTo(t.refreshToken());
        assertThatThrownBy(() -> auth.refresh(new RefreshReq(t.refreshToken())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void forgotResetThenOldRefreshDead() {
        TokenPair t = register("frank", "frank@x.com");
        auth.forgot(new ForgotReq("frank@x.com"));
        String token = cache.issueResetToken(auth.parseAccess(t.accessToken()).userId());
        auth.reset(new ResetReq(token, "newpassword1"));
        assertThatThrownBy(() -> auth.refresh(new RefreshReq(t.refreshToken())))
                .isInstanceOf(ApiException.class);
        assertThat(auth.login(new LoginReq("frank", "newpassword1")).accessToken()).isNotBlank();
    }
}
