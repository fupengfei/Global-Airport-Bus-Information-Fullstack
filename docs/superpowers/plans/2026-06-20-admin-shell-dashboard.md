# 管理后台地基 + 统计概览(#7a)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `/admin` 后台地基 + RBAC 强制 + 三块只读统计(用户/订阅/热度),端到端可部署。

**Architecture:** 后端新建 `com.airportbus.admin` 薄编排模块(只读 + RBAC + DTO),重的聚合查询放在属主模块(user/bus)的 service/mapper 里(守 E5 边界);鉴权扩展现有手写 `CurrentUser.requireAdmin()`,不引入 Spring Security。前端新增 `/admin` 懒加载路由树 + 路由守卫,Element Plus(外壳/卡片/表格)+ ECharts(图表)只进 admin 异步 chunk。

**Tech Stack:** Spring Boot + MyBatis(`#{}` only,`map-underscore-to-camel-case` + record `resultType`)+ Testcontainers(MySQL/Redis)IT;Vue 3 + Pinia + vue-router + Element Plus + ECharts + Vitest。

---

## 上游文档

- spec:`docs/superpowers/specs/2026-06-20-admin-shell-dashboard-design.md`
- 视觉 SoT:`design/admin.html`(其类全部已在 `frontend/src/styles/tokens.css`)
- 约束:`CLAUDE.md` 锁定章节、`docs/design.md` 的 E5/E10/DS5。

## 关键既有事实(实现前必读)

- 鉴权是**手写**的:`JwtAuthFilter`(带 Bearer 才解析)→ ThreadLocal `com.airportbus.user.security.CurrentUser`;受保护端点调 `CurrentUser.require()`(无主体抛 `ApiException(UNAUTHORIZED)` → 401)。`JwtPrincipal(long userId, String role)` 已带 role,登录已把 role 写进 JWT(`AuthService.issueTokens(u.id, u.role)`)。
- 种子:`airportbus.seed.enabled=true` 时 `AdminSeedRunner` 幂等建 `admin / admin12345`(role=`SUPER_ADMIN`)。IT 里登录它拿管理员 token。
- 错误体:`com.airportbus.common.ErrorCode`(枚举带 HttpStatus)+ `ApiException(ErrorCode, msg)` + `GlobalExceptionHandler`,body `{code,message,details,traceId}`。
- MyBatis:`@MapperScan({"com.airportbus.bus.mapper","com.airportbus.user.mapper"})`;mapper xml 在 `classpath:mapper/*.xml`;DTO 用 record + `resultType` + `AS` 别名(见 `BusQueryMapper.xml`)。**本计划不新增 mapper 包**,只往现有 mapper 加方法。
- 表:`app_user(role,created_at,deleted)`、`favorite(user_id,bus_route_id,created_at,deleted)`、`bus_route(source_id,route,destination,airport_id,deleted)`、`airport(code,name,city_id,deleted)`、`city(name,country_id,deleted)`、`country(name,deleted)`、`airport_search_stat(airport_id,day,cnt,deleted)`。所有读路径排除 `deleted=1`。
- 前端:`MeView` 已含 `role`;`stores/auth.ts` 的 `user.role` 可用;`api/client.ts` 的 `http` 自动带 JWT + 401 刷新;路由懒加载;`main.ts` 注释「不挂 Element Plus(公开页用手写组件)」—— 保持全局不挂,EP 仅在 admin SFC 内 import。

## 命令速查

- 后端编译:`cd backend && mvn -q -DskipTests compile`
- 后端单测(纯 JUnit):`cd backend && mvn -q -Dtest=<Class> test`
- 后端 IT(需 Docker 跑 Testcontainers,`*IT` 必须 `-Dtest=` 点名):`cd backend && mvn -q -Dtest=<ClassIT> test`
- 前端单测:`cd frontend && npx vitest run src/test/<file>`
- 前端构建:`cd frontend && npm run build`

## 文件结构

**后端(新建)**
- `backend/src/main/java/com/airportbus/admin/api/AdminStatsController.java` — 4 端点,每个首行 `requireAdmin()`
- `backend/src/main/java/com/airportbus/admin/api/dto/OverviewDto.java`
- `backend/src/main/java/com/airportbus/admin/api/dto/SubscriptionStatsDto.java`
- `backend/src/main/java/com/airportbus/admin/service/AdminStatsService.java`
- `backend/src/main/java/com/airportbus/user/service/UserStatsService.java`(含 `DailyRegistration` record)
- `backend/src/main/java/com/airportbus/user/service/FavoriteStatsService.java`

**后端(修改)**
- `com/airportbus/common/ErrorCode.java` — 加 `ADMIN_FORBIDDEN(FORBIDDEN)`
- `com/airportbus/user/security/CurrentUser.java` — 加 `requireAdmin()`
- `com/airportbus/user/mapper/UserMapper.java` + `resources/mapper/UserMapper.xml` — 计数/趋势查询 + `DayCount` record
- `com/airportbus/user/mapper/FavoriteMapper.java` + `resources/mapper/FavoriteMapper.xml` — 计数/聚合查询 + `RouteSub/AirportSub/CitySub` record
- `com/airportbus/bus/mapper/SearchHotnessMapper.java` + `resources/mapper/SearchHotnessMapper.xml` — `ranking` 查询 + `HotnessRow` record
- `com/airportbus/bus/service/SearchHotnessService.java` — 加 `ranking(window,limit)`

**后端(测试)**
- `backend/src/test/java/com/airportbus/user/service/UserStatsServiceIT.java`
- `backend/src/test/java/com/airportbus/user/service/FavoriteStatsServiceIT.java`
- `backend/src/test/java/com/airportbus/bus/service/HotnessRankingIT.java`
- `backend/src/test/java/com/airportbus/admin/api/AdminStatsApiIT.java`
- `backend/src/test/java/com/airportbus/user/security/CurrentUserTest.java`

**前端(新建)**
- `frontend/src/api/admin.ts`
- `frontend/src/router/adminGuard.ts`
- `frontend/src/components/admin/AdminLayout.vue`
- `frontend/src/pages/admin/AdminOverviewPage.vue`
- `frontend/src/pages/admin/AdminSubscriptionsPage.vue`
- `frontend/src/pages/admin/AdminHotnessPage.vue`
- `frontend/src/test/setup.ts`(ResizeObserver/matchMedia polyfill,供 Element Plus 在 jsdom 工作)
- 测试:`frontend/src/test/admin.api.spec.ts`、`adminGuard.spec.ts`、`AdminOverviewPage.spec.ts`、`AdminSubscriptionsPage.spec.ts`、`AdminHotnessPage.spec.ts`

**前端(修改)**
- `frontend/package.json` — 加 `element-plus`、`echarts` 依赖
- `frontend/vite.config.ts` — `test.setupFiles: ['./src/test/setup.ts']`
- `frontend/src/router/index.ts` — 加 `/admin` 父路由 + 子路由 + `beforeEnter` 守卫

---

## Task 1: RBAC —— `ADMIN_FORBIDDEN` + `CurrentUser.requireAdmin()`

**Files:**
- Modify: `backend/src/main/java/com/airportbus/common/ErrorCode.java`
- Modify: `backend/src/main/java/com/airportbus/user/security/CurrentUser.java`
- Test: `backend/src/test/java/com/airportbus/user/security/CurrentUserTest.java`

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/airportbus/user/security/CurrentUserTest.java`:

```java
package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void cleanup() { CurrentUser.clear(); }

    @Test
    void requireAdmin_allowsSuperAdmin() {
        CurrentUser.set(new JwtPrincipal(1L, "SUPER_ADMIN"));
        assertThat(CurrentUser.requireAdmin().role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void requireAdmin_allowsOperator() {
        CurrentUser.set(new JwtPrincipal(2L, "OPERATOR"));
        assertThat(CurrentUser.requireAdmin().userId()).isEqualTo(2L);
    }

    @Test
    void requireAdmin_rejectsRegularUser_withForbidden() {
        CurrentUser.set(new JwtPrincipal(3L, "USER"));
        assertThatThrownBy(CurrentUser::requireAdmin)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    void requireAdmin_rejectsAnonymous_withUnauthorized() {
        assertThatThrownBy(CurrentUser::requireAdmin)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd backend && mvn -q -Dtest=CurrentUserTest test`
Expected: 编译失败 / FAIL —— `ErrorCode.ADMIN_FORBIDDEN` 与 `CurrentUser.requireAdmin` 不存在。

- [ ] **Step 3: 加 ErrorCode**

在 `ErrorCode.java` 的枚举里(`UNAUTHORIZED` 附近)加一行:

```java
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN),
```

- [ ] **Step 4: 加 requireAdmin**

在 `CurrentUser.java` 的 `require()` 之后加:

```java
    /** 要求当前主体是管理员(SUPER_ADMIN / OPERATOR);否则 401(未登录)或 403(已登录非管理员)。 */
    public static JwtPrincipal requireAdmin() {
        JwtPrincipal p = require(); // 无主体 → 401
        String r = p.role();
        if (!"SUPER_ADMIN".equals(r) && !"OPERATOR".equals(r)) {
            throw new ApiException(ErrorCode.ADMIN_FORBIDDEN, "admin only");
        }
        return p;
    }
```

- [ ] **Step 5: 运行,确认通过**

Run: `cd backend && mvn -q -Dtest=CurrentUserTest test`
Expected: PASS(4 个)。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/airportbus/common/ErrorCode.java \
        backend/src/main/java/com/airportbus/user/security/CurrentUser.java \
        backend/src/test/java/com/airportbus/user/security/CurrentUserTest.java
git commit -m "feat(admin): CurrentUser.requireAdmin + ADMIN_FORBIDDEN (#7a)"
```

---

## Task 2: 用户统计 —— UserMapper 查询 + UserStatsService

**Files:**
- Modify: `backend/src/main/java/com/airportbus/user/mapper/UserMapper.java`
- Modify: `backend/src/main/resources/mapper/UserMapper.xml`
- Create: `backend/src/main/java/com/airportbus/user/service/UserStatsService.java`
- Test: `backend/src/test/java/com/airportbus/user/service/UserStatsServiceIT.java`

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/airportbus/user/service/UserStatsServiceIT.java`(沿用 `FavoriteServiceIT` 的 Testcontainers 模式;通过注册端点造数据,避免直接拼 SQL):

```java
package com.airportbus.user.service;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class UserStatsServiceIT {
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

    @Autowired UserStatsService stats;
    @Autowired UserMapper userMapper;

    /** 直接插用户(沿用 FavoriteServiceIT 的造数法;insertUser 用 useGeneratedKeys 回填 id,
     *  created_at 默认 CURRENT_TIMESTAMP=今天)。 */
    private void insertUser(String name) {
        AppUser u = new AppUser();
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
    }

    @Test
    void totalUsers_includesAdminAndInserted() {
        insertUser("ust1");
        insertUser("ust2");
        // 种子 admin(1) + 2 个插入用户 ⇒ 至少 3(含 admin,决策)
        assertThat(stats.totalUsers()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void newUsersInLastDays_countsRecentRegistrations() {
        long before = stats.newUsersInLastDays(7);
        insertUser("ust3");
        assertThat(stats.newUsersInLastDays(7)).isEqualTo(before + 1);
    }

    @Test
    void registrations_returnsContinuousDaysWithZeroFill() {
        insertUser("ust4"); // 保证今天至少 1 条
        List<UserStatsService.DailyRegistration> pts = stats.registrations(7);
        assertThat(pts).hasSize(7);                       // 连续 7 天
        assertThat(pts.get(6).count()).isGreaterThanOrEqualTo(1); // 末日(今天)≥1
        assertThat(pts.get(0).date()).isLessThan(pts.get(6).date()); // 升序 ISO 串
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd backend && mvn -q -Dtest=UserStatsServiceIT test`
Expected: 编译失败 —— `UserStatsService` 与 `DailyRegistration` 不存在。

- [ ] **Step 3: 加 UserMapper 方法 + DayCount record**

在 `UserMapper.java` 接口体内追加(import `org.apache.ibatis.annotations.Param`、`java.time.LocalDate`、`java.util.List`):

```java
    long countUsers();

    long countUsersSince(@Param("since") java.time.LocalDate since);

    java.util.List<DayCount> countRegistrationsByDay(@Param("since") java.time.LocalDate since);

    /** 某天的注册数(day 为 DATE(created_at))。 */
    record DayCount(java.time.LocalDate day, long count) {}
```

- [ ] **Step 4: 加 UserMapper.xml 查询**

在 `UserMapper.xml` 的 `</mapper>` 之前追加:

```xml
  <select id="countUsers" resultType="long">
    SELECT COUNT(*) FROM app_user WHERE deleted = 0
  </select>

  <select id="countUsersSince" resultType="long">
    SELECT COUNT(*) FROM app_user WHERE deleted = 0 AND created_at &gt;= #{since}
  </select>

  <select id="countRegistrationsByDay" resultType="com.airportbus.user.mapper.UserMapper$DayCount">
    SELECT DATE(created_at) AS day, COUNT(*) AS count
    FROM app_user
    WHERE deleted = 0 AND created_at &gt;= #{since}
    GROUP BY DATE(created_at)
    ORDER BY day
  </select>
```

- [ ] **Step 5: 写 UserStatsService**

`backend/src/main/java/com/airportbus/user/service/UserStatsService.java`:

```java
package com.airportbus.user.service;

import com.airportbus.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 用户统计(只读)。供 admin 概览编排。 */
@Service
public class UserStatsService {

    private final UserMapper users;

    public UserStatsService(UserMapper users) { this.users = users; }

    public long totalUsers() { return users.countUsers(); }

    public long newUsersInLastDays(int days) {
        return users.countUsersSince(LocalDate.now().minusDays(clamp(days) - 1L));
    }

    /** 近 days 天每天注册数,空天补 0,日期连续升序,date 为 ISO 串。 */
    public List<DailyRegistration> registrations(int days) {
        int n = clamp(days);
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(n - 1L);
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (UserMapper.DayCount r : users.countRegistrationsByDay(since)) byDay.put(r.day(), r.count());
        List<DailyRegistration> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = since.plusDays(i);
            out.add(new DailyRegistration(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    private static int clamp(int days) { return days < 1 ? 7 : Math.min(days, 90); }

    public record DailyRegistration(String date, long count) {}
}
```

- [ ] **Step 6: 运行,确认通过**

Run: `cd backend && mvn -q -Dtest=UserStatsServiceIT test`
Expected: PASS(3 个)。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/airportbus/user/mapper/UserMapper.java \
        backend/src/main/resources/mapper/UserMapper.xml \
        backend/src/main/java/com/airportbus/user/service/UserStatsService.java \
        backend/src/test/java/com/airportbus/user/service/UserStatsServiceIT.java
git commit -m "feat(admin): user stats service (total/new/registration trend) (#7a)"
```

---

## Task 3: 订阅统计 —— FavoriteMapper 聚合 + FavoriteStatsService

**Files:**
- Modify: `backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java`
- Modify: `backend/src/main/resources/mapper/FavoriteMapper.xml`
- Create: `backend/src/main/java/com/airportbus/user/service/FavoriteStatsService.java`
- Test: `backend/src/test/java/com/airportbus/user/service/FavoriteStatsServiceIT.java`

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/airportbus/user/service/FavoriteStatsServiceIT.java`:

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
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class FavoriteStatsServiceIT {
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

    @Autowired FavoriteStatsService stats;
    @Autowired FavoriteService favorites;
    @Autowired UserMapper userMapper;

    @AfterEach
    void cleanup() { CurrentUser.clear(); }

    /** 直接插用户(insertUser 回填 id)→ 设进 CurrentUser → 走 FavoriteService 收藏种子线路。
     *  沿用 FavoriteServiceIT 的造数法,不绕 AuthService。 */
    private void insertUserAndFavorite(String name, String sourceId) {
        AppUser u = new AppUser();
        u.username = name; u.email = name + "@x.com"; u.passwordHash = "x";
        u.locale = "zh-CN"; u.role = "USER"; u.emailVerified = false;
        userMapper.insertUser(u);
        CurrentUser.set(new JwtPrincipal(u.id, "USER"));
        favorites.favorite(sourceId);
    }

    @Test
    void topRoutes_rankByFavoriteCount_notifyEqualsFavorite() {
        insertUserAndFavorite("fst1", "vie-vab1");
        insertUserAndFavorite("fst2", "vie-vab1");
        insertUserAndFavorite("fst3", "pvg-line4");

        List<FavoriteMapper.RouteSub> rows = stats.topRoutes(20);
        assertThat(rows).isNotEmpty();
        FavoriteMapper.RouteSub top = rows.get(0);
        assertThat(top.busSourceId()).isEqualTo("vie-vab1");
        assertThat(top.favoriteCount()).isEqualTo(2);
        assertThat(top.notifyCount()).isEqualTo(top.favoriteCount()); // 收藏=订阅=接收通知
        assertThat(top.airportCode()).isEqualTo("VIE");
    }

    @Test
    void totalFavorites_countsActiveOnly() {
        long before = stats.totalFavorites();
        insertUserAndFavorite("fst4", "vie-vab1");
        assertThat(stats.totalFavorites()).isEqualTo(before + 1);
    }

    @Test
    void topAirports_and_topCities_aggregate() {
        insertUserAndFavorite("fst5", "vie-vab1");
        assertThat(stats.topAirports(20)).anyMatch(a -> a.airportCode().equals("VIE"));
        assertThat(stats.topCities(20)).anyMatch(c -> c.favoriteCount() >= 1);
    }
}
```

> 注:种子线路 `vie-vab1` / `pvg-line4` 来自 `data.json`(查询主线 IT 已在用);机场 code `VIE`/`PVG` 以种子数据为准,若大小写不同则按实际调整断言。

- [ ] **Step 2: 运行,确认失败**

Run: `cd backend && mvn -q -Dtest=FavoriteStatsServiceIT test`
Expected: 编译失败 —— `FavoriteStatsService` / `RouteSub` 不存在。

- [ ] **Step 3: 加 FavoriteMapper 方法 + record**

在 `FavoriteMapper.java` 接口体内追加:

```java
    long countFavorites();

    long countFavoritesSince(@Param("since") java.time.LocalDate since);

    java.util.List<RouteSub> topRoutes(@Param("limit") int limit);

    java.util.List<AirportSub> topAirports(@Param("limit") int limit);

    java.util.List<CitySub> topCities(@Param("limit") int limit);

    /** 按线路聚合的订阅数;notifyCount == favoriteCount(收藏=订阅,无独立通知开关)。 */
    record RouteSub(String busSourceId, String route, String destination,
                    String airportCode, String cityName, long favoriteCount, long notifyCount) {}

    record AirportSub(String airportCode, String airportName, String cityName, long favoriteCount) {}

    record CitySub(String cityName, String countryName, long favoriteCount) {}
```

- [ ] **Step 4: 加 FavoriteMapper.xml 聚合查询**

在 `FavoriteMapper.xml` 的 `</mapper>` 之前追加:

```xml
  <select id="countFavorites" resultType="long">
    SELECT COUNT(*) FROM favorite WHERE deleted = 0
  </select>

  <select id="countFavoritesSince" resultType="long">
    SELECT COUNT(*) FROM favorite WHERE deleted = 0 AND created_at &gt;= #{since}
  </select>

  <select id="topRoutes" resultType="com.airportbus.user.mapper.FavoriteMapper$RouteSub">
    SELECT br.source_id AS busSourceId, br.route AS route, br.destination AS destination,
           a.code AS airportCode, ci.name AS cityName,
           COUNT(*) AS favoriteCount, COUNT(*) AS notifyCount
    FROM favorite f
    JOIN bus_route br ON br.id = f.bus_route_id AND br.deleted = 0
    JOIN airport a    ON a.id = br.airport_id  AND a.deleted = 0
    JOIN city ci      ON ci.id = a.city_id     AND ci.deleted = 0
    WHERE f.deleted = 0
    GROUP BY br.id, br.source_id, br.route, br.destination, a.code, ci.name
    ORDER BY favoriteCount DESC, br.source_id ASC
    LIMIT #{limit}
  </select>

  <select id="topAirports" resultType="com.airportbus.user.mapper.FavoriteMapper$AirportSub">
    SELECT a.code AS airportCode, a.name AS airportName, ci.name AS cityName, COUNT(*) AS favoriteCount
    FROM favorite f
    JOIN bus_route br ON br.id = f.bus_route_id AND br.deleted = 0
    JOIN airport a    ON a.id = br.airport_id  AND a.deleted = 0
    JOIN city ci      ON ci.id = a.city_id     AND ci.deleted = 0
    WHERE f.deleted = 0
    GROUP BY a.id, a.code, a.name, ci.name
    ORDER BY favoriteCount DESC, a.code ASC
    LIMIT #{limit}
  </select>

  <select id="topCities" resultType="com.airportbus.user.mapper.FavoriteMapper$CitySub">
    SELECT ci.name AS cityName, co.name AS countryName, COUNT(*) AS favoriteCount
    FROM favorite f
    JOIN bus_route br ON br.id = f.bus_route_id AND br.deleted = 0
    JOIN airport a    ON a.id = br.airport_id  AND a.deleted = 0
    JOIN city ci      ON ci.id = a.city_id     AND ci.deleted = 0
    JOIN country co   ON co.id = ci.country_id AND co.deleted = 0
    WHERE f.deleted = 0
    GROUP BY ci.id, ci.name, co.name
    ORDER BY favoriteCount DESC, ci.name ASC
    LIMIT #{limit}
  </select>
```

- [ ] **Step 5: 写 FavoriteStatsService**

`backend/src/main/java/com/airportbus/user/service/FavoriteStatsService.java`:

```java
package com.airportbus.user.service;

import com.airportbus.user.mapper.FavoriteMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 订阅(收藏)统计(只读)。供 admin 概览编排。 */
@Service
public class FavoriteStatsService {

    private final FavoriteMapper favorites;

    public FavoriteStatsService(FavoriteMapper favorites) { this.favorites = favorites; }

    public long totalFavorites() { return favorites.countFavorites(); }

    public long newFavoritesInLastDays(int days) {
        int n = days < 1 ? 7 : Math.min(days, 90);
        return favorites.countFavoritesSince(LocalDate.now().minusDays(n - 1L));
    }

    public List<FavoriteMapper.RouteSub> topRoutes(int limit)   { return favorites.topRoutes(cap(limit)); }
    public List<FavoriteMapper.AirportSub> topAirports(int limit) { return favorites.topAirports(cap(limit)); }
    public List<FavoriteMapper.CitySub> topCities(int limit)     { return favorites.topCities(cap(limit)); }

    private static int cap(int limit) { return limit < 1 ? 20 : Math.min(limit, 100); }
}
```

- [ ] **Step 6: 运行,确认通过**

Run: `cd backend && mvn -q -Dtest=FavoriteStatsServiceIT test`
Expected: PASS(3 个)。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/airportbus/user/mapper/FavoriteMapper.java \
        backend/src/main/resources/mapper/FavoriteMapper.xml \
        backend/src/main/java/com/airportbus/user/service/FavoriteStatsService.java \
        backend/src/test/java/com/airportbus/user/service/FavoriteStatsServiceIT.java
git commit -m "feat(admin): subscription stats (top routes/airports/cities) (#7a)"
```

---

## Task 4: 机场热度榜单 —— SearchHotnessMapper.ranking + Service

**Files:**
- Modify: `backend/src/main/java/com/airportbus/bus/mapper/SearchHotnessMapper.java`
- Modify: `backend/src/main/resources/mapper/SearchHotnessMapper.xml`
- Modify: `backend/src/main/java/com/airportbus/bus/service/SearchHotnessService.java`
- Test: `backend/src/test/java/com/airportbus/bus/service/HotnessRankingIT.java`

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/airportbus/bus/service/HotnessRankingIT.java`(直接用 mapper.upsertStat 造数,再验榜单):

```java
package com.airportbus.bus.service;

import com.airportbus.bus.mapper.SearchHotnessMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true",
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class HotnessRankingIT {
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

    @Autowired SearchHotnessService service;
    @Autowired SearchHotnessMapper mapper;

    @Test
    void ranking_sumsCntDescByWindow() {
        Long vie = mapper.selectAirportIdByCode("VIE");
        Long pvg = mapper.selectAirportIdByCode("PVG");
        assertThat(vie).isNotNull();
        assertThat(pvg).isNotNull();
        LocalDate today = LocalDate.now();
        mapper.upsertStat(vie, today, 10);
        mapper.upsertStat(vie, today.minusDays(3), 5);   // 仍在 7d 窗内
        mapper.upsertStat(pvg, today, 4);
        mapper.upsertStat(vie, today.minusDays(40), 100); // 7d/30d 窗外

        List<SearchHotnessMapper.HotnessRow> r7 = service.ranking("7d", 20);
        assertThat(r7.get(0).airportCode()).isEqualTo("VIE");
        assertThat(r7.get(0).views()).isEqualTo(15); // 10 + 5,不含 40 天前
        assertThat(r7).anyMatch(x -> x.airportCode().equals("PVG") && x.views() == 4);

        List<SearchHotnessMapper.HotnessRow> rAll = service.ranking("all", 20);
        assertThat(rAll.get(0).views()).isEqualTo(115); // 10 + 5 + 100
    }

    @Test
    void ranking_emptyWhenNoStats_isHandled() {
        List<SearchHotnessMapper.HotnessRow> rows = service.ranking("7d", 20);
        assertThat(rows).isNotNull(); // 可能为空,但不抛错
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd backend && mvn -q -Dtest=HotnessRankingIT test`
Expected: 编译失败 —— `ranking` / `HotnessRow` 不存在。

- [ ] **Step 3: 加 mapper 方法 + record**

在 `SearchHotnessMapper.java` 接口体内追加:

```java
    /** 按窗口汇总各机场搜索量,降序。since 为 null 表示「全部时间」。 */
    java.util.List<HotnessRow> ranking(@Param("since") java.time.LocalDate since,
                                       @Param("limit") int limit);

    record HotnessRow(String airportCode, String airportName, String cityName, long views) {}
```

- [ ] **Step 4: 加 mapper.xml 查询(动态 since)**

在 `SearchHotnessMapper.xml` 的 `</mapper>` 之前追加:

```xml
  <select id="ranking" resultType="com.airportbus.bus.mapper.SearchHotnessMapper$HotnessRow">
    SELECT a.code AS airportCode, a.name AS airportName, ci.name AS cityName,
           COALESCE(SUM(s.cnt), 0) AS views
    FROM airport_search_stat s
    JOIN airport a ON a.id = s.airport_id AND a.deleted = 0
    JOIN city ci   ON ci.id = a.city_id   AND ci.deleted = 0
    WHERE s.deleted = 0
    <if test="since != null"> AND s.day &gt;= #{since} </if>
    GROUP BY a.id, a.code, a.name, ci.name
    ORDER BY views DESC, a.code ASC
    LIMIT #{limit}
  </select>
```

- [ ] **Step 5: 加 SearchHotnessService.ranking**

在 `SearchHotnessService.java` 类体内追加(import `java.util.List`):

```java
    /** 后台榜单:window ∈ {"7d","30d","all"},其余按 7d 兜底。 */
    public java.util.List<SearchHotnessMapper.HotnessRow> ranking(String window, int limit) {
        int cap = limit < 1 ? 20 : Math.min(limit, 100);
        return mapper.ranking(windowSince(window), cap);
    }

    private static java.time.LocalDate windowSince(String window) {
        if ("all".equals(window)) return null;
        if ("30d".equals(window)) return java.time.LocalDate.now().minusDays(29);
        return java.time.LocalDate.now().minusDays(6); // "7d" 及兜底
    }
```

- [ ] **Step 6: 运行,确认通过**

Run: `cd backend && mvn -q -Dtest=HotnessRankingIT test`
Expected: PASS(2 个)。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/airportbus/bus/mapper/SearchHotnessMapper.java \
        backend/src/main/resources/mapper/SearchHotnessMapper.xml \
        backend/src/main/java/com/airportbus/bus/service/SearchHotnessService.java \
        backend/src/test/java/com/airportbus/bus/service/HotnessRankingIT.java
git commit -m "feat(admin): airport hotness ranking read (7d/30d/all) (#7a)"
```

---

## Task 5: admin 模块 —— DTO + AdminStatsService + Controller

**Files:**
- Create: `backend/src/main/java/com/airportbus/admin/api/dto/OverviewDto.java`
- Create: `backend/src/main/java/com/airportbus/admin/api/dto/SubscriptionStatsDto.java`
- Create: `backend/src/main/java/com/airportbus/admin/service/AdminStatsService.java`
- Create: `backend/src/main/java/com/airportbus/admin/api/AdminStatsController.java`

> 本任务只接线,IT 在 Task 6。这里跑编译确认装配无误。

- [ ] **Step 1: 写 OverviewDto**

`backend/src/main/java/com/airportbus/admin/api/dto/OverviewDto.java`:

```java
package com.airportbus.admin.api.dto;

/** 概览统计卡(工单卡是纯前端占位,不在此)。 */
public record OverviewDto(long totalUsers, long newUsersThisWeek,
                          long totalFavorites, long newFavoritesThisWeek) {}
```

- [ ] **Step 2: 写 SubscriptionStatsDto**

`backend/src/main/java/com/airportbus/admin/api/dto/SubscriptionStatsDto.java`:

```java
package com.airportbus.admin.api.dto;

import com.airportbus.user.mapper.FavoriteMapper;

import java.util.List;

public record SubscriptionStatsDto(List<FavoriteMapper.RouteSub> topRoutes,
                                   List<FavoriteMapper.AirportSub> topAirports,
                                   List<FavoriteMapper.CitySub> topCities) {}
```

- [ ] **Step 3: 写 AdminStatsService**

`backend/src/main/java/com/airportbus/admin/service/AdminStatsService.java`:

```java
package com.airportbus.admin.service;

import com.airportbus.admin.api.dto.OverviewDto;
import com.airportbus.admin.api.dto.SubscriptionStatsDto;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.bus.service.SearchHotnessService;
import com.airportbus.user.service.FavoriteStatsService;
import com.airportbus.user.service.UserStatsService;
import org.springframework.stereotype.Service;

import java.util.List;

/** admin 概览编排:只读、聚合属主模块的统计服务。RBAC 在 Controller 强制。 */
@Service
public class AdminStatsService {

    private static final int WEEK = 7;
    private static final int TOP_N = 20;

    private final UserStatsService userStats;
    private final FavoriteStatsService favoriteStats;
    private final SearchHotnessService hotness;

    public AdminStatsService(UserStatsService userStats, FavoriteStatsService favoriteStats,
                             SearchHotnessService hotness) {
        this.userStats = userStats;
        this.favoriteStats = favoriteStats;
        this.hotness = hotness;
    }

    public OverviewDto overview() {
        return new OverviewDto(
                userStats.totalUsers(),
                userStats.newUsersInLastDays(WEEK),
                favoriteStats.totalFavorites(),
                favoriteStats.newFavoritesInLastDays(WEEK));
    }

    public List<UserStatsService.DailyRegistration> registrations(int days) {
        return userStats.registrations(days);
    }

    public SubscriptionStatsDto subscriptions() {
        return new SubscriptionStatsDto(
                favoriteStats.topRoutes(TOP_N),
                favoriteStats.topAirports(TOP_N),
                favoriteStats.topCities(TOP_N));
    }

    public List<SearchHotnessMapper.HotnessRow> hotnessRanking(String window) {
        return hotness.ranking(window, TOP_N);
    }
}
```

- [ ] **Step 4: 写 AdminStatsController**

`backend/src/main/java/com/airportbus/admin/api/AdminStatsController.java`:

```java
package com.airportbus.admin.api;

import com.airportbus.admin.api.dto.OverviewDto;
import com.airportbus.admin.api.dto.SubscriptionStatsDto;
import com.airportbus.admin.service.AdminStatsService;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.service.UserStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "admin-stats", description = "管理后台统计(仅管理员)")
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService service;

    public AdminStatsController(AdminStatsService service) { this.service = service; }

    @Operation(summary = "概览:用户/收藏总数 + 本周新增")
    @GetMapping("/overview")
    public OverviewDto overview() {
        CurrentUser.requireAdmin();
        return service.overview();
    }

    @Operation(summary = "注册趋势:近 days 天每天注册数(空天补 0)")
    @GetMapping("/registrations")
    public List<UserStatsService.DailyRegistration> registrations(
            @RequestParam(defaultValue = "7") int days) {
        CurrentUser.requireAdmin();
        return service.registrations(days);
    }

    @Operation(summary = "订阅统计:按线路/机场/城市聚合收藏数")
    @GetMapping("/subscriptions")
    public SubscriptionStatsDto subscriptions() {
        CurrentUser.requireAdmin();
        return service.subscriptions();
    }

    @Operation(summary = "机场搜索热度榜单(window=7d/30d/all)")
    @GetMapping("/hotness")
    public List<SearchHotnessMapper.HotnessRow> hotness(
            @RequestParam(defaultValue = "7d") String window) {
        CurrentUser.requireAdmin();
        return service.hotnessRanking(window);
    }
}
```

- [ ] **Step 5: 编译确认装配**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS(无缺 bean / 包路径问题;`com.airportbus.admin` 在 `@SpringBootApplication` 扫描范围内)。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/airportbus/admin/
git commit -m "feat(admin): admin stats module (controller/service/dto, requireAdmin) (#7a)"
```

---

## Task 6: admin API 集成测试 —— RBAC + 形状

**Files:**
- Test: `backend/src/test/java/com/airportbus/admin/api/AdminStatsApiIT.java`

- [ ] **Step 1: 写测试**

`backend/src/test/java/com/airportbus/admin/api/AdminStatsApiIT.java`:

```java
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
```

- [ ] **Step 2: 运行,确认通过**

Run: `cd backend && mvn -q -Dtest=AdminStatsApiIT test`
Expected: PASS(5 个)。若 401 断言失败,确认 `JwtAuthFilter` 在带 Bearer 时设了 `CurrentUser`、Controller 首行 `requireAdmin()` 已加。

- [ ] **Step 3: 回归 —— 跑全套后端 IT 不回退**

Run:
```bash
cd backend && mvn -q -Dtest=BusQueryServiceIT,SeedImporterIT,SearchHotnessServiceIT,HotnessRankingIT,AuthFlowIT,AuthServiceIT,AuthCacheServiceIT,FavoriteServiceIT,FavoriteApiIT,UserStatsServiceIT,FavoriteStatsServiceIT,AdminStatsApiIT test
```
Expected: 全 PASS。

- [ ] **Step 4: 提交**

```bash
git add backend/src/test/java/com/airportbus/admin/api/AdminStatsApiIT.java
git commit -m "test(admin): admin stats API IT (401/403/200 + shapes) (#7a)"
```

---

## Task 7: 前端依赖 + admin API 客户端

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/api/admin.ts`
- Test: `frontend/src/test/admin.api.spec.ts`

- [ ] **Step 1: 装依赖**

Run: `cd frontend && npm install element-plus@^2.7.0 echarts@^5.5.0`
Expected: `package.json` 的 `dependencies` 新增两项,`package-lock.json` 更新。

- [ ] **Step 2: 写失败测试**

`frontend/src/test/admin.api.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/client', () => ({
  http: { get: vi.fn(() => Promise.resolve({ data: 'OK' })) },
}))

import { http } from '../api/client'
import * as admin from '../api/admin'

describe('admin api client', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getOverview hits /admin/stats/overview', async () => {
    await admin.getOverview()
    expect(http.get).toHaveBeenCalledWith('/admin/stats/overview')
  })

  it('getRegistrations passes days param', async () => {
    await admin.getRegistrations(7)
    expect(http.get).toHaveBeenCalledWith('/admin/stats/registrations', { params: { days: 7 } })
  })

  it('getSubscriptions hits /admin/stats/subscriptions', async () => {
    await admin.getSubscriptions()
    expect(http.get).toHaveBeenCalledWith('/admin/stats/subscriptions')
  })

  it('getHotness passes window param', async () => {
    await admin.getHotness('30d')
    expect(http.get).toHaveBeenCalledWith('/admin/stats/hotness', { params: { window: '30d' } })
  })
})
```

- [ ] **Step 3: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/admin.api.spec.ts`
Expected: FAIL —— `../api/admin` 不存在。

- [ ] **Step 4: 写 api/admin.ts**

`frontend/src/api/admin.ts`:

```ts
import { http } from './client'

export interface Overview {
  totalUsers: number
  newUsersThisWeek: number
  totalFavorites: number
  newFavoritesThisWeek: number
}
export interface RegistrationPoint { date: string; count: number }
export interface RouteSub {
  busSourceId: string; route: string; destination: string
  airportCode: string; cityName: string; favoriteCount: number; notifyCount: number
}
export interface AirportSub { airportCode: string; airportName: string; cityName: string; favoriteCount: number }
export interface CitySub { cityName: string; countryName: string; favoriteCount: number }
export interface SubscriptionStats { topRoutes: RouteSub[]; topAirports: AirportSub[]; topCities: CitySub[] }
export interface HotnessRow { airportCode: string; airportName: string; cityName: string; views: number }

export const getOverview = () => http.get<Overview>('/admin/stats/overview').then((r) => r.data)
export const getRegistrations = (days = 7) =>
  http.get<RegistrationPoint[]>('/admin/stats/registrations', { params: { days } }).then((r) => r.data)
export const getSubscriptions = () =>
  http.get<SubscriptionStats>('/admin/stats/subscriptions').then((r) => r.data)
export const getHotness = (window = '7d') =>
  http.get<HotnessRow[]>('/admin/stats/hotness', { params: { window } }).then((r) => r.data)
```

- [ ] **Step 5: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/admin.api.spec.ts`
Expected: PASS(4 个)。

- [ ] **Step 6: 提交**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/api/admin.ts frontend/src/test/admin.api.spec.ts
git commit -m "feat(admin): element-plus/echarts deps + admin stats api client (#7a)"
```

---

## Task 8: 路由守卫 + /admin 路由树

**Files:**
- Create: `frontend/src/router/adminGuard.ts`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/test/adminGuard.spec.ts`

- [ ] **Step 1: 写失败测试**

`frontend/src/test/adminGuard.spec.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { isAdminRole, adminGuard } from '../router/adminGuard'
import { useAuth } from '../stores/auth'

const to = { fullPath: '/admin' } as any

describe('isAdminRole', () => {
  it('accepts SUPER_ADMIN and OPERATOR, rejects others', () => {
    expect(isAdminRole('SUPER_ADMIN')).toBe(true)
    expect(isAdminRole('OPERATOR')).toBe(true)
    expect(isAdminRole('USER')).toBe(false)
    expect(isAdminRole(undefined)).toBe(false)
  })
})

describe('adminGuard', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('redirects anonymous to login with redirect', async () => {
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'login', query: { redirect: '/admin' } })
  })

  it('redirects logged-in non-admin to home', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = { username: 'u', email: 'u@x.com', locale: 'zh-CN', role: 'USER' }
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'home' })
  })

  it('allows admin', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'SUPER_ADMIN' }
    const res = await adminGuard(to)
    expect(res).toBe(true)
  })

  it('loads me when authed but user missing, then allows admin', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = null
    vi.spyOn(auth, 'loadMe').mockImplementation(async () => {
      auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'OPERATOR' }
    })
    const res = await adminGuard(to)
    expect(auth.loadMe).toHaveBeenCalled()
    expect(res).toBe(true)
  })
})
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/adminGuard.spec.ts`
Expected: FAIL —— `../router/adminGuard` 不存在。

- [ ] **Step 3: 写 adminGuard.ts**

`frontend/src/router/adminGuard.ts`:

```ts
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { useAuth } from '../stores/auth'

export const isAdminRole = (role?: string) => role === 'SUPER_ADMIN' || role === 'OPERATOR'

/** /admin 守卫:未登录→登录(带回跳);已登录非管理员→首页;管理员→放行。
 *  后端仍独立 403 兜底,前端守卫只是体验。 */
export async function adminGuard(
  to: RouteLocationNormalized,
): Promise<boolean | RouteLocationRaw> {
  const auth = useAuth()
  if (!auth.isAuthed) return { name: 'login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try { await auth.loadMe() } catch { return { name: 'login', query: { redirect: to.fullPath } } }
  }
  if (!isAdminRole(auth.user?.role)) return { name: 'home' }
  return true
}
```

- [ ] **Step 4: 接进 router/index.ts**

在 `frontend/src/router/index.ts` 顶部加 import,并在 `routes` 数组末尾(`/me` 之后)加 `/admin` 父路由:

```ts
import { adminGuard } from './adminGuard'
```

```ts
    {
      path: '/admin',
      component: () => import('../components/admin/AdminLayout.vue'),
      beforeEnter: adminGuard,
      children: [
        { path: '', name: 'admin-overview', component: () => import('../pages/admin/AdminOverviewPage.vue') },
        { path: 'subscriptions', name: 'admin-subscriptions', component: () => import('../pages/admin/AdminSubscriptionsPage.vue') },
        { path: 'hotness', name: 'admin-hotness', component: () => import('../pages/admin/AdminHotnessPage.vue') },
      ],
    },
```

> 此时 `AdminLayout.vue` 等尚未建,`npm run build` 会因动态 import 缺文件报错 —— 后续 Task 9-12 补齐。本任务只需守卫单测通过。

- [ ] **Step 5: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/adminGuard.spec.ts`
Expected: PASS(2 个 describe 共 6 例)。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/router/adminGuard.ts frontend/src/router/index.ts \
        frontend/src/test/adminGuard.spec.ts
git commit -m "feat(admin): /admin routes + role-based route guard (#7a)"
```

---

## Task 9: 测试 setup(EP polyfill)+ AdminLayout 外壳

**Files:**
- Create: `frontend/src/test/setup.ts`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/components/admin/AdminLayout.vue`

- [ ] **Step 1: 写测试 setup(给 jsdom 补 EP 依赖的全局)**

`frontend/src/test/setup.ts`:

```ts
// Element Plus 的部分组件依赖这些浏览器 API,jsdom 缺省没有。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(globalThis as any).ResizeObserver = ResizeObserverStub

if (!window.matchMedia) {
  ;(window as any).matchMedia = () => ({
    matches: false, media: '', onchange: null,
    addListener() {}, removeListener() {},
    addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false },
  })
}
```

- [ ] **Step 2: 注册 setupFiles**

在 `frontend/vite.config.ts` 的 `test` 块里加 `setupFiles`:

```ts
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
```

- [ ] **Step 3: 写 AdminLayout.vue 失败测试**

`frontend/src/test/AdminLayout.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import AdminLayout from '../components/admin/AdminLayout.vue'

describe('AdminLayout', () => {
  it('renders the three nav entries', () => {
    const wrapper = mount(AdminLayout, {
      global: {
        stubs: { 'router-link': RouterLinkStub, 'router-view': { template: '<div />' } },
      },
    })
    const text = wrapper.text()
    expect(text).toContain('概览')
    expect(text).toContain('订阅统计')
    expect(text).toContain('热度榜单')
  })
})
```

- [ ] **Step 4: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/AdminLayout.spec.ts`
Expected: FAIL —— `AdminLayout.vue` 不存在。

- [ ] **Step 5: 写 AdminLayout.vue**

`frontend/src/components/admin/AdminLayout.vue`(EP 布局外壳 + `router-link` 导航;EP CSS 在此 import,因本组件懒加载而进 admin chunk):

```vue
<script setup lang="ts">
import { ElContainer, ElAside, ElMain, ElHeader } from 'element-plus'
import 'element-plus/dist/index.css'

const nav = [
  { to: { name: 'admin-overview' }, label: '概览', icon: '📊' },
  { to: { name: 'admin-subscriptions' }, label: '订阅统计', icon: '⭐' },
  { to: { name: 'admin-hotness' }, label: '热度榜单', icon: '🔥' },
]
</script>

<template>
  <ElContainer class="admin-shell" style="min-height: 100vh">
    <ElHeader class="topbar">
      <div class="topbar__in">
        <router-link class="brand" :to="{ name: 'home' }"><span class="glyph">B</span> 机场巴士信息 · 后台</router-link>
      </div>
    </ElHeader>
    <ElContainer>
      <ElAside width="210px">
        <nav class="adminNav">
          <div class="role">Admin · 控制台</div>
          <router-link
            v-for="item in nav"
            :key="item.label"
            :to="item.to"
            class="admin-navlink"
            active-class="active"
          >{{ item.icon }} {{ item.label }}</router-link>
        </nav>
      </ElAside>
      <ElMain>
        <router-view />
      </ElMain>
    </ElContainer>
  </ElContainer>
</template>

<style scoped>
/* 复用 tokens.css 的 .adminNav 视觉(SoT);router-link 用 .admin-navlink 套同款样式 */
.admin-navlink {
  display: flex; align-items: center; gap: 9px; padding: 10px 12px;
  border-radius: 9px; text-decoration: none; color: var(--ink-soft);
  font-size: 14px; font-weight: 600; margin-bottom: 2px;
}
.admin-navlink:hover { background: var(--paper); }
.admin-navlink.active { background: var(--info-soft); color: var(--brand); }
</style>
```

- [ ] **Step 6: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/AdminLayout.spec.ts`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/test/setup.ts frontend/vite.config.ts \
        frontend/src/components/admin/AdminLayout.vue frontend/src/test/AdminLayout.spec.ts
git commit -m "feat(admin): admin layout shell + EP test polyfill (#7a)"
```

---

## Task 10: 概览页(统计卡 + ECharts 趋势图 + 工单占位)

**Files:**
- Create: `frontend/src/pages/admin/AdminOverviewPage.vue`
- Test: `frontend/src/test/AdminOverviewPage.spec.ts`

- [ ] **Step 1: 写失败测试**(mock api + mock echarts,避免 canvas)

`frontend/src/test/AdminOverviewPage.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api/admin', () => ({
  getOverview: vi.fn(() => Promise.resolve({
    totalUsers: 1284, newUsersThisWeek: 42, totalFavorites: 3907, newFavoritesThisWeek: 118,
  })),
  getRegistrations: vi.fn(() => Promise.resolve([
    { date: '2026-06-14', count: 18 }, { date: '2026-06-20', count: 42 },
  ])),
}))
vi.mock('echarts', () => ({
  init: () => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }),
}))

import AdminOverviewPage from '../pages/admin/AdminOverviewPage.vue'

describe('AdminOverviewPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders stat cards from overview data', async () => {
    const wrapper = mount(AdminOverviewPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('1284')      // 总用户
    expect(text).toContain('3907')      // 收藏(订阅)
    expect(text).toContain('待处理工单') // 占位卡仍在
    expect(text).toContain('—')         // 占位值
  })
})
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/AdminOverviewPage.spec.ts`
Expected: FAIL —— 页面不存在。

- [ ] **Step 3: 写 AdminOverviewPage.vue**

`frontend/src/pages/admin/AdminOverviewPage.vue`:

```vue
<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElCard } from 'element-plus'
import * as echarts from 'echarts'
import { getOverview, getRegistrations, type Overview, type RegistrationPoint } from '../../api/admin'

const overview = ref<Overview | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)

onMounted(async () => {
  overview.value = await getOverview()
  const points: RegistrationPoint[] = await getRegistrations(7)
  await nextTick()
  if (!chartEl.value) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({
    grid: { left: 36, right: 12, top: 16, bottom: 28 },
    xAxis: { type: 'category', data: points.map((p) => p.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: points.map((p) => p.count), itemStyle: { color: '#2f6df6' } }],
    tooltip: { trigger: 'axis' },
  })
})
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">概览</h1>
  <p class="pageDesc">用户注册与订阅概况。</p>

  <div class="statCards">
    <ElCard class="stat" shadow="never">
      <div class="k">总用户</div>
      <div class="v">{{ overview?.totalUsers ?? '—' }}</div>
      <div class="t">+{{ overview?.newUsersThisWeek ?? 0 }} 本周</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">本周新增</div>
      <div class="v">{{ overview?.newUsersThisWeek ?? '—' }}</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">收藏(订阅)</div>
      <div class="v">{{ overview?.totalFavorites ?? '—' }}</div>
      <div class="t">+{{ overview?.newFavoritesThisWeek ?? 0 }} 本周</div>
    </ElCard>
    <ElCard class="stat" shadow="never">
      <div class="k">待处理工单</div>
      <div class="v">—</div>
      <div class="t" style="color: var(--ink-faint)">工单模块上线后接入</div>
    </ElCard>
  </div>

  <div class="panel">
    <h3>注册趋势 · 近 7 天</h3>
    <div ref="chartEl" style="height: 220px"></div>
  </div>
</template>
```

- [ ] **Step 4: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/AdminOverviewPage.spec.ts`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/pages/admin/AdminOverviewPage.vue frontend/src/test/AdminOverviewPage.spec.ts
git commit -m "feat(admin): overview page (stat cards + echarts trend + ticket placeholder) (#7a)"
```

---

## Task 11: 订阅统计页(三张表)

**Files:**
- Create: `frontend/src/pages/admin/AdminSubscriptionsPage.vue`
- Test: `frontend/src/test/AdminSubscriptionsPage.spec.ts`

- [ ] **Step 1: 写失败测试**

`frontend/src/test/AdminSubscriptionsPage.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api/admin', () => ({
  getSubscriptions: vi.fn(() => Promise.resolve({
    topRoutes: [{ busSourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', airportCode: 'VIE', cityName: '维也纳', favoriteCount: 612, notifyCount: 612 }],
    topAirports: [{ airportCode: 'VIE', airportName: '维也纳国际机场', cityName: '维也纳', favoriteCount: 989 }],
    topCities: [{ cityName: '维也纳', countryName: '奥地利', favoriteCount: 989 }],
  })),
}))

import AdminSubscriptionsPage from '../pages/admin/AdminSubscriptionsPage.vue'

describe('AdminSubscriptionsPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders top routes / airports / cities', async () => {
    const wrapper = mount(AdminSubscriptionsPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('VIE')
    expect(text).toContain('维也纳')
    expect(text).toContain('612')
  })
})
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/AdminSubscriptionsPage.spec.ts`
Expected: FAIL —— 页面不存在。

- [ ] **Step 3: 写 AdminSubscriptionsPage.vue**(用 EP 表格;列头「订阅数」对应 notifyCount=favoriteCount)

`frontend/src/pages/admin/AdminSubscriptionsPage.vue`:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn } from 'element-plus'
import { getSubscriptions, type SubscriptionStats } from '../../api/admin'

const data = ref<SubscriptionStats>({ topRoutes: [], topAirports: [], topCities: [] })
onMounted(async () => { data.value = await getSubscriptions() })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">订阅统计</h1>
  <p class="pageDesc">按线路 / 机场 / 城市聚合收藏(= 订阅)计数。</p>

  <div class="panel">
    <h3>最受关注的线路</h3>
    <ElTable :data="data.topRoutes" style="width: 100%">
      <ElTableColumn prop="route" label="线路" />
      <ElTableColumn prop="destination" label="目的地" />
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="cityName" label="城市" width="120" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
      <ElTableColumn prop="notifyCount" label="订阅数" width="100" />
    </ElTable>
  </div>

  <div class="panel">
    <h3>机场维度</h3>
    <ElTable :data="data.topAirports" style="width: 100%">
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="airportName" label="名称" />
      <ElTableColumn prop="cityName" label="城市" width="120" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
    </ElTable>
  </div>

  <div class="panel">
    <h3>城市维度</h3>
    <ElTable :data="data.topCities" style="width: 100%">
      <ElTableColumn prop="cityName" label="城市" />
      <ElTableColumn prop="countryName" label="国家 / 地区" />
      <ElTableColumn prop="favoriteCount" label="收藏数" width="100" />
    </ElTable>
  </div>
</template>
```

- [ ] **Step 4: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/AdminSubscriptionsPage.spec.ts`
Expected: PASS。若 ElTable 在 jsdom 未渲染单元格文本,确认 `setup.ts` 的 ResizeObserver polyfill 已生效(`vite.config.ts` 的 `setupFiles`)。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/pages/admin/AdminSubscriptionsPage.vue frontend/src/test/AdminSubscriptionsPage.spec.ts
git commit -m "feat(admin): subscription stats page (3 tables) (#7a)"
```

---

## Task 12: 热度榜单页(表 + 时间窗切换)

**Files:**
- Create: `frontend/src/pages/admin/AdminHotnessPage.vue`
- Test: `frontend/src/test/AdminHotnessPage.spec.ts`

- [ ] **Step 1: 写失败测试**

`frontend/src/test/AdminHotnessPage.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as admin from '../api/admin'

vi.mock('../api/admin', () => ({
  getHotness: vi.fn(() => Promise.resolve([
    { airportCode: 'VIE', airportName: '维也纳国际机场', cityName: '维也纳', views: 1200 },
    { airportCode: 'PVG', airportName: '浦东国际机场', cityName: '上海', views: 900 },
  ])),
}))

import AdminHotnessPage from '../pages/admin/AdminHotnessPage.vue'

describe('AdminHotnessPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders hotness rows and defaults to 7d window', async () => {
    const wrapper = mount(AdminHotnessPage)
    await flushPromises()
    expect(admin.getHotness).toHaveBeenCalledWith('7d')
    const text = wrapper.text()
    expect(text).toContain('VIE')
    expect(text).toContain('1200')
  })

  it('re-fetches when window changes to 30d', async () => {
    const wrapper = mount(AdminHotnessPage)
    await flushPromises()
    ;(wrapper.vm as any).window = '30d'
    await (wrapper.vm as any).reload()
    expect(admin.getHotness).toHaveBeenLastCalledWith('30d')
  })
})
```

- [ ] **Step 2: 运行,确认失败**

Run: `cd frontend && npx vitest run src/test/AdminHotnessPage.spec.ts`
Expected: FAIL —— 页面不存在。

- [ ] **Step 3: 写 AdminHotnessPage.vue**

`frontend/src/pages/admin/AdminHotnessPage.vue`:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElRadioGroup, ElRadioButton } from 'element-plus'
import { getHotness, type HotnessRow } from '../../api/admin'

const window = ref<'7d' | '30d' | 'all'>('7d')
const rows = ref<HotnessRow[]>([])

async function reload() { rows.value = await getHotness(window.value) }
onMounted(reload)

defineExpose({ window, reload })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">机场搜索热度</h1>
  <p class="pageDesc">按机场展示搜索热度排行,指导数据维护优先级。</p>

  <div class="panel">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px">
      <h3 style="margin: 0">热度榜单</h3>
      <ElRadioGroup v-model="window" size="small" @change="reload">
        <ElRadioButton label="7d">近 7 天</ElRadioButton>
        <ElRadioButton label="30d">近 30 天</ElRadioButton>
        <ElRadioButton label="all">全部</ElRadioButton>
      </ElRadioGroup>
    </div>
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="airportCode" label="机场" width="90" />
      <ElTableColumn prop="airportName" label="名称" />
      <ElTableColumn prop="cityName" label="城市" width="140" />
      <ElTableColumn prop="views" label="搜索量" width="120" />
    </ElTable>
  </div>
</template>
```

- [ ] **Step 4: 运行,确认通过**

Run: `cd frontend && npx vitest run src/test/AdminHotnessPage.spec.ts`
Expected: PASS(2 个)。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/pages/admin/AdminHotnessPage.vue frontend/src/test/AdminHotnessPage.spec.ts
git commit -m "feat(admin): hotness ranking page with window switch (#7a)"
```

---

## Task 13: 全量验证 + 收尾

**Files:**
- Modify: `MEMORY.md` + 新建 `…/memory/admin-shell-shipped.md`(记忆,见下)

- [ ] **Step 1: 前端全量测试 + 构建(确认无回退、代码分割成立)**

Run:
```bash
cd frontend && npx vitest run && npm run build
```
Expected: 全部 spec PASS;`vue-tsc` 无类型错误;`vite build` 产出 admin 异步 chunk(包含 element-plus / echarts)。
检查:`ls -la frontend/dist/assets | grep -i admin`(应见独立的 admin chunk);确认公开首页入口 chunk 不含 element-plus。

- [ ] **Step 2: 后端全量 IT 回归**

Run:
```bash
cd backend && mvn -q -Dtest=BusQueryServiceIT,SeedImporterIT,SearchHotnessServiceIT,HotnessRankingIT,AuthFlowIT,AuthServiceIT,AuthCacheServiceIT,FavoriteServiceIT,FavoriteApiIT,UserStatsServiceIT,FavoriteStatsServiceIT,AdminStatsApiIT,CurrentUserTest test
```
Expected: 全 PASS。

- [ ] **Step 3: 手动全栈验证**(参考 testing-tooling-quirks 记忆)

```bash
docker compose up -d mysql redis
# 后端:SEED_ENABLED=true ... mvn spring-boot:run(控制台会打印种子 admin 账号密码)
# 前端:cd frontend && npx vite
```
逐项确认:
- 用普通用户登录 → 浏览器访问 `/admin` → 被守卫拦回首页;直接 `curl /api/v1/admin/stats/overview`(带普通用户 token)→ 403 `ADMIN_FORBIDDEN`。
- 用种子 admin 登录 → `/admin` 进入,概览显真实总用户/收藏数 + 注册趋势柱状图;订阅统计三表有数据(先用某账号收藏 vie-vab1 再看);热度榜单切 7d/30d/all 有响应。
- 匿名访问 `/admin` → 跳登录,登录后回跳 `/admin`。

- [ ] **Step 4: 更新记忆**

新建 `…/memory/admin-shell-shipped.md`(type: project):记录 #7a 已交付(`com.airportbus.admin` 模块 + `CurrentUser.requireAdmin` + 三块只读统计端点 + 前端 `/admin` 懒加载树 + EP/ECharts 进 admin chunk);follow-up:#7b 审计、#7c 巴士维护+message、#7d 纠错上报;待处理工单卡为前端占位待接;OPERATOR 角色无种子账号(只有 SUPER_ADMIN)。在 `MEMORY.md` 加一行指针。

- [ ] **Step 5: 最终提交**

```bash
git add -A
git commit -m "chore(admin): #7a verification + memory note (admin shell + dashboard shipped)"
```

---

## 自审清单(写计划者已核对)

- **spec 覆盖**:RBAC(Task 1/6)、用户统计(2)、订阅统计(3)、热度榜单(4)、admin 模块/端点(5)、API 契约 + 401/403(6)、前端 api(7)、守卫 + 路由(8)、EP 外壳(9)、概览含工单占位 + ECharts(10)、订阅表(11)、热度页 + 窗口(12)、代码分割 + 全栈验证(13)。✅
- **类型一致**:后端 record 全限定名在 mapper(`UserMapper$DayCount`、`FavoriteMapper$RouteSub/AirportSub/CitySub`、`SearchHotnessMapper$HotnessRow`)与 service/dto/controller 引用一致;前端 `Overview/RegistrationPoint/RouteSub/AirportSub/CitySub/SubscriptionStats/HotnessRow` 在 api 与各页面一致。✅
- **无占位**:每步含完整代码与命令。`AuthService.register` 签名一处提示按现有代码核对(Task 2/3 注)。✅
- **YAGNI/边界**:admin 只读编排;聚合查询归属主模块;EP/ECharts 仅 admin chunk。✅
