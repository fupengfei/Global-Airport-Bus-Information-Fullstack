package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
@Import(AuditAspectIT.ProbeConfig.class)
class AuditAspectIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired AuditService auditService;
    @Autowired Probe probe;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    static class Probe {
        @Audited(action = "UPDATE_BUS", target = "bus")
        public void touch(String sourceId) { /* no-op */ }
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        Probe probe() { return new Probe(); }
    }

    @Test void aspect_writesAuditRow_withActorFromCurrentUser() {
        long before = auditService.list(null, null, 50).size();
        CurrentUser.set(new JwtPrincipal(42L, "SUPER_ADMIN"));
        probe.touch("vie-vab1");
        var rows = auditService.list(null, null, 50);
        assertThat(rows.size()).isEqualTo(before + 1);
        assertThat(rows.get(0).action()).isEqualTo("UPDATE_BUS");
        assertThat(rows.get(0).targetId()).isEqualTo("vie-vab1");
        assertThat(rows.get(0).actorId()).isEqualTo(42L);
    }
}
