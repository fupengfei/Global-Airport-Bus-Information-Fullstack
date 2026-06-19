# 设计:收藏 = 订阅(#3,订阅侧)

> 状态:已批准(2026-06-19 brainstorming)。本期只做**收藏(订阅侧)**,不含站内信 / 变更推送 / admin。
> 上游设计:`docs/design.md`(模块划分、订阅→推送闭环、Redis 用途)。约束以 `CLAUDE.md` 锁定章节为准。

## 1. 范围与边界

**本期做:** 登录用户收藏 / 取消收藏一条 bus 线路;查看「我的收藏」;首页卡片 + 详情页显示收藏态(实心 ❤️)。

**本期不做(留给后续模块):**
- 站内信 `message` 表、未读数、收件箱 UI。
- `BusUpdatedEvent` / `AFTER_COMMIT` 监听 / `@Async` 扇出 / 推送去重。
- admin 后台的 bus 保存触发点(#7)与订阅统计。
- **通知开关** —— CLAUDE.md 锁定「收藏=订阅,没有单独的通知开关」,`favorite` 表不设 `notify` 列。

**关键依赖判断:** 推送闭环需要「管理员保存 bus」这一触发点,而该触发点属于 admin(#7),尚未建。因此本期把订阅侧(收藏)与推送侧解耦,先交付订阅侧,零 admin 依赖、端到端可部署。

## 2. 数据模型 —— `backend/.../db/migration/V4__favorite.sql`

```sql
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

**与常规唯一键约定的偏离(刻意):** 鉴权表用 `(…, deleted)` 复合唯一键。收藏是**可反复开关的关系**,所以唯一键放在 `(user_id, bus_route_id)`(不含 deleted),通过翻转 `deleted` 切换收藏态:收藏 = upsert 置 `deleted=0`,取消 = 置 `deleted=1`。这样天然幂等,也不会对同一对积累一行 deleted=0 + 一行 deleted=1 的重复行。它仍是逻辑删除,所有读路径排除 `deleted=1`。

- `bus_route_id` 存内部自增 FK(引用完整性);对外 API 只用 `source_id`,Service 层做 `source_id → bus_route_id` 解析。
- 字段含义写进列 `COMMENT`,表带 `COMMENT`(项目约定)。

## 3. API 契约

全部需 JWT;`/api/v1` 前缀;对外用 `source_id`;`userId` 从 `CurrentUser`(JWT 上下文)取,绝不从请求体传。错误体沿用统一格式 `{ code, message, details:[{field,issue}], traceId }` + 真实 HTTP 状态码。

| 方法 | 路径 | 行为 | 成功返回 |
|------|------|------|----------|
| `PUT` | `/buses/{sourceId}/favorite` | 幂等收藏(已收藏再点 = no-op) | `200 { "favorited": true }` |
| `DELETE` | `/buses/{sourceId}/favorite` | 幂等取消(未收藏再点 = no-op) | `200 { "favorited": false }` |
| `GET` | `/favorites` | 我的收藏,完整 bus 卡片数组(复用查询主线 bus 详情 DTO),按 `updated_at` 倒序(最近收藏动作在前;复活的收藏因 updated_at 刷新会顶到前面) | `200 [ {bus…}, … ]` |
| `GET` | `/favorites/ids` | 我收藏的 source_id 列表 | `200 ["vie-vab1", …]` |

错误:
- `sourceId` 不存在 → `404`(线路不存在)。
- 匿名(无/无效 JWT)→ `401`,前端拦截器引导登录。
- 已删除(`deleted=1`)的 bus 线路:`source_id` 解析时排除,视同不存在 → `404`。

## 4. 后端实现要点

- 包:`com.airportbus.user`(CLAUDE.md 模块划分把「收藏」归在 user)。
  - `api/FavoriteController.java` —— 4 个端点。
  - `service/FavoriteService.java` —— source_id 解析、幂等 upsert/软删、列表组装。
  - `mapper/FavoriteMapper.java` + XML —— 只用 `#{}`;upsert 用 `INSERT … ON DUPLICATE KEY UPDATE deleted=0, updated_*`;查询排除 `deleted=1`。
- `source_id → bus_route_id` 解析复用 bus 模块现有 mapper(查 `bus_route` 按 `source_id` 且未删除);跨模块只经 Service/Mapper 接口,不直接碰对方表的私有逻辑。
- `/favorites` 列表组装复用查询主线已有的 bus 详情装配(含 stops/schedules/images/files/alerts),保证卡片与首页/详情一致。alerts 过期过滤维持前端既有逻辑(后端原样返回)。
- 审计列 `created_by/updated_by` 暂填当前用户名(全局审计拦截器是后续 follow-up,见 query-mainline 记忆;本期手填,不阻塞)。

## 5. 前端实现要点

- `api/favorites.ts`:`favorite/unfavorite/listFavorites/listFavoriteIds`,走现有 `client.ts`(自动带 JWT + 401 处理)。
- `stores/favorites.ts`(Pinia):
  - state:`ids: Set<string>`。
  - `load()` —— 仅已登录时调 `/favorites/ids` 填充;未登录直接空集。
  - `toggle(sourceId)` —— 乐观更新(先改本地 Set),调 PUT/DELETE,失败回滚。
  - `isFavorited(sourceId)` getter;`clear()` 清空。
- `components/BusCard.vue`:右上角(价格之上,贴 `design/` 原型稿)加收藏心按钮。用自身 `bus.sourceId` 读 favorites store 判断实心/空心。点击:未登录 → 跳登录页(带回跳 `redirect`);已登录 → `toggle(sourceId)`。移除第 23 行「本期无收藏按钮」注释。
- 接入点:`stores/auth.ts` 的 `login`/`loadMe` 成功后 + App 启动时若 `isAuthed` → `favorites.load()`;`logout`/`clear` → `favorites.clear()`。
- `pages/MePage.vue`:新增**我的收藏**区块,调 `listFavorites()` 渲染 `BusCard` 列表(带 `detailLink`),空态用 `components/StateBlock.vue`。
- i18n(`i18n/locales/{zh-CN,en,de}.ts`):`favorite.add`、`favorite.remove`、`favorite.loginPrompt`、`favorite.mine`、`favorite.empty`。台湾相关文案若涉及一律「中国台湾省」(此功能预计不涉及)。

## 6. 收藏态与 Redis 缓存的关系(为什么客户端打标记)

首页搜索 / 详情的 bus 响应走**共享 Redis 缓存(不分用户)**。若把 `favorited` 掺进缓存的 bus 响应,会用 A 用户的收藏态污染 B 用户。因此收藏态**不进** bus 响应:前端登录后单独拉一次 `/favorites/ids` 存进 store,首页卡片与详情页据 `bus.sourceId` 在客户端打标记。bus 查询缓存与失效逻辑完全不受本期影响。

## 7. 测试

**后端 IT**(`*IT` 需 `-Dtest=` 单独跑,actuator+redis health 已关 —— 见测试记忆):
- 收藏 → `/favorites/ids` 含该 source_id。
- 重复 PUT 幂等(仍 200、ids 不重复)。
- DELETE 后 ids 不再含;重复 DELETE 幂等。
- 取消后再 PUT:`deleted` 从 1 翻回 0(同一行复活,不新增行)。
- 收藏不存在 / 已删除的 sourceId → 404。
- 匿名调任一端点 → 401。
- `/favorites` 返回完整 bus DTO(含子表)。
- 逻辑删除的 favorite 不出现在 ids / 列表。

**前端 vitest**:
- favorites store:`load` 填充、`toggle` 乐观更新 + 失败回滚、`clear`。
- `BusCard`:已收藏显实心;匿名点击只跳登录、不发请求;已登录点击触发 toggle。
- `MePage`:渲染收藏列表 + 空态。

## 8. 验收标准

- 登录用户在首页卡片 / 详情页可收藏 / 取消,刷新后状态保持。
- 「我的收藏」列出全部已收藏线路;取消后即时消失。
- 匿名用户点收藏 → 被引导登录,登录后回到原页。
- 收藏态不污染共享 bus 查询缓存。
- 后端 IT + 前端 vitest 全绿。
