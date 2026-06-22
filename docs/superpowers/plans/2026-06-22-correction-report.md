# 匿名纠错上报 实现计划(#7 切片 C · C1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development(推荐)或 superpowers:executing-plans 逐任务实现。步骤用 checkbox(`- [ ]`)。

**Goal:** 交付匿名数据纠错上报:任何旅客在线路详情零登录一键上报「信息有误」(线路 + 问题描述 + 可选联系方式),后端公开 `POST /corrections`(Redis 按 IP 限流),管理员后台队列处理(状态流转 + 内部备注)。

**Architecture:** 新模块 `com.airportbus.ticket`(本期只放 correction 部分,C2 工单复用同模块)。公开端点靠既有 `JwtAuthFilter`「无 token 不拦截」天然支持(控制器不调 `CurrentUser.require()` 即公开)。前端:bus-detail 加 `.overlay/.modal` 纠错框(tokens.css 已有这些类)、admin 加 `AdminCorrectionsPage`(Element Plus 表)。

**Tech Stack:** Spring Boot + MyBatis(`#{}` only)+ Redis(StringRedisTemplate)+ Testcontainers;Vue 3 + Pinia + vue-i18n + Element Plus(仅 admin)+ Vitest。

## 上游与约束
- spec:`docs/superpowers/specs/2026-06-22-feedback-correction-ticket-design.md` §2.1 §4.1 §5 §6.1 §8。
- 复用:`common/ErrorCode`(已有 `RATE_LIMITED`/`VALIDATION_FAILED`/`BUS_NOT_FOUND`/`ADMIN_FORBIDDEN`)、`common/ApiException`、`user/security/CurrentUser`(`requireAdmin()`)、`audit/@Audited`、`bus/mapper/BusWriteMapper.selectVersionHash(sourceId)`(存在性校验,返回 null 即不存在)、`AuthCacheService` 的 Redis 限流写法、`frontend/src/api/client.ts`(`http`)、`frontend/src/pages/BusDetailPage.vue`、admin 页模式(`pages/admin/AdminSubscriptionsPage.vue` + `router/index.ts` admin children + `api/admin.ts`)、tokens.css `.overlay/.modal/.modalSub/.modalClose/.modalActions/.reportTrigger`(已存在)。
- 迁移每列带 COMMENT(表也带);所有查询排除 `deleted=1`。
- `@WebMvcTest` 切片要 `@MockBean` 所有被 `@MapperScan` 扫到的 mapper(否则上下文起不来)。`*IT` 用 `mvn -Dtest=… test` 跑(surefire 默认不含 `*IT`),带 `management.health.redis.enabled=false` + Testcontainers。

## 文件结构
- Backend Create:`backend/src/main/resources/db/migration/V9__correction_report.sql`、`backend/src/main/java/com/airportbus/ticket/CorrectionReport.java`、`ticket/mapper/CorrectionMapper.java`、`backend/src/main/resources/mapper/CorrectionMapper.xml`、`ticket/CorrectionRateLimiter.java`、`ticket/CorrectionService.java`、`ticket/api/CorrectionController.java`、`ticket/api/AdminCorrectionController.java`、`ticket/api/dto/CorrectionDtos.java`
- Backend Modify:`common/ErrorCode.java`(加 `CORRECTION_NOT_FOUND`)
- Backend Test:`backend/src/test/java/com/airportbus/ticket/CorrectionServiceIT.java`、`ticket/api/CorrectionControllerTest.java`
- Frontend Create:`frontend/src/api/corrections.ts`、`frontend/src/components/ReportModal.vue`、`frontend/src/pages/admin/AdminCorrectionsPage.vue`
- Frontend Modify:`frontend/src/pages/BusDetailPage.vue`、`frontend/src/api/admin.ts`、`frontend/src/router/index.ts`、`frontend/src/components/admin/AdminLayout.vue`(加导航项)、`frontend/src/i18n/locales/{zh-CN,en,de}.ts`(加 `report` + admin 文案)
- Frontend Test:`frontend/src/test/corrections.api.spec.ts`、`ReportModal.spec.ts`、`AdminCorrectionsPage.spec.ts`

---

## Task 1: V9 迁移 `correction_report`

**Files:** Create `backend/src/main/resources/db/migration/V9__correction_report.sql`

- [ ] **Step 1: 写迁移**(对齐 V8 风格,每列 COMMENT)
```sql
-- V9__correction_report.sql  匿名数据纠错上报(零登录;管理员后台处理)
CREATE TABLE correction_report (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT          COMMENT '主键',
  related_source_id VARCHAR(64)  NULL                                COMMENT '关联线路业务键source_id(可空;填了由服务端校验存在)',
  description       TEXT         NOT NULL                            COMMENT '问题描述(旅客填,必填)',
  contact           VARCHAR(128) NULL                                COMMENT '联系方式(可选,邮箱/电话;本期不外发)',
  status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN'             COMMENT '状态:OPEN/RESOLVED/DISMISSED',
  resolution_note   TEXT         NULL                                COMMENT '管理员内部处理备注',
  reporter_ip       VARCHAR(64)  NULL                                COMMENT '上报来源IP(限流/审计;admin可见)',
  created_by        VARCHAR(64)  NULL                                COMMENT '创建人(匿名为anonymous)',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  updated_by        VARCHAR(64)  NULL                                COMMENT '更新人(管理员用户名)',
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT(1)   NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  KEY idx_corr_status (status, deleted),
  KEY idx_corr_source (related_source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名数据纠错上报';
```

- [ ] **Step 2: 提交**
```bash
git add backend/src/main/resources/db/migration/V9__correction_report.sql
git commit -m "feat(correction): V9 correction_report 迁移 (#7 C1)"
```

---

## Task 2: 模型 + Mapper + xml

**Files:** Create `ticket/CorrectionReport.java`、`ticket/mapper/CorrectionMapper.java`、`resources/mapper/CorrectionMapper.xml`

- [ ] **Step 1: 写模型** `backend/src/main/java/com/airportbus/ticket/CorrectionReport.java`
```java
package com.airportbus.ticket;

import java.time.LocalDateTime;

/** 匿名纠错上报(对外资源)。 */
public class CorrectionReport {
    public long id;
    public String relatedSourceId;
    public String description;
    public String contact;
    public String status;
    public String resolutionNote;
    public String reporterIp;
    public LocalDateTime createdAt;
}
```

- [ ] **Step 2: 写 Mapper 接口** `backend/src/main/java/com/airportbus/ticket/mapper/CorrectionMapper.java`
```java
package com.airportbus.ticket.mapper;

import com.airportbus.ticket.CorrectionReport;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface CorrectionMapper {
    /** 插入;useGeneratedKeys 回填 row.get("id")。row: relatedSourceId,description,contact,reporterIp,createdBy。 */
    int insert(Map<String, Object> row);

    CorrectionReport selectById(@Param("id") long id);

    List<CorrectionReport> selectPage(@Param("status") String status,
                                      @Param("limit") int limit, @Param("offset") int offset);

    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("resolutionNote") String resolutionNote, @Param("updatedBy") String updatedBy);
}
```

- [ ] **Step 3: 写 xml** `backend/src/main/resources/mapper/CorrectionMapper.xml`(镜像 `MessageMapper.xml` 风格;`#{}` only)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.ticket.mapper.CorrectionMapper">

  <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO correction_report (related_source_id, description, contact, reporter_ip, created_by)
    VALUES (#{relatedSourceId}, #{description}, #{contact}, #{reporterIp}, #{createdBy})
  </insert>

  <select id="selectById" resultType="com.airportbus.ticket.CorrectionReport">
    SELECT id, related_source_id AS relatedSourceId, description, contact, status,
           resolution_note AS resolutionNote, reporter_ip AS reporterIp, created_at AS createdAt
    FROM correction_report WHERE id=#{id} AND deleted=0
  </select>

  <select id="selectPage" resultType="com.airportbus.ticket.CorrectionReport">
    SELECT id, related_source_id AS relatedSourceId, description, contact, status,
           resolution_note AS resolutionNote, reporter_ip AS reporterIp, created_at AS createdAt
    FROM correction_report
    WHERE deleted=0
    <if test="status != null and status != ''"> AND status=#{status} </if>
    ORDER BY created_at DESC, id DESC
    LIMIT #{limit} OFFSET #{offset}
  </select>

  <update id="updateStatus">
    UPDATE correction_report
    SET status=#{status}, resolution_note=#{resolutionNote}, updated_by=#{updatedBy}
    WHERE id=#{id} AND deleted=0
  </update>
</mapper>
```

- [ ] **Step 4: 提交**
```bash
git add backend/src/main/java/com/airportbus/ticket/CorrectionReport.java \
        backend/src/main/java/com/airportbus/ticket/mapper/CorrectionMapper.java \
        backend/src/main/resources/mapper/CorrectionMapper.xml
git commit -m "feat(correction): CorrectionReport 模型 + mapper + xml (#7 C1)"
```

---

## Task 3: CorrectionRateLimiter(Redis 按 IP 限流,故障放行)

**Files:** Create `ticket/CorrectionRateLimiter.java`

- [ ] **Step 1: 写限流器** `backend/src/main/java/com/airportbus/ticket/CorrectionRateLimiter.java`
```java
package com.airportbus.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 公开纠错上报按 IP 限流:窗口内超上限拒绝。Redis 故障放行(可用性优先)。 */
@Component
public class CorrectionRateLimiter {
    private final StringRedisTemplate redis;
    private final int windowSec;
    private final int max;

    public CorrectionRateLimiter(StringRedisTemplate redis,
                                 @Value("${airportbus.correction.rate-limit-window-sec:300}") int windowSec,
                                 @Value("${airportbus.correction.rate-limit-max:5}") int max) {
        this.redis = redis; this.windowSec = windowSec; this.max = max;
    }

    /** true=放行。ip 空或 Redis 异常一律放行。 */
    public boolean allow(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            String key = "corr:rl:" + ip;
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) redis.expire(key, Duration.ofSeconds(windowSec));
            return n == null || n <= max;
        } catch (Exception e) {
            return true; // 缓存故障不阻断公开上报
        }
    }
}
```

- [ ] **Step 2: 提交**
```bash
git add backend/src/main/java/com/airportbus/ticket/CorrectionRateLimiter.java
git commit -m "feat(correction): Redis 按 IP 限流器(故障放行) (#7 C1)"
```

---

## Task 4: ErrorCode + DTO + CorrectionService

**Files:** Modify `common/ErrorCode.java`;Create `ticket/api/dto/CorrectionDtos.java`、`ticket/CorrectionService.java`

- [ ] **Step 1: ErrorCode 加一码** —— 在 `common/ErrorCode.java` 枚举里(`BUS_VERSION_CONFLICT` 之后)加:
```java
    CORRECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
```

- [ ] **Step 2: 写 DTO** `backend/src/main/java/com/airportbus/ticket/api/dto/CorrectionDtos.java`
```java
package com.airportbus.ticket.api.dto;

public class CorrectionDtos {
    /** 公开上报请求。 */
    public record SubmitCorrectionRequest(String sourceId, String description, String contact) {}
    /** 管理员改状态。 */
    public record UpdateCorrectionRequest(String status, String resolutionNote) {}
}
```

- [ ] **Step 3: 写 Service** `backend/src/main/java/com/airportbus/ticket/CorrectionService.java`
```java
package com.airportbus.ticket;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
import com.airportbus.ticket.mapper.CorrectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 匿名纠错上报:公开提交(校验+限流)+ 管理员队列处理。 */
@Service
public class CorrectionService {
    private static final Set<String> VALID_STATUS = Set.of("OPEN", "RESOLVED", "DISMISSED");

    private final CorrectionMapper mapper;
    private final BusWriteMapper busWrite;
    private final CorrectionRateLimiter rateLimiter;

    public CorrectionService(CorrectionMapper mapper, BusWriteMapper busWrite, CorrectionRateLimiter rateLimiter) {
        this.mapper = mapper; this.busWrite = busWrite; this.rateLimiter = rateLimiter;
    }

    @Transactional
    public CorrectionReport submit(SubmitCorrectionRequest req, String ip) {
        if (req.description() == null || req.description().isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "description required");
        String src = (req.sourceId() == null || req.sourceId().isBlank()) ? null : req.sourceId();
        if (src != null && busWrite.selectVersionHash(src) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, src);
        if (!rateLimiter.allow(ip))
            throw new ApiException(ErrorCode.RATE_LIMITED, "too many reports, try later");
        Map<String, Object> row = new HashMap<>();
        row.put("relatedSourceId", src);
        row.put("description", req.description().trim());
        row.put("contact", (req.contact() == null || req.contact().isBlank()) ? null : req.contact().trim());
        row.put("reporterIp", ip);
        row.put("createdBy", "anonymous");
        mapper.insert(row);
        return mapper.selectById(((Number) row.get("id")).longValue());
    }

    public List<CorrectionReport> listForAdmin(String status, int limit, int offset) {
        return mapper.selectPage(status, limit, offset);
    }

    /** @Audited 不放这里:它会触发 CurrentUser.require(),而 IT 直调本方法无主体会抛错。审计放在控制器方法上(见 Task 5)。 */
    @Transactional
    public CorrectionReport updateStatus(long id, UpdateCorrectionRequest req, String adminUser) {
        if (req.status() == null || !VALID_STATUS.contains(req.status()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "bad status");
        int n = mapper.updateStatus(id, req.status(), req.resolutionNote(), adminUser);
        if (n == 0) throw new ApiException(ErrorCode.CORRECTION_NOT_FOUND, String.valueOf(id));
        return mapper.selectById(id);
    }
}
```

- [ ] **Step 3: 提交**
```bash
git add backend/src/main/java/com/airportbus/common/ErrorCode.java \
        backend/src/main/java/com/airportbus/ticket/api/dto/CorrectionDtos.java \
        backend/src/main/java/com/airportbus/ticket/CorrectionService.java
git commit -m "feat(correction): CorrectionService(提交校验+限流+管理员状态) (#7 C1)"
```

---

## Task 5: 控制器(公开 POST + 管理员 GET/PATCH)

**Files:** Create `ticket/api/CorrectionController.java`、`ticket/api/AdminCorrectionController.java`

- [ ] **Step 1: 公开控制器** `backend/src/main/java/com/airportbus/ticket/api/CorrectionController.java`
```java
package com.airportbus.ticket.api;

import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@Tag(name = "correction", description = "匿名数据纠错上报(零登录)")
@RestController
@RequestMapping("/api/v1/corrections")
public class CorrectionController {
    private final CorrectionService service;
    public CorrectionController(CorrectionService service) { this.service = service; }

    /** 公开:无需登录。JwtAuthFilter 无 token 不拦截,这里不调 CurrentUser.require()。 */
    @PostMapping
    public CorrectionReport submit(@RequestBody SubmitCorrectionRequest req, HttpServletRequest http) {
        return service.submit(req, clientIp(http));
    }

    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
```

- [ ] **Step 2: 管理员控制器** `backend/src/main/java/com/airportbus/ticket/api/AdminCorrectionController.java`
```java
package com.airportbus.ticket.api;

import com.airportbus.audit.Audited;
import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-correction", description = "纠错队列(管理员)")
@RestController
@RequestMapping("/api/v1/admin/corrections")
public class AdminCorrectionController {
    private final CorrectionService service;
    public AdminCorrectionController(CorrectionService service) { this.service = service; }

    @GetMapping
    public List<CorrectionReport> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(defaultValue = "0") int offset) {
        CurrentUser.requireAdmin();
        return service.listForAdmin(status, limit, offset);
    }

    /** @Audited 放控制器方法(与 AdminBusController 一致;aspect 读 CurrentUser 取 actor,E10)。 */
    @Audited(action = "UPDATE_CORRECTION", target = "correction")
    @PatchMapping("/{id}")
    public CorrectionReport update(@PathVariable long id, @RequestBody UpdateCorrectionRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return service.updateStatus(id, req, actor(me));
    }

    /** 与 AdminBusController.actor 同款:JwtPrincipal 只有 userId()/role(),无 username()。 */
    private static String actor(JwtPrincipal me) { return "admin:" + me.userId(); }
}
```

- [ ] **Step 3: 提交**
```bash
git add backend/src/main/java/com/airportbus/ticket/api/CorrectionController.java \
        backend/src/main/java/com/airportbus/ticket/api/AdminCorrectionController.java
git commit -m "feat(correction): 公开上报 + 管理员队列 控制器 (#7 C1)"
```

---

## Task 6: CorrectionServiceIT(Testcontainers)

**Files:** Create `backend/src/test/java/com/airportbus/ticket/CorrectionServiceIT.java`

- [ ] **Step 1: 写 IT**(镜像 `message/PushLoopIT` 的容器/属性脚手架)
```java
package com.airportbus.ticket;

import com.airportbus.common.ApiException;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
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
        "airportbus.correction.rate-limit-max=2",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@Testcontainers
class CorrectionServiceIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired CorrectionService service;

    @Test
    void submitWithValidSourceCreatesOpenReport() {
        CorrectionReport r = service.submit(new SubmitCorrectionRequest("vie-vab1", "末班车其实是23:30", "a@b.com"), "1.1.1.1");
        assertThat(r.id).isPositive();
        assertThat(r.status).isEqualTo("OPEN");
        assertThat(r.relatedSourceId).isEqualTo("vie-vab1");
    }

    @Test
    void blankDescriptionRejected() {
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest(null, "  ", null), "2.2.2.2"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownSourceRejected() {
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest("nope-xxx", "x", null), "3.3.3.3"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void rateLimitBlocksAfterMax() {
        String ip = "9.9.9.9";
        service.submit(new SubmitCorrectionRequest(null, "1", null), ip);
        service.submit(new SubmitCorrectionRequest(null, "2", null), ip);
        // rate-limit-max=2 → 第三条拒
        assertThatThrownBy(() -> service.submit(new SubmitCorrectionRequest(null, "3", null), ip))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void adminUpdateStatusPersists() {
        CorrectionReport r = service.submit(new SubmitCorrectionRequest(null, "fix me", null), "4.4.4.4");
        CorrectionReport up = service.updateStatus(r.id, new UpdateCorrectionRequest("RESOLVED", "已核实并更新"), "admin");
        assertThat(up.status).isEqualTo("RESOLVED");
        assertThat(up.resolutionNote).isEqualTo("已核实并更新");
    }
}
```

- [ ] **Step 2: 跑 IT**
Run: `cd backend && mvn -Dtest=CorrectionServiceIT test`
Expected: 5 例绿(需 Docker)。

- [ ] **Step 3: 提交**
```bash
git add backend/src/test/java/com/airportbus/ticket/CorrectionServiceIT.java
git commit -m "test(correction): CorrectionServiceIT(提交/校验/限流/管理员状态) (#7 C1)"
```

---

## Task 7: CorrectionControllerTest(@WebMvcTest)

**Files:** Create `backend/src/test/java/com/airportbus/ticket/api/CorrectionControllerTest.java`

- [ ] **Step 1: 写 controller 切片测试** —— 关键:`@WebMvcTest({CorrectionController.class, AdminCorrectionController.class})` + `@Import(GlobalExceptionHandler.class)`;`@MockBean CorrectionService` + `@MockBean` 所有被 `@MapperScan` 扫到的 mapper(参考 `bus/api/BusQueryControllerTest`:至少 `UserMapper`、`RefreshTokenMapper`、`BusWriteMapper`、`BusQueryMapper`、`SearchHotnessMapper`、`MessageMapper`、`CorrectionMapper` 等;实现时按上下文报错补齐缺的 mapper)。
```java
package com.airportbus.ticket.api;

import com.airportbus.common.GlobalExceptionHandler;
import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CorrectionController.class, AdminCorrectionController.class})
@Import(GlobalExceptionHandler.class)
class CorrectionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CorrectionService service;
    // @MapperScan 扫到的 mapper 都需 @MockBean,否则上下文起不来(见 BusQueryControllerTest 注释)
    @MockBean com.airportbus.user.mapper.UserMapper userMapper;
    @MockBean com.airportbus.user.mapper.RefreshTokenMapper refreshTokenMapper;
    @MockBean com.airportbus.bus.mapper.BusWriteMapper busWriteMapper;
    @MockBean com.airportbus.bus.mapper.BusQueryMapper busQueryMapper;
    @MockBean com.airportbus.bus.mapper.SearchHotnessMapper searchHotnessMapper;
    @MockBean com.airportbus.message.mapper.MessageMapper messageMapper;
    @MockBean com.airportbus.ticket.mapper.CorrectionMapper correctionMapper;

    @Test
    void publicSubmitReturns200WithoutAuth() throws Exception {
        CorrectionReport r = new CorrectionReport();
        r.id = 7; r.status = "OPEN"; r.description = "x";
        when(service.submit(any(), any())).thenReturn(r);
        mvc.perform(post("/api/v1/corrections").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(7))
           .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void adminListWithoutTokenIs401() throws Exception {
        // requireAdmin() 无主体 → ApiException(UNAUTHORIZED) → GlobalExceptionHandler → 401
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/corrections"))
           .andExpect(status().isUnauthorized());
    }
}
```

> 注:若 `@MapperScan` 还扫到本测试未列出的 mapper,按上下文启动报错信息逐个补 `@MockBean`(memory testing-tooling-quirks 已记此坑)。

- [ ] **Step 2: 跑测试**
Run: `cd backend && mvn -Dtest=CorrectionControllerTest test`
Expected: 2 例绿。

- [ ] **Step 3: 提交**
```bash
git add backend/src/test/java/com/airportbus/ticket/api/CorrectionControllerTest.java
git commit -m "test(correction): controller 切片(公开200/管理员401) (#7 C1)"
```

---

## Task 8: 前端 api/corrections.ts

**Files:** Create `frontend/src/api/corrections.ts`;Test `frontend/src/test/corrections.api.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/corrections.api.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  post: vi.fn(() => Promise.resolve({ data: { id: 1, status: 'OPEN' } })),
  get: vi.fn(() => Promise.resolve({ data: [] })),
  patch: vi.fn(() => Promise.resolve({ data: { id: 1, status: 'RESOLVED' } })),
} }))
import { http } from '../api/client'
import * as api from '../api/corrections'

describe('corrections api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('submit posts body', async () => {
    await api.submitCorrection({ sourceId: 'vie-vab1', description: 'x', contact: '' })
    expect(http.post).toHaveBeenCalledWith('/corrections', { sourceId: 'vie-vab1', description: 'x', contact: '' })
  })
  it('admin list with status', async () => {
    await api.listCorrections('OPEN')
    expect(http.get).toHaveBeenCalledWith('/admin/corrections', { params: { status: 'OPEN', limit: 50, offset: 0 } })
  })
  it('admin update patches', async () => {
    await api.updateCorrection(1, { status: 'RESOLVED', resolutionNote: 'ok' })
    expect(http.patch).toHaveBeenCalledWith('/admin/corrections/1', { status: 'RESOLVED', resolutionNote: 'ok' })
  })
})
```

- [ ] **Step 2: 运行确认失败** `cd frontend && npx vitest run src/test/corrections.api.spec.ts` → FAIL。

- [ ] **Step 3: 写 `frontend/src/api/corrections.ts`**
```ts
import { http } from './client'

export interface CorrectionReport {
  id: number; relatedSourceId: string | null; description: string
  contact: string | null; status: string; resolutionNote: string | null
  reporterIp: string | null; createdAt: string
}
export interface SubmitCorrection { sourceId?: string; description: string; contact?: string }

export const submitCorrection = (body: SubmitCorrection) =>
  http.post<CorrectionReport>('/corrections', body).then((r) => r.data)

export const listCorrections = (status = '', limit = 50, offset = 0) =>
  http.get<CorrectionReport[]>('/admin/corrections', { params: { status, limit, offset } }).then((r) => r.data)

export const updateCorrection = (id: number, body: { status: string; resolutionNote?: string }) =>
  http.patch<CorrectionReport>(`/admin/corrections/${id}`, body).then((r) => r.data)
```

- [ ] **Step 4: 运行确认通过** → 3 例绿。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/api/corrections.ts frontend/src/test/corrections.api.spec.ts
git commit -m "feat(correction): 前端 corrections api client (#7 C1)"
```

---

## Task 9: 纠错模态 ReportModal + 接入 bus-detail + i18n

**Files:** Create `frontend/src/components/ReportModal.vue`;Modify `frontend/src/pages/BusDetailPage.vue`、`frontend/src/i18n/locales/{zh-CN,en,de}.ts`;Test `frontend/src/test/ReportModal.spec.ts`

- [ ] **Step 1: i18n 加 `report` 段** —— 三个 locale 文件各加(顶层对象内,与 `msg` 并列):

`zh-CN.ts`:
```ts
  report: {
    trigger: '⚠️ 发现信息有误?上报纠错', title: '数据纠错上报',
    sub: '一键上报,无需登录,管理员后台会处理。',
    descLabel: '问题描述', descPh: '例:末班车实际是 23:30,不是 24:00;或某停靠站已取消…',
    contactLabel: '联系方式(可选)', contactPh: '邮箱 / 电话,方便我们回访(可留空)',
    submit: '提交纠错', cancel: '取消', sent: '已收到,感谢反馈!', failed: '提交失败,请稍后再试。',
    descRequired: '请填写问题描述。',
  },
```
`en.ts`:
```ts
  report: {
    trigger: '⚠️ Spotted an error? Report it', title: 'Report a data error',
    sub: 'One-click, no login required. Admins will review it.',
    descLabel: 'What is wrong', descPh: 'e.g. last bus is actually 23:30 not 24:00; or a stop was removed…',
    contactLabel: 'Contact (optional)', contactPh: 'Email / phone so we can follow up (optional)',
    submit: 'Submit', cancel: 'Cancel', sent: 'Received, thanks for the feedback!', failed: 'Submit failed, please retry later.',
    descRequired: 'Please describe the problem.',
  },
```
`de.ts`:
```ts
  report: {
    trigger: '⚠️ Fehler entdeckt? Melden', title: 'Datenfehler melden',
    sub: 'Ein Klick, kein Login nötig. Admins prüfen es.',
    descLabel: 'Was ist falsch', descPh: 'z.B. letzter Bus ist 23:30 statt 24:00; oder eine Haltestelle entfällt…',
    contactLabel: 'Kontakt (optional)', contactPh: 'E-Mail / Telefon für Rückfragen (optional)',
    submit: 'Senden', cancel: 'Abbrechen', sent: 'Erhalten, danke für das Feedback!', failed: 'Senden fehlgeschlagen, später erneut versuchen.',
    descRequired: 'Bitte das Problem beschreiben.',
  },
```

- [ ] **Step 2: 写失败测试** `frontend/src/test/ReportModal.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from '../i18n/locales/zh-CN'
import * as api from '../api/corrections'
import ReportModal from '../components/ReportModal.vue'

vi.mock('../api/corrections', () => ({ submitCorrection: vi.fn(() => Promise.resolve({ id: 1, status: 'OPEN' })) }))
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const mountModal = () => mount(ReportModal, { props: { sourceId: 'vie-vab1' }, global: { plugins: [i18n] } })

describe('ReportModal', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('opens on trigger click', async () => {
    const w = mountModal()
    expect(w.find('.overlay.open').exists()).toBe(false)
    await w.find('[data-test=report-trigger]').trigger('click')
    expect(w.find('.overlay.open').exists()).toBe(true)
  })
  it('blocks submit when description empty', async () => {
    const w = mountModal()
    await w.find('[data-test=report-trigger]').trigger('click')
    await w.find('[data-test=report-submit]').trigger('click')
    expect(api.submitCorrection).not.toHaveBeenCalled()
  })
  it('submits with description + sourceId then shows sent', async () => {
    const w = mountModal()
    await w.find('[data-test=report-trigger]').trigger('click')
    await w.find('[data-test=report-desc]').setValue('末班车是23:30')
    await w.find('[data-test=report-submit]').trigger('click')
    await new Promise((r) => setTimeout(r))
    expect(api.submitCorrection).toHaveBeenCalledWith({ sourceId: 'vie-vab1', description: '末班车是23:30', contact: '' })
    expect(w.text()).toContain('已收到')
  })
})
```

- [ ] **Step 3: 运行确认失败** → FAIL。

- [ ] **Step 4: 写 `frontend/src/components/ReportModal.vue`**(用 tokens.css 既有 `.overlay/.modal/...`;全 `{{ }}`)
```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { submitCorrection } from '../api/corrections'

const props = defineProps<{ sourceId: string }>()
const { t } = useI18n()
const open = ref(false)
const description = ref('')
const contact = ref('')
const sent = ref(false)
const error = ref('')

function show() { open.value = true; sent.value = false; error.value = '' }
function close() { open.value = false; description.value = ''; contact.value = '' }

async function submit() {
  if (!description.value.trim()) { error.value = t('report.descRequired'); return }
  error.value = ''
  try {
    await submitCorrection({ sourceId: props.sourceId, description: description.value.trim(), contact: contact.value.trim() })
    sent.value = true
  } catch {
    error.value = t('report.failed')
  }
}
</script>

<template>
  <button class="reportTrigger" data-test="report-trigger" @click="show">{{ t('report.trigger') }}</button>

  <div class="overlay" :class="{ open }" data-test="report-overlay" @click.self="close">
    <div class="modal" role="dialog" aria-modal="true" :aria-label="t('report.title')">
      <button class="modalClose" :aria-label="t('report.cancel')" @click="close">✕</button>
      <h3>{{ t('report.title') }}</h3>
      <p class="modalSub">{{ t('report.sub') }}</p>

      <template v-if="!sent">
        <div class="formrow">
          <label>{{ t('report.descLabel') }}</label>
          <textarea class="input" data-test="report-desc" v-model="description" :placeholder="t('report.descPh')"></textarea>
        </div>
        <div class="formrow">
          <label>{{ t('report.contactLabel') }}</label>
          <input class="input" data-test="report-contact" v-model="contact" type="text" :placeholder="t('report.contactPh')" />
        </div>
        <p v-if="error" class="formNote" style="color:var(--alert-red)" data-test="report-error">{{ error }}</p>
        <div class="modalActions">
          <button class="btn btn-primary" data-test="report-submit" @click="submit">{{ t('report.submit') }}</button>
          <button class="btn btn-ghost" @click="close">{{ t('report.cancel') }}</button>
        </div>
      </template>
      <p v-else data-test="report-sent" style="font-weight:600">{{ t('report.sent') }}</p>
    </div>
  </div>
</template>
```

- [ ] **Step 5: 接入 bus-detail** —— `frontend/src/pages/BusDetailPage.vue`:`<script setup>` 加 `import ReportModal from '../components/ReportModal.vue'`;模板里 `<StateBlock>` 内、`<BusCard>` 之后放:
```vue
      <BusCard v-if="data" :bus="data" :detail-link="false" />
      <ReportModal v-if="data" :source-id="sourceId" />
```

- [ ] **Step 6: 运行确认通过** `cd frontend && npx vitest run src/test/ReportModal.spec.ts` → 3 例绿。

- [ ] **Step 7: 提交**
```bash
git add frontend/src/components/ReportModal.vue frontend/src/pages/BusDetailPage.vue \
        frontend/src/i18n/locales/zh-CN.ts frontend/src/i18n/locales/en.ts frontend/src/i18n/locales/de.ts \
        frontend/src/test/ReportModal.spec.ts
git commit -m "feat(correction): 纠错模态 + 接入 bus-detail + i18n (#7 C1)"
```

---

## Task 10: 管理员纠错队列页 AdminCorrectionsPage + 路由 + 导航

**Files:** Create `frontend/src/pages/admin/AdminCorrectionsPage.vue`;Modify `frontend/src/router/index.ts`、`frontend/src/components/admin/AdminLayout.vue`、i18n(admin 文案);Test `frontend/src/test/AdminCorrectionsPage.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/AdminCorrectionsPage.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as api from '../api/corrections'
import AdminCorrectionsPage from '../pages/admin/AdminCorrectionsPage.vue'

vi.mock('../api/corrections', () => ({
  listCorrections: vi.fn(() => Promise.resolve([
    { id: 1, relatedSourceId: 'vie-vab1', description: '末班车是23:30', contact: 'a@b.com', status: 'OPEN', resolutionNote: null, reporterIp: '1.1.1.1', createdAt: '2026-06-22T09:00:00' },
  ])),
  updateCorrection: vi.fn(() => Promise.resolve({ id: 1, status: 'RESOLVED' })),
}))

describe('AdminCorrectionsPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('loads and renders a report row', async () => {
    const w = mount(AdminCorrectionsPage)
    await flushPromises()
    expect(api.listCorrections).toHaveBeenCalled()
    expect(w.text()).toContain('末班车是23:30')
  })
  it('resolve calls updateCorrection', async () => {
    const w = mount(AdminCorrectionsPage)
    await flushPromises()
    await (w.vm as any).setStatus(1, 'RESOLVED')
    expect(api.updateCorrection).toHaveBeenCalledWith(1, { status: 'RESOLVED', resolutionNote: '' })
  })
})
```

- [ ] **Step 2: 运行确认失败** → FAIL。

- [ ] **Step 3: 写 `frontend/src/pages/admin/AdminCorrectionsPage.vue`**(镜像 AdminSubscriptionsPage 的 ElTable 用法)
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElInput, ElButton } from 'element-plus'
import { listCorrections, updateCorrection, type CorrectionReport } from '../../api/corrections'

const rows = ref<CorrectionReport[]>([])
const notes = ref<Record<number, string>>({})

async function load() { rows.value = await listCorrections('') }
onMounted(load)

async function setStatus(id: number, status: string) {
  await updateCorrection(id, { status, resolutionNote: notes.value[id] ?? '' })
  await load()
}
defineExpose({ setStatus })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">纠错队列</h1>
  <p class="pageDesc">匿名旅客上报的数据纠错。处理后置为已解决 / 已忽略,可填内部备注。</p>

  <div class="panel">
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="status" label="状态" width="100" />
      <ElTableColumn prop="relatedSourceId" label="线路" width="120" />
      <ElTableColumn prop="description" label="问题描述" />
      <ElTableColumn prop="contact" label="联系方式" width="160" />
      <ElTableColumn prop="createdAt" label="时间" width="170" />
      <ElTableColumn label="处理" width="320">
        <template #default="{ row }">
          <ElInput v-model="notes[row.id]" placeholder="内部备注(可选)" size="small" style="margin-bottom:6px" />
          <ElButton size="small" type="success" @click="setStatus(row.id, 'RESOLVED')">已解决</ElButton>
          <ElButton size="small" @click="setStatus(row.id, 'DISMISSED')">忽略</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
```

- [ ] **Step 4: 加路由** —— `frontend/src/router/index.ts` admin children 数组里(`audit` 之后)加:
```ts
        { path: 'corrections', name: 'admin-corrections', component: () => import('../pages/admin/AdminCorrectionsPage.vue') },
```

- [ ] **Step 5: 加导航项** —— `frontend/src/components/admin/AdminLayout.vue`:在侧栏导航 `router-link` 列表里(audit 项旁)加一条指向 `/admin/corrections` 的「纠错队列」链接。先 `Read` 该文件确认现有导航项的写法(`<router-link :to>` + label),按同款加一条。

- [ ] **Step 6: 运行确认通过** `cd frontend && npx vitest run src/test/AdminCorrectionsPage.spec.ts` → 2 例绿。

- [ ] **Step 7: 提交**
```bash
git add frontend/src/pages/admin/AdminCorrectionsPage.vue frontend/src/router/index.ts \
        frontend/src/components/admin/AdminLayout.vue frontend/src/test/AdminCorrectionsPage.spec.ts
git commit -m "feat(correction): 管理员纠错队列页 + 路由 + 导航 (#7 C1)"
```

---

## Task 11: 全量验证 + 收尾

- [ ] **Step 1: 后端编译 + 关键测试**
Run: `cd backend && mvn -q -Dtest=CorrectionServiceIT,CorrectionControllerTest test`
Expected: 7 例绿(需 Docker)。再 `mvn -q -DskipTests compile` 确认全模块编译。

- [ ] **Step 2: 前端全测 + 构建**
Run: `cd frontend && npx vitest run && npm run build`
Expected: 全 spec 绿(含新增 3 个 spec 文件);vue-tsc 无错;构建成功。

- [ ] **Step 3: 手动全栈验证**(参考 testing-tooling-quirks 记忆)
`docker compose up -d mysql redis` → 后端 `DB_HOST=localhost DB_PORT=3307 … REDIS_PORT=6380 SEED_ENABLED=true mvn spring-boot:run` → 前端 `VITE_API_TARGET=http://localhost:8080 npx vite`。
- 匿名(未登录)进 `/bus/vie-vab1` → 点「⚠️ 发现信息有误?上报纠错」→ 填描述提交 → 看到「已收到」。
- 管理员登录进 `/admin/corrections` → 看到该上报 → 填备注点「已解决」→ 行状态变 RESOLVED。
- 快速连点上报 >5 次 → 第 6 次返回 429(限流)。

- [ ] **Step 4: 更新记忆** —— 新建 `correction-report-shipped.md`(C1 已交付:模块 `com.airportbus.ticket`、V9、公开 POST + IP 限流、admin 队列、bus-detail 模态);`MEMORY.md` 指针;标注切片 C 剩 C2 用户工单。

- [ ] **Step 5: 最终提交**
```bash
git add -A && git commit -m "chore(correction): #7 C1 验证 + 记忆"
```

---

## 自审清单(写计划者已核对)
- **spec 覆盖**:V9 表(T1)、mapper/xml(T2)、Redis 限流故障放行(T3)、Service 提交校验+管理员状态机(T4)、公开 POST + 管理员队列控制器(T5)、IT(T6)、controller 切片(T7)、前端 api(T8)、纠错模态+bus-detail+i18n(T9)、admin 队列页+路由+导航(T10)、验证(T11)。✅
- **复用**:ErrorCode 既有码(RATE_LIMITED/VALIDATION_FAILED/BUS_NOT_FOUND)+ 仅加 1 码 CORRECTION_NOT_FOUND;BusWriteMapper.selectVersionHash 校验存在性;tokens.css 既有 modal 类;admin 页镜像 AdminSubscriptionsPage。✅
- **公开端点**:靠 JwtAuthFilter「无 token 不拦截」,控制器不调 require() → 天然公开(已在 T5 注释)。✅
- **风险/占位**:`@WebMvcTest` 的 mapper @MockBean 清单可能需按上下文报错补齐(T7 注,memory 已记此坑);AdminLayout 导航写法需先 Read 对齐(T10 Step5)。`@Audited` 放控制器(非 service),避免 IT 直调 service 触发 `CurrentUser.require()` 抛错 —— 已修正(T4/T5)。`actor(me)="admin:"+userId()`(JwtPrincipal 无 username),与 AdminBusController 一致。无 TODO 占位。
