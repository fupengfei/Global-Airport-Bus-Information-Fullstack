package com.airportbus.ticket;

import com.airportbus.common.ApiException;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "airportbus.message.reconcile-delay-ms=3600000",
        "airportbus.correction.rate-limit-max=2",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class CorrectionServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired CorrectionService service;

    @Test
    void submitWithValidSourceCreatesOpenReport() {
        CorrectionReport r = service.submit(new SubmitCorrectionRequest("vie-vab1", "末班车其实是23:30", "a@b.com"), "1.1.1.1");
        assertThat(r.id).isPositive();
        assertThat(r.status).isEqualTo("OPEN");
        assertThat(r.relatedSourceId).isEqualTo("vie-vab1");
    }

    @Test
    void blankDescriptionRejected() {
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest(null, "  ", null), "2.2.2.2"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownSourceRejected() {
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest("nope-xxx", "x", null), "3.3.3.3"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void rateLimitBlocksAfterMax() {
        String ip = "9.9.9.9";
        service.submit(new SubmitCorrectionRequest(null, "1", null), ip);
        service.submit(new SubmitCorrectionRequest(null, "2", null), ip);
        // rate-limit-max=2 → 第三条拒
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest(null, "3", null), ip))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void adminUpdateStatusPersists() {
        CorrectionReport r = service.submit(new SubmitCorrectionRequest(null, "fix me", null), "4.4.4.4");
        CorrectionReport up = service.updateStatus(r.id, new UpdateCorrectionRequest("RESOLVED", "已核实并更新"), "admin");
        assertThat(up.status).isEqualTo("RESOLVED");
        assertThat(up.resolutionNote).isEqualTo("已核实并更新");
    }
}
