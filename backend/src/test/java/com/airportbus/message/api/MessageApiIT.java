package com.airportbus.message.api;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.bus.service.ChangedSummary;
import com.airportbus.message.MessageService;
import com.airportbus.user.service.AuthCacheService;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "airportbus.message.reconcile-delay-ms=3600000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureMockMvc @Testcontainers
class MessageApiIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper om;
    @Autowired AuthCacheService cache; @Autowired MessageService messages; @Autowired BusWriteMapper busWrite;

    private String token(String name) throws Exception {
        String code = cache.issueRegisterCode(name + "@x.com");
        String body = "{\"username\":\"%s\",\"email\":\"%s@x.com\",\"code\":\"%s\",\"password\":\"password123\"}".formatted(name, name, code);
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }

    @Test void anonymous_is401() throws Exception {
        mvc.perform(get("/api/v1/messages/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test void unreadCount_list_markRead_delete_flow() throws Exception {
        String tok = token("msgapi1");
        // 该用户收藏 vie-vab1(订阅),再对其订阅扇出一条
        mvc.perform(put("/api/v1/buses/vie-vab1/favorite").header("Authorization","Bearer "+tok)).andExpect(status().isOk());
        long busId = busWrite.findBusId("vie-vab1");
        var sum = new ChangedSummary(List.of(new ChangedSummary.FieldChange("price","€11","€13")), List.of());
        messages.fanOutUpdated(new BusUpdatedEvent(busId, "vie-vab1", "VAB 1", 123, "h0","h1", sum));

        mvc.perform(get("/api/v1/messages/unread-count").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        String listRes = mvc.perform(get("/api/v1/messages").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].templateCode").value("BUS_UPDATED"))
                .andExpect(jsonPath("$[0].params.route").value("VAB 1"))
                .andReturn().getResponse().getContentAsString();
        long id = om.readTree(listRes).get(0).get("id").asLong();
        mvc.perform(post("/api/v1/messages/read").header("Authorization","Bearer "+tok)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":["+id+"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/messages/unread-count").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(delete("/api/v1/messages/"+id).header("Authorization","Bearer "+tok)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/messages").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
}
