package com.airportbus.ticket;

import com.airportbus.common.ApiException;
import com.airportbus.ticket.api.dto.TicketDtos.CreateTicketRequest;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
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
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class TicketServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired TicketService service;
    @Autowired UserMapper users;

    private long newUser(String name) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        return u.id;
    }

    @Test
    void createOpensTicketWithFirstUserReply() {
        long uid = newUser("u_create");
        TicketThread th = service.create(uid, "vie-vab1", "价格似乎从 €11 变成了 €13");
        assertThat(th.ticket().status()).isEqualTo("OPEN");
        assertThat(th.ticket().relatedSourceId()).isEqualTo("vie-vab1");
        assertThat(th.replies()).hasSize(1);
        assertThat(th.replies().get(0).authorType()).isEqualTo("USER");
        assertThat(th.replies().get(0).body()).isEqualTo("价格似乎从 €11 变成了 €13");
    }

    @Test
    void createWithBlankBodyRejected() {
        long uid = newUser("u_blank");
        assertThatThrownBy(() -> service.create(uid, null, "  ")).isInstanceOf(ApiException.class);
    }

    @Test
    void createWithUnknownSourceRejected() {
        long uid = newUser("u_badsrc");
        assertThatThrownBy(() -> service.create(uid, "nope-xxx", "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void getMineForbiddenForOtherUser() {
        long owner = newUser("u_owner");
        long other = newUser("u_other");
        long tid = service.create(owner, null, "我的工单").ticket().id();
        assertThatThrownBy(() -> service.getMine(other, tid)).isInstanceOf(ApiException.class);
        assertThat(service.getMine(owner, tid).ticket().id()).isEqualTo(tid);
    }

    @Test
    void listMineReturnsOnlyOwn() {
        long a = newUser("u_lista"); long b = newUser("u_listb");
        service.create(a, null, "a1"); service.create(b, null, "b1");
        assertThat(service.listMine(a, null, 20, 0)).allMatch(t -> t.userId() == a);
    }

    @Test
    void userReplyReopensAndAppends() {
        long uid = newUser("u_reply");
        long tid = service.create(uid, null, "建单").ticket().id();
        // 先人为关闭,验证回复能重开
        service.closeAsUser(uid, tid);
        assertThat(service.getMine(uid, tid).ticket().status()).isEqualTo("CLOSED");
        TicketThread th = service.replyAsUser(uid, tid, "补充一句");
        assertThat(th.ticket().status()).isEqualTo("OPEN");
        assertThat(th.replies()).hasSize(2);
        assertThat(th.replies().get(1).authorType()).isEqualTo("USER");
    }

    @Test
    void userReplyForbiddenForOther() {
        long owner = newUser("u_ro"); long other = newUser("u_ro2");
        long tid = service.create(owner, null, "x").ticket().id();
        assertThatThrownBy(() -> service.replyAsUser(other, tid, "hi")).isInstanceOf(ApiException.class);
    }

    @Test
    void userCloseForbiddenForOther() {
        long owner = newUser("u_co"); long other = newUser("u_co2");
        long tid = service.create(owner, null, "x").ticket().id();
        assertThatThrownBy(() -> service.closeAsUser(other, tid)).isInstanceOf(ApiException.class);
    }
}
