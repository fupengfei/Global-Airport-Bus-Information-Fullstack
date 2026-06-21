# message 站内信推送闭环 —— 后端实现计划(#7 切片 B · 后端)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 后端跑通「管理员改/删被收藏线路 → AFTER_COMMIT 异步扇出站内信给订阅者」闭环,含 version 幂等去重、Redis 未读计数、@Scheduled 对账、message API。

**Architecture:** 新建 `com.airportbus.message` 模块;`@TransactionalEventListener(AFTER_COMMIT) @Async` 监听切片 A 的 `BusUpdatedEvent`/新增 `BusDeletedEvent`,经 `MessageService` 查订阅者(走 user 模块 `FavoriteService`,E5)批量插消息(ON DUPLICATE KEY 幂等去重)。未读数 Redis 缓存(DB 权威,写时 invalidate、读时 miss 重建)。

**Tech Stack:** Spring Boot + MyBatis(`#{}` only)+ Flyway + `@EnableAsync`/`@EnableScheduling`(已开)+ StringRedisTemplate + Jackson(params_json)+ Testcontainers IT。

---

## 上游与约束
- spec:`docs/superpowers/specs/2026-06-21-message-push-loop-design.md`(读它)。
- 前置:#3 `favorite`(订阅,无 notify 列);切片 A `BusCommandService.save/delete`、`BusUpdatedEvent`、`version`。
- **本计划只做后端**;前端(收件箱/红点)单独计划,后端落地后写。
- 约定:每表审计列 + `deleted`,迁移每列 COMMENT;读路径排除 `deleted=1`;MyBatis 仅 `#{}`;错误体统一;userId 取自 `CurrentUser` 绝不信请求体。

## 关键既有事实
- `bus/service/BusUpdatedEvent`:`record(long busRouteId, String sourceId, String oldHash, String newHash, ChangedSummary summary)`。`BusCommandService.save()` 两处 publish(create/update),`newVersion` 在作用域;`delete()`(约 106 行)只有 `sourceId`,调 `writeMapper.softDeleteBus(sourceId, actor)`。
- `ChangedSummary`:`record(List<FieldChange> scalars, List<String> changedSubtables)`;`FieldChange(String field, String oldValue, String newValue)`。
- `bus/service/BusCommandService`:注入 `BusWriteMapper writeMapper`、`ApplicationEventPublisher events`。`writeMapper.findBusId(sourceId)`(已排除 deleted=0)。
- `user/.../FavoriteMapper`(+xml):`favorite(user_id,bus_route_id,deleted)`;已有 `selectFavoritedSourceIds` 等。`FavoriteService` 注入 `FavoriteMapper`+`BusQueryService`。
- `user/security/CurrentUser`:`require()` → `JwtPrincipal(userId,role)`。
- `SearchHotnessService` 是 Redis(`StringRedisTemplate`)+ `@Async` + `@Scheduled(fixedDelayString="${...}")` 的范例(吞异常、不阻塞)。
- `@MapperScan({"com.airportbus.bus.mapper","com.airportbus.user.mapper","com.airportbus.audit"})` —— **需加** `"com.airportbus.message"`。`@EnableAsync`/`@EnableScheduling` 已在 `AirportbusApplication`。
- 命令:编译 `cd backend && mvn -q -DskipTests compile`;IT `cd backend && mvn -q -Dtest=<Class> test`(需 Docker)。

## 文件结构
- 迁移:`db/migration/V8__message.sql`
- 改切片 A:`bus/service/BusUpdatedEvent.java`(+version,+route)、`bus/service/BusDeletedEvent.java`(新)、`bus/service/BusCommandService.java`(publish 带 version/route;delete 发事件)、`bus/mapper/BusWriteMapper.java`+xml(`selectRouteBySource`)
- 改 user:`user/mapper/FavoriteMapper.java`+xml、`user/service/FavoriteService.java`(订阅者查询 + 按线路软删收藏)
- 新 message 模块:`message/Message.java`(行模型)、`message/mapper/MessageMapper.java`+`resources/mapper/MessageMapper.xml`、`message/MessageUnreadCounter.java`(Redis)、`message/MessageService.java`、`message/BusEventListener.java`、`message/MessageReconciler.java`、`message/api/MessageController.java`、`message/api/dto/*`
- 改 `AirportbusApplication.java`(@MapperScan +message)

---

## Task 1: V8 message 迁移

**Files:** Create `backend/src/main/resources/db/migration/V8__message.sql`

- [ ] **Step 1: 写迁移**
```sql
-- V8__message.sql  站内信(模板+参数,前端按locale渲染);version去重键幂等
CREATE TABLE message (
  id                   BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  user_id              BIGINT       NOT NULL                            COMMENT '收信人(app_user.id)',
  template_code        VARCHAR(32)  NOT NULL                            COMMENT '模板码:BUS_UPDATED/BUS_OFFLINE',
  params_json          TEXT         NOT NULL                            COMMENT '渲染参数JSON(前端按locale渲染)',
  related_bus_route_id BIGINT       NULL                                COMMENT '关联线路内部ID(系统消息可空)',
  dedup_key            VARCHAR(128) NOT NULL                            COMMENT '幂等去重键:bus:{id}:v:{version} 或 bus:{id}:offline',
  is_read              TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '已读',
  read_at              DATETIME     NULL                                COMMENT '已读时间',
  created_by           VARCHAR(64)  NULL                                COMMENT '创建人',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by           VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted              TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  UNIQUE KEY uk_msg_dedup (user_id, dedup_key, deleted),
  KEY idx_msg_user_read (user_id, is_read, deleted),
  CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';
```

- [ ] **Step 2: 验证迁移应用** `cd backend && mvn -q -Dtest=SeedImporterIT test` → PASS(Flyway 应用 V1–V8 无错)。

- [ ] **Step 3: 提交**
```bash
git add backend/src/main/resources/db/migration/V8__message.sql
git commit -m "feat(message): V8 message 迁移(template+params,version去重键) (#7B)"
```

---

## Task 2: 切片 A 事件加 version/route + BusDeletedEvent

**Files:** Modify `bus/service/BusUpdatedEvent.java`、`bus/service/BusCommandService.java`、`bus/mapper/BusWriteMapper.java`+xml;Create `bus/service/BusDeletedEvent.java`

- [ ] **Step 1: 改 BusUpdatedEvent**(加 version、route)
```java
package com.airportbus.bus.service;

/** 线路内容变化事件;切片 B 用 @TransactionalEventListener(AFTER_COMMIT) 监听。 */
public record BusUpdatedEvent(long busRouteId, String sourceId, String route, int version,
                              String oldHash, String newHash, ChangedSummary summary) {}
```

- [ ] **Step 2: 新建 BusDeletedEvent**
```java
package com.airportbus.bus.service;

/** 线路下线事件;切片 B 监听 → 通知订阅者 + 清理收藏。 */
public record BusDeletedEvent(long busRouteId, String sourceId, String route) {}
```

- [ ] **Step 3: BusWriteMapper 加 selectRouteBySource**
`BusWriteMapper.java`:
```java
    String selectRouteBySource(@Param("sourceId") String sourceId);
```
`BusWriteMapper.xml`:
```xml
  <select id="selectRouteBySource" resultType="string">
    SELECT route FROM bus_route WHERE source_id=#{sourceId} AND deleted=0
  </select>
```

- [ ] **Step 4: 改 BusCommandService publish 处**
两处 `new BusUpdatedEvent(...)` 改为带 version/route。
create 分支(原 `events.publishEvent(new BusUpdatedEvent(busRouteId, sourceId, null, newHash, summary));`):
```java
            if (!suppressEvents) events.publishEvent(
                new BusUpdatedEvent(busRouteId, sourceId, input.route(), newVersion, null, newHash, summary));
```
update 分支(原 `... new BusUpdatedEvent(busRouteId, sourceId, oldHash, newHash, summary)`):
```java
        if (!suppressEvents) events.publishEvent(
                new BusUpdatedEvent(busRouteId, sourceId, input.route(), newVersion, oldHash, newHash, summary));
```
`delete()` 方法体改为(取 busRouteId+route → 软删 → 发事件):
```java
    @Transactional
    public void delete(String sourceId, String actor) {
        if (writeMapper.selectVersionHash(sourceId) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        long busRouteId = writeMapper.findBusId(sourceId);
        String route = writeMapper.selectRouteBySource(sourceId);
        writeMapper.softDeleteBus(sourceId, actor);
        events.publishEvent(new BusDeletedEvent(busRouteId, sourceId, route));
    }
```

- [ ] **Step 5: 编译 + 回归切片 A IT**(事件无监听者时发布即丢弃,IT 不断言事件)
Run: `cd backend && mvn -q -Dtest=BusCommandServiceIT test` → PASS(7)。

- [ ] **Step 6: 提交**
```bash
git add backend/src/main/java/com/airportbus/bus/service/BusUpdatedEvent.java \
        backend/src/main/java/com/airportbus/bus/service/BusDeletedEvent.java \
        backend/src/main/java/com/airportbus/bus/service/BusCommandService.java \
        backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java \
        backend/src/main/resources/mapper/BusWriteMapper.xml
git commit -m "feat(bus): 事件加 version/route + BusDeletedEvent(delete 发布) (#7B)"
```

---

## Task 3: FavoriteService 订阅者查询 + 按线路软删收藏

**Files:** Modify `user/mapper/FavoriteMapper.java`+xml、`user/service/FavoriteService.java`;Test `user/service/FavoriteSubscriberIT.java`

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/user/service/FavoriteSubscriberIT.java`
```java
package com.airportbus.user.service;

import com.airportbus.user.mapper.FavoriteMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class FavoriteSubscriberIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired FavoriteService favorites;
    @Autowired FavoriteMapper favoriteMapper;
    @Autowired UserMapper users;
    @Autowired com.airportbus.bus.mapper.BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId);
        return u.id;
    }

    @Test void activeSubscriberUserIds_listsSubscribers() {
        long busId = busWrite.findBusId("vie-vab1");
        long u1 = subscribe("fsub1", "vie-vab1");
        long u2 = subscribe("fsub2", "vie-vab1");
        List<Long> ids = favorites.activeSubscriberUserIds(busId);
        assertThat(ids).contains(u1, u2);
    }

    @Test void softDeleteByBusRouteId_removesAll() {
        long busId = busWrite.findBusId("vie-vab1");
        subscribe("fsub3", "vie-vab1");
        favorites.softDeleteByBusRouteId(busId, "admin");
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败** `cd backend && mvn -q -Dtest=FavoriteSubscriberIT test` → 编译失败(方法不存在)。

- [ ] **Step 3: FavoriteMapper 加方法**
`FavoriteMapper.java`:
```java
    java.util.List<Long> selectActiveUserIdsByBusRouteId(@Param("busRouteId") long busRouteId);
    int softDeleteByBusRouteId(@Param("busRouteId") long busRouteId, @Param("actor") String actor);
```
`FavoriteMapper.xml`:
```xml
  <select id="selectActiveUserIdsByBusRouteId" resultType="long">
    SELECT user_id FROM favorite WHERE bus_route_id=#{busRouteId} AND deleted=0
  </select>
  <update id="softDeleteByBusRouteId">
    UPDATE favorite SET deleted=1, updated_by=#{actor}, updated_at=CURRENT_TIMESTAMP
    WHERE bus_route_id=#{busRouteId} AND deleted=0
  </update>
```

- [ ] **Step 4: FavoriteService 暴露(供 message 模块跨模块调用,E5)**
`FavoriteService.java` 加:
```java
    public java.util.List<Long> activeSubscriberUserIds(long busRouteId) {
        return mapper.selectActiveUserIdsByBusRouteId(busRouteId);
    }
    public int softDeleteByBusRouteId(long busRouteId, String actor) {
        return mapper.softDeleteByBusRouteId(busRouteId, actor);
    }
```

- [ ] **Step 5: 运行确认通过** → PASS(2)。

- [ ] **Step 6: 提交**
```bash
git add backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java \
        backend/src/main/resources/mapper/FavoriteMapper.xml \
        backend/src/main/java/com/airportbus/user/service/FavoriteService.java \
        backend/src/test/java/com/airportbus/user/service/FavoriteSubscriberIT.java
git commit -m "feat(message): FavoriteService 订阅者查询 + 按线路软删收藏 (#7B)"
```

---

## Task 4: message 行模型 + Mapper + Redis 计数 + @MapperScan

**Files:** Create `message/Message.java`、`message/mapper/MessageMapper.java`+`resources/mapper/MessageMapper.xml`、`message/MessageUnreadCounter.java`;Modify `AirportbusApplication.java`

> 接线 + Redis 组件;由 Task 5/6 IT 覆盖。本任务编译验证 + counter 可单测(用 Testcontainers redis,放进 Task 5 一起测)。

- [ ] **Step 1: Message 行模型** `message/Message.java`
```java
package com.airportbus.message;

import java.time.LocalDateTime;

/** message 行 + 列表视图(relatedSourceId 由 join 得)。 */
public record Message(long id, long userId, String templateCode, String paramsJson,
                      String relatedSourceId, boolean isRead, LocalDateTime createdAt) {}
```

- [ ] **Step 2: MessageMapper** `message/mapper/MessageMapper.java`
```java
package com.airportbus.message.mapper;

import com.airportbus.message.Message;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface MessageMapper {
    /** 批量插(幂等:命中 uk_msg_dedup 则忽略)。rows 每个 map: userId,templateCode,paramsJson,relatedBusRouteId,dedupKey,actor。 */
    int batchInsert(@Param("rows") List<java.util.Map<String, Object>> rows);

    long countUnread(@Param("userId") long userId);

    List<Message> selectPage(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    int markRead(@Param("userId") long userId, @Param("ids") List<Long> ids);

    int softDelete(@Param("userId") long userId, @Param("id") long id);

    /** 对账:有活跃订阅者但缺「当前 version」消息的 (userId, busRouteId, version, route, sourceId)。 */
    List<Backfill> selectMissingForCurrentVersion();

    record Backfill(long userId, long busRouteId, int version, String route, String sourceId) {}
}
```

- [ ] **Step 3: MessageMapper.xml** `backend/src/main/resources/mapper/MessageMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.message.mapper.MessageMapper">

  <insert id="batchInsert">
    INSERT INTO message (user_id, template_code, params_json, related_bus_route_id, dedup_key, created_by)
    VALUES
    <foreach collection="rows" item="r" separator=",">
      (#{r.userId}, #{r.templateCode}, #{r.paramsJson}, #{r.relatedBusRouteId}, #{r.dedupKey}, #{r.actor})
    </foreach>
    ON DUPLICATE KEY UPDATE id = id
  </insert>

  <select id="countUnread" resultType="long">
    SELECT COUNT(*) FROM message WHERE user_id=#{userId} AND is_read=0 AND deleted=0
  </select>

  <select id="selectPage" resultType="com.airportbus.message.Message">
    SELECT m.id, m.user_id AS userId, m.template_code AS templateCode, m.params_json AS paramsJson,
           br.source_id AS relatedSourceId, m.is_read AS isRead, m.created_at AS createdAt
    FROM message m
    LEFT JOIN bus_route br ON br.id = m.related_bus_route_id
    WHERE m.user_id=#{userId} AND m.deleted=0
    ORDER BY m.created_at DESC, m.id DESC
    LIMIT #{limit} OFFSET #{offset}
  </select>

  <update id="markRead">
    UPDATE message SET is_read=1, read_at=CURRENT_TIMESTAMP
    WHERE user_id=#{userId} AND deleted=0 AND is_read=0 AND id IN
    <foreach collection="ids" item="i" open="(" separator="," close=")">#{i}</foreach>
  </update>

  <update id="softDelete">
    UPDATE message SET deleted=1 WHERE user_id=#{userId} AND id=#{id} AND deleted=0
  </update>

  <!-- 对账:活跃订阅 × 线路当前 version,缺对应 BUS_UPDATED 消息的回填项 -->
  <select id="selectMissingForCurrentVersion" resultType="com.airportbus.message.mapper.MessageMapper$Backfill">
    SELECT f.user_id AS userId, b.id AS busRouteId, b.version AS version, b.route AS route, b.source_id AS sourceId
    FROM favorite f
    JOIN bus_route b ON b.id = f.bus_route_id AND b.deleted = 0
    WHERE f.deleted = 0 AND b.version > 0
      AND NOT EXISTS (
        SELECT 1 FROM message m
        WHERE m.user_id = f.user_id AND m.deleted = 0
          AND m.dedup_key = CONCAT('bus:', b.id, ':v:', b.version)
      )
  </select>
</mapper>
```

- [ ] **Step 4: Redis 未读计数** `message/MessageUnreadCounter.java`
```java
package com.airportbus.message;

import com.airportbus.message.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 未读计数:Redis 加速、DB 权威。写时 invalidate(DEL),读时 miss → DB COUNT 重建 + TTL。Redis 异常吞掉、回退 DB。 */
@Component
public class MessageUnreadCounter {
    private static final Logger log = LoggerFactory.getLogger(MessageUnreadCounter.class);
    private static final String PREFIX = "msg:unread:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final MessageMapper mapper;

    public MessageUnreadCounter(StringRedisTemplate redis, MessageMapper mapper) {
        this.redis = redis; this.mapper = mapper;
    }

    public long unread(long userId) {
        String key = PREFIX + userId;
        try {
            String v = redis.opsForValue().get(key);
            if (v != null) return Long.parseLong(v);
        } catch (Exception e) { log.warn("unread redis get failed {}: {}", userId, e.toString()); }
        long count = mapper.countUnread(userId);
        try { redis.opsForValue().set(key, Long.toString(count), TTL); }
        catch (Exception e) { log.warn("unread redis set failed {}: {}", userId, e.toString()); }
        return count;
    }

    public void invalidate(long userId) {
        try { redis.delete(PREFIX + userId); }
        catch (Exception e) { log.warn("unread redis del failed {}: {}", userId, e.toString()); }
    }
}
```

- [ ] **Step 5: @MapperScan 加 message** —— `AirportbusApplication.java` 的 `@MapperScan` 数组加 `"com.airportbus.message.mapper"`(现为 bus.mapper/user.mapper/audit)。

- [ ] **Step 6: 编译** `cd backend && mvn -q -DskipTests compile` → SUCCESS。

- [ ] **Step 7: 提交**
```bash
git add backend/src/main/java/com/airportbus/message/Message.java \
        backend/src/main/java/com/airportbus/message/mapper/MessageMapper.java \
        backend/src/main/resources/mapper/MessageMapper.xml \
        backend/src/main/java/com/airportbus/message/MessageUnreadCounter.java \
        backend/src/main/java/com/airportbus/AirportbusApplication.java
git commit -m "feat(message): Message 模型 + Mapper(批量幂等/未读/分页/对账) + Redis 计数 (#7B)"
```

---

## Task 5: MessageService —— 扇出 + 列表/已读/删除

**Files:** Create `message/MessageService.java`;Test `message/MessageServiceIT.java`

依赖:`MessageMapper`、`MessageUnreadCounter`、`com.airportbus.user.service.FavoriteService`(订阅者/收藏软删)、`com.fasterxml.jackson.databind.ObjectMapper`。

- [ ] **Step 1: 写失败 IT** `backend/src/test/java/com/airportbus/message/MessageServiceIT.java`
```java
package com.airportbus.message;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.bus.service.ChangedSummary;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.service.FavoriteService;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class MessageServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MessageService service;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId); CurrentUser.clear();
        return u.id;
    }
    private BusUpdatedEvent updEvent(int version) {
        long busId = busWrite.findBusId("vie-vab1");
        var sum = new ChangedSummary(List.of(new ChangedSummary.FieldChange("price", "€11", "€13")), List.of());
        return new BusUpdatedEvent(busId, "vie-vab1", "VAB 1", version, "h0", "h1", sum);
    }

    @Test void fanOutUpdated_deliversToSubscribers_withDiff_unreadCounts() {
        long u1 = subscribe("msvc1", "vie-vab1");
        service.fanOutUpdated(updEvent(5));
        assertThat(service.unreadCount(u1)).isEqualTo(1);
        List<Message> page = service.list(u1, 20, 0);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).templateCode()).isEqualTo("BUS_UPDATED");
        assertThat(page.get(0).paramsJson()).contains("price").contains("€13");
    }

    @Test void fanOutUpdated_isIdempotent_onSameVersion() {
        long u1 = subscribe("msvc2", "vie-vab1");
        service.fanOutUpdated(updEvent(7));
        service.fanOutUpdated(updEvent(7)); // 重投同 version
        assertThat(service.unreadCount(u1)).isEqualTo(1);
    }

    @Test void fanOutOffline_notifies_andSoftDeletesFavorites() {
        long u1 = subscribe("msvc3", "vie-vab1");
        long busId = busWrite.findBusId("vie-vab1");
        service.fanOutOffline(new BusDeletedEvent(busId, "vie-vab1", "VAB 1"));
        assertThat(service.unreadCount(u1)).isEqualTo(1);
        assertThat(service.list(u1, 20, 0).get(0).templateCode()).isEqualTo("BUS_OFFLINE");
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty(); // 收藏被软删
    }

    @Test void markRead_and_delete_updateUnread() {
        long u1 = subscribe("msvc4", "vie-vab1");
        service.fanOutUpdated(updEvent(9));
        long id = service.list(u1, 20, 0).get(0).id();
        service.markRead(u1, List.of(id));
        assertThat(service.unreadCount(u1)).isEqualTo(0);
        service.delete(u1, id);
        assertThat(service.list(u1, 20, 0)).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败** → 编译失败(MessageService 不存在)。

- [ ] **Step 3: 写 MessageService** `backend/src/main/java/com/airportbus/message/MessageService.java`
```java
package com.airportbus.message;

import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.message.mapper.MessageMapper;
import com.airportbus.user.service.FavoriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站内信扇出 + 读写。订阅者/收藏软删走 FavoriteService(E5)。 */
@Service
public class MessageService {

    private static final int BATCH = 500; // E12 分批

    private final MessageMapper mapper;
    private final MessageUnreadCounter counter;
    private final FavoriteService favorites;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public MessageService(MessageMapper mapper, MessageUnreadCounter counter, FavoriteService favorites) {
        this.mapper = mapper; this.counter = counter; this.favorites = favorites;
    }

    @Transactional
    public void fanOutUpdated(BusUpdatedEvent e) {
        List<Long> userIds = favorites.activeSubscriberUserIds(e.busRouteId());
        if (userIds.isEmpty()) return;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("route", e.route());
        params.put("sourceId", e.sourceId());
        params.put("changed", e.summary() == null ? List.of() : e.summary().scalars());
        params.put("changedSubtables", e.summary() == null ? List.of() : e.summary().changedSubtables());
        String paramsJson = writeJson(params);
        String dedup = "bus:" + e.busRouteId() + ":v:" + e.version();
        insertFanout(userIds, "BUS_UPDATED", paramsJson, e.busRouteId(), dedup);
    }

    @Transactional
    public void fanOutOffline(BusDeletedEvent e) {
        List<Long> userIds = favorites.activeSubscriberUserIds(e.busRouteId());
        if (!userIds.isEmpty()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("route", e.route());
            params.put("sourceId", e.sourceId());
            insertFanout(userIds, "BUS_OFFLINE", writeJson(params), e.busRouteId(), "bus:" + e.busRouteId() + ":offline");
        }
        favorites.softDeleteByBusRouteId(e.busRouteId(), "system"); // 清理收藏(站内信历史保留)
    }

    /** 对账回填:对单个 (user, bus, version) 补一条 BUS_UPDATED(幂等)。 */
    @Transactional
    public void backfill(MessageMapper.Backfill b) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("route", b.route()); params.put("sourceId", b.sourceId());
        params.put("changed", List.of()); params.put("changedSubtables", List.of());
        insertFanout(List.of(b.userId()), "BUS_UPDATED", writeJson(params), b.busRouteId(),
                "bus:" + b.busRouteId() + ":v:" + b.version());
    }

    private void insertFanout(List<Long> userIds, String code, String paramsJson, long busRouteId, String dedup) {
        for (int i = 0; i < userIds.size(); i += BATCH) {
            List<Long> chunk = userIds.subList(i, Math.min(i + BATCH, userIds.size()));
            List<Map<String, Object>> rows = new ArrayList<>(chunk.size());
            for (Long uid : chunk) {
                Map<String, Object> row = new HashMap<>();
                row.put("userId", uid); row.put("templateCode", code); row.put("paramsJson", paramsJson);
                row.put("relatedBusRouteId", busRouteId); row.put("dedupKey", dedup); row.put("actor", "system");
                rows.add(row);
            }
            mapper.batchInsert(rows);
        }
        for (Long uid : userIds) counter.invalidate(uid); // 写后 invalidate,下次读重建(避开 dedup 计数偏差)
    }

    public long unreadCount(long userId) { return counter.unread(userId); }
    public List<Message> list(long userId, int limit, int offset) {
        return mapper.selectPage(userId, limit < 1 ? 20 : Math.min(limit, 100), Math.max(offset, 0));
    }
    @Transactional
    public int markRead(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = mapper.markRead(userId, ids);
        counter.invalidate(userId);
        return n;
    }
    @Transactional
    public void delete(long userId, long id) { mapper.softDelete(userId, id); counter.invalidate(userId); }

    private String writeJson(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```

- [ ] **Step 4: 运行确认通过** `cd backend && mvn -q -Dtest=MessageServiceIT test` → PASS(4)。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/java/com/airportbus/message/MessageService.java \
        backend/src/test/java/com/airportbus/message/MessageServiceIT.java
git commit -m "feat(message): MessageService 扇出(BUS_UPDATED/OFFLINE 幂等+分批)+列表/已读/删除 (#7B)"
```

---

## Task 6: BusEventListener —— AFTER_COMMIT @Async 端到端

**Files:** Create `message/BusEventListener.java`;Test `message/PushLoopIT.java`

- [ ] **Step 1: 写失败 IT**(端到端:管理员保存/删除 → 订阅者收信)`backend/src/test/java/com/airportbus/message/PushLoopIT.java`
```java
package com.airportbus.message;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.service.FavoriteService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "airportbus.message.reconcile-delay-ms=3600000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class PushLoopIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired BusCommandService busCmd;
    @Autowired MessageService messages;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long subscribe(String name, String sourceId) {
        AppUser u = new AppUser(); u.username=name; u.email=name+"@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER")); favorites.favorite(sourceId); CurrentUser.clear();
        return u.id;
    }
    private BusInput input(String price) {
        return new BusInput("VAB 1", "西站", "ÖBB", null, "40min", price, "03:00-24:00", null,
                List.of("A"), List.of(new BusDetailDto.Schedule("all day","30min",null)), List.of(), List.of(), List.of());
    }

    @Test void adminSave_pushesToSubscriber() {
        long u1 = subscribe("push1", "vie-vab1");
        int v = busWrite.selectVersionHash("vie-vab1").version();
        busCmd.save("vie-vab1", busWrite.findAirportId("VIE"), input("€" + System.currentTimeMillis() % 1000), v, "admin:1", false);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(messages.unreadCount(u1)).isEqualTo(1));
        assertThat(messages.list(u1, 20, 0).get(0).templateCode()).isEqualTo("BUS_UPDATED");
    }

    @Test void adminDelete_pushesOffline_andClearsFavorite() {
        long u1 = subscribe("push2", "vie-vab2");
        long busId = busWrite.findBusId("vie-vab2");
        busCmd.delete("vie-vab2", "admin:1");
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(messages.list(u1, 20, 0)).anyMatch(m -> m.templateCode().equals("BUS_OFFLINE")));
        assertThat(favorites.activeSubscriberUserIds(busId)).isEmpty();
    }
}
```
> 用 `awaitility`(@Async 异步)。若 `org.awaitility:awaitility` 不在测试依赖,先加到 `backend/pom.xml`(test scope)。种子线路 `vie-vab1`/`vie-vab2` 来自 data.json。

- [ ] **Step 2: 运行确认失败** → 监听器不存在,消息不产生(超时失败)。

- [ ] **Step 3: 写 BusEventListener** `backend/src/main/java/com/airportbus/message/BusEventListener.java`
```java
package com.airportbus.message;

import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/** 推送闭环:bus 提交后异步扇出站内信。E3:失败由 MessageReconciler 兜底。 */
@Component
public class BusEventListener {
    private final MessageService messages;
    public BusEventListener(MessageService messages) { this.messages = messages; }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBusUpdated(BusUpdatedEvent e) { messages.fanOutUpdated(e); }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBusDeleted(BusDeletedEvent e) { messages.fanOutOffline(e); }
}
```

- [ ] **Step 4: 运行确认通过** `cd backend && mvn -q -Dtest=PushLoopIT test` → PASS(2)。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/java/com/airportbus/message/BusEventListener.java \
        backend/src/test/java/com/airportbus/message/PushLoopIT.java backend/pom.xml
git commit -m "feat(message): BusEventListener AFTER_COMMIT @Async 扇出(端到端闭环) (#7B)"
```

---

## Task 7: MessageReconciler —— @Scheduled 对账(E3)

**Files:** Create `message/MessageReconciler.java`;Test `message/ReconcilerIT.java`

- [ ] **Step 1: 写失败 IT**(造「有订阅者但缺当前 version 消息」→ 调对账 → 回填)`backend/src/test/java/com/airportbus/message/ReconcilerIT.java`
```java
package com.airportbus.message;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.user.model.AppUser;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import com.airportbus.user.service.FavoriteService;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "airportbus.message.reconcile-delay-ms=3600000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class ReconcilerIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired MessageReconciler reconciler;
    @Autowired MessageService messages;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper users;
    @Autowired BusWriteMapper busWrite;
    @Autowired BusCommandService busCmd;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    @Test void reconcile_backfillsMissedSubscriber() {
        // 用户订阅 vie-vab3 后,直接改 version(绕过监听器无法做);改用:先让 bus 有 version,再订阅(订阅在变更后 → 监听器当时无此订阅者 → 漏发)
        long busId = busWrite.findBusId("vie-vab3");
        // 触发一次内容变更(产生新 version),此刻无人订阅 → 无消息
        int v = busWrite.selectVersionHash("vie-vab3").version();
        busCmd.save("vie-vab3", busWrite.findAirportId("VIE"),
                new BusInput("VAB 3","x","y",null,"d","€"+(System.currentTimeMillis()%1000),"oh",null,
                        List.of("A"), List.of(new BusDetailDto.Schedule("t","i",null)), List.of(), List.of(), List.of()),
                v, "admin:1", true); // suppressEvents=true 模拟「漏发」(事件没发出)
        // 现在用户订阅
        AppUser u = new AppUser(); u.username="rec1"; u.email="rec1@x.com"; u.passwordHash="x";
        u.locale="zh-CN"; u.role="USER"; u.emailVerified=false; users.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id,"USER")); favorites.favorite("vie-vab3"); CurrentUser.clear();
        assertThat(messages.unreadCount(u.id)).isEqualTo(0); // 还没有消息

        reconciler.reconcile(); // 对账回填

        assertThat(messages.unreadCount(u.id)).isEqualTo(1);
        assertThat(messages.list(u.id, 20, 0).get(0).templateCode()).isEqualTo("BUS_UPDATED");
    }
}
```
> 注:用 `suppressEvents=true` 模拟「事件未送达」的漏发态(等价进程崩溃)。`vie-vab3` 是 data.json 种子线路。

- [ ] **Step 2: 运行确认失败** → 编译失败(MessageReconciler 不存在)。

- [ ] **Step 3: 写 MessageReconciler** `backend/src/main/java/com/airportbus/message/MessageReconciler.java`
```java
package com.airportbus.message;

import com.airportbus.message.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** E3 投递对账:周期回填「有活跃订阅者但缺当前 version 消息」的漏发。幂等(走 fanout 的 ON DUPLICATE 去重)。 */
@Component
public class MessageReconciler {
    private static final Logger log = LoggerFactory.getLogger(MessageReconciler.class);
    private final MessageMapper mapper;
    private final MessageService messages;

    public MessageReconciler(MessageMapper mapper, MessageService messages) {
        this.mapper = mapper; this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${airportbus.message.reconcile-delay-ms:300000}")
    public void reconcile() {
        try {
            var missing = mapper.selectMissingForCurrentVersion();
            if (missing.isEmpty()) return;
            for (MessageMapper.Backfill b : missing) messages.backfill(b);
            log.info("message reconcile backfilled {} rows", missing.size());
        } catch (Exception e) {
            log.warn("message reconcile failed: {}", e.toString());
        }
    }
}
```

- [ ] **Step 4: 运行确认通过** `cd backend && mvn -q -Dtest=ReconcilerIT test` → PASS(1)。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/java/com/airportbus/message/MessageReconciler.java \
        backend/src/test/java/com/airportbus/message/ReconcilerIT.java
git commit -m "feat(message): @Scheduled 投递对账回填漏发(E3) (#7B)"
```

---

## Task 8: MessageController + API IT

**Files:** Create `message/api/MessageController.java`、`message/api/dto/MarkReadRequest.java`、`message/api/dto/UnreadCountDto.java`;Test `message/api/MessageApiIT.java`

- [ ] **Step 1: 请求/响应 DTO**
```java
// message/api/dto/MarkReadRequest.java
package com.airportbus.message.api.dto;
import java.util.List;
public record MarkReadRequest(List<Long> ids) {}
```
```java
// message/api/dto/UnreadCountDto.java
package com.airportbus.message.api.dto;
public record UnreadCountDto(long count) {}
```

- [ ] **Step 2: 写失败 IT** `backend/src/test/java/com/airportbus/message/api/MessageApiIT.java`
```java
package com.airportbus.message.api;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.bus.service.ChangedSummary;
import com.airportbus.message.MessageService;
import com.airportbus.user.security.AuthCacheService;
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

    @Test void unreadCount_list_markRead_flow() throws Exception {
        String tok = token("msgapi1");
        // 该用户先收藏 vie-vab1(订阅),再制造一次推送
        mvc.perform(put("/api/v1/buses/vie-vab1/favorite").header("Authorization","Bearer "+tok)).andExpect(status().isOk());
        long busId = busWrite.findBusId("vie-vab1");
        var sum = new ChangedSummary(List.of(new ChangedSummary.FieldChange("price","€11","€13")), List.of());
        // 找该用户 id:解析 token 不便,改为对所有订阅者扇出(只该用户订阅了)
        messages.fanOutUpdated(new BusUpdatedEvent(busId, "vie-vab1", "VAB 1", 123, "h0","h1", sum));

        mvc.perform(get("/api/v1/messages/unread-count").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        String listRes = mvc.perform(get("/api/v1/messages").header("Authorization","Bearer "+tok))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].templateCode").value("BUS_UPDATED"))
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
```
> 注:`token(...)` 注册拿 token、该用户收藏 vie-vab1(订阅)、`messages.fanOutUpdated(...)` 给订阅者造消息(只该用户订阅)。`vie-vab1` 是种子线路。

- [ ] **Step 3: 运行确认失败** → 404/编译。

- [ ] **Step 4: 写 MessageController** `backend/src/main/java/com/airportbus/message/api/MessageController.java`
```java
package com.airportbus.message.api;

import com.airportbus.message.Message;
import com.airportbus.message.MessageService;
import com.airportbus.message.api.dto.MarkReadRequest;
import com.airportbus.message.api.dto.UnreadCountDto;
import com.airportbus.user.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "message", description = "站内信(需登录)")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final MessageService service;
    public MessageController(MessageService service) { this.service = service; }

    @GetMapping("/unread-count")
    public UnreadCountDto unread() {
        return new UnreadCountDto(service.unreadCount(CurrentUser.require().userId()));
    }

    @GetMapping
    public List<Message> list(@RequestParam(defaultValue = "20") int limit,
                              @RequestParam(defaultValue = "0") int offset) {
        return service.list(CurrentUser.require().userId(), limit, offset);
    }

    @PostMapping("/read")
    public java.util.Map<String, Integer> markRead(@RequestBody MarkReadRequest req) {
        int n = service.markRead(CurrentUser.require().userId(), req.ids());
        return java.util.Map.of("updated", n);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.require().userId(), id);
    }
}
```
> `Message.paramsJson` 是 JSON 字符串字段。**前端需要解析后的对象**:控制器返回 `Message`(含 `paramsJson` 字符串)即可,前端 `JSON.parse`。或在 list DTO 里把 params 解析为对象——本期保持返回字符串 `paramsJson`,前端解析(plan 前端任务处理)。

- [ ] **Step 5: 运行确认通过** `cd backend && mvn -q -Dtest=MessageApiIT test` → PASS(2)。

- [ ] **Step 6: 提交**
```bash
git add backend/src/main/java/com/airportbus/message/api/ \
        backend/src/test/java/com/airportbus/message/api/MessageApiIT.java
git commit -m "feat(message): MessageController 未读数/列表/已读/删除 API (#7B)"
```

---

## Task 9: 全量后端 IT 回归

- [ ] **Step 1: 跑全套**
Run:
```bash
cd backend && mvn -q -Dtest=BusCommandServiceIT,SeedImporterIT,BusQueryServiceIT,SearchHotnessServiceIT,HotnessRankingIT,AuthFlowIT,AuthServiceIT,AuthCacheServiceIT,FavoriteServiceIT,FavoriteApiIT,FavoriteSubscriberIT,UserStatsServiceIT,FavoriteStatsServiceIT,AdminStatsApiIT,AuditAspectIT,AdminBusApiIT,AuditApiIT,CurrentUserTest,CurrentUserSuperAdminTest,BusDiffTest,MessageServiceIT,PushLoopIT,ReconcilerIT,MessageApiIT test
```
Expected: 全 PASS。失败读 surefire 报告定位修复。

- [ ] **Step 2: 提交(若有修复)**
```bash
git add -A && git commit -m "test(#7B): 后端全量 IT 回归通过"
```

---

## 自审清单(写计划者已核对)
- **spec 覆盖**:V8 表(T1)、事件加 version/route + BusDeletedEvent(T2)、订阅者查询/收藏软删(T3)、message mapper+Redis 计数(T4)、扇出+读写(T5)、AFTER_COMMIT @Async 端到端(T6)、对账 E3(T7)、API(T8)、回归(T9)。E12 分批 500 在 T5 insertFanout;E14 系统消息 offline 去重键在 T5;D6 params_json 前端渲染(前端 plan)。✅
- **类型一致**:`BusUpdatedEvent(... version, route ...)`、`BusDeletedEvent`、`Message`、`MessageMapper.Backfill`、`MarkReadRequest`/`UnreadCountDto` 全程一致。✅
- **占位**:无。全部完整代码。
- **风险**:`awaitility` 测试依赖(T6 检查 pom);Redis 未读用 invalidate-on-write(规避 dedup 计数偏差,比 +1/-1 更稳);对账查询是跨表只读 join(批量作业可接受)。
