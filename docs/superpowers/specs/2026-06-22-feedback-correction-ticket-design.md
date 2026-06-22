# 设计:反馈闭环 —— 匿名纠错上报 + 用户建议工单(#7 切片 C)

> 状态:已批准(2026-06-22 brainstorming)。这是 `#7` 的切片 C,接切片 A(巴士维护)/切片 B(message 推送闭环)之后,补齐「数据必腐烂 → 众包纠错 / 用户反馈」闭环。
> 上游设计:`docs/design.md`(领域数据「建议工单 + 回复」「匿名数据纠错上报」、工单状态机 L157、E8 URL 校验、E10 后台鉴权 + author 服务端取、D1/D2/D3 API 契约、D6 i18n)。约束以 `CLAUDE.md` 锁定章节为准。
> 视觉 SoT:`design/bus-detail.html`(纠错 `.overlay/.modal` 触发按钮 + 字段)、`design/tickets.html`(工单列表 + 新建 + 气泡线程 + 回复框 + 状态徽章)、`design/admin.html`(后台壳)。
> 已交付前置:#7a admin 壳 + `requireAdmin`;切片 A `@Audited` 切面;切片 B `message` 模块(`MessageService` 扇出、`template_code + params_json`、前端 `renderMessage`/`InboxPage`/顶栏红点);#2 user/JWT/`CurrentUser`;#3 favorite。

## 0. 背景与切片划分
`#7` 切片:#7a(地基/统计 ✅)、切片 A(巴士维护+审计+版本 ✅)、切片 B(message 推送闭环 ✅)、**切片 C(本期)** 反馈闭环。
本期落地新模块 `com.airportbus.ticket`,含两个相关但独立的子系统,共享后台「反馈」区:
- **C1 匿名纠错上报**:零登录,任何旅客在线路详情一键上报「信息有误」,管理员后台处理。
- **C2 用户建议工单**:登录用户提单 + 气泡线程回复,状态机 `OPEN→REPLIED→CLOSED`,管理员回复时给用户发 `TICKET_REPLIED` 站内信(复用切片 B 的 message 模块)。

**拆两份可独立部署的实现计划**:C1 先(标的小、无 message 耦合),C2 后(线程 + 站内信集成 + 前端消息渲染扩展)。本 spec 同时覆盖两者。

## 1. 范围与边界
**本期做:**
- C1:`correction_report` 表;公开 `POST /corrections`(**零登录** + **Redis 按 IP 限流**);admin 队列(列表 + 状态流转 + 内部备注);前端纠错模态(bus-detail)+ `AdminCorrectionsPage`。
- C2:`ticket` + `ticket_reply` 表;用户提单/查单/回复/关闭;admin 队列 + 回复 + 关闭;管理员回复 → `TICKET_REPLIED` 站内信;前端 `/tickets` 页 + `AdminTicketsPage` + `InboxPage/renderMessage` 支持 `TICKET_REPLIED`。
- i18n(zh-CN/en/de):纠错模态、工单页、admin 页、`TICKET_REPLIED` 模板。

**本期不做(follow-up / Phase 2):**
- 纠错/工单的邮件回执(C1 contact 仅存,不外发邮件 —— follow-up)。
- 工单附件/图片上传(YAGNI,本期纯文本)。
- 纠错「一键跳转编辑该线路」深链(admin 可手动去 /admin/buses;深链 follow-up)。
- 反馈分类标签 / SLA / 全文检索(YAGNI)。

**关键依赖判断:** message 模块(切片 B)已可被 ticket 模块调用做 `TICKET_REPLIED` 扇出;`CurrentUser.requireAdmin()`/JWT 主体已具备;bus 存在性校验(`source_id`)可复用查询侧。无新外部依赖。

## 2. 数据模型(迁移每列带 COMMENT,表也带;软删 `deleted` 全程排除)

### 2.1 V9 `correction_report`(C1,匿名)
```
correction_report(
  id              BIGINT PK AUTO_INCREMENT      COMMENT '主键',
  related_source_id VARCHAR(64) NULL            COMMENT '关联线路业务键source_id(可空;填了校验存在)',
  description     TEXT NOT NULL                 COMMENT '问题描述(旅客填,必填)',
  contact         VARCHAR(128) NULL             COMMENT '联系方式(可选,邮箱/电话,本期不外发)',
  status          VARCHAR(16) NOT NULL DEFAULT 'OPEN'  COMMENT '状态:OPEN/RESOLVED/DISMISSED',
  resolution_note TEXT NULL                     COMMENT '管理员内部处理备注',
  reporter_ip     VARCHAR(64) NULL              COMMENT '上报来源IP(限流/审计;admin可见)',
  + created_by/created_at/updated_by/updated_at, deleted TINYINT(1) NOT NULL DEFAULT 0,
  KEY idx_corr_status (status, deleted),
  KEY idx_corr_source (related_source_id, deleted)
)
```
- 匿名:`created_by` 存 `'anonymous'`(无登录主体);状态变更时 `updated_by` 存管理员用户名。
- `reporter_ip` 取 `X-Forwarded-For` 首段回退 `RemoteAddr`(实现层一个 helper)。

### 2.2 V10 `ticket` + `ticket_reply`(C2,登录用户;C1 用 V9、C2 用 V10,各自独立迁移)
```
ticket(
  id              BIGINT PK AUTO_INCREMENT      COMMENT '主键',
  user_id         BIGINT NOT NULL               COMMENT '提单人(app_user.id)',
  related_source_id VARCHAR(64) NULL            COMMENT '关联线路source_id(可空,填了校验)',
  status          VARCHAR(16) NOT NULL DEFAULT 'OPEN'  COMMENT '状态:OPEN/REPLIED/CLOSED',
  last_reply_at   DATETIME NOT NULL             COMMENT '最后一条回复时间(列表排序)',
  + created_by/created_at/updated_by/updated_at, deleted,
  KEY idx_ticket_user (user_id, deleted),
  KEY idx_ticket_status (status, deleted)
)
ticket_reply(
  id              BIGINT PK AUTO_INCREMENT      COMMENT '主键',
  ticket_id       BIGINT NOT NULL               COMMENT '所属工单',
  author_type     VARCHAR(8) NOT NULL           COMMENT '作者类型:USER/ADMIN(服务端从主体取,E10)',
  author_id       BIGINT NOT NULL               COMMENT '作者ID(user.id 或 admin.id)',
  body            TEXT NOT NULL                 COMMENT '回复正文',
  + created_by/created_at/updated_by/updated_at, deleted,
  KEY idx_reply_ticket (ticket_id, deleted, id)
)
```
- 工单无独立「主题/标题」字段(对齐 `tickets.html` 原型:只有「关联线路 + 问题/建议」)。首条内容即第一条 `ticket_reply(author_type=USER)`。

## 3. 状态机(交既有错误包络 + service 层守卫)
- **纠错** `correction_report.status`:`OPEN → RESOLVED`(已处理)/ `OPEN → DISMISSED`(无效忽略);管理员单向,无回流。非法目标态 → `400 CORRECTION_BAD_STATUS`。
- **工单** `ticket.status`(L157):
  - 用户建单 → `OPEN`(建 ticket + 首条 USER reply,`last_reply_at=now`)。
  - 管理员回复 → `REPLIED`(+ 发 `TICKET_REPLIED` 站内信给 `ticket.user_id`)。
  - 用户回复 → `OPEN`(无论原态 REPLIED/CLOSED 都重开)。
  - 任一方「关闭」→ `CLOSED`。
  - **回复永远允许**(含 CLOSED 后用户回复重开);故本期无 `TICKET_CLOSED` 硬阻断,只按作者类型转移状态。

## 4. 后端模块(`com.airportbus.ticket`;MyBatis `#{}` only,`${}` 仅白名单 ORDER BY)

### 4.1 C1 纠错
- `CorrectionMapper`(+xml):`insert`、`selectPage(status?,limit,offset)`、`selectById`、`updateStatus(id,status,resolutionNote,updatedBy)`、`countByStatus`。
- `CorrectionRateLimiter`:Redis `corr:rl:{ip}`,窗口 + 上限(`airportbus.correction.rate-limit-window-sec` 默认 300、`...max` 默认 5;`INCR`+首次 `EXPIRE`),复用切片 #2 的 Redis 限流写法。超限抛 `CORRECTION_RATE_LIMITED`(429)。Redis 不可用时**放行**(吞异常,不阻断公开上报)。
- `CorrectionService`:`submit(req, ip)`(校验 description 非空、related_source_id 若有则查 bus 存在性 → 不存在 `400`、限流 → 插入)、`listForAdmin`、`updateStatus`(@Audited `UPDATE_CORRECTION`)。
- `CorrectionController`:`POST /api/v1/corrections`(公开);`GET /api/v1/admin/corrections`、`PATCH /api/v1/admin/corrections/{id}`(`requireAdmin`)。

### 4.2 C2 工单
- `TicketMapper` / `TicketReplyMapper`(+xml):工单 CRUD、回复插入、按 ticket 取线程、按 user/status 分页、`updateStatusAndLastReply`。
- `TicketService`:
  - `create(userId, sourceId?, body)`:校验 body 非空 + source 存在性 → 插 ticket(OPEN)+ 首条 USER reply。
  - `reply(ticketId, principal, body)`:**author 从主体取(E10)**;USER 回复需校验本人(`TICKET_FORBIDDEN`),置 `OPEN`;ADMIN 回复(经 admin 控制器,`requireAdmin`)置 `REPLIED` + 调 `MessageService` 发 `TICKET_REPLIED`。
  - `close(ticketId, principal)`:USER 限本人 / ADMIN 任意,置 `CLOSED`。
  - `listMine` / `getMine`(越权 `TICKET_FORBIDDEN` / 不存在 `TICKET_NOT_FOUND`);`listForAdmin` / `getForAdmin`。
- `TicketController`(`/api/v1/tickets`,登录)+ `AdminTicketController`(`/api/v1/admin/tickets`,`requireAdmin`,回复/关闭 @Audited)。

### 4.3 message 集成(C2)
- message 模块加模板 `TICKET_REPLIED`:`params{ticketId}`,`dedup_key = ticket:{ticketId}:reply:{replyId}`(每条回复唯一 → 幂等)。`related_bus_route_id` 置空(工单消息不绑线路)。
- `MessageService` 加 `notifyTicketReplied(userId, ticketId, replyId)`:单条插入 + Redis 未读 +1(复用既有 `batchInsert` 幂等路径与计数器)。**不经 BusEvent 链路**,由 `TicketService` 在 admin 回复事务内同步调用(单收件人、无扇出,简单直插;失败不回滚回复 —— 交对账?本期无对账,记 follow-up)。

> 设计判断:工单回复站内信是「单人定向」,不像 bus 变更要扇出多订阅者,故不走 `@TransactionalEventListener` 异步链,直接在 service 内调 `MessageService` 即可,降低复杂度。

## 5. API 契约(`/api/v1`;成功返资源 HTTP 200/201;错误 `{code,message,details:[{field,issue}],traceId}` + 真实状态码;资源词 `corrections`/`tickets`)

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/corrections` | 公开 | `{sourceId?, description, contact?}` → 创建;限流 429 |
| GET | `/admin/corrections?status&limit&offset` | admin | 队列 |
| PATCH | `/admin/corrections/{id}` | admin | `{status, resolutionNote?}` |
| POST | `/tickets` | user | `{sourceId?, body}` → ticket(OPEN)+首条回复 |
| GET | `/tickets` | user | 我的工单(列表 + 状态 + last_reply_at) |
| GET | `/tickets/{id}` | user(本人) | 工单 + 回复线程 |
| POST | `/tickets/{id}/replies` | user(本人) | `{body}` → USER 回复,置 OPEN |
| POST | `/tickets/{id}/close` | user(本人) | 置 CLOSED |
| GET | `/admin/tickets?status&limit&offset` | admin | 队列 |
| GET | `/admin/tickets/{id}` | admin | 线程 |
| POST | `/admin/tickets/{id}/replies` | admin | `{body}` → ADMIN 回复,置 REPLIED + 发站内信 |
| POST | `/admin/tickets/{id}/close` | admin | 置 CLOSED |

错误码新增:`CORRECTION_RATE_LIMITED`(429)、`CORRECTION_NOT_FOUND`、`CORRECTION_BAD_STATUS`、`TICKET_NOT_FOUND`、`TICKET_FORBIDDEN`、校验类 `VALIDATION_ERROR`(description/body 必填,沿用既有)。

## 6. 前端

### 6.1 C1(零登录)
- **bus-detail 页**:加触发按钮「⚠️ 发现信息有误?上报纠错」+ `.overlay/.modal` 纠错框(对齐 `design/bus-detail.html`):问题描述 textarea(必填)、联系方式 input(可选)、提交/取消;Esc / 点遮罩关闭;提交 `POST /corrections` → 成功提示 + 关闭,限流/失败给友好提示。**无需登录**。
- **admin**:`AdminCorrectionsPage`(`/admin/corrections`,EP 队列表:列表 + status 筛选 + 行内改状态 + 备注;admin 区可用 Element Plus)。
- `api/corrections.ts` + 轻量交互;查询页公开组件仍用设计 token,不引 EP 到公开 chunk。

### 6.2 C2(登录)
- **`/tickets` 页**(登录守卫,对齐 `design/tickets.html`):我的工单列表 + 状态徽章(待处理/已回复/已关闭)、新建工单(关联线路可选 + 问题/建议)、每单气泡线程(我/管理员)+ 回复框 + 关闭按钮。
- 顶栏 nav 加「工单」入口(链 `/tickets`)。
- **admin**:`AdminTicketsPage`(`/admin/tickets`:队列 + 线程视图 + 回复框 + 关闭)。
- **InboxPage / renderMessage**:加 `TICKET_REPLIED` 分支 —— 标题(i18n,如「您的工单有新回复」)+「查看详情」链向 `/tickets/{params.ticketId}`(而非 `/bus/...`)。
- 文案全走 vue-i18n;全 `{{ }}` 无 v-html(XSS);description/body 纯文本渲染。

## 7. 错误处理 / 安全
- E10:`ticket_reply.author_type/author_id` 一律服务端从已认证主体取,绝不信请求体;admin 接口 `requireAdmin`(401/403)。
- 越权:用户只能读写自己的 ticket(SQL 带 `user_id` 条件 + service 校验)。
- 限流:公开 `POST /corrections` 按 IP;Redis 故障放行(可用性优先,公开上报不因缓存挂而断)。
- XSS:前端 `{{ }}`;后端存原文,无 HTML 注入面(无 URL 字段,E8 不涉及)。
- 软删:所有查询排除 `deleted=1`;删除走标志位(本期无对外删除端点,预留)。

## 8. 测试(TDD)
- **后端 IT(Testcontainers MySQL/Redis;`management.health.redis.enabled=false`;`*IT` 用 `-Dtest=` 跑)**:
  - C1:`CorrectionServiceIT`(提交成功 / description 空报错 / source 不存在报错 / 限流命中 429 / Redis 关时放行 / admin 改状态 @Audited)。
  - C2:`TicketServiceIT`(建单 OPEN+首条回复 / 管理员回复→REPLIED+发 `TICKET_REPLIED` 站内信 / 用户回复重开 OPEN / CLOSED 后用户回复重开 / 越权 TICKET_FORBIDDEN / 关闭 CLOSED)。
- **后端 @WebMvcTest**(mock 全部 mapper / service):控制器鉴权(公开 vs requireAdmin vs 本人)、错误包络与状态码。
- **前端 vitest**:纠错模态(校验/提交/关闭)、`api/corrections`、tickets store/page(列表/新建/回复/状态)、admin 两页、`renderMessage` 的 `TICKET_REPLIED`、i18n。

## 9. 交付物拆分(两份独立可部署计划)
- **C1 计划**:`correction_report`(V9 迁移)、限流器、CorrectionService/Controller、AdminCorrectionsPage、bus-detail 纠错模态、i18n、测试。端到端:匿名旅客上报 → admin 队列处理。
- **C2 计划**:`ticket`/`ticket_reply`(V10 迁移)、TicketService/Controllers、message `TICKET_REPLIED` 集成、`/tickets` 页 + AdminTicketsPage + InboxPage 扩展、i18n、测试。端到端:用户提单 → 管理员回复(用户收站内信)→ 用户回复重开 → 关闭。
- C1 先(无 message 耦合);C2 接 C1 的模块骨架与 admin 区。

## 10. 自审清单
- **design.md 覆盖**:工单状态机 L157 ✅、匿名上报独立于用户工单 ✅、E10 author 服务端取 ✅、D1/D2/D3 契约 ✅、D6 i18n ✅。
- **复用**:message 模块(TICKET_REPLIED)、requireAdmin、@Audited、bus 存在性校验、Redis 限流写法、设计 token / EP 仅 admin。
- **YAGNI 剔除**:邮件外发、附件、深链跳转、分类/SLA/检索 —— 全列入 follow-up。
- **风险**:工单回复站内信走同步直插(非异步扇出);失败不回滚回复(单收件人,follow-up 可加对账)。限流 Redis 故障放行(可用性优先)。
