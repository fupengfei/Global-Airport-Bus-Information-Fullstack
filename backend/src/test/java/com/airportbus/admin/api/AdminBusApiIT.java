package com.airportbus.admin.api;

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
class AdminBusApiIT {
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
    private String operatorToken(String name) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash=encoder.encode("x");
        u.locale="zh-CN"; u.role="OPERATOR"; u.emailVerified=true; users.insertUser(u);
        return jwt.issueAccess(u.id, "OPERATOR");
    }
    private String body(String price, int version) {
        return ("{\"airportCode\":\"VIE\",\"version\":%d,\"data\":{\"route\":\"VAB Z\",\"price\":\"%s\"," +
                "\"stops\":[\"A\"],\"schedules\":[],\"alerts\":[],\"images\":[],\"files\":[]}}").formatted(version, price);
    }
    private String createBody(String sourceId, String price) {
        return ("{\"sourceId\":\"%s\",\"airportCode\":\"VIE\",\"data\":{\"route\":\"VAB Z\",\"price\":\"%s\"," +
                "\"stops\":[\"A\"],\"schedules\":[],\"alerts\":[],\"images\":[],\"files\":[]}}").formatted(sourceId, price);
    }

    @Test void anonymous_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/buses/tree")).andExpect(status().isUnauthorized());
    }

    @Test void operator_canCreateAndUpdate_butNotDelete() throws Exception {
        String op = operatorToken("op_bus1");
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+op)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api1","€11")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/admin/buses/vie-api1").header("Authorization","Bearer "+op)
                .contentType(MediaType.APPLICATION_JSON).content(body("€13",1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(delete("/api/v1/admin/buses/vie-api1").header("Authorization","Bearer "+op))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
    }

    @Test void staleVersion_409() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api2","€11"))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/buses/vie-api2").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(body("€13",999)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUS_VERSION_CONFLICT"));
    }

    @Test void superAdmin_canDelete() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api3","€11"))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/buses/vie-api3").header("Authorization","Bearer "+su)).andExpect(status().isOk());
    }

    @Test void versions_and_rollback() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api4","€11"))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/buses/vie-api4").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(body("€13",1))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/buses/vie-api4/versions").header("Authorization","Bearer "+su))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/v1/admin/buses/vie-api4/versions/1/rollback").header("Authorization","Bearer "+su))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.data.price").value("€11"));
    }

    @Test void verify_ok() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api5","€11"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/buses/vie-api5/verify").header("Authorization","Bearer "+su)).andExpect(status().isOk());
    }

    @Test void get_returnsEditView() throws Exception {
        String su = superToken();
        mvc.perform(post("/api/v1/admin/buses").header("Authorization","Bearer "+su)
                .contentType(MediaType.APPLICATION_JSON).content(createBody("vie-api6","€11"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/buses/vie-api6").header("Authorization","Bearer "+su))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("vie-api6"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.data.price").value("€11"));
    }
}
