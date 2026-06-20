package com.airportbus.admin.api;

import com.airportbus.user.service.AuthCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminStatsApiIT {
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

    @Autowired MockMvc mvc;
    @Autowired AuthCacheService cache;
    @Autowired ObjectMapper om;

    /** 种子 admin / admin12345(seed.enabled=true 时 AdminSeedRunner 建)。 */
    private String adminToken() throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin\",\"password\":\"admin12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }

    private String userToken(String name) throws Exception {
        String code = cache.issueRegisterCode(name + "@x.com");
        String body = "{\"username\":\"%s\",\"email\":\"%s@x.com\",\"code\":\"%s\",\"password\":\"password123\"}"
                .formatted(name, name, code);
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = om.readTree(res);
        return t.get("accessToken").asText();
    }

    @Test
    void anonymous_is401() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void regularUser_is403() throws Exception {
        String tok = userToken("adminit_user");
        mvc.perform(get("/api/v1/admin/stats/overview").header("Authorization", "Bearer " + tok))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
    }

    @Test
    void admin_overview_returnsCounts() throws Exception {
        String tok = adminToken();
        mvc.perform(get("/api/v1/admin/stats/overview").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.newUsersThisWeek", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalFavorites", greaterThanOrEqualTo(0)));
    }

    @Test
    void admin_registrations_returns7ContinuousDays() throws Exception {
        String tok = adminToken();
        mvc.perform(get("/api/v1/admin/stats/registrations").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[0].count", greaterThanOrEqualTo(0)));
    }

    @Test
    void admin_subscriptions_and_hotness_areShaped() throws Exception {
        String tok = adminToken();
        mvc.perform(get("/api/v1/admin/stats/subscriptions").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRoutes").isArray())
                .andExpect(jsonPath("$.topAirports").isArray())
                .andExpect(jsonPath("$.topCities").isArray());
        mvc.perform(get("/api/v1/admin/stats/hotness?window=7d").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
