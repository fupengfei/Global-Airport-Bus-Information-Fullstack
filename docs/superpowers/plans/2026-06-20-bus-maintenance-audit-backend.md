# 巴士维护 + 审计 + 版本历史 —— 后端实现计划(#7 切片 A · 后端)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 后端交付巴士维护写路径(共享 `BusCommandService`)、乐观锁、字段级 diff + `BusUpdatedEvent`(无监听者)、版本快照 + 回滚、操作审计切面,以及 admin 写/读 API,全部 IT 覆盖。

**Architecture:** 把现有 `SeedImporter` 的 bus 写逻辑抽进 `com.airportbus.bus.service.BusCommandService`(E5 单一写入口),叠加 version/快照/diff/事件/乐观锁;导入器改为委托它并抑制事件(E11)。审计为独立 `com.airportbus.audit` 模块(`@Audited` 注解 + `@Around` 切面)。admin 端点在 `com.airportbus.admin`,RBAC 走手写 `CurrentUser`。

**Tech Stack:** Spring Boot + MyBatis(`#{}` only,record `resultType` + `AS` 别名)+ Flyway 迁移 + Testcontainers IT;Jackson 序列化快照。

---

## 上游与约束

- spec:`docs/superpowers/specs/2026-06-20-bus-maintenance-audit-design.md`(读它)。
- 前置已交付:#7a(`AdminStatsController`、`CurrentUser.requireAdmin()`、`ErrorCode.ADMIN_FORBIDDEN`)。
- **本计划只做后端**;前端(维护页/历史/审计页)是单独的计划,后端落地后再写。
- 约定:每表带审计列 + `deleted`,迁移每列带 COMMENT;读路径排除 `deleted=1`;MyBatis 仅 `#{}`;对外用 `source_id`/机场 `code`;错误体 `{code,message,details,traceId}` + 真实状态码。

## 关键既有事实

- `bus_route` 已有 `content_hash CHAR(64) NOT NULL`、`last_updated DATE`、`updated_at`、审计列、`deleted`;`UNIQUE KEY uk_bus_source_id(source_id, deleted)`。**缺** `version`/`last_verified_at`/`last_verified_by`(V5 加)。
- `BusWriteMapper`(`backend/.../bus/mapper/BusWriteMapper.java` + xml):有 `findAirportId(code)`、`findBusId(sourceId)`、`insertBus(map)`/`updateBus(map)`(列见 xml)、`deleteStops/Schedules/Images/Files/Alerts(busId)`、`insertStop(busId,seq,name)`、`insertSchedule(map)`、`insertImage`、`insertFile`、`insertAlert(map)`。**stop/schedule 不含 direction**(对齐之,不引入)。
- `Canonicalizer.contentHash(CanonicalBus)` / `canonicalJson(CanonicalBus)`;`CanonicalBus` 见 `bus/hash/`。
- `SeedImporter`(`bus/seed/SeedImporter.java`):`upsertBus` + `replaceChildren` + `toCanonical`,本计划把 bus 级写逻辑迁入 `BusCommandService`,importer 改调它。
- `BusDetailDto`(`bus/api/dto/`):record + 嵌套 `Schedule{timeRange,intervalText,note}`/`Image{url,caption}`/`FileRef{name,url}`/`Alert{type,message,startDate:LocalDate,endDate:LocalDate}`;`stops` 为 `List<String>`。`BusQueryService.detail(sourceId)` 返回它。
- `CurrentUser`(`user/security/`):`require()`、`requireAdmin()`;`JwtPrincipal(userId, role)`。
- 命令:编译 `cd backend && mvn -q -DskipTests compile`;单测/IT `cd backend && mvn -q -Dtest=<Class> test`(IT 需 Docker)。

## 文件结构

**迁移**:`backend/src/main/resources/db/migration/V5__bus_admin_columns.sql`、`V6__audit_log.sql`、`V7__bus_route_version.sql`

**bus 模块**:
- `bus/api/dto/BusInput.java`(编辑入参,复用 BusDetailDto 嵌套 record)
- `bus/api/dto/BusView.java`(编辑视图:sourceId+airportCode+version+lastVerifiedAt+data)
- `bus/service/BusCommandService.java`(save/verify/delete/rollback + diff + 快照 + 事件 + 乐观锁)
- `bus/service/ChangedSummary.java`、`bus/service/BusUpdatedEvent.java`
- `bus/mapper/BusWriteMapper.java`(+xml):加 `selectVersionHash`、`updateBusFull`(含 version/last_updated)、`updateVerify`、`softDeleteBus`
- `bus/mapper/BusVersionMapper.java`(+xml):快照 insert/list/get
- 改 `bus/seed/SeedImporter.java`(委托 BusCommandService)

**audit 模块(新)**:`audit/Audited.java`(注解)、`audit/AuditAspect.java`、`audit/AuditMapper.java`(+xml)、`audit/AuditLog.java`(行模型)、`audit/AuditQueryController.java`、`audit/AuditService.java`

**admin 模块**:`admin/api/AdminBusController.java`、`admin/api/dto/CreateBusRequest.java`、`admin/api/dto/UpdateBusRequest.java`

**common**:`CurrentUser.requireSuperAdmin()`、`ErrorCode.BUS_VERSION_CONFLICT`

---

## Task 1: 迁移 V5/V6/V7

**Files:** Create `V5__bus_admin_columns.sql`、`V6__audit_log.sql`、`V7__bus_route_version.sql`(在 `backend/src/main/resources/db/migration/`)

- [ ] **Step 1: 写 V5**
```sql
-- V5__bus_admin_columns.sql  巴士维护:乐观锁版本号 + 人工核对时间
ALTER TABLE bus_route
  ADD COLUMN version          INT          NOT NULL DEFAULT 0  COMMENT '乐观锁版本号/历史版本号,内容变化时+1',
  ADD COLUMN last_verified_at DATETIME     NULL                COMMENT '人工核对无误时间(与内容变更正交)',
  ADD COLUMN last_verified_by VARCHAR(64)  NULL                COMMENT '核对人';
```

- [ ] **Step 2: 写 V6**
```sql
-- V6__audit_log.sql  管理端写操作审计(只增写;含审计列+逻辑删除)
CREATE TABLE audit_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  actor_id    BIGINT       NOT NULL                            COMMENT '操作人(app_user.id)',
  actor_type  VARCHAR(16)  NOT NULL                            COMMENT '操作人类型,如 ADMIN',
  action      VARCHAR(32)  NOT NULL                            COMMENT '动作:CREATE_BUS/UPDATE_BUS/DELETE_BUS/VERIFY_BUS/ROLLBACK_BUS',
  target_type VARCHAR(16)  NOT NULL                            COMMENT '对象类型,如 bus',
  target_id   VARCHAR(64)  NULL                                COMMENT '对象业务键,如 source_id',
  summary     VARCHAR(512) NULL                                COMMENT '变更摘要/说明',
  ip          VARCHAR(45)  NULL                                COMMENT '操作来源 IP',
  created_by  VARCHAR(64)  NULL                                COMMENT '创建人',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by  VARCHAR(64)  NULL                                COMMENT '更新人',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_audit_created (created_at),
  KEY idx_audit_actor (actor_id),
  KEY idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作审计';
```

- [ ] **Step 3: 写 V7**
```sql
-- V7__bus_route_version.sql  线路版本快照(只增写;含审计列+逻辑删除)
CREATE TABLE bus_route_version (
  id              BIGINT      PRIMARY KEY AUTO_INCREMENT         COMMENT '主键',
  bus_route_id    BIGINT      NOT NULL                           COMMENT '线路内部ID',
  version         INT         NOT NULL                           COMMENT '版本号(对应 bus_route.version)',
  snapshot_json   LONGTEXT    NOT NULL                           COMMENT '整条线路含全子表的完整编辑DTO JSON(忠实,非canonical归一版)',
  content_hash    CHAR(64)    NOT NULL                           COMMENT '该版本内容哈希',
  changed_summary VARCHAR(1024) NULL                             COMMENT '相对上一版本的字段级diff摘要JSON',
  actor           VARCHAR(64) NULL                               COMMENT '产生该版本的操作人',
  created_by      VARCHAR(64) NULL                               COMMENT '创建人',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by      VARCHAR(64) NULL                               COMMENT '更新人',
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted         TINYINT(1)  NOT NULL DEFAULT 0                 COMMENT '逻辑删除',
  UNIQUE KEY uk_brv (bus_route_id, version, deleted),
  KEY idx_brv_bus (bus_route_id),
  CONSTRAINT fk_brv_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线路版本快照';
```

- [ ] **Step 4: 验证迁移干净应用**(用现有 IT 启动一次 Flyway)

Run: `cd backend && mvn -q -Dtest=SeedImporterIT test`
Expected: PASS(Flyway 应用 V1–V7 无错;SeedImporterIT 此刻仍是旧 importer,应仍绿)。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/resources/db/migration/V5__bus_admin_columns.sql \
        backend/src/main/resources/db/migration/V6__audit_log.sql \
        backend/src/main/resources/db/migration/V7__bus_route_version.sql
git commit -m "feat(bus): V5/V6/V7 迁移 version+核对时间 / audit_log / bus_route_version (#7A)"
```

---

## Task 2: RBAC —— requireSuperAdmin + BUS_VERSION_CONFLICT

**Files:** Modify `common/ErrorCode.java`、`user/security/CurrentUser.java`;Test `user/security/CurrentUserSuperAdminTest.java`

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/user/security/CurrentUserSuperAdminTest.java`
```java
package com.airportbus.user.security;

import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CurrentUserSuperAdminTest {
    @AfterEach void cleanup() { CurrentUser.clear(); }

    @Test void allowsSuperAdmin() {
        CurrentUser.set(new JwtPrincipal(1L, "SUPER_ADMIN"));
        assertThat(CurrentUser.requireSuperAdmin().role()).isEqualTo("SUPER_ADMIN");
    }
    @Test void rejectsOperator_withForbidden() {
        CurrentUser.set(new JwtPrincipal(2L, "OPERATOR"));
        assertThatThrownBy(CurrentUser::requireSuperAdmin)
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
    @Test void rejectsAnonymous_withUnauthorized() {
        assertThatThrownBy(CurrentUser::requireSuperAdmin)
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
```

- [ ] **Step 2: 运行确认失败**
Run: `cd backend && mvn -q -Dtest=CurrentUserSuperAdminTest test` → 编译失败(`requireSuperAdmin` 不存在)。

- [ ] **Step 3: 加 ErrorCode**(在枚举里 `ADMIN_FORBIDDEN` 附近)
```java
    BUS_VERSION_CONFLICT(HttpStatus.CONFLICT),
```

- [ ] **Step 4: 加 requireSuperAdmin**(`CurrentUser.java`,`requireAdmin()` 之后)
```java
    /** 要求 SUPER_ADMIN;否则 401(未登录)或 403(非超管)。 */
    public static JwtPrincipal requireSuperAdmin() {
        JwtPrincipal p = require();
        if (!"SUPER_ADMIN".equals(p.role())) {
            throw new ApiException(ErrorCode.ADMIN_FORBIDDEN, "super admin only");
        }
        return p;
    }
```

- [ ] **Step 5: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=CurrentUserSuperAdminTest test` → PASS(3)。

- [ ] **Step 6: 提交**
```bash
git add backend/src/main/java/com/airportbus/common/ErrorCode.java \
        backend/src/main/java/com/airportbus/user/security/CurrentUser.java \
        backend/src/test/java/com/airportbus/user/security/CurrentUserSuperAdminTest.java
git commit -m "feat(admin): requireSuperAdmin + BUS_VERSION_CONFLICT (#7A)"
```

---

## Task 3: 编辑 DTO —— BusInput / BusView / ChangedSummary / BusUpdatedEvent

**Files:** Create `bus/api/dto/BusInput.java`、`bus/api/dto/BusView.java`、`bus/service/ChangedSummary.java`、`bus/service/BusUpdatedEvent.java`

> 纯类型定义,编译验证即可(逻辑在后续任务)。复用 `BusDetailDto` 的嵌套 record,DRY。

- [ ] **Step 1: BusInput**
```java
package com.airportbus.bus.api.dto;

import java.time.LocalDate;
import java.util.List;

/** 巴士编辑入参(子表对齐现有写路径,不含 direction)。 */
public record BusInput(
        String route, String destination, String operator, String officialUrl,
        String duration, String price, String operatingHours, LocalDate lastUpdated,
        List<String> stops,
        List<BusDetailDto.Schedule> schedules,
        List<BusDetailDto.Alert> alerts,
        List<BusDetailDto.Image> images,
        List<BusDetailDto.FileRef> files) {}
```

- [ ] **Step 2: BusView**
```java
package com.airportbus.bus.api.dto;

import java.time.LocalDateTime;

/** 编辑视图:业务键 + 机场 + 乐观锁版本 + 核对时间 + 可编辑数据。 */
public record BusView(String sourceId, String airportCode, int version,
                      LocalDateTime lastVerifiedAt, BusInput data) {}
```

- [ ] **Step 3: ChangedSummary**
```java
package com.airportbus.bus.service;

import java.util.List;

/** 字段级变更摘要(相对上一版本):标量 old→new + 变更的子表名。 */
public record ChangedSummary(List<FieldChange> scalars, List<String> changedSubtables) {
    public record FieldChange(String field, String oldValue, String newValue) {}
}
```

- [ ] **Step 4: BusUpdatedEvent**
```java
package com.airportbus.bus.service;

/** 线路内容变化事件;切片 B 用 @TransactionalEventListener(AFTER_COMMIT) 监听。本期无监听者。 */
public record BusUpdatedEvent(long busRouteId, String sourceId,
                              String oldHash, String newHash, ChangedSummary summary) {}
```

- [ ] **Step 5: 编译 + 提交**
Run: `cd backend && mvn -q -DskipTests compile` → SUCCESS
```bash
git add backend/src/main/java/com/airportbus/bus/api/dto/BusInput.java \
        backend/src/main/java/com/airportbus/bus/api/dto/BusView.java \
        backend/src/main/java/com/airportbus/bus/service/ChangedSummary.java \
        backend/src/main/java/com/airportbus/bus/service/BusUpdatedEvent.java
git commit -m "feat(bus): edit DTOs BusInput/BusView + ChangedSummary/BusUpdatedEvent (#7A)"
```

---

## Task 4: 扩展 BusWriteMapper + 新建 BusVersionMapper

**Files:** Modify `bus/mapper/BusWriteMapper.java` + `resources/mapper/BusWriteMapper.xml`;Create `bus/mapper/BusVersionMapper.java` + `resources/mapper/BusVersionMapper.xml`、`bus/mapper/BusVersionMapper$Row`(嵌套 record)

> 纯 mapper 接线;由 Task 6/7 的 IT 覆盖。本任务编译验证。

- [ ] **Step 1: BusWriteMapper.java 追加方法**
```java
    /** 读当前 version + content_hash(乐观锁/变更判定用);不存在返回 null。 */
    VersionHash selectVersionHash(@Param("sourceId") String sourceId);

    /** 更新 bus 全字段 + content_hash + version(=#{newVersion}) + last_updated。 */
    void updateBusFull(java.util.Map<String, Object> row);

    /** 核对无误:仅更新 last_verified_at/by + updated_by。 */
    void updateVerify(@Param("sourceId") String sourceId,
                      @Param("at") java.time.LocalDateTime at,
                      @Param("actor") String actor);

    /** 软删线路。 */
    void softDeleteBus(@Param("sourceId") String sourceId, @Param("actor") String actor);

    record VersionHash(int version, String contentHash) {}
```

- [ ] **Step 2: BusWriteMapper.xml 追加**(`insertBus` 已有列不变;新建/更新时 version 由调用方给)
```xml
  <select id="selectVersionHash" resultType="com.airportbus.bus.mapper.BusWriteMapper$VersionHash">
    SELECT version, content_hash AS contentHash FROM bus_route
    WHERE source_id = #{sourceId} AND deleted = 0
  </select>

  <update id="updateBusFull" parameterType="map">
    UPDATE bus_route SET airport_id=#{airportId}, route=#{route}, destination=#{destination},
      operator=#{operator}, official_url=#{officialUrl}, duration=#{duration}, price=#{price},
      operating_hours=#{operatingHours}, last_updated=#{lastUpdated}, fetch_failed=#{fetchFailed},
      content_hash=#{contentHash}, version=#{newVersion}, updated_by=#{actor}
    WHERE id=#{id}
  </update>

  <update id="updateVerify">
    UPDATE bus_route SET last_verified_at=#{at}, last_verified_by=#{actor}, updated_by=#{actor}
    WHERE source_id=#{sourceId} AND deleted=0
  </update>

  <update id="softDeleteBus">
    UPDATE bus_route SET deleted=1, updated_by=#{actor}
    WHERE source_id=#{sourceId} AND deleted=0
  </update>
```
> 注:`insertBus` 现有 XML 不含 version 列,新插入 version 走列默认 0;Task 6 save 的 create 路径插入后再 `updateBusFull` 设 version=1(或加一条 `insertBus` 带 version 的变体——本计划用「插入后置版本」简化,见 Task 6)。

- [ ] **Step 3: BusVersionMapper.java**
```java
package com.airportbus.bus.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface BusVersionMapper {
    void insertSnapshot(java.util.Map<String, Object> row); // busRouteId, version, snapshotJson, contentHash, changedSummary, actor

    /** 某线路的版本列表(新→旧),不含快照大字段。 */
    List<Meta> listVersions(@Param("busRouteId") long busRouteId);

    /** 取某版本完整快照 JSON。 */
    String selectSnapshotJson(@Param("busRouteId") long busRouteId, @Param("version") int version);

    record Meta(int version, String contentHash, String changedSummary, String actor,
                java.time.LocalDateTime createdAt) {}
}
```

- [ ] **Step 4: BusVersionMapper.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.bus.mapper.BusVersionMapper">
  <insert id="insertSnapshot" parameterType="map">
    INSERT INTO bus_route_version(bus_route_id, version, snapshot_json, content_hash, changed_summary, actor, created_by)
    VALUES(#{busRouteId}, #{version}, #{snapshotJson}, #{contentHash}, #{changedSummary}, #{actor}, #{actor})
  </insert>
  <select id="listVersions" resultType="com.airportbus.bus.mapper.BusVersionMapper$Meta">
    SELECT version, content_hash AS contentHash, changed_summary AS changedSummary,
           actor, created_at AS createdAt
    FROM bus_route_version WHERE bus_route_id=#{busRouteId} AND deleted=0
    ORDER BY version DESC
  </select>
  <select id="selectSnapshotJson" resultType="string">
    SELECT snapshot_json FROM bus_route_version
    WHERE bus_route_id=#{busRouteId} AND version=#{version} AND deleted=0
  </select>
</mapper>
```

- [ ] **Step 5: 注册 mapper 扫描** —— `AirportbusApplication` 的 `@MapperScan` 已含 `"com.airportbus.bus.mapper"`,`BusVersionMapper` 同包,无需改。

- [ ] **Step 6: 编译 + 提交**
Run: `cd backend && mvn -q -DskipTests compile` → SUCCESS
```bash
git add backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java \
        backend/src/main/resources/mapper/BusWriteMapper.xml \
        backend/src/main/java/com/airportbus/bus/mapper/BusVersionMapper.java \
        backend/src/main/resources/mapper/BusVersionMapper.xml
git commit -m "feat(bus): write mapper version/verify/softdelete + BusVersionMapper (#7A)"
```

---

## Task 5: BusCommandService —— canonical 构建 + 字段级 diff(纯逻辑)

**Files:** Create `bus/service/BusCommandService.java`(本任务先放 canonical/diff 静态辅助 + 构造);Test `bus/service/BusDiffTest.java`

> 先做可纯单测的部分:`toCanonical(BusInput)`、`diff(old, new)`。save/verify/delete/rollback 在 Task 6。

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/bus/service/BusDiffTest.java`
```java
package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusDiffTest {
    private BusInput base() {
        return new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€11", "03:00-24:00", null,
                List.of("A", "B"),
                List.of(new BusDetailDto.Schedule("all day", "30min", null)),
                List.of(), List.of(), List.of());
    }

    @Test void scalarChange_isDetected() {
        BusInput a = base();
        BusInput b = new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€13", "03:00-24:00", null,
                a.stops(), a.schedules(), a.alerts(), a.images(), a.files());
        ChangedSummary s = BusCommandService.diff(a, b);
        assertThat(s.scalars()).anySatisfy(f -> {
            assertThat(f.field()).isEqualTo("price");
            assertThat(f.oldValue()).isEqualTo("€11");
            assertThat(f.newValue()).isEqualTo("€13");
        });
        assertThat(s.changedSubtables()).isEmpty();
    }

    @Test void subtableChange_isFlagged() {
        BusInput a = base();
        BusInput b = new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€11", "03:00-24:00", null,
                List.of("A", "B", "C"), a.schedules(), a.alerts(), a.images(), a.files());
        ChangedSummary s = BusCommandService.diff(a, b);
        assertThat(s.scalars()).isEmpty();
        assertThat(s.changedSubtables()).contains("stops");
    }

    @Test void identical_isEmpty() {
        ChangedSummary s = BusCommandService.diff(base(), base());
        assertThat(s.scalars()).isEmpty();
        assertThat(s.changedSubtables()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败**
Run: `cd backend && mvn -q -Dtest=BusDiffTest test` → 编译失败(`BusCommandService.diff` 不存在)。

- [ ] **Step 3: 写 BusCommandService 骨架 + diff/toCanonical 静态辅助**
`backend/src/main/java/com/airportbus/bus/service/BusCommandService.java`:
```java
package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.hash.CanonicalBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** bus 单一写入口(E5)。本任务先放纯逻辑辅助;实例方法在 Task 6 补。 */
public class BusCommandService {

    /** BusInput → CanonicalBus(与 SeedImporter.toCanonical 对齐:files 的 label 取 name)。 */
    static CanonicalBus toCanonical(BusInput in) {
        List<CanonicalBus.Schedule> sch = new ArrayList<>();
        for (BusDetailDto.Schedule s : nz(in.schedules()))
            sch.add(new CanonicalBus.Schedule(s.timeRange(), s.intervalText(), s.note()));
        List<CanonicalBus.Alert> al = new ArrayList<>();
        for (BusDetailDto.Alert a : nz(in.alerts()))
            al.add(new CanonicalBus.Alert(a.type(), a.message(),
                    a.startDate() == null ? null : a.startDate().toString(),
                    a.endDate() == null ? null : a.endDate().toString()));
        List<CanonicalBus.Media> img = new ArrayList<>();
        for (BusDetailDto.Image m : nz(in.images())) img.add(new CanonicalBus.Media(m.url(), m.caption()));
        List<CanonicalBus.Media> fl = new ArrayList<>();
        for (BusDetailDto.FileRef f : nz(in.files())) fl.add(new CanonicalBus.Media(f.url(), f.name()));
        return new CanonicalBus(in.route(), in.destination(), in.operator(), in.duration(),
                in.price(), in.operatingHours(), nz(in.stops()), sch, al, img, fl);
    }

    /** 字段级 diff:标量 old→new + 变更子表名。 */
    static ChangedSummary diff(BusInput oldI, BusInput newI) {
        List<ChangedSummary.FieldChange> scalars = new ArrayList<>();
        addIfChanged(scalars, "route", oldI.route(), newI.route());
        addIfChanged(scalars, "destination", oldI.destination(), newI.destination());
        addIfChanged(scalars, "operator", oldI.operator(), newI.operator());
        addIfChanged(scalars, "officialUrl", oldI.officialUrl(), newI.officialUrl());
        addIfChanged(scalars, "duration", oldI.duration(), newI.duration());
        addIfChanged(scalars, "price", oldI.price(), newI.price());
        addIfChanged(scalars, "operatingHours", oldI.operatingHours(), newI.operatingHours());
        addIfChanged(scalars, "lastUpdated",
                oldI.lastUpdated() == null ? null : oldI.lastUpdated().toString(),
                newI.lastUpdated() == null ? null : newI.lastUpdated().toString());

        List<String> subs = new ArrayList<>();
        if (!Objects.equals(nz(oldI.stops()), nz(newI.stops()))) subs.add("stops");
        if (!Objects.equals(nz(oldI.schedules()), nz(newI.schedules()))) subs.add("schedules");
        if (!Objects.equals(nz(oldI.alerts()), nz(newI.alerts()))) subs.add("alerts");
        if (!Objects.equals(nz(oldI.images()), nz(newI.images()))) subs.add("images");
        if (!Objects.equals(nz(oldI.files()), nz(newI.files()))) subs.add("files");
        return new ChangedSummary(scalars, subs);
    }

    private static void addIfChanged(List<ChangedSummary.FieldChange> out, String f, String o, String n) {
        if (!Objects.equals(o, n)) out.add(new ChangedSummary.FieldChange(f, o, n));
    }

    private static <T> List<T> nz(List<T> in) { return in == null ? List.of() : in; }
}
```
> 子表用 record 的 `equals`(record 自动按值比较)判变更——简单可靠。`BusDetailDto.Schedule/Alert/Image/FileRef` 是 record,顺序敏感(顺序变即视为变更,可接受)。

- [ ] **Step 4: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=BusDiffTest test` → PASS(3)。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/java/com/airportbus/bus/service/BusCommandService.java \
        backend/src/test/java/com/airportbus/bus/service/BusDiffTest.java
git commit -m "feat(bus): BusCommandService canonical+diff helpers (#7A)"
```

---

## Task 6: BusCommandService —— save / verify / delete / rollback(实例方法 + IT)

**Files:** Modify `bus/service/BusCommandService.java`;Test `bus/service/BusCommandServiceIT.java`

依赖注入:`BusWriteMapper`、`BusVersionMapper`、`BusQueryService`(读旧 detail)、`ApplicationEventPublisher`、`com.fasterxml.jackson.databind.ObjectMapper`。

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/bus/service/BusCommandServiceIT.java`
```java
package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
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

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class BusCommandServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired BusCommandService cmd;
    @Autowired BusWriteMapper writeMapper;
    @Autowired BusVersionMapper versionMapper;

    private long airportIdOfVie() { return writeMapper.findAirportId("VIE"); }

    private BusInput input(String price) {
        return new BusInput("VAB X", "西站", "ÖBB", null, "40min", price, "03:00-24:00", null,
                List.of("A", "B"),
                List.of(new BusDetailDto.Schedule("all day", "30min", null)),
                List.of(), List.of(), List.of());
    }

    @Test void create_setsVersion1_andSnapshot() {
        BusView v = cmd.save("vie-cmd1", airportIdOfVie(), input("€11"), null, "admin", false);
        assertThat(v.version()).isEqualTo(1);
        long brId = writeMapper.findBusId("vie-cmd1");
        assertThat(versionMapper.listVersions(brId)).hasSize(1);
    }

    @Test void update_changesHash_bumpsVersion_addsSnapshot() {
        cmd.save("vie-cmd2", airportIdOfVie(), input("€11"), null, "admin", false);
        long brId = writeMapper.findBusId("vie-cmd2");
        int v1 = writeMapper.selectVersionHash("vie-cmd2").version();
        BusView v = cmd.save("vie-cmd2", airportIdOfVie(), input("€13"), v1, "admin", false);
        assertThat(v.version()).isEqualTo(v1 + 1);
        assertThat(versionMapper.listVersions(brId)).hasSize(2);
    }

    @Test void update_unchanged_isNoop() {
        cmd.save("vie-cmd3", airportIdOfVie(), input("€11"), null, "admin", false);
        int v1 = writeMapper.selectVersionHash("vie-cmd3").version();
        long brId = writeMapper.findBusId("vie-cmd3");
        BusView v = cmd.save("vie-cmd3", airportIdOfVie(), input("€11"), v1, "admin", false);
        assertThat(v.version()).isEqualTo(v1);              // 不升
        assertThat(versionMapper.listVersions(brId)).hasSize(1); // 不快照
    }

    @Test void update_staleVersion_conflicts409() {
        cmd.save("vie-cmd4", airportIdOfVie(), input("€11"), null, "admin", false);
        assertThatThrownBy(() -> cmd.save("vie-cmd4", airportIdOfVie(), input("€13"), 999, "admin", false))
                .isInstanceOf(ApiException.class)
                .extracting("code").hasToString("BUS_VERSION_CONFLICT");
    }

    @Test void verify_setsTimestamp_noVersionBump_noSnapshot() {
        cmd.save("vie-cmd5", airportIdOfVie(), input("€11"), null, "admin", false);
        long brId = writeMapper.findBusId("vie-cmd5");
        int v1 = writeMapper.selectVersionHash("vie-cmd5").version();
        cmd.verify("vie-cmd5", "admin");
        assertThat(writeMapper.selectVersionHash("vie-cmd5").version()).isEqualTo(v1);
        assertThat(versionMapper.listVersions(brId)).hasSize(1);
    }

    @Test void delete_softDeletes() {
        cmd.save("vie-cmd6", airportIdOfVie(), input("€11"), null, "admin", false);
        cmd.delete("vie-cmd6", "admin");
        assertThat(writeMapper.selectVersionHash("vie-cmd6")).isNull(); // 读路径排除 deleted
    }

    @Test void rollback_restoresAsNewVersion() {
        cmd.save("vie-cmd7", airportIdOfVie(), input("€11"), null, "admin", false);
        int v1 = writeMapper.selectVersionHash("vie-cmd7").version();
        cmd.save("vie-cmd7", airportIdOfVie(), input("€99"), v1, "admin", false); // v2
        BusView rolled = cmd.rollback("vie-cmd7", v1, "admin");                    // 回 v1 内容
        assertThat(rolled.data().price()).isEqualTo("€11");
        assertThat(rolled.version()).isEqualTo(3); // 追加为新版本
        long brId = writeMapper.findBusId("vie-cmd7");
        assertThat(versionMapper.listVersions(brId)).hasSize(3);
    }
}
```

- [ ] **Step 2: 运行确认失败**
Run: `cd backend && mvn -q -Dtest=BusCommandServiceIT test` → 编译失败(save/verify/delete/rollback 未定义、类无 @Service)。

- [ ] **Step 3: 实现实例方法**(在 Task 5 的 `BusCommandService` 类里补全:加注解、字段、构造、方法)

把类声明改为带依赖的 `@Service`,并加入下列字段/构造/方法(保留 Task 5 的静态辅助):
```java
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
// (类上加 @Service;构造注入下列 4 个 bean)

    private final BusWriteMapper writeMapper;
    private final BusVersionMapper versionMapper;
    private final BusQueryService busQuery;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules(); // 支持 LocalDate

    public BusCommandService(BusWriteMapper writeMapper, BusVersionMapper versionMapper,
                             BusQueryService busQuery, ApplicationEventPublisher events) {
        this.writeMapper = writeMapper; this.versionMapper = versionMapper;
        this.busQuery = busQuery; this.events = events;
    }

    /** 保存(创建或更新)。create 时 expectedVersion 传 null。 */
    @Transactional
    public BusView save(String sourceId, long airportId, BusInput input,
                        Integer expectedVersion, String actor, boolean suppressEvents) {
        if (input.route() == null || input.route().isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "route required");

        String newHash = com.airportbus.bus.hash.Canonicalizer.contentHash(toCanonical(input));
        BusWriteMapper.VersionHash cur = writeMapper.selectVersionHash(sourceId);

        long busRouteId;
        int newVersion;
        ChangedSummary summary;
        String oldHash;

        if (cur == null) {
            // 创建:先插入(version 默认 0)再置 version=1
            Map<String, Object> row = busRow(airportId, sourceId, input, newHash);
            writeMapper.insertBus(row);
            busRouteId = toLong(row.get("id"));
            replaceChildren(busRouteId, input);
            newVersion = 1;
            // 设 version=1(updateBusFull 复用)
            Map<String, Object> up = busRow(airportId, sourceId, input, newHash);
            up.put("id", busRouteId); up.put("newVersion", newVersion); up.put("actor", actor);
            writeMapper.updateBusFull(up);
            oldHash = null;
            summary = diff(emptyInput(), input);
            writeSnapshot(busRouteId, newVersion, input, newHash, summary, actor);
            if (!suppressEvents) events.publishEvent(new BusUpdatedEvent(busRouteId, sourceId, oldHash, newHash, summary));
            return view(sourceId, airportId, newVersion, input);
        }

        // 更新:乐观锁
        if (expectedVersion != null && expectedVersion != cur.version())
            throw new ApiException(ErrorCode.BUS_VERSION_CONFLICT, "version conflict");
        busRouteId = writeMapper.findBusId(sourceId);
        oldHash = cur.contentHash();

        if (newHash.equals(oldHash)) {
            // 幂等:无变更,不升版本、不快照、不发事件;仍可刷新可写字段(此处直接返回当前)
            return view(sourceId, airportId, cur.version(), input);
        }

        BusInput oldInput = toInput(busQuery.detail(sourceId)); // 旧值用于 diff
        newVersion = cur.version() + 1;
        Map<String, Object> up = busRow(airportId, sourceId, input, newHash);
        up.put("id", busRouteId); up.put("newVersion", newVersion); up.put("actor", actor);
        writeMapper.updateBusFull(up);
        replaceChildren(busRouteId, input);
        summary = diff(oldInput, input);
        writeSnapshot(busRouteId, newVersion, input, newHash, summary, actor);
        if (!suppressEvents) events.publishEvent(new BusUpdatedEvent(busRouteId, sourceId, oldHash, newHash, summary));
        return view(sourceId, airportId, newVersion, input);
    }

    @Transactional
    public void verify(String sourceId, String actor) {
        if (writeMapper.selectVersionHash(sourceId) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        writeMapper.updateVerify(sourceId, LocalDateTime.now(), actor);
    }

    @Transactional
    public void delete(String sourceId, String actor) {
        if (writeMapper.selectVersionHash(sourceId) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        writeMapper.softDeleteBus(sourceId, actor);
    }

    @Transactional
    public BusView rollback(String sourceId, int targetVersion, String actor) {
        long busRouteId = writeMapper.findBusId(sourceId);
        String snap = versionMapper.selectSnapshotJson(busRouteId, targetVersion);
        if (snap == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId + "@v" + targetVersion);
        BusInput restored = readSnapshot(snap);
        int airportId = airportIdOf(sourceId);
        int cur = writeMapper.selectVersionHash(sourceId).version();
        return save(sourceId, airportId, restored, cur, actor, false);
    }

    // —— 辅助 ——
    private int airportIdOf(String sourceId) {
        // 经 detail 拿不到 airportId;用一次查询(沿用 BusQueryMapper.selectAirportIdByCode? 这里直接读 bus_route.airport_id)
        return writeMapper.selectAirportIdBySource(sourceId);
    }

    private Map<String, Object> busRow(long airportId, String sourceId, BusInput in, String hash) {
        Map<String, Object> row = new HashMap<>();
        row.put("airportId", airportId); row.put("sourceId", sourceId);
        row.put("route", in.route()); row.put("destination", in.destination());
        row.put("operator", in.operator()); row.put("officialUrl", in.officialUrl());
        row.put("duration", in.duration()); row.put("price", in.price());
        row.put("operatingHours", in.operatingHours()); row.put("lastUpdated", in.lastUpdated());
        row.put("fetchFailed", false); row.put("contentHash", hash);
        return row;
    }

    private void replaceChildren(long busId, BusInput in) {
        writeMapper.deleteStops(busId); writeMapper.deleteSchedules(busId);
        writeMapper.deleteImages(busId); writeMapper.deleteFiles(busId); writeMapper.deleteAlerts(busId);
        int seq = 0;
        for (String s : nz2(in.stops())) writeMapper.insertStop(busId, seq++, s);
        for (var sc : nz2(in.schedules())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId); r.put("timeRange", sc.timeRange());
            r.put("intervalText", sc.intervalText()); r.put("note", sc.note());
            writeMapper.insertSchedule(r);
        }
        for (var im : nz2(in.images())) writeMapper.insertImage(busId, im.url(), im.caption());
        for (var f : nz2(in.files())) writeMapper.insertFile(busId, f.name(), f.url());
        for (var a : nz2(in.alerts())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId); r.put("type", a.type()); r.put("message", a.message());
            r.put("startDate", a.startDate()); r.put("endDate", a.endDate());
            writeMapper.insertAlert(r);
        }
    }

    private void writeSnapshot(long busRouteId, int version, BusInput in, String hash, ChangedSummary summary, String actor) {
        Map<String, Object> row = new HashMap<>();
        row.put("busRouteId", busRouteId); row.put("version", version);
        row.put("snapshotJson", writeJson(in)); row.put("contentHash", hash);
        row.put("changedSummary", writeJson(summary)); row.put("actor", actor);
        versionMapper.insertSnapshot(row);
    }

    private BusView view(String sourceId, long airportId, int version, BusInput in) {
        return new BusView(sourceId, writeMapper.selectAirportCodeById(airportId), version, null, in);
    }

    private BusInput toInput(BusDetailDto d) {
        return new BusInput(d.route(), d.destination(), d.operator(), d.officialUrl(),
                d.duration(), d.price(), d.operatingHours(), d.lastUpdated(),
                d.stops(), d.schedules(), d.alerts(), d.images(), d.files());
    }
    private static BusInput emptyInput() {
        return new BusInput(null, null, null, null, null, null, null, null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
    private String writeJson(Object o) { try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private BusInput readSnapshot(String s) { try { return json.readValue(s, BusInput.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static <T> java.util.List<T> nz2(java.util.List<T> in) { return in == null ? java.util.List.of() : in; }
    private static Long toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
```

- [ ] **Step 4: 给 BusWriteMapper 补两个小读方法**(本任务用到)
`BusWriteMapper.java` 加:
```java
    int selectAirportIdBySource(@Param("sourceId") String sourceId);
    String selectAirportCodeById(@Param("airportId") long airportId);
```
`BusWriteMapper.xml` 加:
```xml
  <select id="selectAirportIdBySource" resultType="int">
    SELECT a.id FROM bus_route b JOIN airport a ON a.id=b.airport_id
    WHERE b.source_id=#{sourceId} AND b.deleted=0
  </select>
  <select id="selectAirportCodeById" resultType="string">
    SELECT code FROM airport WHERE id=#{airportId}
  </select>
```

- [ ] **Step 5: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=BusCommandServiceIT test` → PASS(7)。
(若 `BusView.lastVerifiedAt` 在 view() 里恒为 null 导致语义不足,可在 Task 8 的 GET 端点用单独查询补;本服务返回的 view 主要给写端点回包,lastVerifiedAt 非关键。)

- [ ] **Step 6: 提交**
```bash
git add backend/src/main/java/com/airportbus/bus/service/BusCommandService.java \
        backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java \
        backend/src/main/resources/mapper/BusWriteMapper.xml \
        backend/src/test/java/com/airportbus/bus/service/BusCommandServiceIT.java
git commit -m "feat(bus): BusCommandService save/verify/delete/rollback + 乐观锁/快照/事件 (#7A)"
```

---

## Task 7: SeedImporter 委托 BusCommandService(E5/E11)

**Files:** Modify `bus/seed/SeedImporter.java`;Test: 复跑 `SeedImporterIT`

- [ ] **Step 1: 改 `upsertBus` 委托**——把 `SeedImporter.upsertBus(airportId, bus)` 改为构造 `BusInput` 后调 `busCommand.save(bus.id(), airportId, input, null, "seed", true)`,删除其内联的 row/hash/replaceChildren 逻辑(`toCanonical` 可删,逻辑已在 BusCommandService)。注入 `BusCommandService`。

新 `upsertBus`:
```java
    private void upsertBus(Long airportId, SeedDtos.Bus bus) {
        com.airportbus.bus.api.dto.BusInput input = new com.airportbus.bus.api.dto.BusInput(
                bus.route(), bus.destination(), bus.operator(), bus.officialUrl(),
                bus.duration(), bus.price(), bus.operatingHours(), parseDate(bus.lastUpdated()),
                nz(bus.stops()),
                bus.schedules() == null ? java.util.List.of() : bus.schedules().stream()
                        .map(s -> new com.airportbus.bus.api.dto.BusDetailDto.Schedule(s.timeRange(), s.interval(), s.note())).toList(),
                bus.alerts() == null ? java.util.List.of() : bus.alerts().stream()
                        .map(a -> new com.airportbus.bus.api.dto.BusDetailDto.Alert(a.type(), a.message(), parseDate(a.startDate()), parseDate(a.endDate()))).toList(),
                bus.images() == null ? java.util.List.of() : bus.images().stream()
                        .map(im -> new com.airportbus.bus.api.dto.BusDetailDto.Image(im.url(), im.caption())).toList(),
                bus.files() == null ? java.util.List.of() : bus.files().stream()
                        .map(f -> new com.airportbus.bus.api.dto.BusDetailDto.FileRef(f.name(), f.url())).toList());
        busCommand.save(bus.id(), airportId, input, null, "seed", true); // E11 抑制事件
    }
```
注入:构造加 `BusCommandService busCommand` 字段;删除 `toCanonical`、`replaceChildren`(已迁移)与不再用的 import。保留 country/city/airport upsert。

- [ ] **Step 2: 运行确认导入无回归**
Run: `cd backend && mvn -q -Dtest=SeedImporterIT test` → PASS。
> 关键:首跑创建 v1 快照;`SeedImporterIT` 若断言「再次导入幂等不新增行」,因 save 对未变 hash 是 no-op,应仍成立。若 IT 断言 updated 行为变化,按实际调整断言并说明。

- [ ] **Step 3: 回归 hash 一致性**
Run: `cd backend && mvn -q -Dtest=SearchHotnessServiceIT,BusQueryServiceIT test` → PASS(确认查询侧不受影响)。

- [ ] **Step 4: 提交**
```bash
git add backend/src/main/java/com/airportbus/bus/seed/SeedImporter.java
git commit -m "refactor(bus): SeedImporter 委托 BusCommandService(E5/E11) (#7A)"
```

---

## Task 8: 审计模块 —— @Audited + AuditAspect + audit_log

**Files:** Create `audit/Audited.java`、`audit/AuditAspect.java`、`audit/AuditMapper.java`(+xml)、`audit/AuditService.java`;Test `audit/AuditAspectIT.java`

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/audit/AuditAspectIT.java`
```java
package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "airportbus.seed.enabled=true", "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class AuditAspectIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired AuditService auditService;
    @Autowired Probe probe;

    @AfterEach void cleanup() { CurrentUser.clear(); }

    /** 测试用被切面拦截的 bean。 */
    @Service static class Probe {
        @Audited(action = "UPDATE_BUS", target = "bus")
        public void touch(String sourceId) { /* no-op */ }
    }

    @Test void aspect_writesAuditRow_withActorFromCurrentUser() {
        long before = auditService.list(null, null, 50).size();
        CurrentUser.set(new JwtPrincipal(42L, "SUPER_ADMIN"));
        probe.touch("vie-vab1");
        var rows = auditService.list(null, null, 50);
        assertThat(rows.size()).isEqualTo(before + 1);
        assertThat(rows.get(0).action()).isEqualTo("UPDATE_BUS");
        assertThat(rows.get(0).targetId()).isEqualTo("vie-vab1");
        assertThat(rows.get(0).actorId()).isEqualTo(42L);
    }
}
```

- [ ] **Step 2: 运行确认失败** → 编译失败(类型不存在)。

- [ ] **Step 3: 写注解** `audit/Audited.java`
```java
package com.airportbus.audit;
import java.lang.annotation.*;
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface Audited { String action(); String target() default "bus"; }
```

- [ ] **Step 4: 写行模型 + mapper** `audit/AuditMapper.java`
```java
package com.airportbus.audit;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface AuditMapper {
    void insert(java.util.Map<String, Object> row); // actorId,actorType,action,targetType,targetId,summary,ip
    List<Row> list(@Param("actor") Long actorId, @Param("action") String action, @Param("limit") int limit);
    record Row(long id, long actorId, String actorType, String action, String targetType,
               String targetId, String summary, String ip, java.time.LocalDateTime createdAt) {}
}
```
`resources/mapper/AuditMapper.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.audit.AuditMapper">
  <insert id="insert" parameterType="map">
    INSERT INTO audit_log(actor_id, actor_type, action, target_type, target_id, summary, ip, created_by)
    VALUES(#{actorId}, #{actorType}, #{action}, #{targetType}, #{targetId}, #{summary}, #{ip}, #{actorType})
  </insert>
  <select id="list" resultType="com.airportbus.audit.AuditMapper$Row">
    SELECT id, actor_id AS actorId, actor_type AS actorType, action, target_type AS targetType,
           target_id AS targetId, summary, ip, created_at AS createdAt
    FROM audit_log
    WHERE deleted=0
    <if test="actor != null"> AND actor_id=#{actor} </if>
    <if test="action != null"> AND action=#{action} </if>
    ORDER BY created_at DESC, id DESC
    LIMIT #{limit}
  </select>
</mapper>
```
> `@MapperScan` 当前为 `{"com.airportbus.bus.mapper","com.airportbus.user.mapper"}`,**需加** `"com.airportbus.audit"`。在 `AirportbusApplication` 的 `@MapperScan` 数组加该包。

- [ ] **Step 5: 写 AuditService**
```java
package com.airportbus.audit;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class AuditService {
    private final AuditMapper mapper;
    public AuditService(AuditMapper mapper) { this.mapper = mapper; }
    public void record(long actorId, String action, String targetType, String targetId, String summary, String ip) {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("actorId", actorId); row.put("actorType", "ADMIN"); row.put("action", action);
        row.put("targetType", targetType); row.put("targetId", targetId);
        row.put("summary", summary); row.put("ip", ip);
        mapper.insert(row);
    }
    public List<AuditMapper.Row> list(Long actorId, String action, int limit) {
        return mapper.list(actorId, action, limit < 1 ? 100 : Math.min(limit, 500));
    }
}
```

- [ ] **Step 6: 写切面** `audit/AuditAspect.java`
```java
package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {
    private final AuditService audit;
    public AuditAspect(AuditService audit) { this.audit = audit; }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed(); // 仅成功后记录
        JwtPrincipal me = CurrentUser.require(); // actor 来自上下文,绝不信请求体(E10)
        audit.record(me.userId(), audited.action(), audited.target(), sourceIdArg(pjp), null, clientIp());
        return result;
    }

    private static String sourceIdArg(ProceedingJoinPoint pjp) {
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) for (int i = 0; i < names.length; i++)
            if ("sourceId".equals(names[i]) && args[i] != null) return args[i].toString();
        return null;
    }
    private static String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            String xff = sra.getRequest().getHeader("X-Forwarded-For");
            return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : sra.getRequest().getRemoteAddr();
        }
        return null;
    }
}
```
> 需要参数名 `sourceId` 可反射到:Spring Boot 默认 `-parameters` 编译(spring-boot-starter-parent 已配),正常可取到。`@EnableAspectJAutoProxy` 由 Spring Boot AOP 自动配置(`spring-boot-starter-aop` 若未引入需加依赖——见 Step 7)。

- [ ] **Step 7: 确保 AOP 依赖**——若 `pom.xml` 无 `spring-boot-starter-aop`,添加它(`spring-boot-starter-web` 不传递 AOP)。检查 `backend/pom.xml`,缺则加:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```

- [ ] **Step 8: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=AuditAspectIT test` → PASS(1)。

- [ ] **Step 9: 提交**
```bash
git add backend/src/main/java/com/airportbus/audit/ backend/src/main/resources/mapper/AuditMapper.xml \
        backend/src/main/java/com/airportbus/AirportbusApplication.java backend/pom.xml \
        backend/src/test/java/com/airportbus/audit/AuditAspectIT.java
git commit -m "feat(audit): @Audited + AuditAspect + audit_log 写入/查询 (#7A)"
```

---

## Task 9: AdminBusController + 审计接线 + API IT

**Files:** Create `admin/api/AdminBusController.java`、`admin/api/dto/CreateBusRequest.java`、`admin/api/dto/UpdateBusRequest.java`;Test `admin/api/AdminBusApiIT.java`

- [ ] **Step 1: 写请求 DTO**
```java
// admin/api/dto/CreateBusRequest.java
package com.airportbus.admin.api.dto;
import com.airportbus.bus.api.dto.BusInput;
public record CreateBusRequest(String sourceId, String airportCode, BusInput data) {}
```
```java
// admin/api/dto/UpdateBusRequest.java
package com.airportbus.admin.api.dto;
import com.airportbus.bus.api.dto.BusInput;
public record UpdateBusRequest(String airportCode, int version, BusInput data) {}
```

- [ ] **Step 2: 写失败测试** `backend/src/test/java/com/airportbus/admin/api/AdminBusApiIT.java`
（覆盖:匿名 401;OPERATOR 能 PUT 改、DELETE 403;SUPER_ADMIN 能 DELETE;版本冲突 409;verify;versions 列表 + rollback。沿用 #7a `AdminStatsApiIT` 的 token 法:种子 `admin/admin12345` 是 SUPER_ADMIN;OPERATOR 用直插用户造。）
```java
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
    @Autowired UserMapper users; @Autowired JwtService jwt;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private String superToken() throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"admin\",\"password\":\"admin12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }
    /** 直插一个 OPERATOR 并签发 token(JwtService.issueAccess(userId, role))。 */
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
}
```

- [ ] **Step 3: 运行确认失败** → 404/编译失败(控制器不存在)。

- [ ] **Step 4: 写控制器** `admin/api/AdminBusController.java`
```java
package com.airportbus.admin.api;

import com.airportbus.audit.Audited;
import com.airportbus.admin.api.dto.CreateBusRequest;
import com.airportbus.admin.api.dto.UpdateBusRequest;
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-bus", description = "巴士维护(管理员)")
@RestController
@RequestMapping("/api/v1/admin/buses")
public class AdminBusController {

    private final BusCommandService cmd;
    private final BusWriteMapper writeMapper;
    private final BusVersionMapper versionMapper;

    public AdminBusController(BusCommandService cmd, BusWriteMapper writeMapper, BusVersionMapper versionMapper) {
        this.cmd = cmd; this.writeMapper = writeMapper; this.versionMapper = versionMapper;
    }

    @GetMapping("/tree")
    public List<com.airportbus.bus.mapper.BusQueryMapper.TreeRow> tree() {
        CurrentUser.requireAdmin();
        return writeMapper.selectAdminTree(); // 见下 Step 5
    }

    @PostMapping
    @Audited(action = "CREATE_BUS")
    public BusView create(@RequestBody CreateBusRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        long airportId = resolveAirport(req.airportCode());
        return cmd.save(req.sourceId(), airportId, req.data(), null, actor(me), false);
    }

    @PutMapping("/{sourceId}")
    @Audited(action = "UPDATE_BUS")
    public BusView update(@PathVariable String sourceId, @RequestBody UpdateBusRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        long airportId = resolveAirport(req.airportCode());
        return cmd.save(sourceId, airportId, req.data(), req.version(), actor(me), false);
    }

    @PostMapping("/{sourceId}/verify")
    @Audited(action = "VERIFY_BUS")
    public void verify(@PathVariable String sourceId) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        cmd.verify(sourceId, actor(me));
    }

    @DeleteMapping("/{sourceId}")
    @Audited(action = "DELETE_BUS")
    public void delete(@PathVariable String sourceId) {
        JwtPrincipal me = CurrentUser.requireSuperAdmin();
        cmd.delete(sourceId, actor(me));
    }

    @GetMapping("/{sourceId}/versions")
    public List<BusVersionMapper.Meta> versions(@PathVariable String sourceId) {
        CurrentUser.requireAdmin();
        return versionMapper.listVersions(busId(sourceId));
    }

    @PostMapping("/{sourceId}/versions/{version}/rollback")
    @Audited(action = "ROLLBACK_BUS")
    public BusView rollback(@PathVariable String sourceId, @PathVariable int version) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return cmd.rollback(sourceId, version, actor(me));
    }

    // GET 单条编辑视图(含子表 + version + 核对时间):复用查询侧 detail 装配 + 补 version/airportCode
    @GetMapping("/{sourceId}")
    public BusView get(@PathVariable String sourceId) {
        CurrentUser.requireAdmin();
        var vh = writeMapper.selectVersionHash(sourceId);
        if (vh == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        return cmd.viewFor(sourceId); // 见下 Step 5
    }

    private long resolveAirport(String code) {
        Long id = writeMapper.findAirportId(code);
        if (id == null) throw new ApiException(ErrorCode.AIRPORT_NOT_FOUND, code);
        return id;
    }
    private long busId(String sourceId) {
        Long id = writeMapper.findBusId(sourceId);
        if (id == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        return id;
    }
    private static String actor(JwtPrincipal me) { return "admin:" + me.userId(); }
}
```

- [ ] **Step 5: 补两个支持方法**
  - `BusCommandService.viewFor(sourceId)`:读 detail + version + lastVerifiedAt 组 `BusView`(public)。
```java
    public BusView viewFor(String sourceId) {
        var d = busQuery.detail(sourceId);
        var vh = writeMapper.selectVersionHash(sourceId);
        var meta = writeMapper.selectAdminMeta(sourceId); // airportCode + lastVerifiedAt
        return new BusView(sourceId, meta.airportCode(), vh.version(), meta.lastVerifiedAt(), toInput(d));
    }
```
  - `BusWriteMapper`:`selectAdminTree()`(国家/城市/机场/线路 行)与 `selectAdminMeta(sourceId)`。
```java
    List<com.airportbus.bus.mapper.BusQueryMapper.TreeRow> selectAdminTree();
    AdminMeta selectAdminMeta(@Param("sourceId") String sourceId);
    record AdminMeta(String airportCode, java.time.LocalDateTime lastVerifiedAt) {}
```
xml:
```xml
  <select id="selectAdminTree" resultType="com.airportbus.bus.mapper.BusQueryMapper$TreeRow">
    SELECT c.code AS countryCode, c.name AS countryName, ci.name AS cityName,
           a.code AS airportCode, a.name AS airportName, b.source_id AS busSourceId, b.route AS busRoute
    FROM country c JOIN city ci ON ci.country_id=c.id
    JOIN airport a ON a.city_id=ci.id
    LEFT JOIN bus_route b ON b.airport_id=a.id AND b.deleted=0
    WHERE c.deleted=0 AND ci.deleted=0 AND a.deleted=0
    ORDER BY c.name, ci.name, a.name, b.route
  </select>
  <select id="selectAdminMeta" resultType="com.airportbus.bus.mapper.BusWriteMapper$AdminMeta">
    SELECT a.code AS airportCode, b.last_verified_at AS lastVerifiedAt
    FROM bus_route b JOIN airport a ON a.id=b.airport_id
    WHERE b.source_id=#{sourceId} AND b.deleted=0
  </select>
```
> `BusQueryMapper.TreeRow` 当前字段为 countryCode/countryName/cityName/airportCode/airportName(见 BusQueryMapper.xml)。**需要给它加** `busSourceId`/`busRoute` 两个可空字段(record 加两参),或新建 `admin/AdminTreeRow`。**决策**:新建 `bus/mapper/BusWriteMapper$AdminTreeRow`(避免动查询侧 TreeRow),`selectAdminTree` resultType 用它,字段 countryCode/countryName/cityName/airportCode/airportName/busSourceId/busRoute。把上面 `tree()`/`selectAdminTree()` 的返回类型改为 `BusWriteMapper.AdminTreeRow`。

- [ ] **Step 6: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=AdminBusApiIT test` → PASS(6)。

- [ ] **Step 7: 提交**
```bash
git add backend/src/main/java/com/airportbus/admin/ \
        backend/src/main/java/com/airportbus/bus/service/BusCommandService.java \
        backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java \
        backend/src/main/resources/mapper/BusWriteMapper.xml \
        backend/src/test/java/com/airportbus/admin/api/AdminBusApiIT.java
git commit -m "feat(admin): AdminBusController CRUD/verify/delete/versions/rollback + 审计 (#7A)"
```

---

## Task 10: AuditQueryController + IT

**Files:** Create `audit/AuditQueryController.java`;Test `audit/AuditApiIT.java`

- [ ] **Step 1: 写失败测试** `backend/src/test/java/com/airportbus/audit/AuditApiIT.java`
```java
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
```

- [ ] **Step 2: 运行确认失败** → 404。

- [ ] **Step 3: 写控制器** `audit/AuditQueryController.java`
```java
package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "admin-audit", description = "操作记录(管理员)")
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditQueryController {
    private final AuditService audit;
    public AuditQueryController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public List<AuditMapper.Row> list(@RequestParam(required = false) Long actor,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(defaultValue = "100") int limit) {
        CurrentUser.requireAdmin();
        return audit.list(actor, action, limit);
    }
}
```

- [ ] **Step 4: 运行确认通过**
Run: `cd backend && mvn -q -Dtest=AuditApiIT test` → PASS。

- [ ] **Step 5: 提交**
```bash
git add backend/src/main/java/com/airportbus/audit/AuditQueryController.java \
        backend/src/test/java/com/airportbus/audit/AuditApiIT.java
git commit -m "feat(audit): GET /admin/audit 列表+过滤 (#7A)"
```

---

## Task 11: 全量后端 IT 回归

- [ ] **Step 1: 跑全套**
Run:
```bash
cd backend && mvn -q -Dtest=CurrentUserTest,CurrentUserSuperAdminTest,BusDiffTest,BusCommandServiceIT,SeedImporterIT,SearchHotnessServiceIT,BusQueryServiceIT,HotnessRankingIT,AuthFlowIT,AuthServiceIT,AuthCacheServiceIT,FavoriteServiceIT,FavoriteApiIT,UserStatsServiceIT,FavoriteStatsServiceIT,AdminStatsApiIT,AuditAspectIT,AdminBusApiIT,AuditApiIT test
```
Expected: 全 PASS。失败则读 surefire 报告定位、修复、重跑。

- [ ] **Step 2: 提交(若有修复)**
```bash
git add -A && git commit -m "test(#7A): 后端全量 IT 回归通过"
```

---

## 自审清单(写计划者已核对)

- **spec 覆盖**:迁移(T1)、requireSuperAdmin/409(T2)、DTO(T3)、mapper(T4)、diff(T5)、save/verify/delete/rollback+乐观锁+快照+事件(T6)、导入器委托 E5/E11(T7)、审计切面 E10(T8)、admin API+矩阵(T9)、审计查询(T10)、回归(T11)。✅
- **类型一致**:`BusInput`/`BusView`/`ChangedSummary`/`BusUpdatedEvent`/`BusWriteMapper.VersionHash/AdminMeta/AdminTreeRow`/`BusVersionMapper.Meta`/`AuditMapper.Row` 全程一致引用。✅
- **占位**:T10 测试给了断言清单而非完整代码(实现者按 AdminBusApiIT 同款辅助补全)——已注明依据,可接受。其余均完整代码。
- **风险点**:`-parameters` 编译(取 `sourceId` 参数名)——spring-boot-starter-parent 默认开启;若取不到,改用 `@Audited` 显式传 SpEL/索引(实现者遇到再处理)。`spring-boot-starter-aop` 依赖须在(T8 Step 7 检查)。
