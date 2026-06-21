package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
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

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class BusCommandServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired BusCommandService cmd;
    @Autowired BusWriteMapper writeMapper;
    @Autowired BusVersionMapper versionMapper;

    private long airportIdOfVie() { return writeMapper.findAirportId("VIE"); }

    private BusInput input(String price) {
        return new BusInput("VAB X", "西站", "ÖBB", null, "40min", price, "03:00-24:00", null,
                List.of("A", "B"),
                List.of(new BusDetailDto.Schedule("all day", "30min", null)),
                List.of(), List.of(), List.of());
    }

    @Test void create_setsVersion1_andSnapshot() {
        BusView v = cmd.save("vie-cmd1", airportIdOfVie(), input("€11"), null, "admin", false);
        assertThat(v.version()).isEqualTo(1);
        long brId = writeMapper.findBusId("vie-cmd1");
        assertThat(versionMapper.listVersions(brId)).hasSize(1);
    }

    @Test void update_changesHash_bumpsVersion_addsSnapshot() {
        cmd.save("vie-cmd2", airportIdOfVie(), input("€11"), null, "admin", false);
        long brId = writeMapper.findBusId("vie-cmd2");
        int v1 = writeMapper.selectVersionHash("vie-cmd2").version();
        BusView v = cmd.save("vie-cmd2", airportIdOfVie(), input("€13"), v1, "admin", false);
        assertThat(v.version()).isEqualTo(v1 + 1);
        assertThat(versionMapper.listVersions(brId)).hasSize(2);
    }

    @Test void update_unchanged_isNoop() {
        cmd.save("vie-cmd3", airportIdOfVie(), input("€11"), null, "admin", false);
        int v1 = writeMapper.selectVersionHash("vie-cmd3").version();
        long brId = writeMapper.findBusId("vie-cmd3");
        BusView v = cmd.save("vie-cmd3", airportIdOfVie(), input("€11"), v1, "admin", false);
        assertThat(v.version()).isEqualTo(v1);
        assertThat(versionMapper.listVersions(brId)).hasSize(1);
    }

    @Test void update_staleVersion_conflicts409() {
        cmd.save("vie-cmd4", airportIdOfVie(), input("€11"), null, "admin", false);
        assertThatThrownBy(() -> cmd.save("vie-cmd4", airportIdOfVie(), input("€13"), 999, "admin", false))
                .isInstanceOf(ApiException.class)
                .extracting("code").hasToString("BUS_VERSION_CONFLICT");
    }

    @Test void verify_setsTimestamp_noVersionBump_noSnapshot() {
        cmd.save("vie-cmd5", airportIdOfVie(), input("€11"), null, "admin", false);
        long brId = writeMapper.findBusId("vie-cmd5");
        int v1 = writeMapper.selectVersionHash("vie-cmd5").version();
        cmd.verify("vie-cmd5", "admin");
        assertThat(writeMapper.selectVersionHash("vie-cmd5").version()).isEqualTo(v1);
        assertThat(versionMapper.listVersions(brId)).hasSize(1);
    }

    @Test void delete_softDeletes() {
        cmd.save("vie-cmd6", airportIdOfVie(), input("€11"), null, "admin", false);
        cmd.delete("vie-cmd6", "admin");
        assertThat(writeMapper.selectVersionHash("vie-cmd6")).isNull();
    }

    @Test void rollback_restoresAsNewVersion() {
        cmd.save("vie-cmd7", airportIdOfVie(), input("€11"), null, "admin", false);
        int v1 = writeMapper.selectVersionHash("vie-cmd7").version();
        cmd.save("vie-cmd7", airportIdOfVie(), input("€99"), v1, "admin", false);
        BusView rolled = cmd.rollback("vie-cmd7", v1, "admin");
        assertThat(rolled.data().price()).isEqualTo("€11");
        assertThat(rolled.version()).isEqualTo(3);
        long brId = writeMapper.findBusId("vie-cmd7");
        assertThat(versionMapper.listVersions(brId)).hasSize(3);
    }
}
