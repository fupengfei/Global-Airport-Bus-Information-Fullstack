# 收藏 = 订阅(#3 订阅侧)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让登录用户收藏 / 取消收藏 bus 线路、查看「我的收藏」,首页卡片与详情页显示收藏态;零 admin / 零 message 依赖,端到端可部署。

**Architecture:** 后端在 `user` 模块新增 `favorite` 表 + Mapper/Service/Controller,经 `BusQueryService` 解析 `source_id → bus_route_id`(跨模块走 Service 接口)。收藏是可反复开关的关系:唯一键 `(user_id, bus_route_id)` 不含 `deleted`,靠翻转 `deleted` 切换。收藏态不进共享 Redis 查询缓存,前端登录后单独拉 `/favorites/ids` 在客户端打标记。

**Tech Stack:** Spring Boot + MyBatis(只用 `#{}`)+ Flyway 迁移;Vue 3 + Pinia + vue-i18n + vitest。

参考规范:`docs/design.md`(订阅→推送闭环、Redis 用途)、`CLAUDE.md`(锁定:收藏=订阅、无通知开关)、`docs/superpowers/specs/2026-06-19-favorites-subscription-design.md`。

## 命令速查

- 后端编译:`cd backend && mvn -q -DskipTests compile`
- 后端 IT(`*IT` 默认 surefire 不跑,必须 `-Dtest=`;需 Docker 跑 Testcontainers):
  `cd backend && mvn -q -Dtest=<ClassName> test`
- 前端单测:`cd frontend && npx vitest run src/test/<file>.spec.ts`
- 前端全量:`cd frontend && npm run test`

## 文件结构

后端(`backend/src/main/java/com/airportbus/`):
- `user/api/FavoriteController.java` —— 4 个端点。
- `user/api/dto/FavoriteStatusDto.java` —— `{favorited}` 返回体。
- `user/service/FavoriteService.java` —— source_id 解析 + 幂等开关 + 列表组装。
- `user/mapper/FavoriteMapper.java` + `resources/mapper/FavoriteMapper.xml` —— upsert / 软删 / 查收藏 source_id。
- `resources/db/migration/V4__favorite.sql` —— 建表。
- `bus/mapper/BusQueryMapper.java`(改)+ `BusQueryMapper.xml`(改)—— 加 `selectIdBySourceId`。
- `bus/service/BusQueryService.java`(改)—— 加 `requireBusRouteId`。
- 测试:`user/service/FavoriteServiceIT.java`、`user/api/FavoriteApiIT.java`。

前端(`frontend/src/`):
- `api/favorites.ts` —— 4 个调用。
- `stores/favorites.ts` —— Pinia 收藏态 store。
- `components/BusCard.vue`(改)—— 收藏心。
- `stores/auth.ts`(改)+ `App.vue`(改)—— 登录后加载 / 登出清空。
- `pages/LoginPage.vue`(改)—— 登录 / 注册后按 `redirect` 回跳。
- `pages/MePage.vue`(改)—— 我的收藏区块。
- `i18n/locales/{zh-CN,en,de}.ts`(改)—— `favorite.*` 文案。
- 测试:`test/favorites.store.spec.ts`、`test/BusCard.spec.ts`、`test/MePage.spec.ts`。

---

## Task 1:迁移 + source_id → bus_route_id 解析器

打底:建 `favorite` 表,并在 bus 模块提供「按 source_id 取内部 id(排除逻辑删除)」的解析器。用 `BusQueryServiceIT` 加两条测试驱动解析器。

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__favorite.sql`
- Modify: `backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java`
- Modify: `backend/src/main/resources/mapper/BusQueryMapper.xml`
- Modify: `backend/src/main/java/com/airportbus/bus/service/BusQueryService.java`
- Test: `backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java`

- [ ] **Step 1:写迁移文件**

`backend/src/main/resources/db/migration/V4__favorite.sql`:

```sql
-- V4__favorite.sql  收藏(=订阅);唯一键不含 deleted,靠翻转 deleted 切换收藏态;字段带列注释
CREATE TABLE favorite (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT             COMMENT '主键',
  user_id       BIGINT      NOT NULL                              COMMENT '收藏人(app_user.id)',
  bus_route_id  BIGINT      NOT NULL                              COMMENT '被收藏的巴士线路内部ID(非source_id)',
  created_by    VARCHAR(64) NULL                                  COMMENT '创建人',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  updated_by    VARCHAR(64) NULL                                  COMMENT '更新人',
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT(1)  NOT NULL DEFAULT 0                    COMMENT '逻辑删除/收藏态:0=已收藏,1=已取消',
  UNIQUE KEY uk_fav_user_bus (user_id, bus_route_id),
  KEY idx_fav_user (user_id),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏(=订阅)巴士线路';
```

- [ ] **Step 2:在 `BusQueryMapper.java` 加解析方法**

在接口里(其它 `select*` 方法旁)新增:

```java
    Long selectIdBySourceId(@Param("sourceId") String sourceId);
```

- [ ] **Step 3:在 `BusQueryMapper.xml` 加对应 SQL**

在 `selectBusHead` 之后新增(**带 `deleted = 0` 过滤**,与收藏语义一致):

```xml
  <select id="selectIdBySourceId" resultType="long">
    SELECT id FROM bus_route WHERE source_id = #{sourceId} AND deleted = 0
  </select>
```

- [ ] **Step 4:在 `BusQueryService` 加 `requireBusRouteId`**

在类中新增(非缓存方法;不存在按统一错误码 404):

```java
    /** source_id → 内部 bus_route_id;不存在或已删除抛 BUS_NOT_FOUND(404)。收藏模块跨模块复用。 */
    public long requireBusRouteId(String sourceId) {
        Long id = mapper.selectIdBySourceId(sourceId);
        if (id == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: " + sourceId);
        return id;
    }
```

- [ ] **Step 5:写失败测试**

在 `BusQueryServiceIT` 里加两个测试方法(该 IT 已配 Testcontainers + seed;参考类内现有用例的注入与 `@Test` 写法)。已知种子线路 `vie-vab1`:

```java
    @Test
    void requireBusRouteId_returnsId_forKnownSource() {
        long id = service.requireBusRouteId("vie-vab1");
        org.junit.jupiter.api.Assertions.assertTrue(id > 0);
    }

    @Test
    void requireBusRouteId_throwsBusNotFound_forUnknown() {
        com.airportbus.common.ApiException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.airportbus.common.ApiException.class,
                () -> service.requireBusRouteId("no-such-bus"));
        org.junit.jupiter.api.Assertions.assertEquals(
                com.airportbus.common.ErrorCode.BUS_NOT_FOUND, ex.code); // ApiException.code 是 public 字段
    }
```

- [ ] **Step 6:跑测试,确认通过**

Run: `cd backend && mvn -q -Dtest=BusQueryServiceIT test`
Expected: 全绿(含两条新用例)。若解析器 SQL / 迁移有误会在此暴露。

- [ ] **Step 7:提交**

```bash
git add backend/src/main/resources/db/migration/V4__favorite.sql \
        backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java \
        backend/src/main/resources/mapper/BusQueryMapper.xml \
        backend/src/main/java/com/airportbus/bus/service/BusQueryService.java \
        backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java
git commit -m "feat(user): favorite 表迁移 + source_id→bus_route_id 解析器(#3 T1)"
```

---

## Task 2:FavoriteMapper + FavoriteService(IT 驱动)

幂等收藏 / 取消 / 列表的核心逻辑。用 `FavoriteServiceIT`(Testcontainers + 真实迁移 + 种子)测试先行。

**Files:**
- Create: `backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java`
- Create: `backend/src/main/resources/mapper/FavoriteMapper.xml`
- Create: `backend/src/main/java/com/airportbus/user/service/FavoriteService.java`
- Test: `backend/src/test/java/com/airportbus/user/service/FavoriteServiceIT.java`

- [ ] **Step 1:写失败 IT**

`backend/src/test/java/com/airportbus/user/service/FavoriteServiceIT.java`(结构照搬 `AuthServiceIT`/`AuthFlowIT` 的 Testcontainers 样板,但 **seed 开启**以便有 `vie-vab1`,并注入一个真实用户):

```java
package com.airportbus.user.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.common.ApiException;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class FavoriteServiceIT {
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

    @Autowired FavoriteService service;
    @Autowired UserMapper userMapper;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    private long newUserContext(String name) {
        AppUser u = new AppUser(); // 字段为 public,直接赋值;insertUser 用 useGeneratedKeys 回填 u.id
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        return u.id;
    }

    @Test
    void favoriteThenIdsContainsIt_idempotent() {
        newUserContext("favu1");
        assertTrue(service.favorite("vie-vab1").favorited());
        assertTrue(service.favorite("vie-vab1").favorited()); // 重复幂等
        assertEquals(List.of("vie-vab1"), service.myIds());
    }

    @Test
    void unfavoriteRemoves_idempotent() {
        newUserContext("favu2");
        service.favorite("vie-vab1");
        assertFalse(service.unfavorite("vie-vab1").favorited());
        assertFalse(service.unfavorite("vie-vab1").favorited()); // 重复幂等
        assertTrue(service.myIds().isEmpty());
    }

    @Test
    void refavoriteResurrectsSameRow() {
        newUserContext("favu3");
        service.favorite("vie-vab1");
        service.unfavorite("vie-vab1");
        service.favorite("vie-vab1"); // deleted 1→0,不新增行
        assertEquals(List.of("vie-vab1"), service.myIds());
    }

    @Test
    void favoriteUnknownSource_throwsBusNotFound() {
        newUserContext("favu4");
        assertThrows(ApiException.class, () -> service.favorite("no-such-bus"));
    }

    @Test
    void myFavorites_returnsFullBusDetail() {
        newUserContext("favu5");
        service.favorite("vie-vab1");
        List<BusDetailDto> list = service.myFavorites();
        assertEquals(1, list.size());
        assertEquals("vie-vab1", list.get(0).sourceId());
        assertFalse(list.get(0).stops().isEmpty()); // 含子表
    }
}
```

> 注:`FavoriteStatusDto` 在 Step 3 定义(record,提供 `favorited()` 访问器)。`AppUser` 字段为 public,已按实际写法直接赋值。

- [ ] **Step 2:跑测试,确认失败**

Run: `cd backend && mvn -q -Dtest=FavoriteServiceIT test`
Expected: 编译失败(`FavoriteService` / `FavoriteStatusDto` 不存在)。

- [ ] **Step 3:写 `FavoriteStatusDto`**

`backend/src/main/java/com/airportbus/user/api/dto/FavoriteStatusDto.java`:

```java
package com.airportbus.user.api.dto;

/** 收藏 / 取消端点的返回体:favorited=true 已收藏,false 已取消。 */
public record FavoriteStatusDto(boolean favorited) {}
```

- [ ] **Step 4:写 `FavoriteMapper` 接口**

`backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java`:

```java
package com.airportbus.user.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FavoriteMapper {
    /** upsert:不存在则插入(deleted=0),存在则把 deleted 置回 0 并刷新 updated_*。 */
    int upsertFavorite(@Param("userId") long userId,
                       @Param("busRouteId") long busRouteId,
                       @Param("actor") String actor);

    /** 软删:把 deleted 置 1(仅作用于当前 deleted=0 的行)。 */
    int softDeleteFavorite(@Param("userId") long userId,
                           @Param("busRouteId") long busRouteId,
                           @Param("actor") String actor);

    /** 当前用户已收藏(deleted=0)且线路未删除的 source_id,按收藏动作时间倒序。 */
    List<String> selectFavoritedSourceIds(@Param("userId") long userId);
}
```

- [ ] **Step 5:写 `FavoriteMapper.xml`**

`backend/src/main/resources/mapper/FavoriteMapper.xml`(只用 `#{}`;`ON DUPLICATE KEY` 命中 `uk_fav_user_bus`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.user.mapper.FavoriteMapper">

  <insert id="upsertFavorite">
    INSERT INTO favorite (user_id, bus_route_id, created_by, updated_by, deleted)
    VALUES (#{userId}, #{busRouteId}, #{actor}, #{actor}, 0)
    ON DUPLICATE KEY UPDATE deleted = 0, updated_by = #{actor}, updated_at = CURRENT_TIMESTAMP
  </insert>

  <update id="softDeleteFavorite">
    UPDATE favorite SET deleted = 1, updated_by = #{actor}, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = #{userId} AND bus_route_id = #{busRouteId} AND deleted = 0
  </update>

  <select id="selectFavoritedSourceIds" resultType="string">
    SELECT br.source_id
    FROM favorite f
    JOIN bus_route br ON br.id = f.bus_route_id AND br.deleted = 0
    WHERE f.user_id = #{userId} AND f.deleted = 0
    ORDER BY f.updated_at DESC
  </select>
</mapper>
```

- [ ] **Step 6:写 `FavoriteService`**

`backend/src/main/java/com/airportbus/user/service/FavoriteService.java`:

```java
package com.airportbus.user.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.service.BusQueryService;
import com.airportbus.user.api.dto.FavoriteStatusDto;
import com.airportbus.user.mapper.FavoriteMapper;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 收藏 = 订阅(订阅侧)。userId 一律取自 CurrentUser(JWT 上下文)。 */
@Service
public class FavoriteService {

    private final FavoriteMapper mapper;
    private final BusQueryService busQuery;

    public FavoriteService(FavoriteMapper mapper, BusQueryService busQuery) {
        this.mapper = mapper;
        this.busQuery = busQuery;
    }

    public FavoriteStatusDto favorite(String sourceId) {
        JwtPrincipal me = CurrentUser.require();
        long busRouteId = busQuery.requireBusRouteId(sourceId); // 不存在 → 404
        mapper.upsertFavorite(me.userId(), busRouteId, actor(me));
        return new FavoriteStatusDto(true);
    }

    public FavoriteStatusDto unfavorite(String sourceId) {
        JwtPrincipal me = CurrentUser.require();
        long busRouteId = busQuery.requireBusRouteId(sourceId); // 不存在 → 404
        mapper.softDeleteFavorite(me.userId(), busRouteId, actor(me));
        return new FavoriteStatusDto(false);
    }

    public List<String> myIds() {
        return mapper.selectFavoritedSourceIds(CurrentUser.require().userId());
    }

    public List<BusDetailDto> myFavorites() {
        List<BusDetailDto> out = new ArrayList<>();
        for (String sourceId : myIds()) {
            out.add(busQuery.detail(sourceId)); // 复用查询主线装配(命中缓存时不重复计热度)
        }
        return out;
    }

    private static String actor(JwtPrincipal me) {
        return "user:" + me.userId();
    }
}
```

- [ ] **Step 7:跑测试,确认通过**

Run: `cd backend && mvn -q -Dtest=FavoriteServiceIT test`
Expected: 5 条用例全绿。

- [ ] **Step 8:提交**

```bash
git add backend/src/main/java/com/airportbus/user/api/dto/FavoriteStatusDto.java \
        backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java \
        backend/src/main/resources/mapper/FavoriteMapper.xml \
        backend/src/main/java/com/airportbus/user/service/FavoriteService.java \
        backend/src/test/java/com/airportbus/user/service/FavoriteServiceIT.java
git commit -m "feat(user): FavoriteService 幂等收藏/取消/列表 + IT(#3 T2)"
```

---

## Task 3:FavoriteController + HTTP IT

把 Service 暴露成 REST,并验证鉴权(匿名 401)、404、JSON 形状。

**Files:**
- Create: `backend/src/main/java/com/airportbus/user/api/FavoriteController.java`
- Test: `backend/src/test/java/com/airportbus/user/api/FavoriteApiIT.java`

- [ ] **Step 1:写失败 IT**

`backend/src/test/java/com/airportbus/user/api/FavoriteApiIT.java`(MockMvc + Testcontainers,seed 开启;先注册一个用户拿 access token,样板照搬 `AuthFlowIT`):

```java
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
```

- [ ] **Step 2:跑测试,确认失败**

Run: `cd backend && mvn -q -Dtest=FavoriteApiIT test`
Expected: 失败 —— 端点不存在(PUT 收藏返回 404/405 而非 200)。

- [ ] **Step 3:写 `FavoriteController`**

`backend/src/main/java/com/airportbus/user/api/FavoriteController.java`:

```java
package com.airportbus.user.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.user.api.dto.FavoriteStatusDto;
import com.airportbus.user.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "favorite", description = "收藏 = 订阅(需登录)")
@RestController
@RequestMapping("/api/v1")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @Operation(summary = "收藏一条线路(幂等)")
    @PutMapping("/buses/{sourceId}/favorite")
    public FavoriteStatusDto favorite(@PathVariable String sourceId) {
        return service.favorite(sourceId);
    }

    @Operation(summary = "取消收藏(幂等)")
    @DeleteMapping("/buses/{sourceId}/favorite")
    public FavoriteStatusDto unfavorite(@PathVariable String sourceId) {
        return service.unfavorite(sourceId);
    }

    @Operation(summary = "我的收藏(完整卡片,按收藏时间倒序)")
    @GetMapping("/favorites")
    public List<BusDetailDto> myFavorites() {
        return service.myFavorites();
    }

    @Operation(summary = "我收藏的 source_id 列表(供前端打标记)")
    @GetMapping("/favorites/ids")
    public List<String> myIds() {
        return service.myIds();
    }
}
```

- [ ] **Step 4:跑测试,确认通过**

Run: `cd backend && mvn -q -Dtest=FavoriteApiIT test`
Expected: 3 条用例全绿。

- [ ] **Step 5:回归后端 IT 全集**

Run: `cd backend && mvn -q -Dtest=BusQueryServiceIT,SeedImporterIT,SearchHotnessServiceIT,AuthFlowIT,AuthServiceIT,AuthCacheServiceIT,FavoriteServiceIT,FavoriteApiIT test`
Expected: 全绿。

- [ ] **Step 6:提交**

```bash
git add backend/src/main/java/com/airportbus/user/api/FavoriteController.java \
        backend/src/test/java/com/airportbus/user/api/FavoriteApiIT.java
git commit -m "feat(user): FavoriteController 4 端点 + HTTP IT(#3 T3)"
```

---

## Task 4:前端 favorites API + store

**Files:**
- Create: `frontend/src/api/favorites.ts`
- Create: `frontend/src/stores/favorites.ts`
- Test: `frontend/src/test/favorites.store.spec.ts`

- [ ] **Step 1:写 `api/favorites.ts`**

```ts
import { http } from './client'
import type { BusDetail } from './bus'

export const listFavoriteIds = () => http.get<string[]>('/favorites/ids').then((r) => r.data)
export const listFavorites = () => http.get<BusDetail[]>('/favorites').then((r) => r.data)
export const favorite = (sourceId: string) =>
  http.put<{ favorited: boolean }>(`/buses/${encodeURIComponent(sourceId)}/favorite`).then((r) => r.data)
export const unfavorite = (sourceId: string) =>
  http.delete<{ favorited: boolean }>(`/buses/${encodeURIComponent(sourceId)}/favorite`).then((r) => r.data)
```

- [ ] **Step 2:写失败测试**

`frontend/src/test/favorites.store.spec.ts`(参考 `auth.store.spec.ts` 的 mock + pinia 写法):

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve(['vie-vab1'])),
  favorite: vi.fn(() => Promise.resolve({ favorited: true })),
  unfavorite: vi.fn(() => Promise.resolve({ favorited: false })),
}))

import * as api from '../api/favorites'
import { useFavorites } from '../stores/favorites'

describe('favorites store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('load fills the id set', async () => {
    const s = useFavorites()
    await s.load()
    expect(s.isFavorited('vie-vab1')).toBe(true)
  })

  it('toggle adds optimistically then calls favorite()', async () => {
    const s = useFavorites()
    await s.toggle('pvg-line4')
    expect(s.isFavorited('pvg-line4')).toBe(true)
    expect(api.favorite).toHaveBeenCalledWith('pvg-line4')
  })

  it('toggle removes an existing favorite via unfavorite()', async () => {
    const s = useFavorites()
    await s.load()
    await s.toggle('vie-vab1')
    expect(s.isFavorited('vie-vab1')).toBe(false)
    expect(api.unfavorite).toHaveBeenCalledWith('vie-vab1')
  })

  it('rolls back on failure', async () => {
    ;(api.favorite as any).mockRejectedValueOnce(new Error('boom'))
    const s = useFavorites()
    await expect(s.toggle('pvg-line7')).rejects.toThrow()
    expect(s.isFavorited('pvg-line7')).toBe(false) // 回滚
  })

  it('clear empties the set', async () => {
    const s = useFavorites()
    await s.load()
    s.clear()
    expect(s.isFavorited('vie-vab1')).toBe(false)
  })
})
```

- [ ] **Step 3:跑测试,确认失败**

Run: `cd frontend && npx vitest run src/test/favorites.store.spec.ts`
Expected: 失败 —— `../stores/favorites` 不存在。

- [ ] **Step 4:写 `stores/favorites.ts`**

```ts
import { defineStore } from 'pinia'
import * as api from '../api/favorites'

export const useFavorites = defineStore('favorites', {
  state: () => ({ ids: new Set<string>() }),
  getters: {
    // 用函数 getter 按 sourceId 查询;Set 重新赋值触发响应式更新。
    isFavorited: (state) => (sourceId: string) => state.ids.has(sourceId),
  },
  actions: {
    async load() {
      this.ids = new Set(await api.listFavoriteIds())
    },
    clear() {
      this.ids = new Set()
    },
    async toggle(sourceId: string) {
      const had = this.ids.has(sourceId)
      const next = new Set(this.ids)
      if (had) next.delete(sourceId)
      else next.add(sourceId)
      this.ids = next // 乐观更新
      try {
        if (had) await api.unfavorite(sourceId)
        else await api.favorite(sourceId)
      } catch (e) {
        const rollback = new Set(this.ids)
        if (had) rollback.add(sourceId)
        else rollback.delete(sourceId)
        this.ids = rollback // 回滚
        throw e
      }
    },
  },
})
```

- [ ] **Step 5:跑测试,确认通过**

Run: `cd frontend && npx vitest run src/test/favorites.store.spec.ts`
Expected: 5 条全绿。

- [ ] **Step 6:提交**

```bash
git add frontend/src/api/favorites.ts frontend/src/stores/favorites.ts \
        frontend/src/test/favorites.store.spec.ts
git commit -m "feat(frontend): favorites api + Pinia store(乐观更新/回滚)(#3 T4)"
```

---

## Task 5:BusCard 收藏心 + 登录态接入 + i18n

卡片右上角加收藏心(贴 `design/` 原型稿,价格之上);匿名点击跳登录(带回跳),已登录点击切换。登录后加载收藏、登出清空。

**Files:**
- Modify: `frontend/src/components/BusCard.vue`
- Modify: `frontend/src/stores/auth.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/pages/LoginPage.vue`
- Modify: `frontend/src/i18n/locales/zh-CN.ts`
- Modify: `frontend/src/i18n/locales/en.ts`
- Modify: `frontend/src/i18n/locales/de.ts`
- Test: `frontend/src/test/BusCard.spec.ts`

- [ ] **Step 1:加 i18n 文案(三语)**

`zh-CN.ts` —— 在 `detail: {...}` 块之后、`state:` 之前插入:

```ts
  favorite: {
    add: '收藏', remove: '取消收藏', loginPrompt: '登录后即可收藏线路',
    mine: '我的收藏', empty: '还没有收藏任何线路。',
  },
```

`en.ts` —— 同位置:

```ts
  favorite: {
    add: 'Save', remove: 'Unsave', loginPrompt: 'Log in to save routes',
    mine: 'My favorites', empty: 'No saved routes yet.',
  },
```

`de.ts` —— 同位置:

```ts
  favorite: {
    add: 'Merken', remove: 'Entfernen', loginPrompt: 'Zum Merken anmelden',
    mine: 'Meine Favoriten', empty: 'Noch keine gemerkten Linien.',
  },
```

- [ ] **Step 2:写失败测试**

`frontend/src/test/BusCard.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { setActivePinia, createPinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ fullPath: '/bus/vie-vab1' }),
}))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve([])),
  favorite: vi.fn(() => Promise.resolve({ favorited: true })),
  unfavorite: vi.fn(() => Promise.resolve({ favorited: false })),
}))

import * as favApi from '../api/favorites'
import { useAuth } from '../stores/auth'
import BusCard from '../components/BusCard.vue'

const bus = {
  sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB', officialUrl: null,
  duration: '40 min', price: '€11', operatingHours: '03:00–24:00', lastUpdated: '2026-06-03', fetchFailed: false,
  stops: ['维也纳机场', 'Westbahnhof'], schedules: [], images: [], files: [], alerts: [],
}

function mountCard() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(BusCard, {
    props: { bus, detailLink: false },
    global: { plugins: [i18n], stubs: { 'router-link': true } },
  })
}

describe('BusCard favorite heart', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); vi.clearAllMocks() })

  it('anonymous click goes to login and does not call api', async () => {
    const w = mountCard()
    await w.find('.favBtn').trigger('click')
    expect(push).toHaveBeenCalled()
    expect(favApi.favorite).not.toHaveBeenCalled()
  })

  it('authed click toggles favorite via api', async () => {
    const auth = useAuth()
    auth.accessToken = 'tok' // isAuthed=true
    const w = mountCard()
    await w.find('.favBtn').trigger('click')
    expect(favApi.favorite).toHaveBeenCalledWith('vie-vab1')
  })
})
```

- [ ] **Step 3:跑测试,确认失败**

Run: `cd frontend && npx vitest run src/test/BusCard.spec.ts`
Expected: 失败 —— `.favBtn` 不存在。

- [ ] **Step 4:改 `BusCard.vue`**

把 `<script setup>` 顶部改为(新增 store/router 依赖与 `faved`/`onFav`):

```ts
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import type { BusDetail } from '../api/bus'
import AlertList from './AlertList.vue'
import FreshnessBadge from './FreshnessBadge.vue'
import { useAuth } from '../stores/auth'
import { useFavorites } from '../stores/favorites'

const props = defineProps<{ bus: BusDetail; detailLink?: boolean }>()
const { t } = useI18n()
const auth = useAuth()
const favs = useFavorites()
const router = useRouter()
const route = useRoute()

const faved = computed(() => favs.isFavorited(props.bus.sourceId))
async function onFav() {
  if (!auth.isAuthed) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try { await favs.toggle(props.bus.sourceId) } catch { /* 401 由拦截器处理,其余忽略 */ }
}
```

(保留原有 `priceMain`/`priceSub`/`hasSchedules` 计算属性不变。)

在模板 `card__top` 里,把价格块替换为「收藏心 + 价格」竖排(收藏在价格之上):

```html
    <div class="card__top">
      <div>
        <div class="route">{{ bus.route }}</div>
        <div class="dest">{{ bus.destination ?? bus.route }}</div>
        <div v-if="bus.operator" class="operator">{{ bus.operator }}</div>
      </div>
      <div class="topRight">
        <button
          class="favBtn"
          :class="{ favOn: faved }"
          :aria-pressed="faved"
          :title="auth.isAuthed ? (faved ? t('favorite.remove') : t('favorite.add')) : t('favorite.loginPrompt')"
          @click="onFav"
        >{{ faved ? '♥' : '♡' }}</button>
        <div v-if="priceMain" class="price">
          {{ priceMain }}<small v-if="priceSub">{{ priceSub }}</small>
        </div>
      </div>
    </div>
```

并在文件末尾追加 scoped 样式(组件原本无 `<style>`):

```html
<style scoped>
.topRight { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.favBtn {
  border: none; background: none; cursor: pointer; line-height: 1;
  font-size: 22px; color: var(--muted, #9aa0a6); padding: 2px 4px;
}
.favBtn.favOn { color: #e0245e; }
</style>
```

> 删掉原 `card__top` 里第 23 行的注释「本期无收藏按钮」。

- [ ] **Step 5:跑测试,确认通过**

Run: `cd frontend && npx vitest run src/test/BusCard.spec.ts`
Expected: 2 条全绿。

- [ ] **Step 6:登录态接入 —— 改 `stores/auth.ts`**

在 `loadMe` 成功后加载收藏、`clear` 时清空。改动两处:

`loadMe` 改为:

```ts
    async loadMe() {
      this.user = await api.getMe()
      const { useFavorites } = await import('./favorites')
      try { await useFavorites().load() } catch { /* 收藏加载失败不阻塞登录 */ }
    },
```

`clear` 改为(登出清空收藏):

```ts
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      import('./favorites').then((m) => m.useFavorites().clear())
    },
```

> 用动态 `import` 避免 auth ↔ favorites 循环依赖。

- [ ] **Step 7:应用启动时若已登录则加载收藏 —— 改 `App.vue`**

`<script setup>` 改为:

```ts
<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
import { useAuth } from './stores/auth'
import { useFavorites } from './stores/favorites'
const { t, locale } = useI18n()
const auth = useAuth()
onMounted(() => {
  if (auth.isAuthed) useFavorites().load().catch(() => { /* 忽略 */ })
})
</script>
```

(模板不变。)

- [ ] **Step 8:登录 / 注册后按 `redirect` 回跳 —— 改 `LoginPage.vue`**

`<script setup>` 顶部加 `useRoute`:

```ts
import { useRouter, useRoute } from 'vue-router'
```

```ts
const route = useRoute()
function go() {
  const r = route.query.redirect
  router.push(typeof r === 'string' && r ? r : '/')
}
```

把 `doLogin`/`doRegister` 里的 `router.push('/')` 都改成 `go()`。

- [ ] **Step 9:修既有页面测试(BusCard 现在依赖 pinia + useRoute)**

`HomePage.spec.ts` 与 `BusDetailPage.spec.ts` 都渲染 `BusCard`,而 BusCard 现在调用 `useAuth()`/`useFavorites()`/`useRoute()`。两处都缺 pinia,且 vue-router mock 缺 `useRoute`,需补齐,否则挂载即报错。

`HomePage.spec.ts`:把第 36 行的 vue-router mock 补上 `useRoute`,并在 imports 后加 favorites api mock,在 `describe` 里建 pinia。

```ts
// 第 36 行原:vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ fullPath: '/' }) }))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve([])),
  favorite: vi.fn(), unfavorite: vi.fn(),
}))
```

并在文件顶部 import 处加 `import { setActivePinia, createPinia } from 'pinia'`,在 `describe('HomePage', ...)` 内最前加:

```ts
  beforeEach(() => { setActivePinia(createPinia()) })
```

(若 `describe` 回调没有 `beforeEach`,补 `import { beforeEach } from 'vitest'` 或并入既有 import。)

`BusDetailPage.spec.ts`:它原本未 mock vue-router,需新增 vue-router + favorites mock + pinia:

```ts
// 加在现有 vi.mock('../api/bus', ...) 之后
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ fullPath: '/bus/vie-vab1' }) }))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve([])),
  favorite: vi.fn(), unfavorite: vi.fn(),
}))
```

import 处加 `import { setActivePinia, createPinia } from 'pinia'` 与 `beforeEach`(从 vitest),并在 `describe` 内加 `beforeEach(() => { setActivePinia(createPinia()) })`。`stubs` 已含 `router-link`,无需改。

- [ ] **Step 9b:回归前端全量测试**

Run: `cd frontend && npm run test`
Expected: 全绿。

- [ ] **Step 10:提交**

```bash
git add frontend/src/components/BusCard.vue frontend/src/stores/auth.ts frontend/src/App.vue \
        frontend/src/pages/LoginPage.vue frontend/src/i18n/locales \
        frontend/src/test/BusCard.spec.ts \
        frontend/src/test/HomePage.spec.ts frontend/src/test/BusDetailPage.spec.ts
git commit -m "feat(frontend): BusCard 收藏心 + 登录态加载/清空 + 回跳 + 三语(#3 T5)"
```

---

## Task 6:MePage 我的收藏区块

**Files:**
- Modify: `frontend/src/pages/MePage.vue`
- Test: `frontend/src/test/MePage.spec.ts`

- [ ] **Step 1:写失败测试**

`frontend/src/test/MePage.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { setActivePinia, createPinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('../api/auth', () => ({
  getMe: vi.fn(() => Promise.resolve({ username: 'zoe', email: 'z@x.com', locale: 'zh-CN', role: 'USER' })),
  changePassword: vi.fn(),
}))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve(['vie-vab1'])),
  listFavorites: vi.fn(() => Promise.resolve([{
    sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB', officialUrl: null,
    duration: null, price: null, operatingHours: null, lastUpdated: null, fetchFailed: false,
    stops: [], schedules: [], images: [], files: [], alerts: [],
  }])),
  favorite: vi.fn(), unfavorite: vi.fn(),
}))

import { useAuth } from '../stores/auth'
import MePage from '../pages/MePage.vue'

function mountMe() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(MePage, { global: { plugins: [i18n], stubs: { 'router-link': true } } })
}

describe('MePage favorites', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('renders my favorites list', async () => {
    const auth = useAuth()
    auth.accessToken = 'tok'
    auth.user = { username: 'zoe', email: 'z@x.com', locale: 'zh-CN', role: 'USER' }
    const w = mountMe()
    await flushPromises()
    expect(w.text()).toContain('我的收藏')
    expect(w.text()).toContain('VAB 1')
  })
})
```

- [ ] **Step 2:跑测试,确认失败**

Run: `cd frontend && npx vitest run src/test/MePage.spec.ts`
Expected: 失败 —— 页面无「我的收藏」。

- [ ] **Step 3:改 `MePage.vue`**

`<script setup>` 增补收藏加载:

```ts
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import { changePassword } from '../api/auth'
import { listFavorites } from '../api/favorites'
import { asApiError } from '../api/client'
import type { BusDetail } from '../api/bus'
import BusCard from '../components/BusCard.vue'
import StateBlock from '../components/StateBlock.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuth()
const oldPw = ref(''); const newPw = ref(''); const msg = ref(''); const err = ref('')
const favorites = ref<BusDetail[]>([]); const favLoading = ref(true)

onMounted(async () => {
  if (!auth.isAuthed) { router.push('/login'); return }
  if (!auth.user) { try { await auth.loadMe() } catch { /* 401 拦截器处理 */ } }
  try { favorites.value = await listFavorites() } catch { /* 忽略 */ } finally { favLoading.value = false }
})
```

(`changePw` / `doLogout` 保持不变。)

在模板里、`<h2>个人中心</h2>` 那段资料之后、修改密码 `<h3>` 之前,插入收藏区块:

```html
    <h3>{{ t('favorite.mine') }}</h3>
    <StateBlock :loading="favLoading" :empty="!favLoading && favorites.length === 0" :empty-text="t('favorite.empty')">
      <BusCard v-for="b in favorites" :key="b.sourceId" :bus="b" :detail-link="true" />
    </StateBlock>
```

- [ ] **Step 4:跑测试,确认通过**

Run: `cd frontend && npx vitest run src/test/MePage.spec.ts`
Expected: 通过。

- [ ] **Step 5:回归前端全量 + 提交**

Run: `cd frontend && npm run test`
Expected: 全绿。

```bash
git add frontend/src/pages/MePage.vue frontend/src/test/MePage.spec.ts
git commit -m "feat(frontend): 个人中心『我的收藏』区块(#3 T6)"
```

---

## 收尾(人工验证 + 文档)

- [ ] 全栈手动验证(参考 testing-tooling-quirks 记忆):`docker compose up -d mysql redis` → 后端 `SEED_ENABLED=true ... mvn spring-boot:run` → 前端 `npx vite`。登录后:首页卡片心可点亮、刷新保持;详情页同步;个人中心「我的收藏」列出;匿名点心跳登录并回跳;取消后即时消失。
- [ ] 更新 `MEMORY.md`:把「#3 收藏=订阅」标记为已交付,记录关键点(favorite 唯一键不含 deleted、收藏态走 /favorites/ids 客户端打标记、跨模块经 BusQueryService.requireBusRouteId)。
- [ ] 按 `superpowers:finishing-a-development-branch` 决定合并方式。
```
