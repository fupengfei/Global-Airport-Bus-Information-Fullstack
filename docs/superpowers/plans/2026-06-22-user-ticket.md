# 用户建议工单(#7 切片 C - C2)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录用户提交建议/纠错工单并与管理员气泡线程对话,状态机 `OPEN→REPLIED→CLOSED`,管理员回复时给用户发 `TICKET_REPLIED` 站内信。

**Architecture:** 在已交付的 `com.airportbus.ticket` 模块(C1 已落地 Correction*)内新增 `ticket` 子系统:`ticket` + `ticket_reply` 两表,`TicketService` 持有全部状态机守卫,用户/管理员各一个控制器(E10 author 服务端取)。管理员回复在事务内同步调用切片 B 的 `MessageService.notifyTicketReplied`(单收件人定向,不走 BusEvent 异步扇出)。前端 `/tickets` 页(store 驱动)+ `AdminTicketsPage`(直调 api)+ `InboxPage/renderMessage` 扩展 `TICKET_REPLIED`。

**Tech Stack:** Spring Boot 模块化单体、MyBatis(`#{}` only)、Flyway(V10)、Testcontainers(MySQL+Redis)、Vue 3 + Pinia + vue-i18n + Element Plus(仅 admin chunk)、vitest。

**复用既有件:** `MessageService`(切片 B)、`CurrentUser.require()/requireAdmin()`、`@Audited` 切面、`BusWriteMapper.selectVersionHash`(source 存在性)、`ApiException/ErrorCode/GlobalExceptionHandler`、设计 token / `design/tickets.html`。

---

## 文件结构

**后端(新建,除非标注 Modify):**
- `backend/src/main/resources/db/migration/V10__ticket.sql` — 建表
- `backend/src/main/java/com/airportbus/ticket/Ticket.java` — 工单记录
- `backend/src/main/java/com/airportbus/ticket/TicketReply.java` — 回复记录
- `backend/src/main/java/com/airportbus/ticket/TicketThread.java` — 工单+线程视图
- `backend/src/main/java/com/airportbus/ticket/api/dto/TicketDtos.java` — 请求 DTO
- `backend/src/main/java/com/airportbus/ticket/mapper/TicketMapper.java` + `resources/mapper/TicketMapper.xml`
- `backend/src/main/java/com/airportbus/ticket/mapper/TicketReplyMapper.java` + `resources/mapper/TicketReplyMapper.xml`
- `backend/src/main/java/com/airportbus/ticket/TicketService.java`
- `backend/src/main/java/com/airportbus/ticket/api/TicketController.java`(用户)
- `backend/src/main/java/com/airportbus/ticket/api/AdminTicketController.java`(管理员)
- Modify `backend/src/main/java/com/airportbus/common/ErrorCode.java` — 加 `TICKET_NOT_FOUND`/`TICKET_FORBIDDEN`
- Modify `backend/src/main/java/com/airportbus/message/MessageService.java` — 加 `notifyTicketReplied`

**前端(新建,除非标注 Modify):**
- `frontend/src/api/tickets.ts`
- `frontend/src/stores/tickets.ts`(用户页)
- `frontend/src/pages/TicketsPage.vue`(`/tickets`)
- `frontend/src/pages/admin/AdminTicketsPage.vue`(`/admin/tickets`)
- Modify `frontend/src/components/renderMessage.ts` — `TICKET_REPLIED` 分支 + `link`
- Modify `frontend/src/api/messages.ts` — `MessageParams.ticketId`
- Modify `frontend/src/pages/InboxPage.vue` — 用 `view.link`
- Modify `frontend/src/router/index.ts` — `/tickets` + `admin/tickets`
- Modify `frontend/src/App.vue` — 顶栏「工单」入口
- Modify `frontend/src/components/admin/AdminLayout.vue` — admin 侧栏「工单队列」
- Modify `frontend/src/i18n/locales/{zh-CN,en,de}.ts` — ticket/admin/msg 文案
- 测试:`frontend/src/test/tickets.api.spec.ts`、`tickets.store.spec.ts`、`TicketsPage.spec.ts`、`AdminTicketsPage.spec.ts`、Modify `renderMessage.spec.ts`

---

## Task 1: V10 迁移(ticket + ticket_reply)

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__ticket.sql`

- [ ] **Step 1: 写迁移 SQL(每列带 COMMENT,表也带;含审计列 + 软删)**

```sql
-- V10__ticket.sql  用户建议工单 + 气泡线程回复(状态机 OPEN→REPLIED→CLOSED)
CREATE TABLE ticket (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  user_id           BIGINT       NOT NULL                            COMMENT '提单人(app_user.id)',
  related_source_id VARCHAR(64)  NULL                                COMMENT '关联线路业务键source_id(可空;填了由服务端校验存在)',
  status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN'             COMMENT '状态:OPEN/REPLIED/CLOSED',
  last_reply_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '最后一条回复时间(列表排序)',
  created_by        VARCHAR(64)  NULL                                COMMENT '创建人(user:{id})',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by        VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_ticket_user (user_id, deleted),
  KEY idx_ticket_status (status, deleted),
  CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户建议工单';

CREATE TABLE ticket_reply (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  ticket_id   BIGINT       NOT NULL                            COMMENT '所属工单(ticket.id)',
  author_type VARCHAR(8)   NOT NULL                            COMMENT '作者类型:USER/ADMIN(服务端从认证主体取,E10)',
  author_id   BIGINT       NOT NULL                            COMMENT '作者ID(app_user.id 或管理员user.id)',
  body        TEXT         NOT NULL                            COMMENT '回复正文(纯文本)',
  created_by  VARCHAR(64)  NULL                                COMMENT '创建人(user:{id}/admin:{id})',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by  VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_reply_ticket (ticket_id, deleted, id),
  CONSTRAINT fk_reply_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单回复(气泡线程)';
```

- [ ] **Step 2: 验证迁移命名顺序**

Run: `ls backend/src/main/resources/db/migration/`
Expected: 看到 `V10__ticket.sql`,排在 `V9__correction_report.sql` 之后(Flyway 按版本号递增)。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__ticket.sql
git commit -m "feat(ticket): V10 ticket + ticket_reply 迁移 (#7 C2)"
```

---

## Task 2: 领域记录 + DTO + 错误码

**Files:**
- Create: `backend/src/main/java/com/airportbus/ticket/Ticket.java`
- Create: `backend/src/main/java/com/airportbus/ticket/TicketReply.java`
- Create: `backend/src/main/java/com/airportbus/ticket/TicketThread.java`
- Create: `backend/src/main/java/com/airportbus/ticket/api/dto/TicketDtos.java`
- Modify: `backend/src/main/java/com/airportbus/common/ErrorCode.java`

- [ ] **Step 1: 写 Ticket 记录(MyBatis 按规范构造器参数名映射,与 `Message` record 同款)**

```java
package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 工单(对外资源)。 */
public record Ticket(long id, long userId, String relatedSourceId, String status,
                     LocalDateTime lastReplyAt, LocalDateTime createdAt) {}
```

- [ ] **Step 2: 写 TicketReply 记录**

```java
package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 工单回复(气泡线程一条)。 */
public record TicketReply(long id, long ticketId, String authorType, long authorId,
                          String body, LocalDateTime createdAt) {}
```

- [ ] **Step 3: 写 TicketThread 视图(工单 + 全部回复)**

```java
package com.airportbus.ticket;

import java.util.List;

/** 工单详情:工单本体 + 时间正序回复线程。 */
public record TicketThread(Ticket ticket, List<TicketReply> replies) {}
```

- [ ] **Step 4: 写请求 DTO**

```java
package com.airportbus.ticket.api.dto;

public class TicketDtos {
    /** 用户建单:关联线路可选 + 问题/建议正文。 */
    public record CreateTicketRequest(String sourceId, String body) {}
    /** 回复(用户/管理员通用):仅正文,author 服务端从主体取(E10)。 */
    public record ReplyRequest(String body) {}
}
```

- [ ] **Step 5: 加错误码**

In `backend/src/main/java/com/airportbus/common/ErrorCode.java`, 在 `CORRECTION_NOT_FOUND(HttpStatus.NOT_FOUND),` 行后新增:

```java
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    TICKET_FORBIDDEN(HttpStatus.FORBIDDEN),
```

- [ ] **Step 6: 编译通过**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS(无引用错误)。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/Ticket.java \
        backend/src/main/java/com/airportbus/ticket/TicketReply.java \
        backend/src/main/java/com/airportbus/ticket/TicketThread.java \
        backend/src/main/java/com/airportbus/ticket/api/dto/TicketDtos.java \
        backend/src/main/java/com/airportbus/common/ErrorCode.java
git commit -m "feat(ticket): 工单领域记录 + DTO + 错误码 (#7 C2)"
```

---

## Task 3: Mapper + XML

**Files:**
- Create: `backend/src/main/java/com/airportbus/ticket/mapper/TicketMapper.java`
- Create: `backend/src/main/java/com/airportbus/ticket/mapper/TicketReplyMapper.java`
- Create: `backend/src/main/resources/mapper/TicketMapper.xml`
- Create: `backend/src/main/resources/mapper/TicketReplyMapper.xml`

> 注:`com.airportbus.ticket.mapper` 包已被现有 `@MapperScan` 覆盖(C1 的 `CorrectionMapper` 在此包,已验证可扫到)。无需改扫描配置。

- [ ] **Step 1: 写 TicketMapper 接口**

```java
package com.airportbus.ticket.mapper;

import com.airportbus.ticket.Ticket;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface TicketMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: userId,relatedSourceId,createdBy。 */
    int insert(Map<String, Object> row);

    Ticket selectById(@Param("id") long id);

    /** 我的工单(按 user 过滤,status 可空)。 */
    List<Ticket> selectByUser(@Param("userId") long userId, @Param("status") String status,
                              @Param("limit") int limit, @Param("offset") int offset);

    /** 管理员队列(status 可空)。 */
    List<Ticket> selectPage(@Param("status") String status,
                            @Param("limit") int limit, @Param("offset") int offset);

    /** 回复后:置状态 + last_reply_at=NOW()。 */
    int updateStatusAndLastReply(@Param("id") long id, @Param("status") String status);

    /** 关闭:仅置状态(不动 last_reply_at)。 */
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
```

- [ ] **Step 2: 写 TicketReplyMapper 接口**

```java
package com.airportbus.ticket.mapper;

import com.airportbus.ticket.TicketReply;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface TicketReplyMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: ticketId,authorType,authorId,body,createdBy。 */
    int insert(Map<String, Object> row);

    /** 按工单取线程(时间正序)。 */
    List<TicketReply> selectByTicket(@Param("ticketId") long ticketId);
}
```

- [ ] **Step 3: 写 TicketMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.ticket.mapper.TicketMapper">

  <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO ticket (user_id, related_source_id, created_by)
    VALUES (#{userId}, #{relatedSourceId}, #{createdBy})
  </insert>

  <select id="selectById" resultType="com.airportbus.ticket.Ticket">
    SELECT id, user_id AS userId, related_source_id AS relatedSourceId, status,
           last_reply_at AS lastReplyAt, created_at AS createdAt
    FROM ticket WHERE id=#{id} AND deleted=0
  </select>

  <select id="selectByUser" resultType="com.airportbus.ticket.Ticket">
    SELECT id, user_id AS userId, related_source_id AS relatedSourceId, status,
           last_reply_at AS lastReplyAt, created_at AS createdAt
    FROM ticket
    WHERE deleted=0 AND user_id=#{userId}
    <if test="status != null and status != ''"> AND status=#{status} </if>
    ORDER BY last_reply_at DESC, id DESC
    LIMIT #{limit} OFFSET #{offset}
  </select>

  <select id="selectPage" resultType="com.airportbus.ticket.Ticket">
    SELECT id, user_id AS userId, related_source_id AS relatedSourceId, status,
           last_reply_at AS lastReplyAt, created_at AS createdAt
    FROM ticket
    WHERE deleted=0
    <if test="status != null and status != ''"> AND status=#{status} </if>
    ORDER BY last_reply_at DESC, id DESC
    LIMIT #{limit} OFFSET #{offset}
  </select>

  <update id="updateStatusAndLastReply">
    UPDATE ticket SET status=#{status}, last_reply_at=NOW() WHERE id=#{id} AND deleted=0
  </update>

  <update id="updateStatus">
    UPDATE ticket SET status=#{status} WHERE id=#{id} AND deleted=0
  </update>
</mapper>
```

- [ ] **Step 4: 写 TicketReplyMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.ticket.mapper.TicketReplyMapper">

  <insert id="insert" parameterType="map" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO ticket_reply (ticket_id, author_type, author_id, body, created_by)
    VALUES (#{ticketId}, #{authorType}, #{authorId}, #{body}, #{createdBy})
  </insert>

  <select id="selectByTicket" resultType="com.airportbus.ticket.TicketReply">
    SELECT id, ticket_id AS ticketId, author_type AS authorType, author_id AS authorId,
           body, created_at AS createdAt
    FROM ticket_reply
    WHERE deleted=0 AND ticket_id=#{ticketId}
    ORDER BY id ASC
  </select>
</mapper>
```

- [ ] **Step 5: 编译通过**

Run: `cd backend && ./mvnw -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/mapper/ backend/src/main/resources/mapper/TicketMapper.xml backend/src/main/resources/mapper/TicketReplyMapper.xml
git commit -m "feat(ticket): TicketMapper/TicketReplyMapper + xml (#7 C2)"
```

---

## Task 4: MessageService.notifyTicketReplied(站内信单人定向)

**Files:**
- Modify: `backend/src/main/java/com/airportbus/message/MessageService.java`
- Test: `backend/src/test/java/com/airportbus/message/MessageServiceIT.java`

- [ ] **Step 1: 写失败测试(在 MessageServiceIT 末尾加一个用例 + helper 建用户)**

在 `MessageServiceIT` 类内新增(`users`/`UserMapper` 已 autowired,`AppUser` 已 import):

```java
    @Test
    void notifyTicketRepliedInsertsSingleUnread() {
        AppUser u = new AppUser(); u.username="ticketee"; u.email="ticketee@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);

        service.notifyTicketReplied(u.id, 1042L, 99L);

        assertThat(service.unreadCount(u.id)).isEqualTo(1);
        List<Message> list = service.list(u.id, 20, 0);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).templateCode()).isEqualTo("TICKET_REPLIED");
        assertThat(list.get(0).params()).contains("\"ticketId\":1042");
        assertThat(list.get(0).relatedSourceId()).isNull();
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw -q -Dtest=MessageServiceIT#notifyTicketRepliedInsertsSingleUnread test`
Expected: 编译失败 — `notifyTicketReplied` 方法不存在。

- [ ] **Step 3: 实现 notifyTicketReplied**

在 `MessageService` 类内(`backfill` 方法之后)新增。复用既有 `mapper.batchInsert` 幂等路径与 `counter.invalidate`;`relatedBusRouteId` 置 null(工单消息不绑线路):

```java
    /** 工单回复站内信:单人定向直插(非 BusEvent 异步扇出)。dedup 按 replyId 幂等。 */
    @Transactional
    public void notifyTicketReplied(long userId, long ticketId, long replyId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ticketId", ticketId);
        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("templateCode", "TICKET_REPLIED");
        row.put("paramsJson", writeJson(params));
        row.put("relatedBusRouteId", null);
        row.put("dedupKey", "ticket:" + ticketId + ":reply:" + replyId);
        row.put("actor", "system");
        mapper.batchInsert(List.of(row));
        counter.invalidate(userId);
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && ./mvnw -q -Dtest=MessageServiceIT#notifyTicketRepliedInsertsSingleUnread test`
Expected: PASS(1 条未读、TICKET_REPLIED、params 含 ticketId、relatedSourceId 为 null)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/message/MessageService.java backend/src/test/java/com/airportbus/message/MessageServiceIT.java
git commit -m "feat(message): notifyTicketReplied 单人定向站内信 (#7 C2)"
```

---

## Task 5: TicketService —— 建单 + 我的列表/详情(用户侧只读)

**Files:**
- Create: `backend/src/main/java/com/airportbus/ticket/TicketService.java`
- Test: `backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java`

- [ ] **Step 1: 写失败测试(Testcontainers 样板照搬 CorrectionServiceIT)**

```java
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
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: 编译失败 — `TicketService` 不存在。

- [ ] **Step 3: 实现 TicketService 的建单/查询部分(本任务先不含 reply/close,留 Task 6/7)**

```java
package com.airportbus.ticket;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.message.MessageService;
import com.airportbus.ticket.mapper.TicketMapper;
import com.airportbus.ticket.mapper.TicketReplyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 用户建议工单:建单/线程查询 + 状态机回复/关闭。author 一律服务端从主体取(E10)。 */
@Service
public class TicketService {
    private final TicketMapper tickets;
    private final TicketReplyMapper replies;
    private final BusWriteMapper busWrite;
    private final MessageService messages;

    public TicketService(TicketMapper tickets, TicketReplyMapper replies,
                         BusWriteMapper busWrite, MessageService messages) {
        this.tickets = tickets; this.replies = replies; this.busWrite = busWrite; this.messages = messages;
    }

    @Transactional
    public TicketThread create(long userId, String sourceId, String body) {
        requireBody(body);
        String src = normalizeSource(sourceId);
        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("relatedSourceId", src);
        row.put("createdBy", "user:" + userId);
        tickets.insert(row);
        long ticketId = ((Number) row.get("id")).longValue();
        insertReply(ticketId, "USER", userId, body.trim());
        return thread(ticketId);
    }

    public List<Ticket> listMine(long userId, String status, int limit, int offset) {
        return tickets.selectByUser(userId, status, page(limit), Math.max(offset, 0));
    }

    public TicketThread getMine(long userId, long ticketId) {
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        return new TicketThread(t, replies.selectByTicket(ticketId));
    }

    // ---- 内部 helper ----
    private TicketThread thread(long ticketId) {
        return new TicketThread(tickets.selectById(ticketId), replies.selectByTicket(ticketId));
    }

    /** 返回新回复的 id(admin 回复发站内信需要)。 */
    private long insertReply(long ticketId, String authorType, long authorId, String body) {
        Map<String, Object> row = new HashMap<>();
        row.put("ticketId", ticketId);
        row.put("authorType", authorType);
        row.put("authorId", authorId);
        row.put("body", body);
        row.put("createdBy", authorType.toLowerCase() + ":" + authorId);
        replies.insert(row);
        return ((Number) row.get("id")).longValue();
    }

    private Ticket requireTicket(long ticketId) {
        Ticket t = tickets.selectById(ticketId);
        if (t == null) throw new ApiException(ErrorCode.TICKET_NOT_FOUND, String.valueOf(ticketId));
        return t;
    }

    private void requireBody(String body) {
        if (body == null || body.isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "body required");
    }

    private String normalizeSource(String sourceId) {
        String src = (sourceId == null || sourceId.isBlank()) ? null : sourceId.trim();
        if (src != null && busWrite.selectVersionHash(src) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, src);
        return src;
    }

    private static int page(int limit) { return limit < 1 ? 20 : Math.min(limit, 100); }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: PASS(5 个用例)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/TicketService.java backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java
git commit -m "feat(ticket): TicketService 建单+我的列表/详情 (#7 C2)"
```

---

## Task 6: TicketService —— 用户回复(重开)+ 关闭

**Files:**
- Modify: `backend/src/main/java/com/airportbus/ticket/TicketService.java`
- Test: `backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java`

- [ ] **Step 1: 加失败测试**

在 `TicketServiceIT` 内新增:

```java
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
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: 编译失败 — `replyAsUser`/`closeAsUser` 不存在。

- [ ] **Step 3: 实现用户回复/关闭(在 `getMine` 之后插入)**

```java
    @Transactional
    public TicketThread replyAsUser(long userId, long ticketId, String body) {
        requireBody(body);
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        insertReply(ticketId, "USER", userId, body.trim());
        tickets.updateStatusAndLastReply(ticketId, "OPEN"); // 用户回复永远重开
        return thread(ticketId);
    }

    @Transactional
    public Ticket closeAsUser(long userId, long ticketId) {
        Ticket t = requireTicket(ticketId);
        if (t.userId() != userId) throw new ApiException(ErrorCode.TICKET_FORBIDDEN, String.valueOf(ticketId));
        tickets.updateStatus(ticketId, "CLOSED");
        return tickets.selectById(ticketId);
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: PASS(8 个用例)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/TicketService.java backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java
git commit -m "feat(ticket): 用户回复重开 + 关闭 + 越权守卫 (#7 C2)"
```

---

## Task 7: TicketService —— 管理员回复(→REPLIED + 站内信)+ 关闭 + 队列

**Files:**
- Modify: `backend/src/main/java/com/airportbus/ticket/TicketService.java`
- Test: `backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java`

- [ ] **Step 1: 加失败测试(注入 MessageService 验证站内信)**

在 `TicketServiceIT` 顶部加注入:

```java
    @Autowired com.airportbus.message.MessageService messages;
```

新增用例:

```java
    @Test
    void adminReplySetsRepliedAndNotifiesUser() {
        long uid = newUser("u_adminreply");
        long tid = service.create(uid, null, "请帮忙核对").ticket().id();
        TicketThread th = service.replyAsAdmin(7001L, tid, "已核实并更新");
        assertThat(th.ticket().status()).isEqualTo("REPLIED");
        assertThat(th.replies()).hasSize(2);
        assertThat(th.replies().get(1).authorType()).isEqualTo("ADMIN");
        // 用户收到 1 条 TICKET_REPLIED 站内信
        assertThat(messages.unreadCount(uid)).isEqualTo(1);
    }

    @Test
    void adminCloseSetsClosed() {
        long uid = newUser("u_adminclose");
        long tid = service.create(uid, null, "x").ticket().id();
        Ticket t = service.closeAsAdmin(tid);
        assertThat(t.status()).isEqualTo("CLOSED");
    }

    @Test
    void listForAdminSeesAllTickets() {
        long a = newUser("u_adm_a"); long b = newUser("u_adm_b");
        service.create(a, null, "a"); service.create(b, null, "b");
        assertThat(service.listForAdmin(null, 50, 0).size()).isGreaterThanOrEqualTo(2);
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: 编译失败 — `replyAsAdmin`/`closeAsAdmin`/`listForAdmin`/`getForAdmin` 不存在。

- [ ] **Step 3: 实现管理员侧(在 `closeAsUser` 之后插入)**

> 设计判断:站内信与回复在**同一事务**内同步直插(单收件人,简单、原子)。若 message 写失败则整体回滚、管理员重试即可。spec §4.3 提到的「失败不回滚回复 + 对账」属 follow-up,本期不做。

```java
    @Transactional
    public TicketThread replyAsAdmin(long adminUserId, long ticketId, String body) {
        requireBody(body);
        Ticket t = requireTicket(ticketId);
        long replyId = insertReply(ticketId, "ADMIN", adminUserId, body.trim());
        tickets.updateStatusAndLastReply(ticketId, "REPLIED");
        messages.notifyTicketReplied(t.userId(), ticketId, replyId); // 单人定向站内信(切片 B)
        return thread(ticketId);
    }

    @Transactional
    public Ticket closeAsAdmin(long ticketId) {
        requireTicket(ticketId);
        tickets.updateStatus(ticketId, "CLOSED");
        return tickets.selectById(ticketId);
    }

    public List<Ticket> listForAdmin(String status, int limit, int offset) {
        return tickets.selectPage(status, page(limit), Math.max(offset, 0));
    }

    public TicketThread getForAdmin(long ticketId) {
        Ticket t = requireTicket(ticketId);
        return new TicketThread(t, replies.selectByTicket(ticketId));
    }
```

- [ ] **Step 4: 运行确认通过(整套 IT)**

Run: `cd backend && ./mvnw -q -Dtest=TicketServiceIT test`
Expected: PASS(11 个用例)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/TicketService.java backend/src/test/java/com/airportbus/ticket/TicketServiceIT.java
git commit -m "feat(ticket): 管理员回复(REPLIED+站内信)+关闭+队列 (#7 C2)"
```

---

## Task 8: 控制器(用户 + 管理员)+ 鉴权测试

**Files:**
- Create: `backend/src/main/java/com/airportbus/ticket/api/TicketController.java`
- Create: `backend/src/main/java/com/airportbus/ticket/api/AdminTicketController.java`
- Test: `backend/src/test/java/com/airportbus/ticket/api/TicketControllerTest.java`

- [ ] **Step 1: 写用户控制器**

```java
package com.airportbus.ticket.api;

import com.airportbus.ticket.Ticket;
import com.airportbus.ticket.TicketService;
import com.airportbus.ticket.TicketThread;
import com.airportbus.ticket.api.dto.TicketDtos.CreateTicketRequest;
import com.airportbus.ticket.api.dto.TicketDtos.ReplyRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ticket", description = "用户建议工单")
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService service;
    public TicketController(TicketService service) { this.service = service; }

    @PostMapping
    public TicketThread create(@RequestBody CreateTicketRequest req) {
        JwtPrincipal me = CurrentUser.require();
        return service.create(me.userId(), req.sourceId(), req.body());
    }

    @GetMapping
    public List<Ticket> mine(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "20") int limit,
                             @RequestParam(defaultValue = "0") int offset) {
        JwtPrincipal me = CurrentUser.require();
        return service.listMine(me.userId(), status, limit, offset);
    }

    @GetMapping("/{id}")
    public TicketThread one(@PathVariable long id) {
        JwtPrincipal me = CurrentUser.require();
        return service.getMine(me.userId(), id);
    }

    @PostMapping("/{id}/replies")
    public TicketThread reply(@PathVariable long id, @RequestBody ReplyRequest req) {
        JwtPrincipal me = CurrentUser.require();
        return service.replyAsUser(me.userId(), id, req.body());
    }

    @PostMapping("/{id}/close")
    public Ticket close(@PathVariable long id) {
        JwtPrincipal me = CurrentUser.require();
        return service.closeAsUser(me.userId(), id);
    }
}
```

- [ ] **Step 2: 写管理员控制器(`@Audited` 放控制器方法,与 AdminCorrectionController 一致)**

```java
package com.airportbus.ticket.api;

import com.airportbus.audit.Audited;
import com.airportbus.ticket.Ticket;
import com.airportbus.ticket.TicketService;
import com.airportbus.ticket.TicketThread;
import com.airportbus.ticket.api.dto.TicketDtos.ReplyRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-ticket", description = "工单队列(管理员)")
@RestController
@RequestMapping("/api/v1/admin/tickets")
public class AdminTicketController {
    private final TicketService service;
    public AdminTicketController(TicketService service) { this.service = service; }

    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "20") int limit,
                             @RequestParam(defaultValue = "0") int offset) {
        CurrentUser.requireAdmin();
        return service.listForAdmin(status, limit, offset);
    }

    @GetMapping("/{id}")
    public TicketThread one(@PathVariable long id) {
        CurrentUser.requireAdmin();
        return service.getForAdmin(id);
    }

    @Audited(action = "REPLY_TICKET", target = "ticket")
    @PostMapping("/{id}/replies")
    public TicketThread reply(@PathVariable long id, @RequestBody ReplyRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return service.replyAsAdmin(me.userId(), id, req.body());
    }

    @Audited(action = "CLOSE_TICKET", target = "ticket")
    @PostMapping("/{id}/close")
    public Ticket close(@PathVariable long id) {
        CurrentUser.requireAdmin();
        return service.closeAsAdmin(id);
    }
}
```

- [ ] **Step 3: 写 @WebMvcTest(mock 全部 @MapperScan mapper —— 含新增 TicketMapper/TicketReplyMapper)**

```java
package com.airportbus.ticket.api;

import com.airportbus.common.GlobalExceptionHandler;
import com.airportbus.ticket.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TicketController.class, AdminTicketController.class})
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {
    @Autowired MockMvc mvc;
    @MockBean TicketService service;
    // @MapperScan 扫到的 mapper 都需 @MockBean,否则上下文起不来
    @MockBean com.airportbus.user.mapper.UserMapper userMapper;
    @MockBean com.airportbus.user.mapper.RefreshTokenMapper refreshTokenMapper;
    @MockBean com.airportbus.user.mapper.FavoriteMapper favoriteMapper;
    @MockBean com.airportbus.bus.mapper.BusWriteMapper busWriteMapper;
    @MockBean com.airportbus.bus.mapper.BusQueryMapper busQueryMapper;
    @MockBean com.airportbus.bus.mapper.BusVersionMapper busVersionMapper;
    @MockBean com.airportbus.bus.mapper.SearchHotnessMapper searchHotnessMapper;
    @MockBean com.airportbus.message.mapper.MessageMapper messageMapper;
    @MockBean com.airportbus.audit.AuditMapper auditMapper;
    @MockBean com.airportbus.ticket.mapper.CorrectionMapper correctionMapper;
    @MockBean com.airportbus.ticket.mapper.TicketMapper ticketMapper;
    @MockBean com.airportbus.ticket.mapper.TicketReplyMapper ticketReplyMapper;

    @Test
    void createWithoutTokenIs401() throws Exception {
        // CurrentUser.require() 无主体 → ApiException(UNAUTHORIZED) → 401
        mvc.perform(post("/api/v1/tickets").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void adminListWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/admin/tickets")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && ./mvnw -q -Dtest=TicketControllerTest test`
Expected: PASS(2 个用例;鉴权 401 正确)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/ticket/api/TicketController.java \
        backend/src/main/java/com/airportbus/ticket/api/AdminTicketController.java \
        backend/src/test/java/com/airportbus/ticket/api/TicketControllerTest.java
git commit -m "feat(ticket): 用户/管理员控制器 + 鉴权测试 (#7 C2)"
```

---

## Task 9: 前端 api/tickets.ts

**Files:**
- Create: `frontend/src/api/tickets.ts`
- Test: `frontend/src/test/tickets.api.spec.ts`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  post: vi.fn(() => Promise.resolve({ data: { ticket: { id: 1, status: 'OPEN' }, replies: [] } })),
  get: vi.fn(() => Promise.resolve({ data: [] })),
} }))
import { http } from '../api/client'
import * as api from '../api/tickets'

describe('tickets api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('create posts sourceId+body', async () => {
    await api.createTicket({ sourceId: 'vie-vab1', body: 'x' })
    expect(http.post).toHaveBeenCalledWith('/tickets', { sourceId: 'vie-vab1', body: 'x' })
  })
  it('list passes status', async () => {
    await api.listTickets('OPEN')
    expect(http.get).toHaveBeenCalledWith('/tickets', { params: { status: 'OPEN' } })
  })
  it('reply posts body to replies', async () => {
    await api.replyTicket(5, 'hi')
    expect(http.post).toHaveBeenCalledWith('/tickets/5/replies', { body: 'hi' })
  })
  it('close posts empty body', async () => {
    await api.closeTicket(5)
    expect(http.post).toHaveBeenCalledWith('/tickets/5/close', {})
  })
  it('admin reply hits admin path', async () => {
    await api.adminReplyTicket(5, 'ok')
    expect(http.post).toHaveBeenCalledWith('/admin/tickets/5/replies', { body: 'ok' })
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/test/tickets.api.spec.ts`
Expected: FAIL — `../api/tickets` 不存在。

- [ ] **Step 3: 实现 api 客户端**

```ts
import { http } from './client'

export interface TicketReply {
  id: number; authorType: 'USER' | 'ADMIN'; authorId: number; body: string; createdAt: string
}
export interface Ticket {
  id: number; userId: number; relatedSourceId: string | null
  status: string; lastReplyAt: string; createdAt: string
}
export interface TicketThread { ticket: Ticket; replies: TicketReply[] }

// 用户侧
export const listTickets = (status = '') =>
  http.get<Ticket[]>('/tickets', { params: { status } }).then((r) => r.data)
export const getTicket = (id: number) =>
  http.get<TicketThread>(`/tickets/${id}`).then((r) => r.data)
export const createTicket = (body: { sourceId?: string; body: string }) =>
  http.post<TicketThread>('/tickets', body).then((r) => r.data)
export const replyTicket = (id: number, body: string) =>
  http.post<TicketThread>(`/tickets/${id}/replies`, { body }).then((r) => r.data)
export const closeTicket = (id: number) =>
  http.post<Ticket>(`/tickets/${id}/close`, {}).then((r) => r.data)

// 管理员侧
export const adminListTickets = (status = '') =>
  http.get<Ticket[]>('/admin/tickets', { params: { status } }).then((r) => r.data)
export const adminGetTicket = (id: number) =>
  http.get<TicketThread>(`/admin/tickets/${id}`).then((r) => r.data)
export const adminReplyTicket = (id: number, body: string) =>
  http.post<TicketThread>(`/admin/tickets/${id}/replies`, { body }).then((r) => r.data)
export const adminCloseTicket = (id: number) =>
  http.post<Ticket>(`/admin/tickets/${id}/close`, {}).then((r) => r.data)
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/test/tickets.api.spec.ts`
Expected: PASS(5 个用例)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/tickets.ts frontend/src/test/tickets.api.spec.ts
git commit -m "feat(ticket): 前端 tickets api client (#7 C2)"
```

---

## Task 10: 前端 stores/tickets.ts(用户页)

**Files:**
- Create: `frontend/src/stores/tickets.ts`
- Test: `frontend/src/test/tickets.store.spec.ts`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
vi.mock('../api/tickets', () => ({
  listTickets: vi.fn(() => Promise.resolve([{ id: 1, status: 'OPEN', relatedSourceId: null }])),
  getTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [{ id: 9, authorType: 'USER', body: 'hi' }] })),
  createTicket: vi.fn(() => Promise.resolve({ ticket: { id: 2, status: 'OPEN' }, replies: [] })),
  replyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [{ id: 9 }, { id: 10 }] })),
  closeTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'
import { useTickets } from '../stores/tickets'

describe('tickets store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('load fills list', async () => {
    const s = useTickets(); await s.load()
    expect(s.list).toHaveLength(1)
    expect(api.listTickets).toHaveBeenCalled()
  })
  it('open loads thread into threads map', async () => {
    const s = useTickets(); await s.open(1)
    expect(s.threads[1].replies).toHaveLength(1)
  })
  it('create prepends new ticket and reloads list', async () => {
    const s = useTickets(); await s.create({ body: 'new' })
    expect(api.createTicket).toHaveBeenCalledWith({ body: 'new' })
    expect(api.listTickets).toHaveBeenCalled()
  })
  it('reply updates thread + status in list', async () => {
    const s = useTickets(); await s.load(); await s.reply(1, 'more')
    expect(api.replyTicket).toHaveBeenCalledWith(1, 'more')
    expect(s.threads[1].replies).toHaveLength(2)
  })
  it('close sets status CLOSED in list', async () => {
    const s = useTickets(); await s.load(); await s.close(1)
    expect(s.list[0].status).toBe('CLOSED')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/test/tickets.store.spec.ts`
Expected: FAIL — `../stores/tickets` 不存在。

- [ ] **Step 3: 实现 store**

```ts
import { defineStore } from 'pinia'
import * as api from '../api/tickets'
import type { Ticket, TicketThread } from '../api/tickets'

export const useTickets = defineStore('tickets', {
  state: () => ({
    list: [] as Ticket[],
    threads: {} as Record<number, TicketThread>,
  }),
  actions: {
    async load(status = '') { this.list = await api.listTickets(status) },
    async open(id: number) { this.threads[id] = await api.getTicket(id) },
    async create(body: { sourceId?: string; body: string }) {
      const th = await api.createTicket(body)
      this.threads[th.ticket.id] = th
      await this.load()
      return th
    },
    async reply(id: number, body: string) {
      const th = await api.replyTicket(id, body)
      this.threads[id] = th
      this.syncStatus(id, th.ticket.status)
    },
    async close(id: number) {
      const t = await api.closeTicket(id)
      this.syncStatus(id, t.status)
      if (this.threads[id]) this.threads[id].ticket.status = t.status
    },
    syncStatus(id: number, status: string) {
      const row = this.list.find((t) => t.id === id)
      if (row) row.status = status
    },
  },
})
```

- [ ] **Step 4: 运行确认通过**

Run: `cd frontend && npx vitest run src/test/tickets.store.spec.ts`
Expected: PASS(5 个用例)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/stores/tickets.ts frontend/src/test/tickets.store.spec.ts
git commit -m "feat(ticket): 前端 tickets pinia store (#7 C2)"
```

---

## Task 11: 前端 i18n 文案(ticket/admin/msg)

**Files:**
- Modify: `frontend/src/i18n/locales/zh-CN.ts`
- Modify: `frontend/src/i18n/locales/en.ts`
- Modify: `frontend/src/i18n/locales/de.ts`

> 先加文案,后续 Task 12/13/14 的页面直接引用,避免重复改三份 locale。

- [ ] **Step 1: zh-CN —— 在 `msg` 对象内补一行,并在导出对象顶层加 `ticket` 块**

在 `msg:` 对象的 `unknown` 同行后补 `ticketReplied`:

```ts
    busUpdated: '线路 {route} 已更新', busOffline: '线路 {route} 已下线', unknown: '您有一条新通知',
    ticketReplied: '您的工单 #{ticketId} 有新回复',
```

在导出对象顶层(与 `msg`/`correction` 同级)新增:

```ts
  ticket: {
    nav: '工单', title: '建议工单',
    desc: '提交建议或纠错,管理员回复后状态变为「已回复」,你再回复会重新打开。',
    newBtn: '+ 新建工单', newTitle: '新建工单',
    sourceLabel: '关联线路(可选)', sourcePlaceholder: '线路 source_id,如 vie-vab1',
    bodyLabel: '问题 / 建议', bodyPlaceholder: '请描述你遇到的问题或建议…',
    submit: '提交', reply: '回复', close: '关闭工单', empty: '暂无工单',
    replyPlaceholder: '继续回复会把工单重新打开…',
    closedNote: '已关闭。再次回复会重新打开为「待处理」。',
    me: '我', admin: '管理员',
    status: { OPEN: '待处理', REPLIED: '已回复', CLOSED: '已关闭' },
    queueTitle: '工单队列', queueDesc: '用户提交的建议/纠错工单。回复后用户会收到站内信。',
  },
```

在 admin 侧栏文案(若有集中 admin nav i18n 则补 `admin.nav.tickets: '工单队列'`;本项目 admin 侧栏在 `AdminLayout.vue` 内硬编码中文,见 Task 13,无需在此加)。

- [ ] **Step 2: en —— 同结构英文**

`msg` 内补:

```ts
    ticketReplied: 'Your ticket #{ticketId} has a new reply',
```

顶层加:

```ts
  ticket: {
    nav: 'Tickets', title: 'Suggestion Tickets',
    desc: 'Submit a suggestion or correction. After an admin replies the status becomes “Replied”; replying again reopens it.',
    newBtn: '+ New ticket', newTitle: 'New ticket',
    sourceLabel: 'Related route (optional)', sourcePlaceholder: 'Route source_id, e.g. vie-vab1',
    bodyLabel: 'Issue / suggestion', bodyPlaceholder: 'Describe the issue or suggestion…',
    submit: 'Submit', reply: 'Reply', close: 'Close ticket', empty: 'No tickets yet',
    replyPlaceholder: 'Replying reopens the ticket…',
    closedNote: 'Closed. Replying again reopens it as “Open”.',
    me: 'Me', admin: 'Admin',
    status: { OPEN: 'Open', REPLIED: 'Replied', CLOSED: 'Closed' },
    queueTitle: 'Ticket queue', queueDesc: 'User-submitted suggestion/correction tickets. The user is notified in-app after a reply.',
  },
```

- [ ] **Step 3: de —— 同结构德文**

`msg` 内补:

```ts
    ticketReplied: 'Ihr Ticket #{ticketId} hat eine neue Antwort',
```

顶层加:

```ts
  ticket: {
    nav: 'Tickets', title: 'Vorschlags-Tickets',
    desc: 'Reichen Sie einen Vorschlag oder eine Korrektur ein. Nach einer Admin-Antwort wird der Status „Beantwortet“; eine erneute Antwort öffnet es wieder.',
    newBtn: '+ Neues Ticket', newTitle: 'Neues Ticket',
    sourceLabel: 'Zugehörige Linie (optional)', sourcePlaceholder: 'Linien-source_id, z. B. vie-vab1',
    bodyLabel: 'Problem / Vorschlag', bodyPlaceholder: 'Beschreiben Sie das Problem oder den Vorschlag…',
    submit: 'Absenden', reply: 'Antworten', close: 'Ticket schließen', empty: 'Noch keine Tickets',
    replyPlaceholder: 'Eine Antwort öffnet das Ticket erneut…',
    closedNote: 'Geschlossen. Eine erneute Antwort öffnet es wieder als „Offen“.',
    me: 'Ich', admin: 'Admin',
    status: { OPEN: 'Offen', REPLIED: 'Beantwortet', CLOSED: 'Geschlossen' },
    queueTitle: 'Ticket-Warteschlange', queueDesc: 'Von Nutzern eingereichte Vorschlags-/Korrektur-Tickets. Der Nutzer wird nach einer Antwort in der App benachrichtigt.',
  },
```

- [ ] **Step 4: 类型/解析校验(确保三份 locale 不破坏构建)**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无类型错误(若项目用 `tsconfig` 构建,确保 locale 对象结构一致)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/i18n/locales/zh-CN.ts frontend/src/i18n/locales/en.ts frontend/src/i18n/locales/de.ts
git commit -m "feat(ticket): i18n 工单/站内信文案 zh-CN/en/de (#7 C2)"
```

---

## Task 12: 用户工单页 /tickets

**Files:**
- Create: `frontend/src/pages/TicketsPage.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/App.vue`
- Test: `frontend/src/test/TicketsPage.spec.ts`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import zh from '../i18n/locales/zh-CN'
import TicketsPage from '../pages/TicketsPage.vue'

vi.mock('../api/tickets', () => ({
  listTickets: vi.fn(() => Promise.resolve([
    { id: 1, status: 'REPLIED', relatedSourceId: 'vie-vab1', lastReplyAt: '2026-06-20', createdAt: '2026-06-19' },
  ])),
  getTicket: vi.fn(() => Promise.resolve({
    ticket: { id: 1, status: 'REPLIED', relatedSourceId: 'vie-vab1' },
    replies: [
      { id: 1, authorType: 'USER', body: '价格变了', createdAt: '2026-06-19' },
      { id: 2, authorType: 'ADMIN', body: '已更新', createdAt: '2026-06-20' },
    ],
  })),
  createTicket: vi.fn(() => Promise.resolve({ ticket: { id: 2, status: 'OPEN' }, replies: [] })),
  replyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [] })),
  closeTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zh } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('TicketsPage', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('renders my tickets list with status badge', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    expect(api.listTickets).toHaveBeenCalled()
    expect(w.text()).toContain('已回复')
  })
  it('submitting new ticket calls createTicket', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    await w.find('[data-test="new-body"]').setValue('请新增一条线路')
    await w.find('[data-test="new-submit"]').trigger('click')
    expect(api.createTicket).toHaveBeenCalledWith({ sourceId: undefined, body: '请新增一条线路' })
  })
  it('opening a ticket loads thread', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    await w.find('[data-test="open-1"]').trigger('click')
    await flushPromises()
    expect(api.getTicket).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('已更新')
  })
})

async function flushPromises() { await new Promise((r) => setTimeout(r)) }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/test/TicketsPage.spec.ts`
Expected: FAIL — `../pages/TicketsPage.vue` 不存在。

- [ ] **Step 3: 实现 TicketsPage.vue(手写组件 + 设计 token,不引 Element Plus;气泡线程对齐 `design/tickets.html`)**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTickets } from '../stores/tickets'

const { t } = useI18n()
const store = useTickets()

const opened = ref<number | null>(null)
const newSource = ref('')
const newBody = ref('')
const replyBody = ref<Record<number, string>>({})

onMounted(() => store.load())

async function submitNew() {
  if (!newBody.value.trim()) return
  await store.create({ sourceId: newSource.value.trim() || undefined, body: newBody.value.trim() })
  newSource.value = ''; newBody.value = ''
}
async function open(id: number) {
  opened.value = opened.value === id ? null : id
  if (opened.value === id && !store.threads[id]) await store.open(id)
}
async function sendReply(id: number) {
  const body = (replyBody.value[id] ?? '').trim()
  if (!body) return
  await store.reply(id, body); replyBody.value[id] = ''
}
async function doClose(id: number) { await store.close(id) }
function statusLabel(s: string) { return t('ticket.status.' + s) }
defineExpose({ submitNew, open, sendReply, doClose })
</script>

<template>
  <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:14px;margin-top:8px;flex-wrap:wrap">
    <div>
      <h1 class="pageH2" style="margin:0">{{ t('ticket.title') }}</h1>
      <p class="pageDesc" style="margin:4px 0 0">{{ t('ticket.desc') }}</p>
    </div>
  </div>

  <section class="panel" style="margin-top:18px">
    <h3>{{ t('ticket.newTitle') }}</h3>
    <div class="formrow"><label>{{ t('ticket.sourceLabel') }}</label>
      <input class="input" type="text" v-model="newSource" :placeholder="t('ticket.sourcePlaceholder')" />
    </div>
    <div class="formrow"><label>{{ t('ticket.bodyLabel') }}</label>
      <textarea class="input" data-test="new-body" v-model="newBody" :placeholder="t('ticket.bodyPlaceholder')"></textarea>
    </div>
    <button class="btn btn-primary" data-test="new-submit" @click="submitNew">{{ t('ticket.submit') }}</button>
  </section>

  <div v-if="store.list.length === 0" class="panel">{{ t('ticket.empty') }}</div>

  <section v-for="tk in store.list" :key="tk.id" class="panel">
    <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px">
      <h3 style="margin:0;cursor:pointer" :data-test="`open-${tk.id}`" @click="open(tk.id)">
        #{{ tk.id }}<span v-if="tk.relatedSourceId"> · {{ tk.relatedSourceId }}</span>
      </h3>
      <span class="statusBadge" :class="tk.status.toLowerCase()">{{ statusLabel(tk.status) }}</span>
    </div>

    <template v-if="opened === tk.id && store.threads[tk.id]">
      <div class="thread">
        <div v-for="rp in store.threads[tk.id].replies" :key="rp.id"
             class="bubble" :class="rp.authorType === 'ADMIN' ? 'admin' : 'user'">
          <div class="who">{{ rp.authorType === 'ADMIN' ? t('ticket.admin') : t('ticket.me') }} · {{ rp.createdAt }}</div>
          {{ rp.body }}
        </div>
      </div>
      <div v-if="tk.status === 'CLOSED'" class="formNote">{{ t('ticket.closedNote') }}</div>
      <div class="replyBox">
        <textarea class="input" :data-test="`reply-${tk.id}`" v-model="replyBody[tk.id]" :placeholder="t('ticket.replyPlaceholder')"></textarea>
        <button class="btn btn-primary" @click="sendReply(tk.id)">{{ t('ticket.reply') }}</button>
        <button v-if="tk.status !== 'CLOSED'" class="btn btn-ghost" @click="doClose(tk.id)">{{ t('ticket.close') }}</button>
      </div>
    </template>
  </section>
</template>
```

- [ ] **Step 4: 路由加 `/tickets`(登录守卫,照搬 `/inbox` 写法)**

In `frontend/src/router/index.ts`, 在 `/inbox` 路由对象之后新增:

```ts
    {
      path: '/tickets', name: 'tickets',
      component: () => import('../pages/TicketsPage.vue'),
      beforeEnter: (to) => {
        const auth = useAuth()
        return auth.isAuthed ? true : { name: 'login', query: { redirect: to.fullPath } }
      },
    },
```

- [ ] **Step 5: 顶栏加「工单」入口(App.vue,仅登录可见,放在 bell 与 /me 之间)**

In `frontend/src/App.vue`, 在 `<template v-else>` 块内、bell 的 `router-link` 之后新增:

```html
          <router-link class="btn btn-ghost btn-sm" to="/tickets">{{ t('ticket.nav') }}</router-link>
```

- [ ] **Step 6: 运行确认通过**

Run: `cd frontend && npx vitest run src/test/TicketsPage.spec.ts`
Expected: PASS(3 个用例)。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/TicketsPage.vue frontend/src/router/index.ts frontend/src/App.vue frontend/src/test/TicketsPage.spec.ts
git commit -m "feat(ticket): /tickets 用户工单页 + 路由 + 顶栏入口 (#7 C2)"
```

---

## Task 13: 管理员工单队列页 /admin/tickets

**Files:**
- Create: `frontend/src/pages/admin/AdminTicketsPage.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/components/admin/AdminLayout.vue`
- Test: `frontend/src/test/AdminTicketsPage.spec.ts`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AdminTicketsPage from '../pages/admin/AdminTicketsPage.vue'

vi.mock('../api/tickets', () => ({
  adminListTickets: vi.fn(() => Promise.resolve([
    { id: 1, status: 'OPEN', relatedSourceId: 'vie-vab1', userId: 5, lastReplyAt: '2026-06-20', createdAt: '2026-06-19' },
  ])),
  adminGetTicket: vi.fn(() => Promise.resolve({
    ticket: { id: 1, status: 'OPEN', userId: 5 },
    replies: [{ id: 1, authorType: 'USER', body: '请核对', createdAt: '2026-06-19' }],
  })),
  adminReplyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'REPLIED' }, replies: [] })),
  adminCloseTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'

describe('AdminTicketsPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('loads queue on mount', async () => {
    const w = mount(AdminTicketsPage)
    await flushPromises()
    expect(api.adminListTickets).toHaveBeenCalled()
    expect(w.text()).toContain('vie-vab1')
  })
  it('replying as admin calls adminReplyTicket', async () => {
    const w = mount(AdminTicketsPage)
    await flushPromises()
    await (w.vm as any).openThread(1)
    await flushPromises()
    ;(w.vm as any).replyDraft[1] = '已更新'
    await (w.vm as any).sendReply(1)
    expect(api.adminReplyTicket).toHaveBeenCalledWith(1, '已更新')
  })
})

async function flushPromises() { await new Promise((r) => setTimeout(r)) }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/test/AdminTicketsPage.spec.ts`
Expected: FAIL — 页面不存在。

- [ ] **Step 3: 实现 AdminTicketsPage.vue(admin 区可用 Element Plus,直调 api)**

```vue
<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElTable, ElTableColumn, ElInput, ElButton } from 'element-plus'
import {
  adminListTickets, adminGetTicket, adminReplyTicket, adminCloseTicket,
  type Ticket, type TicketThread,
} from '../../api/tickets'

const rows = ref<Ticket[]>([])
const threads = reactive<Record<number, TicketThread>>({})
const replyDraft = reactive<Record<number, string>>({})
const opened = ref<number | null>(null)

async function load() { rows.value = await adminListTickets('') }
onMounted(load)

async function openThread(id: number) {
  opened.value = opened.value === id ? null : id
  if (opened.value === id) threads[id] = await adminGetTicket(id)
}
async function sendReply(id: number) {
  const body = (replyDraft[id] ?? '').trim()
  if (!body) return
  threads[id] = await adminReplyTicket(id, body)
  replyDraft[id] = ''
  await load()
}
async function close(id: number) { await adminCloseTicket(id); await load() }
defineExpose({ openThread, sendReply, close, replyDraft, threads })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">工单队列</h1>
  <p class="pageDesc">用户提交的建议/纠错工单。回复后用户会收到 TICKET_REPLIED 站内信。</p>

  <div class="panel">
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="status" label="状态" width="100" />
      <ElTableColumn prop="id" label="#" width="80" />
      <ElTableColumn prop="relatedSourceId" label="线路" width="140" />
      <ElTableColumn prop="userId" label="用户" width="90" />
      <ElTableColumn prop="lastReplyAt" label="最后回复" width="180" />
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openThread(row.id)">查看线程</ElButton>
          <ElButton size="small" type="danger" @click="close(row.id)">关闭</ElButton>
          <div v-if="opened === row.id && threads[row.id]" style="margin-top:10px">
            <div v-for="rp in threads[row.id].replies" :key="rp.id"
                 class="bubble" :class="rp.authorType === 'ADMIN' ? 'admin' : 'user'">
              <div class="who">{{ rp.authorType }} · {{ rp.createdAt }}</div>{{ rp.body }}
            </div>
            <ElInput v-model="replyDraft[row.id]" type="textarea" placeholder="回复用户(发出后用户收站内信)" style="margin:8px 0" />
            <ElButton size="small" type="primary" @click="sendReply(row.id)">回复</ElButton>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
```

- [ ] **Step 4: 路由加 admin/tickets 子路由**

In `frontend/src/router/index.ts`, 在 `admin` children 数组里 `corrections` 之后新增:

```ts
        { path: 'tickets', name: 'admin-tickets', component: () => import('../pages/admin/AdminTicketsPage.vue') },
```

- [ ] **Step 5: AdminLayout 侧栏加「工单队列」**

In `frontend/src/components/admin/AdminLayout.vue`, 在 `nav` 数组 `admin-corrections` 项之后新增:

```ts
  { to: { name: 'admin-tickets' }, label: '工单队列', icon: '💬' },
```

- [ ] **Step 6: 运行确认通过**

Run: `cd frontend && npx vitest run src/test/AdminTicketsPage.spec.ts`
Expected: PASS(2 个用例)。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/admin/AdminTicketsPage.vue frontend/src/router/index.ts frontend/src/components/admin/AdminLayout.vue frontend/src/test/AdminTicketsPage.spec.ts
git commit -m "feat(ticket): /admin/tickets 队列页 + 路由 + 侧栏 (#7 C2)"
```

---

## Task 14: 站内信 TICKET_REPLIED 渲染 + 跳转

**Files:**
- Modify: `frontend/src/api/messages.ts`
- Modify: `frontend/src/components/renderMessage.ts`
- Modify: `frontend/src/pages/InboxPage.vue`
- Test: `frontend/src/test/renderMessage.spec.ts`

- [ ] **Step 1: 加失败测试(在 renderMessage.spec.ts 末尾)**

在 `t` 的 `map` 里补一行:

```ts
    'msg.ticketReplied': '您的工单 #{ticketId} 有新回复',
```

新增用例(在 `describe` 内):

```ts
  it('TICKET_REPLIED title + ticket link', () => {
    const r = renderMessage('TICKET_REPLIED', { ticketId: 1042 } as any, t as any)
    expect(r.title).toBe('您的工单 #1042 有新回复')
    expect(r.diffs).toEqual([])
    expect(r.link).toBe('/tickets/1042')
  })
```

- [ ] **Step 2: 运行确认失败**

Run: `cd frontend && npx vitest run src/test/renderMessage.spec.ts`
Expected: FAIL — `TICKET_REPLIED` 落入 unknown 兜底,`r.link` 为 undefined。

- [ ] **Step 3: MessageParams 加 ticketId**

In `frontend/src/api/messages.ts`, 把 `MessageParams` 改为:

```ts
export interface MessageParams { route?: string; sourceId?: string; changed?: FieldChange[]; changedSubtables?: string[]; ticketId?: number }
```

- [ ] **Step 4: renderMessage 加 link + TICKET_REPLIED 分支**

In `frontend/src/components/renderMessage.ts`, 把 `RenderedMessage` 接口与函数体改为:

```ts
export interface RenderedMessage { title: string; diffs: RenderedDiff[]; link?: string }
```

在 `BUS_OFFLINE` 分支之后、`return { title: t('msg.unknown'), ... }` 之前插入:

```ts
  if (templateCode === 'TICKET_REPLIED') {
    return { title: t('msg.ticketReplied', { ticketId: params?.ticketId }), diffs: [], link: `/tickets/${params?.ticketId}` }
  }
```

(`BUS_UPDATED`/`BUS_OFFLINE` 分支不返回 `link`,保持原行为;InboxPage 对它们继续用 `relatedSourceId` 构造 `/bus` 链接。)

- [ ] **Step 5: InboxPage 优先用 view.link**

In `frontend/src/pages/InboxPage.vue`, 把详情按钮那段 `router-link` 替换为(先 ticket 链接、再回退 bus 链接):

```html
        <router-link v-if="r.view.link" class="btn btn-ghost btn-sm" :to="r.view.link">{{ t('msg.viewDetail') }}</router-link>
        <router-link v-else-if="r.raw.relatedSourceId" class="btn btn-ghost btn-sm" :to="`/bus/${r.raw.relatedSourceId}`">{{ t('msg.viewDetail') }}</router-link>
```

- [ ] **Step 6: 运行确认通过(renderMessage 全套)**

Run: `cd frontend && npx vitest run src/test/renderMessage.spec.ts`
Expected: PASS(原 4 + 新 1 = 5 个用例)。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/messages.ts frontend/src/components/renderMessage.ts frontend/src/pages/InboxPage.vue frontend/src/test/renderMessage.spec.ts
git commit -m "feat(ticket): 站内信 TICKET_REPLIED 渲染 + /tickets 跳转 (#7 C2)"
```

---

## Task 15: 全量回归 + 收尾

**Files:** 无新增(仅运行验证)

- [ ] **Step 1: 后端单测(非 *IT)全绿**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS(`@WebMvcTest` 等普通测试通过;注意 `*IT` 不走 `mvn test`)。

- [ ] **Step 2: 后端关键 IT(显式 -Dtest)全绿**

Run: `cd backend && ./mvnw -q -Dtest='TicketServiceIT,MessageServiceIT,CorrectionServiceIT' test`
Expected: PASS(Testcontainers 起 MySQL+Redis;ticket 链路端到端绿)。

- [ ] **Step 3: 前端全量测试 + 类型检查**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: 全部 spec 通过、无类型错误。

- [ ] **Step 4: 人工冒烟核对(可选,按 verification-before-completion)**

确认:`/tickets` 建单 → 后台 `/admin/tickets` 回复 → 用户顶栏 bell 红点 +1 → `/inbox` 点「查看详情」跳 `/tickets/{id}`。

- [ ] **Step 5: 收尾 —— 交 finishing-a-development-branch 决定合并/PR**

(不在此 commit;由执行者按 superpowers:finishing-a-development-branch 处理分支落地。)

---

## 自审清单(对照 spec §9 C2 + §3/§4/§5)

- **数据模型** §2.2:`ticket`/`ticket_reply` 两表、无独立标题、首条即 USER reply、`last_reply_at` 排序 → Task 1/3/5 ✅
- **状态机** §3:建单 OPEN、admin 回复 REPLIED+站内信、用户回复永远重开 OPEN(含 CLOSED 后)、任一方关闭 CLOSED、回复永远允许 → Task 5/6/7 用例覆盖 ✅
- **后端** §4.2:create/reply(user 本人校验)/close/listMine/getMine/listForAdmin/getForAdmin → Task 5/6/7 ✅;E10 author 服务端取(`replyAsUser`/`replyAsAdmin` 用主体 id,DTO 只有 body)→ Task 8 ✅
- **message 集成** §4.3:`TICKET_REPLIED` 模板、`dedup=ticket:{id}:reply:{replyId}` 幂等、`related_bus_route_id` 空、同步直插不走 BusEvent → Task 4/7 ✅;同事务原子(失败回滚,对账列 follow-up)→ Task 7 设计判断已注明
- **API 契约** §5:全部 11 个端点、资源词 `tickets`、成功返资源、`requireAdmin`/本人守卫、错误码 `TICKET_NOT_FOUND`/`TICKET_FORBIDDEN`/`VALIDATION_FAILED` → Task 2/8 ✅
- **前端** §6.2:`/tickets` 页(列表+状态徽章+新建+气泡线程+回复+关闭)、顶栏入口、`AdminTicketsPage`、`InboxPage/renderMessage` 的 `TICKET_REPLIED` 链向 `/tickets/{id}`、全 `{{ }}` 无 v-html → Task 12/13/14 ✅
- **i18n** §1:zh-CN/en/de 三语,工单页/admin/`TICKET_REPLIED` 模板 → Task 11 ✅
- **YAGNI**(§1 不做):邮件回执、附件、深链跳转编辑、分类/SLA/检索 —— 均未进计划 ✅
- **类型一致性**:`replyAsUser/replyAsAdmin/closeAsUser/closeAsAdmin/listForAdmin/getForAdmin` 命名贯穿 service→controller;`TicketThread{ticket,replies}` 前后端字段一致;`updateStatusAndLastReply`(回复)vs `updateStatus`(关闭)两 mapper 方法名一致 ✅
- **测试基建坑(记忆)**:`*IT` 用 `-Dtest=`、`management.health.redis.enabled=false`、`@WebMvcTest` mock 全部 @MapperScan mapper(已含新 TicketMapper/TicketReplyMapper)→ Task 8/15 已落实 ✅
