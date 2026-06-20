package com.airportbus.user.api;

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
class FavoriteApiIT {
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

    private String registerAndGetToken(String name) throws Exception {
        String code = cache.issueRegisterCode(name + "@x.com");
        String body = """
            {"username":"%s","email":"%s@x.com","code":"%s","password":"password123"}"""
                .formatted(name, name, code);
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode t = om.readTree(res);
        return t.get("accessToken").asText();
    }

    @Test
    void favoriteFlow_put_ids_list_delete() throws Exception {
        String tok = registerAndGetToken("favapi1");

        mvc.perform(put("/api/v1/buses/vie-vab1/favorite").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));

        mvc.perform(get("/api/v1/favorites/ids").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("vie-vab1"));

        mvc.perform(get("/api/v1/favorites").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceId").value("vie-vab1"))
                .andExpect(jsonPath("$[0].stops").isArray());

        mvc.perform(delete("/api/v1/buses/vie-vab1/favorite").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mvc.perform(get("/api/v1/favorites/ids").header("Authorization", "Bearer " + tok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anonymous_is401() throws Exception {
        mvc.perform(put("/api/v1/buses/vie-vab1/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void favoriteUnknownBus_is404() throws Exception {
        String tok = registerAndGetToken("favapi2");
        mvc.perform(put("/api/v1/buses/no-such-bus/favorite").header("Authorization", "Bearer " + tok))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"));
    }
}
