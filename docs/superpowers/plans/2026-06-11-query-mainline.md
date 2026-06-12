# 查询主线 Implementation Plan(Query Mainline)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「按 国家/城市/机场 查到维也纳、上海两城巴士线路并看详情」这条主线端到端跑通、可 `docker-compose` 一键部署。

**Architecture:** Spring Boot 3 模块化单体(本期只落 `bus` + `common` 两个包),MyBatis + MySQL 8 + Redis;data.json 进仓做幂等种子导入,导入器与运行时**共用同一个 canonicalizer**(E2)算 `content_hash`(为后续推送闭环打地基,本期只算不推)。前端 Vue 3 + Vite + vue-i18n + TanStack Query;**公开查询页用轻量手写组件 + 设计稿 tokens(见下「设计稿与设计决策」),不用 Element Plus**(Element Plus 留给后续 /admin 模块,不在本计划范围);移动优先,零登录查询。错误走结构化包络(D2),成功直接返回资源体。对外资源用 `source_id` 标识(D3)。

**Tech Stack:** Java 21 (JDK 21) / Spring Boot 3.2.5 / Maven / MyBatis 3.0.3 / Flyway / MySQL 8 / Redis;Vue 3 / TypeScript / Vite 5 / Pinia / vue-i18n / @tanstack/vue-query / axios(**公开页轻量手写组件 + 设计稿 tokens,不用 Element Plus**);字体 Sora / Noto Sans SC / JetBrains Mono;Vitest;docker-compose。

**契约锁定(跨切面,所有任务遵守):**
- API 前缀 `/api/v1`。
- 对外资源标识:巴士用 `source_id`(data.json 的 `id`,如 `vie-vab1`),机场用 `code`(IATA),不暴露自增主键(D3)。
- 成功响应:直接返回资源 JSON,HTTP 200。
- 错误响应:真实 HTTP 状态码 + body `{ code, message, details: [{field, issue}], traceId }`(D2)。
- 资源词汇:`bus`(不用 route/line)、`airport`、`city`、`country`(D3)。
- 字符集统一 utf8mb4。
- **全表审计列 + 逻辑删除**:每张表都带 `created_by` / `created_at` / `updated_by` / `updated_at` / `deleted`(`TINYINT(1) NOT NULL DEFAULT 0`)。**不物理删除**,只置 `deleted=1`;所有查询过滤 `deleted=0`。用 MyBatis 拦截器/基础 mapper 统一自动填充审计列 + 注入 `deleted=0` 过滤,**不逐条手写**。种子表、审计表同样适用。
- **台湾一律写「中国台湾省」**(不得出现裸「台湾」/「Taiwan」),UI 文案、种子数据、国家/地区列表、文档统一。
- **机场搜索热度**:查询机场(树/详情命中)时累加该机场计数 —— Redis `INCR`(异步、不阻塞查询路径),周期落库;后台读热度榜。是只增统计,与 audit_log 分开(详见下「附加功能」)。

---

## 设计稿与设计决策(锁定 — 视觉事实源)

前端高保真稿已在 `design/` 完成并经多轮评审定稿,**它就是前端实现目标,优先级高于本计划里早期写的 Vue 代码示例**。`design/styles.css` 进仓后作为前端**全局样式(tokens + 组件类)**,Vue 组件复用其 class(`.card`/`.stops`/`.schedTable`/`.alert`/`.chip`/`.skel`/`.empty`/`.overlay`/`.modal` 等),不引入 Element Plus。

**稿件清单(`design/`):** `home.html`(搜索 + 国家/城市/机场选择器 + 线路卡)、`airport.html`(机场线路列表)、`bus-detail.html`(线路详情)、`states.html`(加载骨架/空态/抓取失败)、`login.html`、`account.html`、`inbox.html`、`tickets.html`、`admin.html`、`styles.css`。

**设计 tokens(来自 `design/styles.css`,实现照搬):**
- 颜色:墨蓝 `--ink:#0f2030`、品牌蓝 `--brand:#1f5fb0`、深蓝 `--brand-deep:#143e75`、橙强调 `--accent:#e8741e`、冷纸底 `--paper:#f4f6f9`、卡片白 `#fff`、发丝线 `--line:#dde4ec`、成功绿 `--good:#1f9d72`、收藏亮蓝 `#2f80ed`。
- 字体:`Sora`(标题/价格 display)、`Noto Sans SC`(正文 + 中文)、`JetBrains Mono`(标签 / `//` 脚注)。圆角 16/10,阴影见 `--shadow`。
- 公开页走语义化 HTML + 轻样式(与 EN1 一致);Element Plus 重组件留给 `/admin`(后续模块)。

**统一卡片排版(home / airport / bus-detail 三页一致):**
`card__top`(左:`route` 路线名 → `dest` 目的地 → `operator` 运营商;右上角:**收藏按钮在上、价格在下**)→ `metaRow`(时长 / 运营,虚线分隔)→ 途经站点(**竖向时间轴** `.stops`:圆点 + 竖虚线,始发/终点实心加粗)→ 分时段班次(`.schedTable` 表格)→ **提醒/改道**(`.alert`,放内容**下方**、班次之后)→ 图片/文件(`.media`/`.fileLink`)→ 新鲜度 footer(`.chip`)。

**已锁定的 UX 决策(部分属后续模块,先记录,实现到对应模块时遵守):**
- **收藏 = 订阅**:按钮在卡片右上角(价格上方),`.favbtn` 胶囊 + 书签 SVG,选中填充亮蓝 `#2f80ed`。**去掉独立「通知」开关** —— 收藏即订阅其更新通知,不再单列 notify 开关(影响 user 模块与个人中心收藏列表)。
- **匿名纠错上报 = 卡片内按钮 → 弹出悬浮框 modal**(`.overlay`/`.modal`),✕ / 点遮罩 / Esc 关闭;零登录(EN4,属 feedback 模块)。
- **砍掉「变更记录」时间线 UI 模块**(EN3 前端时间线不做);但 `content_hash` 仍照算、推送闭环仍保留(EN3 数据层不受影响)。
- **提醒过滤**:`end_date < 今天` 的过期提醒前端不展示;无 `end_date` 视为长期保留。
- **新鲜度 3 档 chip**:绿「近期已核对」/ 普通「数据日期」/ 橙「抓取失败,可能过期」。
- 头部两态:公开页(home/airport/bus-detail)登出态 + 「登录/注册」入口(DS4 零登录);登录态页(account/inbox/tickets)导航 + 🔔红点 + 头像。

**本计划(查询主线)范围内的落地**:卡片骨架、tokens、状态(骨架/空/错)、提醒过滤、新鲜度 chip、竖向停靠站、班次表、图片/文件 **现在就按设计稿做**;**收藏按钮、纠错上报按钮/弹窗暂不渲染**(需 auth / feedback 后端,随后续模块加)。下面 **Task 13 / Task 16 的 Vue 代码示例改用下表的手写组件,`el-*` 写法作废**:

| 设计稿元素 | 用(手写,见 `design/styles.css`) | 取代计划早期的 |
|---|---|---|
| 加载骨架 | `.skel` / `.skelCard` div | `el-skeleton` |
| 空态 | `.empty`(图标 + 「仅覆盖两城」) | `el-empty` |
| 错误态 | `.empty` 变体 / 错误块 | `el-result` |
| 途经站点 | 竖向 `.stops`(`.stopRow`/`.stopLine`) | `el-steps` |
| 分时段班次 | `.schedTable` 表格 | `el-collapse` |
| 提醒/改道 | `.alert` / `.alertInfo` | `el-alert` |
| 新鲜度徽标 | `.chip.ok` / `.chip.warn` | `el-tag` |

---

## File Structure

**后端**(`backend/`,Maven,根包 `com.airportbus`):
```
backend/
├── pom.xml                                   依赖与构建
├── Dockerfile                                打可执行 jar 镜像
├── src/main/resources/
│   ├── application.yml                       数据源/Redis/导入开关
│   ├── data/data.json                        进仓种子(D4,vendor)
│   ├── db/migration/V1__schema.sql           Flyway 建表
│   ├── mapper/BusQueryMapper.xml             MyBatis 查询 SQL
│   └── mapper/BusWriteMapper.xml             MyBatis 写库 SQL(导入用)
├── src/main/java/com/airportbus/
│   ├── AirportbusApplication.java
│   ├── common/
│   │   ├── ApiError.java                      错误体(D2)
│   │   ├── ErrorCode.java                     业务错误码枚举
│   │   ├── ApiException.java                  业务异常
│   │   ├── GlobalExceptionHandler.java        @RestControllerAdvice
│   │   └── RedisCacheConfig.java              Spring Cache + TTL + null 缓存
│   └── bus/
│       ├── hash/
│       │   ├── CanonicalBus.java              规范化值对象(E2 共享)
│       │   └── Canonicalizer.java             canonicalJson + contentHash
│       ├── seed/
│       │   ├── SeedDtos.java                  data.json 反序列化 record
│       │   ├── SeedImporter.java              幂等导入(suppressEvents)
│       │   └── SeedImportRunner.java          启动按开关执行
│       ├── api/
│       │   ├── BusQueryController.java        /tree /airports/{code}/buses /buses/{id}
│       │   └── dto/                           TreeDto / BusSummaryDto / BusDetailDto
│       ├── mapper/BusQueryMapper.java         MyBatis 查询接口
│       ├── mapper/BusWriteMapper.java         MyBatis 写库接口
│       └── service/BusQueryService.java       查询 + 缓存
└── src/test/java/com/airportbus/...           单元 + 集成测试
```

**前端**(`frontend/`,Vite):
```
frontend/
├── package.json  vite.config.ts  tsconfig.json  Dockerfile  nginx.conf
├── .env.example
├── src/
│   ├── main.ts                                挂载 i18n / VueQuery / router + import tokens.css
│   ├── App.vue
│   ├── styles/tokens.css                      由 design/styles.css 复制(tokens + 组件类,单一事实源)
│   ├── api/
│   │   ├── client.ts                          axios 实例 + 错误包络解析
│   │   └── bus.ts                             tree/buses/detail 调用 + 类型
│   ├── i18n/
│   │   ├── index.ts                           vue-i18n 配置 + locale 持久化
│   │   └── locales/{zh-CN.ts,en.ts}
│   ├── router/index.ts                        / /airports/:code /bus/:sourceId
│   ├── components/
│   │   ├── StateBlock.vue                     loading/error/empty 统一态(DS3)
│   │   ├── alertFilter.ts                     过期 alert 过滤纯函数
│   │   ├── AlertList.vue                      过期 alert 过滤(DS3)
│   │   └── FreshnessBadge.vue                 fetch_failed + last_updated(DS3)
│   ├── pages/
│   │   ├── HomePage.vue                       城市卡片 + 机场搜索(DS1/DS4)
│   │   ├── AirportBusesPage.vue               某机场线路列表
│   │   └── BusDetailPage.vue                  详情(DS2 信息优先级)
│   └── test/                                  Vitest 组件/单元测试
```

---

## Task 1: 后端 Maven 脚手架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/airportbus/AirportbusApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/.gitignore`

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    <groupId>com.airportbus</groupId>
    <artifactId>airportbus</artifactId>
    <version>0.1.0</version>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写主类**

`backend/src/main/java/com/airportbus/AirportbusApplication.java`
```java
package com.airportbus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan("com.airportbus.bus.mapper")
public class AirportbusApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirportbusApplication.class, args);
    }
}
```

- [ ] **Step 3: 写 application.yml**

`backend/src/main/resources/application.yml`
```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/airportbus?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    username: ${DB_USER:airportbus}
    password: ${DB_PASSWORD:airportbus}
  flyway:
    enabled: true
    baseline-on-migrate: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
airportbus:
  seed:
    enabled: ${SEED_ENABLED:true}   # D4: 启动幂等导入开关
```

- [ ] **Step 4: 写 .gitignore**

`backend/.gitignore`
```
target/
*.class
.idea/
*.iml
```

- [ ] **Step 5: 验证编译**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS(无源码错误)。

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/airportbus/AirportbusApplication.java backend/src/main/resources/application.yml backend/.gitignore
git commit -m "chore(backend): scaffold Spring Boot 3 + MyBatis + Redis project"
```

---

## Task 2: 本地基础设施 docker-compose(mysql + redis)

测试与开发都依赖 MySQL/Redis,先把它们起起来。完整含前后端的编排在 Task 19。

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`

- [ ] **Step 1: 写 docker-compose.yml(基础设施部分)**

`docker-compose.yml`
```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: airportbus
      MYSQL_USER: airportbus
      MYSQL_PASSWORD: airportbus
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
    ports: ["3306:3306"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-proot"]
      interval: 5s
      timeout: 3s
      retries: 20
    volumes: ["mysql_data:/var/lib/mysql"]
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20
volumes:
  mysql_data:
```

- [ ] **Step 2: 写 .env.example(D4)**

`.env.example`
```
DB_HOST=localhost
DB_PORT=3306
DB_USER=airportbus
DB_PASSWORD=airportbus
REDIS_HOST=localhost
REDIS_PORT=6379
SEED_ENABLED=true
```

- [ ] **Step 3: 起基础设施并验证健康**

Run: `docker compose up -d mysql redis && sleep 8 && docker compose ps`
Expected: mysql、redis 两个服务 `healthy`。

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml .env.example
git commit -m "chore: add docker-compose for mysql + redis and .env.example"
```

---

## Task 3: 数据库 Schema(Flyway 迁移)

落地设计「系统核心数据结构」:`国家→城市→机场→巴士线路→子表`。全部 utf8mb4,自然键唯一索引 + 外键,`content_hash` 列就位。

> **全表必加(契约锁定)**:下面每个 `CREATE TABLE` 都要在末尾追加这 5 列(为简洁,示例 SQL 用注释占位,落地时逐表展开):
> ```sql
>   created_by   VARCHAR(64)  NULL,
>   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
>   updated_by   VARCHAR(64)  NULL,
>   updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
>   deleted      TINYINT(1)   NOT NULL DEFAULT 0
> ```
> 唯一键改成「自然键 + deleted」复合(如 `uk_bus_source_id (source_id, deleted)`),否则逻辑删除后无法重建同键。MyBatis 拦截器自动填充审计列 + 自动注入 `deleted=0` 过滤(见契约锁定)。
>
> **双向预留(本期补充)**:`bus_stop` 与 `bus_schedule` 各加一列
> ```sql
>   direction    VARCHAR(16)  NOT NULL DEFAULT 'TO_AIRPORT',   -- TO_AIRPORT(进城→到达机场) / FROM_AIRPORT(出机场)
> ```
> 用于存「所有方向」的站点与时间(为以后升级预留)。本期 data.json 未拆方向,导入器先全部写 `TO_AIRPORT`;canonicalizer 的 stops/schedules 排序键须把 `direction` 纳入,保证哈希稳定。前端按 `direction` 分「到达机场 / 从机场出发」两栏展示(见设计稿)。

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__schema.sql`

- [ ] **Step 1: 写迁移脚本**(每个 CREATE TABLE 末尾追加上面的 5 个审计/删除列;`bus_stop`/`bus_schedule` 再加 `direction` 列;唯一键并入 `deleted`)

`backend/src/main/resources/db/migration/V1__schema.sql`
```sql
CREATE TABLE country (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(8)   NOT NULL,
  name VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_country_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE city (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  country_id BIGINT       NOT NULL,
  name       VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_city_country_name (country_id, name),
  CONSTRAINT fk_city_country FOREIGN KEY (country_id) REFERENCES country(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE airport (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  city_id      BIGINT       NOT NULL,
  code         VARCHAR(8)   NOT NULL,
  name         VARCHAR(128) NOT NULL,
  official_url VARCHAR(512) NULL,
  UNIQUE KEY uk_airport_code (code),
  KEY idx_airport_city (city_id),
  CONSTRAINT fk_airport_city FOREIGN KEY (city_id) REFERENCES city(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_route (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  airport_id      BIGINT       NOT NULL,
  source_id       VARCHAR(64)  NOT NULL,
  route           VARCHAR(128) NOT NULL,
  destination     VARCHAR(255) NULL,
  operator        VARCHAR(255) NULL,
  official_url    VARCHAR(512) NULL,
  duration        VARCHAR(255) NULL,
  price           VARCHAR(255) NULL,
  operating_hours VARCHAR(255) NULL,
  last_updated    DATE         NULL,
  fetch_failed    TINYINT(1)   NOT NULL DEFAULT 0,
  content_hash    CHAR(64)     NOT NULL,
  UNIQUE KEY uk_bus_source_id (source_id),
  KEY idx_bus_airport (airport_id),
  CONSTRAINT fk_bus_airport FOREIGN KEY (airport_id) REFERENCES airport(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_stop (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  seq          INT          NOT NULL,
  name         VARCHAR(255) NOT NULL,
  KEY idx_stop_bus (bus_route_id),
  CONSTRAINT fk_stop_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_schedule (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  bus_route_id  BIGINT       NOT NULL,
  time_range    VARCHAR(255) NULL,
  interval_text VARCHAR(255) NULL,
  note          VARCHAR(512) NULL,
  KEY idx_sched_bus (bus_route_id),
  CONSTRAINT fk_sched_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_image (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  url          VARCHAR(512) NOT NULL,
  caption      VARCHAR(255) NULL,
  KEY idx_img_bus (bus_route_id),
  CONSTRAINT fk_img_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_file (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  name         VARCHAR(255) NULL,
  url          VARCHAR(512) NOT NULL,
  KEY idx_file_bus (bus_route_id),
  CONSTRAINT fk_file_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_alert (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bus_route_id BIGINT       NOT NULL,
  type         VARCHAR(32)  NOT NULL,
  message      VARCHAR(1024) NOT NULL,
  start_date   DATE         NULL,
  end_date     DATE         NULL,
  KEY idx_alert_bus (bus_route_id),
  CONSTRAINT fk_alert_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 启动应用让 Flyway 迁移执行**

Run: `cd backend && SEED_ENABLED=false mvn -q spring-boot:run`(等待 "Started AirportbusApplication" 后 Ctrl-C)
Expected: 日志含 `Migrating schema ... to version "1 - schema"`,无错误。

- [ ] **Step 3: 验证表已建**

Run: `docker compose exec -T mysql mysql -uairportbus -pairportbus airportbus -e "SHOW TABLES;"`
Expected: 列出 country, city, airport, bus_route, bus_stop, bus_schedule, bus_image, bus_file, bus_alert, flyway_schema_history。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__schema.sql
git commit -m "feat(db): add V1 schema for country/city/airport/bus and subtables"
```

---

## Task 4: 进仓种子数据 data.json

**Files:**
- Create: `backend/src/main/resources/data/data.json`

- [ ] **Step 1: 抓取并落盘种子(D4:不依赖外部 URL)**

Run:
```bash
curl -fsSL https://raw.githubusercontent.com/fupengfei/Global-Airport-Bus-Information/main/data.json \
  -o backend/src/main/resources/data/data.json
```
Expected: 文件生成,无报错。

- [ ] **Step 2: 校验是合法 JSON 且结构符合预期**

Run: `python3 -c "import json; d=json.load(open('backend/src/main/resources/data/data.json')); print('countries=',len(d['countries']), 'first_bus=', d['countries'][0]['cities'][0]['airports'][0]['buses'][0]['id'])"`
Expected: 打印出 countries 数量 ≥ 2,且首条 bus 的 `id` 形如 `vie-...`。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/data/data.json
git commit -m "chore(seed): vendor data.json into repo"
```

---

## Task 5: Canonicalizer + content_hash(纯逻辑,TDD)

E2 契约:输入按 UTF-8 + Unicode **NFC** 归一 + **trim** 尾空白;null/空串统一视为「缺失」。key 有序、数组按规则排序(stops 保序;schedules/alerts/images/files 排序),`content_hash = SHA256(canonicalJson)` 的十六进制。导入器与运行时**共用此类**。

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/hash/CanonicalBus.java`
- Create: `backend/src/main/java/com/airportbus/bus/hash/Canonicalizer.java`
- Test: `backend/src/test/java/com/airportbus/bus/hash/CanonicalizerTest.java`

- [ ] **Step 1: 写值对象 CanonicalBus**

`backend/src/main/java/com/airportbus/bus/hash/CanonicalBus.java`
```java
package com.airportbus.bus.hash;

import java.util.List;

/** 规范化值对象:导入器(从 JSON)与运行时(从 DB)都构造它来算 hash。 */
public record CanonicalBus(
        String route,
        String destination,
        String operator,
        String duration,
        String price,
        String operatingHours,
        List<String> stops,                 // 保序(seq 升序)
        List<Schedule> schedules,
        List<Alert> alerts,
        List<Media> images,
        List<Media> files
) {
    public record Schedule(String timeRange, String intervalText, String note) {}
    public record Alert(String type, String message, String startDate, String endDate) {}
    public record Media(String url, String label) {}
}
```

- [ ] **Step 2: 写失败测试**

`backend/src/test/java/com/airportbus/bus/hash/CanonicalizerTest.java`
```java
package com.airportbus.bus.hash;

import com.airportbus.bus.hash.CanonicalBus.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalizerTest {

    private CanonicalBus base() {
        return new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("A", "B"),
                List.of(new Schedule("all day", "30min", "note")),
                List.of(new Alert("info", "msg", "2026-05-01", "2026-06-30")),
                List.of(new Media("http://x/2.png", "two"), new Media("http://x/1.png", "one")),
                List.of(new Media("http://x/f.pdf", "file")));
    }

    @Test
    void hashIsStableAcrossRuns() {
        assertThat(Canonicalizer.contentHash(base()))
                .isEqualTo(Canonicalizer.contentHash(base()))
                .hasSize(64);
    }

    @Test
    void imageOrderDoesNotChangeHash_sortedByUrl() {
        CanonicalBus reordered = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("A", "B"),
                List.of(new Schedule("all day", "30min", "note")),
                List.of(new Alert("info", "msg", "2026-05-01", "2026-06-30")),
                List.of(new Media("http://x/1.png", "one"), new Media("http://x/2.png", "two")),
                List.of(new Media("http://x/f.pdf", "file")));
        assertThat(Canonicalizer.contentHash(reordered)).isEqualTo(Canonicalizer.contentHash(base()));
    }

    @Test
    void stopOrderChangesHash() {
        CanonicalBus swapped = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("B", "A"),
                base().schedules(), base().alerts(), base().images(), base().files());
        assertThat(Canonicalizer.contentHash(swapped)).isNotEqualTo(Canonicalizer.contentHash(base()));
    }

    @Test
    void nullAndEmptyAndWhitespaceNormalizeToSame() {
        CanonicalBus withNulls = new CanonicalBus("VAB 1", null, "  ", null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
        CanonicalBus withEmpties = new CanonicalBus("VAB 1", "", "", "", "", "",
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(Canonicalizer.contentHash(withNulls)).isEqualTo(Canonicalizer.contentHash(withEmpties));
    }

    @Test
    void trailingWhitespaceTrimmed() {
        CanonicalBus trimmed = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        CanonicalBus untrimmed = new CanonicalBus("VAB 1 ", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        assertThat(Canonicalizer.contentHash(trimmed)).isEqualTo(Canonicalizer.contentHash(untrimmed));
    }
}
```

- [ ] **Step 2b: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=CanonicalizerTest test`
Expected: 编译失败 / FAIL —— `Canonicalizer` 不存在。

- [ ] **Step 3: 实现 Canonicalizer**

`backend/src/main/java/com/airportbus/bus/hash/Canonicalizer.java`
```java
package com.airportbus.bus.hash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;

/** E2:共享 canonicalizer。NFC + trim;null/空串=缺失;key 有序 + 数组规则排序。 */
public final class Canonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MISSING = " "; // 缺失哨兵,绝不会与真实文本冲突

    private Canonicalizer() {}

    public static String canonicalJson(CanonicalBus b) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("route", norm(b.route()));
        root.put("destination", norm(b.destination()));
        root.put("operator", norm(b.operator()));
        root.put("duration", norm(b.duration()));
        root.put("price", norm(b.price()));
        root.put("operatingHours", norm(b.operatingHours()));

        // stops:保序
        List<String> stops = new ArrayList<>();
        for (String s : nz(b.stops())) stops.add(norm(s));
        root.put("stops", stops);

        // schedules:按 (timeRange, intervalText) 稳定排序
        List<Map<String, String>> schedules = new ArrayList<>();
        for (CanonicalBus.Schedule s : nz(b.schedules())) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("timeRange", norm(s.timeRange()));
            m.put("intervalText", norm(s.intervalText()));
            m.put("note", norm(s.note()));
            schedules.add(m);
        }
        schedules.sort(Comparator.comparing((Map<String, String> m) -> m.get("timeRange"))
                .thenComparing(m -> m.get("intervalText")));
        root.put("schedules", schedules);

        // alerts:按 (type, startDate, endDate) 稳定排序
        List<Map<String, String>> alerts = new ArrayList<>();
        for (CanonicalBus.Alert a : nz(b.alerts())) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("type", norm(a.type()));
            m.put("message", norm(a.message()));
            m.put("startDate", norm(a.startDate()));
            m.put("endDate", norm(a.endDate()));
            alerts.add(m);
        }
        alerts.sort(Comparator.comparing((Map<String, String> m) -> m.get("type"))
                .thenComparing(m -> m.get("startDate")).thenComparing(m -> m.get("endDate")));
        root.put("alerts", alerts);

        root.put("images", media(b.images()));
        root.put("files", media(b.files()));

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("canonical json failed", e);
        }
    }

    public static String contentHash(CanonicalBus b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalJson(b).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : digest) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** media:按 url 稳定排序。 */
    private static List<Map<String, String>> media(List<CanonicalBus.Media> in) {
        List<Map<String, String>> out = new ArrayList<>();
        for (CanonicalBus.Media m : nz(in)) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("url", norm(m.url()));
            e.put("label", norm(m.label()));
            out.add(e);
        }
        out.sort(Comparator.comparing(m -> m.get("url")));
        return out;
    }

    /** NFC 归一 + trim;null/空 → 缺失哨兵。 */
    private static String norm(String s) {
        if (s == null) return MISSING;
        String n = Normalizer.normalize(s, Normalizer.Form.NFC).trim();
        return n.isEmpty() ? MISSING : n;
    }

    private static <T> List<T> nz(List<T> in) {
        return in == null ? List.of() : in;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=CanonicalizerTest test`
Expected: PASS(5 个测试全绿)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/hash backend/src/test/java/com/airportbus/bus/hash
git commit -m "feat(bus): add shared canonicalizer and content_hash (E2)"
```

---

## Task 6: 错误包络 + 错误码(D2)

成功直接返回资源;错误用 `{code, message, details, traceId}` + 真实 HTTP 状态码。

**Files:**
- Create: `backend/src/main/java/com/airportbus/common/ErrorCode.java`
- Create: `backend/src/main/java/com/airportbus/common/ApiError.java`
- Create: `backend/src/main/java/com/airportbus/common/ApiException.java`
- Create: `backend/src/main/java/com/airportbus/common/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/airportbus/common/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写错误码枚举**

`backend/src/main/java/com/airportbus/common/ErrorCode.java`
```java
package com.airportbus.common;

import org.springframework.http.HttpStatus;

/** 业务错误码:body.code 带业务原因,status 带 HTTP 类别(D2)。 */
public enum ErrorCode {
    BUS_NOT_FOUND(HttpStatus.NOT_FOUND),
    AIRPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
}
```

- [ ] **Step 2: 写错误体 + 异常**

`backend/src/main/java/com/airportbus/common/ApiError.java`
```java
package com.airportbus.common;

import java.util.List;

public record ApiError(String code, String message, List<Detail> details, String traceId) {
    public record Detail(String field, String issue) {}
}
```

`backend/src/main/java/com/airportbus/common/ApiException.java`
```java
package com.airportbus.common;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 3: 写失败测试**

`backend/src/test/java/com/airportbus/common/GlobalExceptionHandlerTest.java`
```java
package com.airportbus.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.Probe.class)
@Import({GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @RestController
    static class Probe {
        @GetMapping("/__probe/notfound")
        String notFound() { throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no such bus"); }
    }

    @Autowired MockMvc mvc;

    @Test
    void apiExceptionMapsToStatusAndEnvelope() throws Exception {
        mvc.perform(get("/__probe/notfound"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"))
           .andExpect(jsonPath("$.message").value("no such bus"))
           .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
```

- [ ] **Step 3b: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: FAIL —— `GlobalExceptionHandler` 不存在。

- [ ] **Step 4: 实现全局异常处理**

`backend/src/main/java/com/airportbus/common/GlobalExceptionHandler.java`
```java
package com.airportbus.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.code, ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(ErrorCode.INTERNAL_ERROR, "internal error", List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.Detail> details) {
        ApiError body = new ApiError(code.name(), message, details, UUID.randomUUID().toString());
        return ResponseEntity.status(code.status).body(body);
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=GlobalExceptionHandlerTest test`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/airportbus/common backend/src/test/java/com/airportbus/common
git commit -m "feat(common): structured error envelope + error codes (D2)"
```

---

## Task 7: 种子反序列化 DTO + 导入器(幂等,TDD 集成)

E11:导入走 `suppressEvents` 路径(本期无事件,语义上为「只落库不推送」),E2:导入器复用 Canonicalizer。幂等键:country.code / (country_id, city.name) / airport.code / bus_route.source_id;子表先删后插。

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/seed/SeedDtos.java`
- Create: `backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java`
- Create: `backend/src/main/resources/mapper/BusWriteMapper.xml`
- Create: `backend/src/main/java/com/airportbus/bus/seed/SeedImporter.java`
- Test: `backend/src/test/java/com/airportbus/bus/seed/SeedImporterIT.java`

- [ ] **Step 1: 写种子 DTO(对齐 data.json 字段名)**

`backend/src/main/java/com/airportbus/bus/seed/SeedDtos.java`
```java
package com.airportbus.bus.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class SeedDtos {
    public record Root(List<Country> countries) {}
    public record Country(String code, String name, List<City> cities) {}
    public record City(String name, List<Airport> airports) {}
    public record Airport(String code, String name, String officialUrl, List<Bus> buses) {}
    public record Bus(String id, String route, String destination, String operator, String officialUrl,
                      String duration, String price, String operatingHours,
                      List<String> stops, List<Schedule> schedules, List<Image> images,
                      List<FileRef> files, List<Alert> alerts, String lastUpdated, boolean fetchFailed) {}
    public record Schedule(String timeRange, String interval, String note) {}
    public record Image(String url, String caption) {}
    public record FileRef(String name, String url) {}
    public record Alert(String type, String message, String startDate, String endDate) {}

    private SeedDtos() {}
}
```

- [ ] **Step 2: 写写库 Mapper 接口**

`backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java`
```java
package com.airportbus.bus.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface BusWriteMapper {
    Long findCountryId(@Param("code") String code);
    void insertCountry(@Param("code") String code, @Param("name") String name);

    Long findCityId(@Param("countryId") Long countryId, @Param("name") String name);
    void insertCity(Map<String, Object> row);    // keys: countryId, name -> 回填 id

    Long findAirportId(@Param("code") String code);
    void insertAirport(Map<String, Object> row);  // keys: cityId, code, name, officialUrl -> 回填 id
    void updateAirport(Map<String, Object> row);  // keys: id, cityId, name, officialUrl

    Long findBusId(@Param("sourceId") String sourceId);
    void insertBus(Map<String, Object> row);      // 见 XML 列;回填 id
    void updateBus(Map<String, Object> row);

    void deleteStops(@Param("busId") Long busId);
    void deleteSchedules(@Param("busId") Long busId);
    void deleteImages(@Param("busId") Long busId);
    void deleteFiles(@Param("busId") Long busId);
    void deleteAlerts(@Param("busId") Long busId);

    void insertStop(@Param("busId") Long busId, @Param("seq") int seq, @Param("name") String name);
    void insertSchedule(Map<String, Object> row);  // busId, timeRange, intervalText, note
    void insertImage(@Param("busId") Long busId, @Param("url") String url, @Param("caption") String caption);
    void insertFile(@Param("busId") Long busId, @Param("name") String name, @Param("url") String url);
    void insertAlert(Map<String, Object> row);     // busId, type, message, startDate, endDate
    List<Long> ignored();                          // 占位,防接口为空时某些工具告警(可删)
}
```

> 注:`ignored()` 仅为示意,可删;不要在 XML 里为它写映射。

- [ ] **Step 3: 写写库 Mapper XML**

`backend/src/main/resources/mapper/BusWriteMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.bus.mapper.BusWriteMapper">

  <select id="findCountryId" resultType="java.lang.Long">
    SELECT id FROM country WHERE code = #{code}
  </select>
  <insert id="insertCountry">
    INSERT INTO country(code, name) VALUES(#{code}, #{name})
  </insert>

  <select id="findCityId" resultType="java.lang.Long">
    SELECT id FROM city WHERE country_id = #{countryId} AND name = #{name}
  </select>
  <insert id="insertCity" useGeneratedKeys="true" keyProperty="id" parameterType="map">
    INSERT INTO city(country_id, name) VALUES(#{countryId}, #{name})
  </insert>

  <select id="findAirportId" resultType="java.lang.Long">
    SELECT id FROM airport WHERE code = #{code}
  </select>
  <insert id="insertAirport" useGeneratedKeys="true" keyProperty="id" parameterType="map">
    INSERT INTO airport(city_id, code, name, official_url) VALUES(#{cityId}, #{code}, #{name}, #{officialUrl})
  </insert>
  <update id="updateAirport" parameterType="map">
    UPDATE airport SET city_id=#{cityId}, name=#{name}, official_url=#{officialUrl} WHERE id=#{id}
  </update>

  <select id="findBusId" resultType="java.lang.Long">
    SELECT id FROM bus_route WHERE source_id = #{sourceId}
  </select>
  <insert id="insertBus" useGeneratedKeys="true" keyProperty="id" parameterType="map">
    INSERT INTO bus_route(airport_id, source_id, route, destination, operator, official_url,
                          duration, price, operating_hours, last_updated, fetch_failed, content_hash)
    VALUES(#{airportId}, #{sourceId}, #{route}, #{destination}, #{operator}, #{officialUrl},
           #{duration}, #{price}, #{operatingHours}, #{lastUpdated}, #{fetchFailed}, #{contentHash})
  </insert>
  <update id="updateBus" parameterType="map">
    UPDATE bus_route SET airport_id=#{airportId}, route=#{route}, destination=#{destination},
      operator=#{operator}, official_url=#{officialUrl}, duration=#{duration}, price=#{price},
      operating_hours=#{operatingHours}, last_updated=#{lastUpdated}, fetch_failed=#{fetchFailed},
      content_hash=#{contentHash}
    WHERE id=#{id}
  </update>

  <delete id="deleteStops">DELETE FROM bus_stop WHERE bus_route_id = #{busId}</delete>
  <delete id="deleteSchedules">DELETE FROM bus_schedule WHERE bus_route_id = #{busId}</delete>
  <delete id="deleteImages">DELETE FROM bus_image WHERE bus_route_id = #{busId}</delete>
  <delete id="deleteFiles">DELETE FROM bus_file WHERE bus_route_id = #{busId}</delete>
  <delete id="deleteAlerts">DELETE FROM bus_alert WHERE bus_route_id = #{busId}</delete>

  <insert id="insertStop">INSERT INTO bus_stop(bus_route_id, seq, name) VALUES(#{busId}, #{seq}, #{name})</insert>
  <insert id="insertSchedule" parameterType="map">
    INSERT INTO bus_schedule(bus_route_id, time_range, interval_text, note)
    VALUES(#{busId}, #{timeRange}, #{intervalText}, #{note})
  </insert>
  <insert id="insertImage">INSERT INTO bus_image(bus_route_id, url, caption) VALUES(#{busId}, #{url}, #{caption})</insert>
  <insert id="insertFile">INSERT INTO bus_file(bus_route_id, name, url) VALUES(#{busId}, #{name}, #{url})</insert>
  <insert id="insertAlert" parameterType="map">
    INSERT INTO bus_alert(bus_route_id, type, message, start_date, end_date)
    VALUES(#{busId}, #{type}, #{message}, #{startDate}, #{endDate})
  </insert>
</mapper>
```

> `ignored()` 不在此 XML 中映射;若保留接口方法会报「statement not found」,执行时直接从接口删掉该方法即可。

- [ ] **Step 4: 写失败的集成测试(Testcontainers MySQL)**

`backend/src/test/java/com/airportbus/bus/seed/SeedImporterIT.java`
```java
package com.airportbus.bus.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"airportbus.seed.enabled=false", "spring.cache.type=none"})
@Testcontainers
class SeedImporterIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @MockBean RedisConnectionFactory redisConnectionFactory; // 不连真实 Redis

    @Autowired SeedImporter importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importIsIdempotent_andHashStableOnReimport() {
        importer.importFromClasspath("data/data.json");
        Integer busCount1 = jdbc.queryForObject("SELECT COUNT(*) FROM bus_route", Integer.class);
        String hash1 = jdbc.queryForObject(
                "SELECT content_hash FROM bus_route WHERE source_id = 'vie-vab1'", String.class);

        importer.importFromClasspath("data/data.json"); // 重跑

        Integer busCount2 = jdbc.queryForObject("SELECT COUNT(*) FROM bus_route", Integer.class);
        String hash2 = jdbc.queryForObject(
                "SELECT content_hash FROM bus_route WHERE source_id = 'vie-vab1'", String.class);

        assertThat(busCount2).isEqualTo(busCount1);        // 不重复插入
        assertThat(hash2).isEqualTo(hash1);                // hash 稳定(无幻象变更)
        assertThat(busCount1).isGreaterThan(0);
        Integer stops = jdbc.queryForObject("SELECT COUNT(*) FROM bus_stop bs " +
                "JOIN bus_route br ON br.id = bs.bus_route_id WHERE br.source_id='vie-vab1'", Integer.class);
        assertThat(stops).isEqualTo(3); // vie-vab1 有 3 个停靠站
    }
}
```

- [ ] **Step 4b: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=SeedImporterIT test`
Expected: FAIL —— `SeedImporter` 不存在 / 编译失败。(需本机 Docker 运行 Testcontainers。)

- [ ] **Step 5: 实现 SeedImporter**

`backend/src/main/java/com/airportbus/bus/seed/SeedImporter.java`
```java
package com.airportbus.bus.seed;

import com.airportbus.bus.hash.CanonicalBus;
import com.airportbus.bus.hash.Canonicalizer;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/** E11:只落库不发事件;E2:复用 Canonicalizer。以自然键 upsert,子表先删后插,幂等。 */
@Service
public class SeedImporter {

    private final BusWriteMapper mapper;
    private final ObjectMapper json = new ObjectMapper();

    public SeedImporter(BusWriteMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void importFromClasspath(String path) {
        SeedDtos.Root root = read(path);
        for (SeedDtos.Country c : nz(root.countries())) {
            Long countryId = upsertCountry(c);
            for (SeedDtos.City city : nz(c.cities())) {
                Long cityId = upsertCity(countryId, city);
                for (SeedDtos.Airport ap : nz(city.airports())) {
                    Long airportId = upsertAirport(cityId, ap);
                    for (SeedDtos.Bus bus : nz(ap.buses())) {
                        upsertBus(airportId, bus);
                    }
                }
            }
        }
    }

    private SeedDtos.Root read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, SeedDtos.Root.class);
        } catch (Exception e) {
            throw new IllegalStateException("read seed failed: " + path, e);
        }
    }

    private Long upsertCountry(SeedDtos.Country c) {
        Long id = mapper.findCountryId(c.code());
        if (id != null) return id;
        mapper.insertCountry(c.code(), c.name());
        return mapper.findCountryId(c.code());
    }

    private Long upsertCity(Long countryId, SeedDtos.City city) {
        Long id = mapper.findCityId(countryId, city.name());
        if (id != null) return id;
        Map<String, Object> row = new HashMap<>();
        row.put("countryId", countryId);
        row.put("name", city.name());
        mapper.insertCity(row);
        return (Long) row.get("id");
    }

    private Long upsertAirport(Long cityId, SeedDtos.Airport ap) {
        Long id = mapper.findAirportId(ap.code());
        Map<String, Object> row = new HashMap<>();
        row.put("cityId", cityId);
        row.put("code", ap.code());
        row.put("name", ap.name());
        row.put("officialUrl", ap.officialUrl());
        if (id == null) {
            mapper.insertAirport(row);
            return (Long) row.get("id");
        }
        row.put("id", id);
        mapper.updateAirport(row);
        return id;
    }

    private void upsertBus(Long airportId, SeedDtos.Bus bus) {
        String hash = Canonicalizer.contentHash(toCanonical(bus));
        Long id = mapper.findBusId(bus.id());
        Map<String, Object> row = new HashMap<>();
        row.put("airportId", airportId);
        row.put("sourceId", bus.id());
        row.put("route", bus.route());
        row.put("destination", bus.destination());
        row.put("operator", bus.operator());
        row.put("officialUrl", bus.officialUrl());
        row.put("duration", bus.duration());
        row.put("price", bus.price());
        row.put("operatingHours", bus.operatingHours());
        row.put("lastUpdated", parseDate(bus.lastUpdated()));
        row.put("fetchFailed", bus.fetchFailed());
        row.put("contentHash", hash);
        if (id == null) {
            mapper.insertBus(row);
            id = (Long) row.get("id");
        } else {
            row.put("id", id);
            mapper.updateBus(row);
        }
        replaceChildren(id, bus);
    }

    private void replaceChildren(Long busId, SeedDtos.Bus bus) {
        mapper.deleteStops(busId);
        mapper.deleteSchedules(busId);
        mapper.deleteImages(busId);
        mapper.deleteFiles(busId);
        mapper.deleteAlerts(busId);

        int seq = 0;
        for (String s : nz(bus.stops())) mapper.insertStop(busId, seq++, s);
        for (SeedDtos.Schedule sc : nz(bus.schedules())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("timeRange", sc.timeRange());
            r.put("intervalText", sc.interval());
            r.put("note", sc.note());
            mapper.insertSchedule(r);
        }
        for (SeedDtos.Image im : nz(bus.images())) mapper.insertImage(busId, im.url(), im.caption());
        for (SeedDtos.FileRef f : nz(bus.files())) mapper.insertFile(busId, f.name(), f.url());
        for (SeedDtos.Alert a : nz(bus.alerts())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("type", a.type());
            r.put("message", a.message());
            r.put("startDate", parseDate(a.startDate()));
            r.put("endDate", parseDate(a.endDate()));
            mapper.insertAlert(r);
        }
    }

    /** SeedBus -> CanonicalBus,字段对齐(interval->intervalText, caption/name->label)。 */
    static CanonicalBus toCanonical(SeedDtos.Bus bus) {
        List<CanonicalBus.Schedule> schedules = new ArrayList<>();
        for (SeedDtos.Schedule sc : nz(bus.schedules()))
            schedules.add(new CanonicalBus.Schedule(sc.timeRange(), sc.interval(), sc.note()));
        List<CanonicalBus.Alert> alerts = new ArrayList<>();
        for (SeedDtos.Alert a : nz(bus.alerts()))
            alerts.add(new CanonicalBus.Alert(a.type(), a.message(), a.startDate(), a.endDate()));
        List<CanonicalBus.Media> images = new ArrayList<>();
        for (SeedDtos.Image im : nz(bus.images()))
            images.add(new CanonicalBus.Media(im.url(), im.caption()));
        List<CanonicalBus.Media> files = new ArrayList<>();
        for (SeedDtos.FileRef f : nz(bus.files()))
            files.add(new CanonicalBus.Media(f.url(), f.name()));
        return new CanonicalBus(bus.route(), bus.destination(), bus.operator(), bus.duration(),
                bus.price(), bus.operatingHours(), nz(bus.stops()), schedules, alerts, images, files);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim());
    }

    private static <T> List<T> nz(List<T> in) {
        return in == null ? List.of() : in;
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=SeedImporterIT test`
Expected: PASS(幂等、hash 稳定、停靠站不翻倍)。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/seed backend/src/main/java/com/airportbus/bus/mapper/BusWriteMapper.java backend/src/main/resources/mapper/BusWriteMapper.xml backend/src/test/java/com/airportbus/bus/seed
git commit -m "feat(bus): idempotent seed importer reusing canonicalizer (E2/E11)"
```

---

## Task 8: 启动时按开关导入(D4)

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/seed/SeedImportRunner.java`

- [ ] **Step 1: 写启动 Runner**

`backend/src/main/java/com/airportbus/bus/seed/SeedImportRunner.java`
```java
package com.airportbus.bus.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** D4:SEED_ENABLED=true 时启动幂等导入。重跑安全。 */
@Component
@ConditionalOnProperty(name = "airportbus.seed.enabled", havingValue = "true")
public class SeedImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedImportRunner.class);
    private final SeedImporter importer;

    public SeedImportRunner(SeedImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seed import starting (idempotent)...");
        importer.importFromClasspath("data/data.json");
        log.info("Seed import done.");
    }
}
```

- [ ] **Step 2: 启动应用验证导入入库**

Run: `cd backend && mvn -q spring-boot:run`(等 "Seed import done." 后 Ctrl-C)
然后:`docker compose exec -T mysql mysql -uairportbus -pairportbus airportbus -e "SELECT source_id, route FROM bus_route LIMIT 5;"`
Expected: 打印出 `vie-vab1` 等线路行。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/seed/SeedImportRunner.java
git commit -m "feat(bus): run idempotent seed import on startup behind flag (D4)"
```

---

## Task 9: 查询 Mapper + DTO + Service(含 Redis 缓存)

只读查询:树、机场下线路列表、线路详情。Redis 用 Spring Cache,TTL + null 缓存(防击穿)。本期无写操作,失效靠 TTL(写失效钩子留给 admin 计划)。

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/api/dto/TreeDto.java`
- Create: `backend/src/main/java/com/airportbus/bus/api/dto/BusSummaryDto.java`
- Create: `backend/src/main/java/com/airportbus/bus/api/dto/BusDetailDto.java`
- Create: `backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java`
- Create: `backend/src/main/resources/mapper/BusQueryMapper.xml`
- Create: `backend/src/main/java/com/airportbus/common/RedisCacheConfig.java`
- Create: `backend/src/main/java/com/airportbus/bus/service/BusQueryService.java`
- Test: `backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java`

- [ ] **Step 1: 写 DTO**

`backend/src/main/java/com/airportbus/bus/api/dto/TreeDto.java`
```java
package com.airportbus.bus.api.dto;

import java.util.List;

public record TreeDto(List<Country> countries) {
    public record Country(String code, String name, List<City> cities) {}
    public record City(String name, List<Airport> airports) {}
    public record Airport(String code, String name) {}
}
```

`backend/src/main/java/com/airportbus/bus/api/dto/BusSummaryDto.java`
```java
package com.airportbus.bus.api.dto;

import java.time.LocalDate;

public record BusSummaryDto(
        String sourceId, String route, String destination, String operator,
        String duration, String price, LocalDate lastUpdated, boolean fetchFailed) {}
```

`backend/src/main/java/com/airportbus/bus/api/dto/BusDetailDto.java`
```java
package com.airportbus.bus.api.dto;

import java.time.LocalDate;
import java.util.List;

public record BusDetailDto(
        String sourceId, String route, String destination, String operator, String officialUrl,
        String duration, String price, String operatingHours,
        LocalDate lastUpdated, boolean fetchFailed,
        List<String> stops,
        List<Schedule> schedules,
        List<Image> images,
        List<FileRef> files,
        List<Alert> alerts) {

    public record Schedule(String timeRange, String intervalText, String note) {}
    public record Image(String url, String caption) {}
    public record FileRef(String name, String url) {}
    public record Alert(String type, String message, LocalDate startDate, LocalDate endDate) {}

    /** MyBatis 查 bus_route 头部行用;Service 再拼子表。 */
    public record HeadRow(Long id, String sourceId, String route, String destination, String operator,
                          String officialUrl, String duration, String price, String operatingHours,
                          LocalDate lastUpdated, boolean fetchFailed) {}
}
```

- [ ] **Step 2: 写查询 Mapper 接口**

`backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java`
```java
package com.airportbus.bus.mapper;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusSummaryDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BusQueryMapper {
    List<TreeRow> selectTreeRows();

    Long selectAirportIdByCode(@Param("code") String airportCode);
    List<BusSummaryDto> selectBusesByAirport(@Param("code") String airportCode);

    BusDetailDto.HeadRow selectBusHead(@Param("sourceId") String sourceId);
    List<String> selectStops(@Param("busId") Long busId);
    List<BusDetailDto.Schedule> selectSchedules(@Param("busId") Long busId);
    List<BusDetailDto.Image> selectImages(@Param("busId") Long busId);
    List<BusDetailDto.FileRef> selectFiles(@Param("busId") Long busId);
    List<BusDetailDto.Alert> selectAlerts(@Param("busId") Long busId);

    record TreeRow(String countryCode, String countryName, String cityName,
                   String airportCode, String airportName) {}
}
```

- [ ] **Step 3: 写查询 Mapper XML**

`backend/src/main/resources/mapper/BusQueryMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.airportbus.bus.mapper.BusQueryMapper">

  <select id="selectTreeRows" resultType="com.airportbus.bus.mapper.BusQueryMapper$TreeRow">
    SELECT c.code AS countryCode, c.name AS countryName, ci.name AS cityName,
           a.code AS airportCode, a.name AS airportName
    FROM country c
    JOIN city ci   ON ci.country_id = c.id
    JOIN airport a ON a.city_id = ci.id
    ORDER BY c.name, ci.name, a.name
  </select>

  <select id="selectAirportIdByCode" resultType="java.lang.Long">
    SELECT id FROM airport WHERE code = #{code}
  </select>

  <select id="selectBusesByAirport" resultType="com.airportbus.bus.api.dto.BusSummaryDto">
    SELECT b.source_id AS sourceId, b.route, b.destination, b.operator,
           b.duration, b.price, b.last_updated AS lastUpdated, b.fetch_failed AS fetchFailed
    FROM bus_route b
    JOIN airport a ON a.id = b.airport_id
    WHERE a.code = #{code}
    ORDER BY b.route
  </select>

  <select id="selectBusHead" resultType="com.airportbus.bus.api.dto.BusDetailDto$HeadRow">
    SELECT id, source_id AS sourceId, route, destination, operator, official_url AS officialUrl,
           duration, price, operating_hours AS operatingHours,
           last_updated AS lastUpdated, fetch_failed AS fetchFailed
    FROM bus_route WHERE source_id = #{sourceId}
  </select>

  <select id="selectStops" resultType="java.lang.String">
    SELECT name FROM bus_stop WHERE bus_route_id = #{busId} ORDER BY seq
  </select>
  <select id="selectSchedules" resultType="com.airportbus.bus.api.dto.BusDetailDto$Schedule">
    SELECT time_range AS timeRange, interval_text AS intervalText, note
    FROM bus_schedule WHERE bus_route_id = #{busId} ORDER BY id
  </select>
  <select id="selectImages" resultType="com.airportbus.bus.api.dto.BusDetailDto$Image">
    SELECT url, caption FROM bus_image WHERE bus_route_id = #{busId} ORDER BY id
  </select>
  <select id="selectFiles" resultType="com.airportbus.bus.api.dto.BusDetailDto$FileRef">
    SELECT name, url FROM bus_file WHERE bus_route_id = #{busId} ORDER BY id
  </select>
  <select id="selectAlerts" resultType="com.airportbus.bus.api.dto.BusDetailDto$Alert">
    SELECT type, message, start_date AS startDate, end_date AS endDate
    FROM bus_alert WHERE bus_route_id = #{busId} ORDER BY id
  </select>
</mapper>
```

- [ ] **Step 4: 写 Redis 缓存配置**

`backend/src/main/java/com/airportbus/common/RedisCacheConfig.java`
```java
package com.airportbus.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))               // TTL 兜底
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        // 默认允许缓存 null(未调 disableCachingNullValues)→ 防穿透
        return RedisCacheManager.builder(cf).cacheDefaults(config).build();
    }
}
```

- [ ] **Step 5: 写 Service 失败测试**

`backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java`
```java
package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.seed.SeedImporter;
import com.airportbus.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"airportbus.seed.enabled=false", "spring.cache.type=none"})
@Testcontainers
class BusQueryServiceIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("airportbus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @MockBean RedisConnectionFactory redisConnectionFactory;

    @Autowired SeedImporter importer;
    @Autowired BusQueryService service;

    @BeforeEach
    void seed() { importer.importFromClasspath("data/data.json"); }

    @Test
    void treeHasTwoCountries() {
        assertThat(service.tree().countries()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void detailLoadsStopsInOrder() {
        BusDetailDto d = service.detail("vie-vab1");
        assertThat(d.route()).isEqualTo("VAB 1");
        assertThat(d.stops()).containsExactly(
                "维也纳西站 Westbahnhof", "维也纳中央车站 Hauptbahnhof (南入口)", "维也纳机场");
    }

    @Test
    void unknownBusThrowsNotFound() {
        assertThatThrownBy(() -> service.detail("nope-xxx")).isInstanceOf(ApiException.class);
    }
}
```

Run: `cd backend && mvn -q -Dtest=BusQueryServiceIT test`
Expected: FAIL —— `BusQueryService` 不存在。

- [ ] **Step 6: 实现 Service**

`backend/src/main/java/com/airportbus/bus/service/BusQueryService.java`
```java
package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.*;
import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BusQueryService {

    private final BusQueryMapper mapper;

    public BusQueryService(BusQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Cacheable(cacheNames = "tree")
    public TreeDto tree() {
        Map<String, TreeAcc> countries = new LinkedHashMap<>();
        for (BusQueryMapper.TreeRow row : mapper.selectTreeRows()) {
            TreeAcc c = countries.computeIfAbsent(row.countryCode(),
                    k -> new TreeAcc(row.countryName()));
            c.cities.computeIfAbsent(row.cityName(), k -> new ArrayList<>())
                    .add(new TreeDto.Airport(row.airportCode(), row.airportName()));
        }
        List<TreeDto.Country> result = new ArrayList<>();
        countries.forEach((code, acc) -> {
            List<TreeDto.City> cityList = new ArrayList<>();
            acc.cities.forEach((cityName, airports) ->
                    cityList.add(new TreeDto.City(cityName, airports)));
            result.add(new TreeDto.Country(code, acc.name, cityList));
        });
        return new TreeDto(result);
    }

    @Cacheable(cacheNames = "airportBuses", key = "#airportCode")
    public List<BusSummaryDto> busesByAirport(String airportCode) {
        if (mapper.selectAirportIdByCode(airportCode) == null) {
            throw new ApiException(ErrorCode.AIRPORT_NOT_FOUND, "no airport: " + airportCode);
        }
        return mapper.selectBusesByAirport(airportCode); // 空机场返回空数组
    }

    @Cacheable(cacheNames = "busDetail", key = "#sourceId")
    public BusDetailDto detail(String sourceId) {
        BusDetailDto.HeadRow h = mapper.selectBusHead(sourceId);
        if (h == null) throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: " + sourceId);
        return new BusDetailDto(
                h.sourceId(), h.route(), h.destination(), h.operator(), h.officialUrl(),
                h.duration(), h.price(), h.operatingHours(), h.lastUpdated(), h.fetchFailed(),
                mapper.selectStops(h.id()),
                mapper.selectSchedules(h.id()),
                mapper.selectImages(h.id()),
                mapper.selectFiles(h.id()),
                mapper.selectAlerts(h.id()));
    }

    private static final class TreeAcc {
        final String name;
        final Map<String, List<TreeDto.Airport>> cities = new LinkedHashMap<>();
        TreeAcc(String name) { this.name = name; }
    }
}
```

- [ ] **Step 7: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=BusQueryServiceIT test`
Expected: PASS(树 ≥2 国、详情 stops 有序、未知线路抛 ApiException)。

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/api/dto backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java backend/src/main/resources/mapper/BusQueryMapper.xml backend/src/main/java/com/airportbus/common/RedisCacheConfig.java backend/src/main/java/com/airportbus/bus/service backend/src/test/java/com/airportbus/bus/service
git commit -m "feat(bus): query service for tree/airport-buses/detail with redis cache"
```

---

## Task 10: 查询 Controller + OpenAPI(D1/D5)

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/api/BusQueryController.java`
- Test: `backend/src/test/java/com/airportbus/bus/api/BusQueryControllerTest.java`

- [ ] **Step 1: 写失败的 Controller 切片测试**

`backend/src/test/java/com/airportbus/bus/api/BusQueryControllerTest.java`
```java
package com.airportbus.bus.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.service.BusQueryService;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusQueryController.class)
@Import(GlobalExceptionHandler.class)
class BusQueryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BusQueryService service;

    @Test
    void detailReturnsResourceBody() throws Exception {
        when(service.detail("vie-vab1")).thenReturn(new BusDetailDto(
                "vie-vab1", "VAB 1", "Westbahnhof", "ÖBB", "http://x", "40min", "€11", "03:00-24:00",
                null, false, List.of("A"), List.of(), List.of(), List.of(), List.of()));
        mvc.perform(get("/api/v1/buses/vie-vab1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sourceId").value("vie-vab1"))
           .andExpect(jsonPath("$.route").value("VAB 1"));
    }

    @Test
    void unknownBusReturns404Envelope() throws Exception {
        when(service.detail("nope")).thenThrow(new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: nope"));
        mvc.perform(get("/api/v1/buses/nope"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"));
    }
}
```

Run: `cd backend && mvn -q -Dtest=BusQueryControllerTest test`
Expected: FAIL —— `BusQueryController` 不存在。

- [ ] **Step 2: 实现 Controller**

`backend/src/main/java/com/airportbus/bus/api/BusQueryController.java`
```java
package com.airportbus.bus.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusSummaryDto;
import com.airportbus.bus.api.dto.TreeDto;
import com.airportbus.bus.service.BusQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "bus-query", description = "公开巴士查询(零登录)")
@RestController
@RequestMapping("/api/v1")
public class BusQueryController {

    private final BusQueryService service;

    public BusQueryController(BusQueryService service) {
        this.service = service;
    }

    @Operation(summary = "国家/城市/机场 导航树")
    @GetMapping("/tree")
    public TreeDto tree() {
        return service.tree();
    }

    @Operation(summary = "某机场下的巴士线路列表(空机场返回空数组)")
    @GetMapping("/airports/{code}/buses")
    public List<BusSummaryDto> busesByAirport(@PathVariable String code) {
        return service.busesByAirport(code);
    }

    @Operation(summary = "线路详情(按 source_id)")
    @GetMapping("/buses/{sourceId}")
    public BusDetailDto detail(@PathVariable String sourceId) {
        return service.detail(sourceId);
    }
}
```

- [ ] **Step 3: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=BusQueryControllerTest test`
Expected: PASS。

- [ ] **Step 4: 启动并人工验证三个端点 + Swagger**

Run: `cd backend && mvn -q spring-boot:run`(另开终端):
```bash
curl -s localhost:8080/api/v1/tree | head -c 200
curl -s localhost:8080/api/v1/buses/vie-vab1 | head -c 200
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/v1/buses/nope   # 期望 404
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/swagger-ui/index.html # 期望 200
```
Expected: tree/detail 返回 JSON;未知线路 404;swagger 200。验证完 Ctrl-C。

- [ ] **Step 5: 跑全部后端测试**

Run: `cd backend && mvn -q test`
Expected: 全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/api/BusQueryController.java backend/src/test/java/com/airportbus/bus/api/BusQueryControllerTest.java
git commit -m "feat(bus): query REST endpoints + OpenAPI (D1/D5)"
```

---

## Task 11: 前端脚手架(Vite + Vue3 + TS + i18n + VueQuery + 设计稿 tokens)

**Files:**
- Create: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/index.html`, `frontend/.env.example`
- Create: `frontend/src/main.ts`, `frontend/src/App.vue`
- Create: `frontend/src/i18n/index.ts`, `frontend/src/i18n/locales/zh-CN.ts`, `frontend/src/i18n/locales/en.ts`
- Create: `frontend/src/router/index.ts`

- [ ] **Step 1: 写 package.json**

`frontend/package.json`
```json
{
  "name": "airportbus-frontend",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "@tanstack/vue-query": "^5.51.0",
    "axios": "^1.7.0",
    "pinia": "^2.1.0",
    "vue": "^3.4.0",
    "vue-i18n": "^9.13.0",
    "vue-router": "^4.3.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "@vue/test-utils": "^2.4.0",
    "jsdom": "^24.0.0",
    "typescript": "^5.4.0",
    "vite": "^5.2.0",
    "vitest": "^1.6.0",
    "vue-tsc": "^2.0.0"
  }
}
```

- [ ] **Step 2: 写 vite.config.ts(含 dev proxy,D4 免 CORS)**

`frontend/vite.config.ts`
```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
```

- [ ] **Step 3: 写 tsconfig.json**

`frontend/tsconfig.json`
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "jsx": "preserve",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "types": ["vitest/globals"],
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["src/**/*.ts", "src/**/*.vue"]
}
```

- [ ] **Step 4: 写 index.html + .env.example**

`frontend/index.html`(引入设计稿字体;全局样式由 `main.ts` 导入)
```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>全球机场巴士信息</title>
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Sora:wght@400;600;700;800&family=Noto+Sans+SC:wght@400;500;700&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet" />
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

> **设计稿样式落地**:把 `design/styles.css` 复制到 `frontend/src/styles/tokens.css`(tokens + 组件类,单一事实源),在 `main.ts` 里 `import './styles/tokens.css'`。Vue 组件直接用其中的 class(`.card`/`.stops`/`.schedTable`/`.alert`/`.chip`/`.skel`/`.empty` 等),不再逐组件写重复 CSS。

`frontend/.env.example`
```
VITE_API_TARGET=http://localhost:8080
```

- [ ] **Step 5: 写 i18n(zh-CN / en,locale 持久化)**

`frontend/src/i18n/locales/zh-CN.ts`
```ts
export default {
  app: { title: '全球机场巴士信息' },
  home: { searchAirport: '搜索机场', onlyTwoCities: '当前仅覆盖维也纳、上海两座城市。' },
  detail: { destination: '目的地', duration: '时长', price: '价格', hours: '运营时间',
            stops: '停靠站', schedules: '班次', images: '图片', files: '文件', official: '官方链接' },
  state: { loading: '加载中…', error: '加载失败,请重试', empty: '暂无数据' },
  freshness: { updated: '数据日期', fetchFailed: '抓取失败,信息可能过期' },
}
```

`frontend/src/i18n/locales/en.ts`
```ts
export default {
  app: { title: 'Global Airport Bus Info' },
  home: { searchAirport: 'Search airport', onlyTwoCities: 'Currently only Vienna and Shanghai are covered.' },
  detail: { destination: 'Destination', duration: 'Duration', price: 'Price', hours: 'Operating hours',
            stops: 'Stops', schedules: 'Schedules', images: 'Images', files: 'Files', official: 'Official link' },
  state: { loading: 'Loading…', error: 'Failed to load, please retry', empty: 'No data' },
  freshness: { updated: 'Data date', fetchFailed: 'Fetch failed, info may be outdated' },
}
```

`frontend/src/i18n/index.ts`
```ts
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'

const saved = localStorage.getItem('locale')
const fallback = navigator.language.startsWith('zh') ? 'zh-CN' : 'en'

export const i18n = createI18n({
  legacy: false,
  locale: saved ?? fallback,
  fallbackLocale: 'en',
  messages: { 'zh-CN': zhCN, en },
})

export function setLocale(locale: 'zh-CN' | 'en') {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
}
```

- [ ] **Step 6: 写 router(占位页留待后续任务填充)**

`frontend/src/router/index.ts`
```ts
import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../pages/HomePage.vue') },
    { path: '/airports/:code', name: 'airport', component: () => import('../pages/AirportBusesPage.vue'), props: true },
    { path: '/bus/:sourceId', name: 'bus', component: () => import('../pages/BusDetailPage.vue'), props: true },
  ],
})
```

- [ ] **Step 7: 写 main.ts + App.vue**

`frontend/src/main.ts`
```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import './styles/tokens.css'        // 设计稿全局样式(由 design/styles.css 复制而来)
import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(i18n)
  .use(VueQueryPlugin)               // 不挂 Element Plus(公开页用设计稿手写组件)
  .mount('#app')
```

`frontend/src/App.vue`
```vue
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale } from './i18n'
const { t, locale } = useI18n()
</script>

<template>
  <header class="topbar">
    <router-link to="/" class="brand">{{ t('app.title') }}</router-link>
    <select :value="locale" @change="(e) => setLocale((e.target as HTMLSelectElement).value as 'zh-CN' | 'en')">
      <option value="zh-CN">中文</option>
      <option value="en">English</option>
    </select>
  </header>
  <main class="content"><router-view /></main>
</template>

<style>
:root { --maxw: 880px; }
.topbar { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #eee; }
.brand { font-weight:700; text-decoration:none; color:inherit; }
.content { max-width: var(--maxw); margin: 0 auto; padding: 16px; }
</style>
```

- [ ] **Step 8: 安装依赖**

Run: `cd frontend && npm install`
Expected: 安装成功(`vue-tsc` 类型检查等占位页建好后再跑,见 Task 13 Step 0)。

- [ ] **Step 9: Commit**

```bash
mkdir -p frontend/src/styles && cp design/styles.css frontend/src/styles/tokens.css   # 设计稿样式进工程(单一事实源)
git add frontend/package.json frontend/vite.config.ts frontend/tsconfig.json frontend/index.html frontend/.env.example frontend/src/main.ts frontend/src/App.vue frontend/src/styles/tokens.css frontend/src/i18n frontend/src/router
git commit -m "chore(frontend): scaffold Vue3 + Vite + i18n + VueQuery + design tokens"
```

---

## Task 12: 前端 API 客户端 + 类型(对齐后端契约)

**Files:**
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/bus.ts`
- Test: `frontend/src/test/client.spec.ts`

- [ ] **Step 1: 写 axios 客户端(解析错误包络)**

`frontend/src/api/client.ts`
```ts
import axios, { AxiosError } from 'axios'

export interface ApiError {
  code: string
  message: string
  details: { field: string; issue: string }[]
  traceId: string
}

export const http = axios.create({ baseURL: '/api/v1' })

http.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = localStorage.getItem('locale') ?? 'en'
  return config
})

export function asApiError(err: unknown): ApiError | null {
  const e = err as AxiosError<ApiError>
  if (e.isAxiosError && e.response?.data?.code) return e.response.data
  return null
}
```

- [ ] **Step 2: 写类型 + 调用(对齐后端 DTO 字段名)**

`frontend/src/api/bus.ts`
```ts
import { http } from './client'

export interface Tree {
  countries: { code: string; name: string; cities: { name: string; airports: { code: string; name: string }[] }[] }[]
}
export interface BusSummary {
  sourceId: string; route: string; destination: string | null; operator: string | null
  duration: string | null; price: string | null; lastUpdated: string | null; fetchFailed: boolean
}
export interface Alert { type: string; message: string; startDate: string | null; endDate: string | null }
export interface BusDetail {
  sourceId: string; route: string; destination: string | null; operator: string | null; officialUrl: string | null
  duration: string | null; price: string | null; operatingHours: string | null
  lastUpdated: string | null; fetchFailed: boolean
  stops: string[]
  schedules: { timeRange: string | null; intervalText: string | null; note: string | null }[]
  images: { url: string; caption: string | null }[]
  files: { name: string | null; url: string }[]
  alerts: Alert[]
}

export const getTree = () => http.get<Tree>('/tree').then((r) => r.data)
export const getAirportBuses = (code: string) =>
  http.get<BusSummary[]>(`/airports/${encodeURIComponent(code)}/buses`).then((r) => r.data)
export const getBusDetail = (sourceId: string) =>
  http.get<BusDetail>(`/buses/${encodeURIComponent(sourceId)}`).then((r) => r.data)
```

- [ ] **Step 3: 写失败测试**

`frontend/src/test/client.spec.ts`
```ts
import { describe, it, expect } from 'vitest'
import { asApiError } from '../api/client'

describe('asApiError', () => {
  it('extracts the structured envelope from an axios error', () => {
    const fakeErr = {
      isAxiosError: true,
      response: { data: { code: 'BUS_NOT_FOUND', message: 'no bus', details: [], traceId: 't1' } },
    }
    const parsed = asApiError(fakeErr)
    expect(parsed?.code).toBe('BUS_NOT_FOUND')
  })

  it('returns null for non-envelope errors', () => {
    expect(asApiError(new Error('boom'))).toBeNull()
  })
})
```

Run: `cd frontend && npx vitest run src/test/client.spec.ts`
Expected: 先 FAIL(client.ts 未就位时)→ 写好后 PASS。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/test/client.spec.ts`
Expected: PASS(2 测试)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api frontend/src/test/client.spec.ts
git commit -m "feat(frontend): typed api client matching backend contract"
```

---

## Task 13: 通用状态组件(DS3)

loading 骨架 / 错误 / 空态统一组件 + 过期 alert 过滤 + fetch_failed 徽标。

**Files:**
- Create: `frontend/src/pages/HomePage.vue`, `AirportBusesPage.vue`, `BusDetailPage.vue`(空占位,先让路由可编译)
- Create: `frontend/src/components/StateBlock.vue`
- Create: `frontend/src/components/alertFilter.ts`
- Create: `frontend/src/components/AlertList.vue`
- Create: `frontend/src/components/FreshnessBadge.vue`
- Test: `frontend/src/test/AlertList.spec.ts`

- [ ] **Step 0: 建空占位页(让 Task 11 的路由可编译)**

`frontend/src/pages/HomePage.vue`、`AirportBusesPage.vue`、`BusDetailPage.vue` 各写:
```vue
<template><div /></template>
```
然后类型检查:`cd frontend && npx vue-tsc --noEmit`(应通过)。

- [ ] **Step 1: 写 StateBlock**

`frontend/src/components/StateBlock.vue`(手写,复用 tokens.css 的 `.skel` / `.empty`)
```vue
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ loading?: boolean; error?: boolean; empty?: boolean; emptyText?: string }>()
const { t } = useI18n()
</script>

<template>
  <div v-if="loading" class="skelCard">
    <div class="skel skelLine" style="width:40%"></div>
    <div class="skel" style="width:62%;height:20px;margin:10px 0"></div>
    <div class="skel skelLine" style="width:80%"></div>
  </div>
  <div v-else-if="error" class="empty">
    <div class="emptyIcon">⚠️</div>
    <p>{{ t('state.error') }}</p>
  </div>
  <div v-else-if="empty" class="empty">
    <div class="emptyIcon">🚌</div>
    <p>{{ emptyText ?? t('state.empty') }}</p>
  </div>
  <slot v-else />
</template>
```

- [ ] **Step 2: 写 FreshnessBadge(DS3:fetch_failed + last_updated)**

`frontend/src/components/FreshnessBadge.vue`(手写 `.chip`;本期两档:抓取失败=warn,否则数据日期=普通。绿「近期已核对」档需 EN4 的人工核对时间,后续模块再加)
```vue
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ lastUpdated: string | null; fetchFailed: boolean }>()
const { t } = useI18n()
</script>

<template>
  <span class="chip" :class="{ warn: fetchFailed }">
    <span class="d"></span>
    <template v-if="fetchFailed">{{ t('freshness.fetchFailed') }}</template>
    <template v-else>{{ t('freshness.updated') }} {{ lastUpdated }}</template>
  </span>
</template>
```

- [ ] **Step 3: 写 AlertList 失败测试(过期过滤)**

`frontend/src/test/AlertList.spec.ts`
```ts
import { describe, it, expect } from 'vitest'
import { activeAlerts } from '../components/alertFilter'

const today = '2026-06-11'

describe('activeAlerts', () => {
  it('drops alerts whose endDate is before today', () => {
    const alerts = [
      { type: 'info', message: 'old', startDate: null, endDate: '2026-01-01' },
      { type: 'info', message: 'live', startDate: null, endDate: '2026-12-31' },
    ]
    expect(activeAlerts(alerts, today).map((a) => a.message)).toEqual(['live'])
  })

  it('keeps alerts with no endDate (long-term)', () => {
    const alerts = [{ type: 'warn', message: 'forever', startDate: null, endDate: null }]
    expect(activeAlerts(alerts, today)).toHaveLength(1)
  })
})
```

Run: `cd frontend && npx vitest run src/test/AlertList.spec.ts`
Expected: FAIL —— `alertFilter` 不存在。

- [ ] **Step 4: 实现过滤逻辑 + AlertList 组件**

`frontend/src/components/alertFilter.ts`
```ts
import type { Alert } from '../api/bus'

/** end_date 早于今天的过期 alert 过滤掉;无 end_date 视为长期保留(DS3)。 */
export function activeAlerts(alerts: Alert[], today: string): Alert[] {
  return alerts.filter((a) => !a.endDate || a.endDate >= today)
}
```

`frontend/src/components/AlertList.vue`(手写 `.alert`;`info` → 蓝边 `.alertInfo`,其余橙边。类型文案先内联,后续按 D6 走 vue-i18n)
```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { Alert } from '../api/bus'
import { activeAlerts } from './alertFilter'

const props = defineProps<{ alerts: Alert[] }>()
const today = new Date().toISOString().slice(0, 10)
const active = computed(() => activeAlerts(props.alerts, today))
const icon = (t: string) => (t === 'reroute' ? '🔁' : t === 'warning' ? '⚠️' : 'ℹ️')
const label = (t: string) => (t === 'reroute' ? '改道' : t === 'warning' ? '注意' : '提醒')
</script>

<template>
  <div v-for="(a, i) in active" :key="i" class="alert" :class="{ alertInfo: a.type === 'info' }">
    <span class="alertIcon">{{ icon(a.type) }}</span>
    <div class="alertBody">
      <span class="alertType">{{ label(a.type) }}</span>{{ a.message }}
      <span v-if="a.startDate || a.endDate" class="alertDate">{{ a.startDate }} → {{ a.endDate }}</span>
    </div>
  </div>
</template>
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/test/AlertList.spec.ts`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components frontend/src/pages frontend/src/test/AlertList.spec.ts
git commit -m "feat(frontend): shared state/alert/freshness components (DS3)"
```

---

## Task 14: 落地页(城市卡片 + 机场入口,DS1/DS4)

DS1:两城直接出城市卡片,单选项层级跳过;DS4:全程零登录。

> **以设计稿 `design/home.html` 为准(定稿优先于下方早期示例)**:落地页最终是 **搜索框(反向检索入口,EN2)+ 国家/城市/机场 三联选择器 + 选中机场后展示线路卡**。线路卡复用 Task 16 的统一卡片骨架(`.card`/`.metaRow`/`.stops`/`.alert`/`.chip`,本期只读、无收藏/上报按钮)。下面的「城市卡片」示例是更早的简化版,可作为 `/tree` 数据到位前的占位;真正实现按设计稿的选择器 + 卡片来,二者都吃同一个 `/api/v1/tree` 与 `/airports/{code}/buses`。

**Files:**
- Modify: `frontend/src/pages/HomePage.vue`(覆盖 Task 13 占位)
- Test: `frontend/src/test/HomePage.spec.ts`

- [ ] **Step 1: 写失败测试(渲染城市卡片)**

`frontend/src/test/HomePage.spec.ts`
```ts
import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('../api/bus', () => ({
  getTree: vi.fn().mockResolvedValue({
    countries: [{ code: 'AT', name: 'Austria',
      cities: [{ name: 'Vienna', airports: [{ code: 'VIE', name: 'Vienna Intl' }] }] }],
  }),
}))

import HomePage from '../pages/HomePage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('HomePage', () => {
  it('renders a city card from the tree', async () => {
    const wrapper = mount(HomePage, { global: { plugins: [i18n, VueQueryPlugin], stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('Vienna')
  })
})
```

Run: `cd frontend && npx vitest run src/test/HomePage.spec.ts`
Expected: FAIL —— HomePage 还是空占位。

- [ ] **Step 2: 实现 HomePage**

`frontend/src/pages/HomePage.vue`
```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { getTree } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'

const { t } = useI18n()
const { data, isLoading, isError } = useQuery({ queryKey: ['tree'], queryFn: getTree })

// DS1:把树拍平成「城市 → 机场」卡片,城市少时直接平铺
const cities = computed(() =>
  (data.value?.countries ?? []).flatMap((c) =>
    c.cities.map((city) => ({ country: c.name, name: city.name, airports: city.airports }))))
</script>

<template>
  <StateBlock :loading="isLoading" :error="isError" :empty="!isLoading && !cities.length"
              :empty-text="t('home.onlyTwoCities')">
    <p class="hint">{{ t('home.onlyTwoCities') }}</p>
    <div class="grid">
      <div v-for="city in cities" :key="city.name" class="card">
        <h3>{{ city.name }}</h3>
        <small>{{ city.country }}</small>
        <ul>
          <li v-for="ap in city.airports" :key="ap.code">
            <router-link :to="{ name: 'airport', params: { code: ap.code } }">
              {{ ap.name }} ({{ ap.code }})
            </router-link>
          </li>
        </ul>
      </div>
    </div>
  </StateBlock>
</template>

<style scoped>
.hint{color:#888;font-size:13px;margin:8px 0 16px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px}
.card{border:1px solid #eee;border-radius:12px;padding:16px}
.card h3{margin:0 0 4px}
.card ul{margin:12px 0 0;padding-left:18px}
</style>
```

- [ ] **Step 3: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/test/HomePage.spec.ts`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/HomePage.vue frontend/src/test/HomePage.spec.ts
git commit -m "feat(frontend): home page with city cards (DS1/DS4)"
```

---

## Task 15: 机场线路列表页

**Files:**
- Modify: `frontend/src/pages/AirportBusesPage.vue`
- Test: `frontend/src/test/AirportBusesPage.spec.ts`

- [ ] **Step 1: 写失败测试**

`frontend/src/test/AirportBusesPage.spec.ts`
```ts
import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('../api/bus', () => ({
  getAirportBuses: vi.fn().mockResolvedValue([
    { sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB',
      duration: '40min', price: '€11', lastUpdated: '2026-06-03', fetchFailed: false },
  ]),
}))

import AirportBusesPage from '../pages/AirportBusesPage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('AirportBusesPage', () => {
  it('lists buses for the airport code', async () => {
    const wrapper = mount(AirportBusesPage, {
      props: { code: 'VIE' },
      global: { plugins: [i18n, VueQueryPlugin], stubs },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('VAB 1')
    expect(wrapper.text()).toContain('Westbahnhof')
  })
})
```

Run: `cd frontend && npx vitest run src/test/AirportBusesPage.spec.ts`
Expected: FAIL(占位页)。

- [ ] **Step 2: 实现 AirportBusesPage**

`frontend/src/pages/AirportBusesPage.vue`
```vue
<script setup lang="ts">
import { toRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getAirportBuses } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import FreshnessBadge from '../components/FreshnessBadge.vue'

const props = defineProps<{ code: string }>()
const code = toRef(props, 'code')
const { data, isLoading, isError } = useQuery({
  queryKey: ['airportBuses', code],
  queryFn: () => getAirportBuses(code.value),
})
</script>

<template>
  <StateBlock :loading="isLoading" :error="isError" :empty="!isLoading && !(data?.length)">
    <ul class="routes">
      <li v-for="b in data" :key="b.sourceId" class="route">
        <router-link :to="{ name: 'bus', params: { sourceId: b.sourceId } }" class="route-link">
          <strong>{{ b.route }}</strong> → {{ b.destination }}
        </router-link>
        <div class="meta">{{ b.duration }} · {{ b.price }}</div>
        <FreshnessBadge :last-updated="b.lastUpdated" :fetch-failed="b.fetchFailed" />
      </li>
    </ul>
  </StateBlock>
</template>

<style scoped>
.routes{list-style:none;padding:0;margin:0;display:flex;flex-direction:column;gap:12px}
.route{border:1px solid #eee;border-radius:10px;padding:12px 14px}
.route-link{text-decoration:none;color:inherit}
.meta{color:#666;font-size:13px;margin:4px 0}
</style>
```

- [ ] **Step 3: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/test/AirportBusesPage.spec.ts`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/AirportBusesPage.vue frontend/src/test/AirportBusesPage.spec.ts
git commit -m "feat(frontend): airport bus list page"
```

---

## Task 16: 线路详情页(DS2 信息优先级 + 统一卡片排版)

按设计稿 `design/bus-detail.html`:统一卡片(`route`/`dest`/`operator` + 右上价格)→ 时长/运营 metaRow → **竖向时间轴**停靠站 → 班次表 → **提醒放下方**(过期已过滤)→ 图片/文件 → 新鲜度。移动优先。**本期只读**:收藏按钮、纠错上报弹窗随后续 user/feedback 模块加,这里不渲染。

**Files:**
- Modify: `frontend/src/pages/BusDetailPage.vue`
- Test: `frontend/src/test/BusDetailPage.spec.ts`

- [ ] **Step 1: 写失败测试**

`frontend/src/test/BusDetailPage.spec.ts`
```ts
import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('../api/bus', () => ({
  getBusDetail: vi.fn().mockResolvedValue({
    sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB',
    officialUrl: 'https://example.com', duration: '40min', price: '€11', operatingHours: '03:00-24:00',
    lastUpdated: '2026-06-03', fetchFailed: false,
    stops: ['Westbahnhof', 'Hauptbahnhof', 'Airport'],
    schedules: [{ timeRange: 'all day', intervalText: '30min', note: '' }],
    images: [], files: [],
    alerts: [{ type: 'info', message: 'live', startDate: null, endDate: '2099-01-01' }],
  }),
}))

import BusDetailPage from '../pages/BusDetailPage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })

describe('BusDetailPage', () => {
  it('shows decision bar fields and stops', async () => {
    const wrapper = mount(BusDetailPage, {
      props: { sourceId: 'vie-vab1' },
      global: { plugins: [i18n, VueQueryPlugin] },
    })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('€11')
    expect(text).toContain('Westbahnhof')
    expect(text).toContain('live') // 未过期 alert 展示
  })
})
```

Run: `cd frontend && npx vitest run src/test/BusDetailPage.spec.ts`
Expected: FAIL(占位页)。

- [ ] **Step 2: 实现 BusDetailPage**

`frontend/src/pages/BusDetailPage.vue`
```vue
<script setup lang="ts">
import { toRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { getBusDetail } from '../api/bus'
import StateBlock from '../components/StateBlock.vue'
import AlertList from '../components/AlertList.vue'
import FreshnessBadge from '../components/FreshnessBadge.vue'

const props = defineProps<{ sourceId: string }>()
const sourceId = toRef(props, 'sourceId')
const { t } = useI18n()
const { data, isLoading, isError } = useQuery({
  queryKey: ['busDetail', sourceId],
  queryFn: () => getBusDetail(sourceId.value),
})
</script>

<template>
  <StateBlock :loading="isLoading" :error="isError">
    <article v-if="data" class="card">
      <!-- 头部:路线/目的地/运营商 + 右上价格(收藏按钮随 user 模块加) -->
      <div class="card__top">
        <div>
          <div class="route">{{ data.route }}</div>
          <div class="dest">{{ data.destination }}</div>
          <div class="operator">{{ data.operator }}</div>
        </div>
        <div class="price">{{ data.price }}</div>
      </div>

      <!-- 时长 / 运营 -->
      <div class="metaRow">
        <div class="metaItem"><span class="metaLabel">{{ t('detail.duration') }}</span><span class="metaVal">{{ data.duration }}</span></div>
        <div class="metaItem"><span class="metaLabel">{{ t('detail.hours') }}</span><span class="metaVal">{{ data.operatingHours }}</span></div>
      </div>

      <!-- 途经站点:竖向时间轴 -->
      <div class="section" v-if="data.stops.length">
        <div class="secLabel">{{ t('detail.stops') }}</div>
        <div class="stops">
          <template v-for="(s, i) in data.stops" :key="i">
            <div class="stopRow">
              <span class="stopDot" :class="{ stopDotEnd: i === 0 || i === data.stops.length - 1 }"></span>
              <span :class="i === data.stops.length - 1 ? 'stopNameEnd' : 'stopName'">{{ s }}</span>
            </div>
            <div v-if="i < data.stops.length - 1" class="stopLine"></div>
          </template>
        </div>
      </div>

      <!-- 分时段班次 -->
      <div class="section" v-if="data.schedules.length">
        <div class="secLabel">{{ t('detail.schedules') }}</div>
        <table class="schedTable">
          <tr v-for="(s, i) in data.schedules" :key="i">
            <td class="schedTime">{{ s.timeRange }}</td>
            <td class="schedInterval">{{ s.intervalText }}</td>
            <td class="schedNote">{{ s.note }}</td>
          </tr>
        </table>
      </div>

      <!-- 提醒/改道:放下方(过期已过滤) -->
      <AlertList :alerts="data.alerts" />

      <!-- 图片 / 文件 -->
      <div class="media" v-if="data.images.length || data.files.length || data.officialUrl">
        <img v-for="(im, i) in data.images" :key="i" class="thumb" :src="im.url" :alt="im.caption ?? ''" />
        <div class="files">
          <a v-if="data.officialUrl" class="fileLink" :href="data.officialUrl" target="_blank" rel="noopener noreferrer">🔗 {{ t('detail.official') }}</a>
          <a v-for="(f, i) in data.files" :key="i" class="fileLink" :href="f.url" target="_blank" rel="noopener noreferrer">📄 {{ f.name ?? f.url }}</a>
        </div>
      </div>

      <!-- 新鲜度 -->
      <div class="updated">
        <FreshnessBadge :last-updated="data.lastUpdated" :fetch-failed="data.fetchFailed" />
      </div>
    </article>
  </StateBlock>
</template>
```
> 组件用的 `.card`/`.metaRow`/`.stops`/`.schedTable`/`.media`/`.fileLink`/`.updated` 全部来自 `tokens.css`(由 `design/styles.css` 而来),无需 scoped 样式。停靠站为**竖向时间轴**(对齐 home 与 `design/bus-detail.html`)。

- [ ] **Step 3: 跑测试确认通过**

Run: `cd frontend && npx vitest run src/test/BusDetailPage.spec.ts`
Expected: PASS。

- [ ] **Step 4: 跑全部前端测试 + 类型检查**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: 全部 PASS;`vue-tsc` 无错误。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/BusDetailPage.vue frontend/src/test/BusDetailPage.spec.ts
git commit -m "feat(frontend): bus detail page with DS2 info priority"
```

---

## Task 17: 端到端冒烟(前端 dev + 后端联调)

- [ ] **Step 1: 起后端 + 基础设施**

Run: `docker compose up -d mysql redis && cd backend && mvn -q spring-boot:run`(保持运行)

- [ ] **Step 2: 起前端 dev**

Run(另开终端): `cd frontend && npm run dev`,打开 `http://localhost:5173`。

- [ ] **Step 3: 人工核对主线**

核对清单:
- 落地页出现维也纳、上海城市卡片(DS1)。
- 点机场 → 列出线路;点线路 → 详情页决策栏首屏可见、停靠站 stepper、班次折叠。
- 切换语言中/英,标签文案变化(i18n)。
- 直接访问 `/bus/nope-xxx` → 详情页显示错误态(StateBlock error)。
- 全程没有任何登录墙(DS4)。

Expected: 上述全部成立。如有问题,用 superpowers:systematic-debugging 回到对应任务修复。

- [ ] **Step 4: 无代码改动则跳过 commit**(如修了 bug,按所在任务提交)

---

## Task 18: 容器化(后端 + 前端镜像)

**Files:**
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`, `frontend/nginx.conf`

- [ ] **Step 1: 写后端 Dockerfile**

`backend/Dockerfile`
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/airportbus-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: 写前端 Dockerfile + nginx**

`frontend/Dockerfile`
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

`frontend/nginx.conf`
```nginx
server {
  listen 80;
  location /api/ {
    proxy_pass http://app:8080;
    proxy_set_header Host $host;
  }
  location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;   # SPA history 路由回退
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile frontend/nginx.conf
git commit -m "chore: dockerfiles for backend jar and frontend nginx"
```

---

## Task 19: 完整 docker-compose 编排 + Quickstart(D4)

**Files:**
- Modify: `docker-compose.yml`(加 app + web 服务)
- Modify: `README.md`(加 Quickstart 命令序列)

- [ ] **Step 1: 扩展 docker-compose 加 app + web**

在 `docker-compose.yml` 的 `services:` 下、`volumes:` 之前追加:
```yaml
  app:
    build: ./backend
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      DB_HOST: mysql
      DB_PORT: 3306
      DB_USER: airportbus
      DB_PASSWORD: airportbus
      REDIS_HOST: redis
      REDIS_PORT: 6379
      SEED_ENABLED: "true"
    ports: ["8080:8080"]
  web:
    build: ./frontend
    depends_on: ["app"]
    ports: ["8081:80"]
```

- [ ] **Step 2: 一键起全栈并验证**

Run: `docker compose up -d --build && sleep 45`
然后:
```bash
curl -s -o /dev/null -w "tree=%{http_code}\n" localhost:8080/api/v1/tree
curl -s -o /dev/null -w "web=%{http_code}\n"  localhost:8081/
curl -s -o /dev/null -w "via-web-proxy=%{http_code}\n" localhost:8081/api/v1/buses/vie-vab1
```
Expected: 三个都 200。

- [ ] **Step 3: 写 README Quickstart**

在 `README.md`「下一步」之上追加:
````markdown
## Quickstart(本地一键起)

```bash
cp .env.example .env
docker compose up -d --build      # mysql + redis + 后端 + 前端
# 前端: http://localhost:8081
# 后端 API: http://localhost:8080/api/v1/tree
# Swagger: http://localhost:8080/swagger-ui/index.html
```

种子数据(维也纳/上海)在后端首启时幂等导入(`SEED_ENABLED=true`)。

### 本地开发(热重载)

```bash
docker compose up -d mysql redis
cd backend && mvn spring-boot:run         # :8080
cd frontend && npm install && npm run dev  # :5173,已配 /api 代理到 :8080
```
````

- [ ] **Step 4: 跑全量测试守门**

Run: `cd backend && mvn -q test && cd ../frontend && npx vitest run`
Expected: 前后端测试全绿。

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml README.md
git commit -m "chore: full-stack docker-compose + quickstart (D4)"
```

---

## 附加功能:机场搜索热度(本期记录,后台榜单后续)

记录每个机场被搜索/查看的次数,后台按热度排行。本计划只落**记录**侧(在查询路径累加);**后台展示**随 admin 模块做。

- **计数时机**:`GET /tree` 命中的机场聚合较粗,主要在 **`GET /airports/{code}/buses` 与 `GET /buses/{sourceId}`** 命中时对所属机场 `+1`。
- **写法(不阻塞查询)**:Redis `INCR airport:hot:{airportCode}`(及可选 `airport:hot:{code}:{yyyyMMdd}` 做趋势),`@Async` 触发,查询主流程不等待、失败不影响响应。
- **落库**:`@Scheduled` 周期(如每 5 分钟)把 Redis 计数刷入一张 `airport_search_stat(airport_id, day, cnt, ...)` 表(同样带审计/逻辑删除列);Redis 为加速、MySQL 为权威(与未读计数同一取舍,E13)。
- **隐私**:只计数,不记录用户/IP(与 audit_log 划清)。
- **后台(后续 admin 模块)**:按机场/时间窗读榜单 + 趋势图。

> 本期交付:查询端点接入异步计数 + `airport_search_stat` 表 + 周期落库;给计数加一个 service 单测(命中端点 → Redis 计数 +1)。

---

## Self-Review

**Spec coverage(对照 design.md 查询主线相关条目):**
- 三级筛选 + 两城详情 → Task 9/10(后端)、Task 14/15/16(前端)。✅
- 种子导入 + 幂等 + 共用 canonicalizer(E2/E11)→ Task 5/7/8。✅
- content_hash 覆盖子表 → Task 5(stops 保序、其余排序)。✅
- API 契约 + 错误包络(D1/D2)→ 头部「契约锁定」+ Task 6/10。✅
- source_id 对外标识(D3)→ 全程锁定,Task 9/10/12。✅
- Redis 缓存 TTL + null 缓存 → Task 9。✅(写失效钩子有意延后到 admin 计划——本期无写。)
- OpenAPI/swagger(D5)→ Task 10。✅
- TTHW/vendor data.json/compose/.env/quickstart(D4)→ Task 2/4/18/19。✅
- DS1 直达(搜索 + 选择器 + 卡片)→ Task 14(设计稿 `home.html`);DS2 信息优先级 → Task 16;DS3 状态全集 → Task 13;DS4 零登录 → 全程无登录墙;DS5 移动优先 → 设计稿 tokens 的响应式断点(`design/styles.css`,公开页不用 Element Plus)。✅
- 空态文案「仅覆盖两城」→ Task 11 i18n + Task 14。✅
- i18n zh-CN/en + locale 偏好 + Accept-Language → Task 11/12。✅

**有意排除(后续计划):** 用户/收藏/站内信/推送闭环/工单/后台/审计;EN1 SSR、EN2 反向检索、EN3 变更历史、EN4 PWA(本期只把 content_hash 算出存好,为 EN3/推送打地基)。

**Placeholder 扫描:** 每个代码步骤均含完整可运行代码,无 TODO/「类似上文」。✅(`BusWriteMapper.ignored()` 已显式标注为可删占位,不影响实现。)

**类型一致性核对:** `Canonicalizer.contentHash/canonicalJson` 全程一致;`CanonicalBus(.Schedule/.Alert/.Media)` 字段在 Task 5/7 一致;后端 DTO 字段名(`sourceId/intervalText/operatingHours/fetchFailed/lastUpdated`)与前端 `bus.ts` 接口逐字段对齐;`BusDetailDto.HeadRow` 在 Task 9 定义并被 mapper/service 共用;`ErrorCode`/`ApiError` 在 Task 6/10 一致;路由名 `home/airport/bus` 在 Task 11/14/15/16 一致。✅

**执行注意:** 前端 devDependency 正确包名是 `@vitejs/plugin-vue`;`SeedImporterIT`/`BusQueryServiceIT` 需要本机 Docker(Testcontainers)。
