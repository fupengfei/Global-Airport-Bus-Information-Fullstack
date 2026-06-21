# 设计:message 站内信推送闭环(#7 切片 B)

> 状态:已批准(2026-06-21 brainstorming)。这是 `#7` 的切片 B,接 #7a + 切片 A(巴士维护)之后,收口项目核心「订阅 → 推送」闭环。
> 上游设计:`docs/design.md`(核心流程「变更推送闭环」、E3 投递兜底、E11 导入抑制、E12 批量插入、E14 系统消息去重、D6 i18n、Redis 用途)。约束以 `CLAUDE.md` 锁定章节为准。
> 视觉 SoT:`design/inbox.html`(站内信列表 + diff 块 + 顶栏 bell 红点)。
> 已交付前置:#3 收藏=订阅(`favorite` 表,无 notify 列 → 收藏即订阅);切片 A(`BusUpdatedEvent` 接缝、`BusCommandService.save/delete`、版本号 `version`)。

## 0. 背景与切片划分
`#7` 切片:#7a(地基/统计 ✅)、切片 A(巴士维护+审计+版本 ✅)、**切片 B(本期)** message 推送闭环、切片 C 匿名纠错上报。
本期消费切片 A 发出的 `BusUpdatedEvent`,把「管理员改线路 → 自动通知订阅者」闭环跑通。

## 1. 范围与边界
**本期做:**
- `message` 表(`template_code + params_json`,前端按 locale 渲染,D6)。
- `BusUpdatedEvent` 监听(`@TransactionalEventListener(AFTER_COMMIT) @Async`)→ 查该线路活跃订阅者 → **批量插** BUS_UPDATED 消息(E12 分批 500)+ 按去重键幂等。
- 删除线路 → BUS_OFFLINE 消息给订阅者 + **软删其收藏**(站内信历史保留)。
- **Redis 未读计数**(DB 权威 + Redis 加速,缺失自愈)。
- **`@Scheduled` 投递对账(E3 简版)**:回填「有活跃订阅者但缺当前 version 消息」的漏发。
- message API:未读数 / 列表 / 批量已读 / 删除。
- 前端:收件箱页(列表 + diff 渲染 + 批量已读 + 删除 + 空态)、顶栏 bell + 红点(登录后轮询未读数)、i18n 模板渲染。

**本期不做(Phase 2 / follow-up):**
- SSE 实时红点(Phase 2;本期轮询 —— design.md 锁定)。
- 站内信 90 天归档/保留期(follow-up)。
- 邮件/推送通道(仅站内信)。

**关键依赖判断:** 切片 A 已发 `BusUpdatedEvent`(本期加 `version` 字段)+ `delete()` 加发 `BusDeletedEvent`;#3 favorite 提供订阅关系。无新外部依赖。

## 2. 切片 A 小改(为本期铺垫,靠既有 IT + 本期监听器 IT 回归)
- `bus/service/BusUpdatedEvent`:record 加 `int version`。`BusCommandService.save()` 发布处把 `newVersion` 带上(该变量已在作用域)。
- `bus/service/BusDeletedEvent`:新 record `(long busRouteId, String sourceId)`。`BusCommandService.delete()` 软删后 `events.publishEvent(new BusDeletedEvent(...))`(同 save 的发布方式;导入路径不涉及删除,无 E11 顾虑)。
- 现有 `BusCommandServiceIT` 应仍绿(事件无监听者时发布即丢弃)。

## 3. 数据模型(V8 `message`,迁移每列带 COMMENT)
```
message(
  id           BIGINT PK AUTO_INCREMENT          COMMENT '主键',
  user_id      BIGINT NOT NULL                   COMMENT '收信人(app_user.id)',
  template_code VARCHAR(32) NOT NULL             COMMENT '消息模板码:BUS_UPDATED/BUS_OFFLINE',
  params_json  TEXT NOT NULL                     COMMENT '渲染参数JSON(前端按locale渲染);含route/sourceId/changed等',
  related_bus_route_id BIGINT NULL               COMMENT '关联线路内部ID(系统消息可空)',
  dedup_key    VARCHAR(128) NOT NULL             COMMENT '幂等去重键:bus:{id}:v:{version} 或 bus:{id}:offline',
  is_read      TINYINT(1) NOT NULL DEFAULT 0     COMMENT '已读',
  read_at      DATETIME NULL                     COMMENT '已读时间',
  + created_by/created_at/updated_by/updated_at, deleted,
  UNIQUE KEY uk_msg_dedup (user_id, dedup_key, deleted),  -- 幂等:同人同变更只一条
  KEY idx_msg_user_read (user_id, is_read, deleted)
)
```
- 去重键(决策:用 **version** 而非 hash):BUS_UPDATED = `bus:{busRouteId}:v:{version}`(单调,每次变更唯一 → 必通知;回滚生成新 version 也通知;重投/对账命中 uk → 幂等不重复)。BUS_OFFLINE = `bus:{busRouteId}:offline`(E14:系统消息独立非空去重标识)。

## 4. 后端 message 模块(`com.airportbus.message`)
- `MessageMapper`(+xml,`#{}` only):
  - `batchInsert`(批量;用 `INSERT ... ON DUPLICATE KEY UPDATE id=id` 实现幂等忽略,E12 调用方分 500 批)。
  - `countUnread(@Param userId)`、`selectPage(userId,limit,offset)`、`markRead(userId, ids)`(仅本人,置 is_read=1+read_at;返回受影响数)、`softDelete(userId,id)`。
  - 对账:`selectActiveSubscriberBusVersions()`(有活跃订阅者的线路 + 当前 version + 订阅者)与「缺消息」判定(实现层用 `NOT EXISTS message` 或左连接筛 null)。
- `BusEventListener`:`@Component`;`@TransactionalEventListener(phase = AFTER_COMMIT) @Async onBusUpdated(BusUpdatedEvent)` / `onBusDeleted(BusDeletedEvent)` → 调 `MessageService`。`@Async` 需 `@EnableAsync`(已开,切片 A/热度在用)。
- `MessageService`:
  - `fanOutUpdated(event)`:查订阅者 userIds → 每人组 `params{route,sourceId,changed:[{field,old,new}],changedSubtables:[]}`(来自 event.summary)→ `dedup=bus:{id}:v:{version}` → 批量插(幂等)→ Redis 每人 `INCR`。
  - `fanOutOffline(event)`:查订阅者 → 组 `params{route,sourceId}` + `dedup=bus:{id}:offline` → 批量插 → Redis +1 → **软删该线路所有活跃收藏**(`favorite.softDeleteByBusRouteId`)。
  - `unreadCount/list/markRead/delete` + 维护 Redis 计数。
- `MessageReconciler`:`@Scheduled(fixedDelay)` 调用对账 → 对漏发补插(走同一 `fanOut` 幂等路径,Redis 同步)。可用开关 `airportbus.message.reconcile-delay-ms`(测试调大以禁用,沿用热度 IT 套路)。
- **跨模块(E5)**:订阅者/收藏走 user 模块 —— `FavoriteMapper/FavoriteService` 加 `selectActiveUserIdsByBusRouteId(busId)`、`softDeleteByBusRouteId(busId, actor)`。message 注入 user 模块的 service 调用,不直接碰 favorite 表私有逻辑。
- **Redis 未读计数**:key `msg:unread:{userId}`(StringRedisTemplate)。`unreadCount`:`GET`,缺失 → DB `countUnread` 重建 + `SET` TTL(如 1h);写信 `INCR`;`markRead` `DECRBY` 实际标记数;删未读 `DECR`。允许短暂漂移,缺失/TTL 自愈(design.md 锁定)。Redis 异常吞掉、回退 DB COUNT(不阻塞)。

## 5. API 契约(`/api/v1/messages/**`,需 JWT;错误体沿用统一格式)
| 方法 | 路径 | 行为 | 返回 |
|------|------|------|------|
| GET | `/messages/unread-count` | 当前用户未读数(轮询) | `200 {count}` |
| GET | `/messages?limit=20&offset=0` | 我的消息分页(倒序) | `200 [{id,templateCode,params,relatedSourceId,isRead,createdAt}]` |
| POST | `/messages/read` body `{ids:[]}` | 批量标记已读(仅本人) | `200 {updated}` |
| DELETE | `/messages/{id}` | 软删(仅本人) | `200` |
- `userId` 一律取自 `CurrentUser`(JWT),绝不信请求体;markRead/delete 的 SQL 带 `user_id=#{me}` 防越权。
- `params` 直接回传解析后的对象(后端 `params_json` → JSON);`relatedSourceId` 由 `related_bus_route_id` join `bus_route` 得 source_id(供前端「查看详情」链接)。
- 读路径排除 `deleted=1`。

## 6. 前端
- `api/messages.ts`:`unreadCount/listMessages/markRead/deleteMessage`(走 `client.ts`)。
- `stores/messages.ts`(Pinia):`unread` 数 + `list`;`startPolling()`(登录后 `setInterval` 每 ~30s 拉 unreadCount)、`stopPolling()`;`loadList/markRead/remove`;`auth.login/loadMe` 成功后 start、`logout/clear` 时 stop+清零。
- 顶栏 **bell + 红点**:登录后显未读数(>0 显红点 + 数字),点击进 `/inbox`。加到应用头部(查现有 home/账户页顶栏结构,放共享处)。
- `pages/InboxPage.vue`(路由 `/inbox`,`beforeEnter` 需登录,沿用 auth 守卫思路):
  - 列表渲染 `renderMessage(templateCode, params, t)`:`BUS_UPDATED` → 标题「线路 {route} 已更新」+ **diff 块**(遍历 `params.changed` 渲染 `字段 old → new`,字段名走 i18n);`BUS_OFFLINE` → 「线路 {route} 已下线」;**未知 code 兜底**「您有一条新通知」(D6)。
  - 批量勾选 → 标记已读;单条删除;空态(`StateBlock`);已读/未读视觉区分。
  - 点消息可跳 `relatedSourceId` 的详情(线路仍在时)。
- i18n(`zh-CN/en/de`):模板文案键 + diff 字段名(route/price/duration…)+ 操作文案。按 `user.locale` 渲染(Accept-Language 兜底,D6)。
- XSS(E7):全 `{{ }}` 自动转义,禁 `v-html`;params 文本同。

## 7. 测试
**后端 IT**(Testcontainers;`*IT` 须 `-Dtest=`;对账调度测试期调大延迟禁用):
- 保存线路(管理员)→ 订阅者收到 1 条 BUS_UPDATED,`params.changed` 含字段 diff,未读数 +1;非订阅者不收。
- 重复保存同 version / 再次扇出 / 对账 → 不重复(uk 幂等)。
- 内容变更产生新 version → 订阅者再收一条。
- 删除线路 → 订阅者收 BUS_OFFLINE,且其 favorite 被软删(`/favorites/ids` 不再含)。
- `markRead`/`delete` 仅作用于本人(越权 id 不动);未读数随之变。
- Redis 未读计数:写信 +1、标记已读减少、缓存缺失从库 COUNT 重建。
- 对账:人为制造「有订阅者但缺消息」→ `@Scheduled`/手调对账方法回填一条。
- `BusCommandServiceIT`(切片 A)加 version 字段后仍绿。

**前端 vitest**:
- `messages` store:轮询拉未读数、logout 清零停轮询。
- 顶栏红点:unread>0 显、=0 不显。
- `InboxPage`:渲染 BUS_UPDATED diff、BUS_OFFLINE 文案、未知 code 兜底;批量已读调 API、删除调 API;空态。
- `renderMessage` 纯函数各分支。
- `api/messages.ts` URL/参数正确。

## 8. 验收标准
- 管理员改一条被收藏的线路 → 订阅者在站内信看到一条带「字段 old→new」diff 的更新通知,顶栏红点 +1;刷新/轮询可见。
- 同一变更不重复推送(version 去重);进程崩溃漏发由 `@Scheduled` 对账回填。
- 删除被收藏线路 → 订阅者收「已下线」通知,收藏被清理,站内信历史保留。
- 未读数即时(轮询级)反映;标记已读/删除后红点更新;Redis 计数缺失能从库自愈。
- 站内信按用户 locale 渲染(模板+参数);未知模板有兜底。
- 越权访问他人消息被 `user_id` 约束挡住。
- 后端 IT + 前端 vitest 全绿;切片 A 回归不破。
