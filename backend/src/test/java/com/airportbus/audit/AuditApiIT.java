package com.airportbus.audit;

import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc @Testcontainers
class AuditApiIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper om;
    @Autowired UserMapper users; @Autowired JwtService jwt; @Autowired PasswordEncoder encoder;

    private String superToken() throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"admin\",\"password\":\"admin12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
    private String userToken(String name) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash=encoder.encode("x");
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=true; users.insertUser(u);
        return jwt.issueAccess(u.id, "USER");
    }
    private String createBody(String sourceId) {
        return ("{\"sourceId\":\"%s\",\"airportCode\":\"VIE\",\"data\":{\"route\":\"VAB Z\",\"price\":\"€11\"," +
                "\"stops\":[\"A\"],\"schedules\":[],\"alerts\":[],\"images\":[],\"files\":[]}}").formatted(sourceId);
    }

    @Test void anonymous_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());
    }

    @Test void regularUser_is403() throws Exception {
        String u = userToken("audit_user1");
        mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+u))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
    }

    @Test void create_thenAuditListHasRow() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-audit1")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit?action=CREATE_BUS").header("Authorization","Bearer "+su))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATE_BUS"))
                .andExpect(jsonPath("$[0].targetId").value("vie-audit1"));
    }
}
