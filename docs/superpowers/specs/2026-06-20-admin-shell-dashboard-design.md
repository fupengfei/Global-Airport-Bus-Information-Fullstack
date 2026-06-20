# 设计:管理后台地基 + 统计概览(#7a)

> 状态:已批准(2026-06-20 brainstorming)。这是 `#7 admin 后台`拆分后的**第一个切片**,只做地基 + 只读统计,不含巴士维护 / 推送 / 审计 / 纠错上报。
> 上游设计:`docs/design.md`(模块划分、管理后台章节、E5/E10/DS5)。约束以 `CLAUDE.md` 锁定章节为准。
> 视觉唯一事实来源:`design/admin.html`。

## 0. #7 拆分背景(为什么先做 #7a)

`admin.html` 有 5 个区块,实为几个相互独立的子系统。拆成独立切片逐个交付:

- **#7a(本期)** 后台地基 + RBAC 强制 + 统计概览(用户/订阅/热度,**全只读,依赖全就绪**)。
- **#7b** 操作审计 audit(`audit_log` + `@Around` 切面)。
- **#7c** 巴士维护 + 推送闭环(bus 命令服务 + **新建 message 站内信模块**,项目核心亮点,最大一块)。
- **#7d** 匿名纠错上报(公开模态框 + admin 队列)。

本期零跨切片依赖、端到端可部署,并立起后续所有切片都要踩的 `/admin` 地基。

## 1. 范围与边界

**本期做:**
- `/admin/*` 前端路由分区 + 引入 Element Plus + ECharts(仅 admin chunk)。
- **RBAC 强制**:扩展现有手写 `CurrentUser`,`/admin/**` 端点默认拒绝非管理员。
- 统计概览三块(照 `admin.html`):**概览**(用户统计 + 注册趋势图)、**订阅统计**(按线路/机场/城市聚合 favorite)、**机场搜索热度榜单**。

**本期不做(留给后续切片):**
- 巴士维护树 + 编辑表单 + 保存触发推送(#7c)。
- `message` 站内信模块 / `BusUpdatedEvent` / 扇出(#7c)。
- `audit_log` 表 + 操作记录列表(#7b)。
- 匿名纠错上报队列(#7d)。
- 「待处理工单」概览卡片的**真实数据** —— ticket/report 模块未建。本期**保留该卡片做占位展示**(前端静态占位 `—`,不接后端、不造假数字),后续工单/纠错切片再接真实计数。
- OPERATOR vs SUPER_ADMIN **细粒度能力矩阵** —— 本期全只读,两种角色都能看;矩阵留到有写操作的 #7c。

**关键依赖判断:** 统计三块的数据源(`app_user`、`favorite`、`airport_search_stat`)均已随 #2/#3/查询主线落地,无新建表、无跨切片依赖。

## 2. 后端:`com.airportbus.admin` 模块

新建包 `com.airportbus.admin`,职责**只读 + 编排 + RBAC + DTO 组装**。遵 **E5 模块边界**:重的聚合查询放在**属主模块**的 service/mapper 里,admin 只调它们的接口,不直接碰别人的表。

```
com.airportbus.admin
├── api/AdminStatsController.java     // 4 端点,每个首行 requireAdmin()
└── api/dto/                          // OverviewDto / RegistrationPointDto / SubscriptionStatsDto / HotnessRowDto
```

属主模块各加一个只读聚合方法(新增,不改既有逻辑):

| 数据 | 属主模块(新增方法) |
|---|---|
| 用户总数 / 近 N 天注册数 / 注册趋势 | `user`:`UserMapper` + `UserStatsService`(或在现有 user service 加只读方法) |
| 收藏总数 / 近 N 天新增 / 按线路·机场·城市聚合 | `user`(favorite 归在 user 模块):`FavoriteMapper` + `FavoriteStatsService` |
| 机场搜索热度榜单 | `bus`:扩展**已存在的** `SearchHotnessService` 加 `ranking(window)` 读方法 |

MyBatis 仅用 `#{}`;聚合查询排除 `deleted=1`(沿用约定)。

## 3. API 契约

全部 `/api/v1` 前缀、需 JWT 且**必须是管理员**;错误体沿用统一格式 `{ code, message, details:[{field,issue}], traceId }` + 真实 HTTP 状态码。对外不暴露数据库自增 id(机场用 `code`/IATA,线路用 `source_id`)。

**决策:细端点(测试隔离 > 单大端点)。**

| 方法 | 路径 | 成功返回 |
|------|------|----------|
| `GET` | `/admin/stats/overview` | `200 { totalUsers, newUsersThisWeek, totalFavorites, newFavoritesThisWeek }`(无工单字段,占位卡纯前端) |
| `GET` | `/admin/stats/registrations?days=7` | `200 [ { date:"2026-06-20", count:42 }, … ]`(按天升序,缺省 7 天,**无注册的天补 0** 保证连续) |
| `GET` | `/admin/stats/subscriptions` | `200 { topRoutes:[…], topAirports:[…], topCities:[…] }` |
| `GET` | `/admin/stats/hotness?window=7d` | `200 [ { airportCode, airportName, cityName, views }, … ]`(按 views 倒序) |

- `overview.totalUsers`:**含 admin 账号**(决策);统计 `app_user` 全部 `deleted=0`。
- `newUsersThisWeek` / `newFavoritesThisWeek`:近 7 天 `created_at`。
- `subscriptions.topRoutes` 行:`{ busSourceId, route, destination, airportCode, cityName, favoriteCount, notifyCount }`。
  - **注**:CLAUDE.md/#3 锁定「收藏=订阅,没有通知开关」,`favorite` 表无 `notify` 列。故 `notifyCount` **不取自不存在的列**:本期 `notifyCount = favoriteCount`(收藏即订阅即接收通知),前端列头标注「订阅数」。若未来真加通知开关再回填。
  - `topAirports` 行:`{ airportCode, airportName, cityName, favoriteCount }`;`topCities` 行:`{ cityName, countryName, favoriteCount }`。各取 Top N(默认 20)。
- `hotness.window`:接受 `7d` / `30d` / `all`;数据来自 `airport_search_stat`(查询主线热度记录侧已落库)。

错误:
- 无 / 无效 JWT → `401`。
- 已登录但非管理员(role=`USER`)→ `403`,`code = ADMIN_FORBIDDEN`。

## 4. RBAC 强制(扩展手写 CurrentUser)

现状:无 Spring Security 过滤链;`JwtAuthFilter` 解析 Bearer 放入 ThreadLocal `CurrentUser`,受保护端点用 `CurrentUser.require()` 触发 401。**不引入** `@PreAuthorize`/`@EnableMethodSecurity`(避免两套鉴权并存)。

- `CurrentUser` 加 `requireAdmin()`:先 `require()`(无主体 → 401),再校验 `role ∈ {SUPER_ADMIN, OPERATOR}`,否则抛 `ApiException(403, ADMIN_FORBIDDEN)`。
- `ErrorCode` 新增 `ADMIN_FORBIDDEN`。
- `AdminStatsController` 每个端点首行 `CurrentUser.requireAdmin()` —— **默认拒绝**,满足 E10 意图。
- JWT 已带 `role` claim、`JwtPrincipal` 已有 `role`,无需改 token 结构。

## 5. 前端:`/admin` 地基

- **路由**:`router/index.ts` 加 `/admin` 父路由,子路由 `/admin`(概览)、`/admin/subscriptions`、`/admin/hotness`。父路由组件 **lazy import**,使 Element Plus + ECharts 只进 admin 异步 chunk,**不污染公开页 bundle**(回应 design.md 对 EP 包体顾虑)。
- **守卫**:`/admin` 父路由 `beforeEnter`:读 auth store 的 `role`,不在 `{SUPER_ADMIN, OPERATOR}` → 未登录跳 `/login?redirect=`,已登录非管理员跳首页(或 403 提示)。**后端独立兜底**,前端守卫仅体验。
  - 前置:确认 `stores/auth.ts` 的用户对象含 `role`;若缺则补(loadMe 已返回 role 则透传)。
- **Element Plus 局部注册**:仅在 admin 入口/布局注册(或 admin chunk 内 `app.use`),**不进全局 `main.ts`**,保持公开页轻量。
- **页面 / 组件**:
  - `components/admin/AdminLayout.vue` —— 顶栏 + 侧栏(照 `admin.html`)。侧栏本期仅「概览 / 订阅统计 / 热度榜单」;「巴士维护 / 纠错上报 / 操作记录」**先不放**,各切片再加。
  - `pages/admin/AdminOverviewPage.vue` —— 统计卡 + ECharts 注册趋势柱状图。卡片照原型保留 4 张:总用户 / 本周新增 / 收藏订阅 / **待处理工单(占位)**。前三张接 `overview` 真实数据,第 4 张显静态 `—` 并带「敬请期待」式提示(后续切片接真实计数,不返工布局)。
  - `pages/admin/AdminSubscriptionsPage.vue` —— 三张表(线路 / 机场 / 城市)。
  - `pages/admin/AdminHotnessPage.vue` —— 热度榜单表 + 时间窗切换(7d/30d/all)。
- **API 客户端**:`api/admin.ts`,走现有 `client.ts`(自动带 JWT + 401 处理)。
- **文案**:admin 为内部工具,本期 zh-CN 直写(i18n 不阻塞);如涉及地区名一律「中国台湾省」(本期数据仅 VIE/PVG,不涉及)。
- **XSS(E7)**:所有数据走 `{{ }}` 自动转义,禁 `v-html`。

## 6. 测试

**后端 IT**(`*IT` 需 `-Dtest=` 单独跑;actuator+redis health 已关 —— 见测试记忆;`@WebMvcTest` 要 mock 全部 mapper):
- 四端点:无 token → 401;普通用户(role=USER)→ 403 且 `code=ADMIN_FORBIDDEN`;管理员 → 200 且结构正确。
- `overview` 计数含 admin 账号、排除 `deleted=1`。
- `registrations` 按天分组、缺省 7 天、空天补 0(连续日期序列)。
- `subscriptions` 聚合排序正确、`notifyCount == favoriteCount`、逻辑删除的 favorite 不计入。
- `hotness` 按 views 倒序、window 过滤生效。

**前端 vitest**:
- 路由守卫:匿名 / 普通用户访问 `/admin` 被拦(跳转正确);管理员放行。
- `AdminOverviewPage`:用 mock api 渲染统计卡 + 图表容器。
- `api/admin.ts`:四个调用拼对 URL + query。

## 7. 验收标准

- 管理员登录后可进 `/admin`,看到真实的用户数 / 收藏数 / 注册趋势 / 订阅榜 / 热度榜。
- 普通用户或匿名访问 `/admin` 被前端拦截;直接打 `/api/v1/admin/**` 被后端 403/401。
- 公开页(首页/详情)bundle 不含 Element Plus / ECharts(代码分割生效)。
- 后端 IT + 前端 vitest 全绿。
- 侧栏只露本期三项;后续切片各自往里加入口,不返工地基。
