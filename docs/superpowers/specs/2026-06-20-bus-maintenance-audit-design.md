# 设计:巴士维护 + 审计 + 版本历史(#7 切片 A)

> 状态:已批准(2026-06-20 brainstorming)。这是 `#7 admin 后台`继 #7a 之后的切片 A。
> 上游设计:`docs/design.md`(E2/E5/E10/E11、content_hash、订阅→推送闭环、管理后台、三个时间语义);约束以 `CLAUDE.md` 锁定章节为准。
> 视觉 SoT:`design/admin.html`(树 + 编辑表单 + 操作记录)。
> 已交付前置:#7a(`com.airportbus.admin` 模块 + 手写 `CurrentUser.requireAdmin()` + /admin 前端外壳),见 `2026-06-20-admin-shell-dashboard-design.md`。

## 0. 背景与切片划分

`#7` 剩余拆为 3 切片,逐个交付:
- **切片 A(本期)** 巴士维护(写)+ 审计 + 版本历史/回滚。
- **切片 B** message 站内信推送闭环(消费本期发出的 `BusUpdatedEvent`)。
- **切片 C** 匿名纠错上报。

**CLAUDE.md 偏离声明(用户裁决)**:CLAUDE.md 写「没有变更历史时间线 UI(EN3 已从前端移除)」。本期用户明确要「版本历史 + 可回滚」,**以用户指令为准**,重新加入版本历史(数据层快照 + 后台 UI + 回滚)。其余锁定约定不变。

## 1. 范围与边界

**本期做:**
- 共享 `BusCommandService`(E5):`save` / `verify` / `delete` / `rollback`;导入器改为委托它并抑制事件(E11)。
- admin 巴士增 / 改 / 软删 / 核对无误;**乐观锁**(version,冲突 409)。
- 保存时算**字段级 diff**(changedSummary),hash 变化则发 `BusUpdatedEvent`(本期**无监听者** —— 干净接缝,留给切片 B)。
- **版本历史**:每次内容变化写一条完整快照到 `bus_route_version`;后台可查历史列表 / 看某版本 / **回滚**(回滚 = 把旧内容存为新版本,历史只增不毁)。
- **操作审计(#7b)**:`audit_log` + `@Audited` 标记注解 + `@Around` 切面;后台审计列表 + 过滤。
- admin 前端:巴士维护页(树 + 编辑表单含全部子表 + 版本历史面板)、操作记录页;侧栏加入口。
- **能力矩阵(E10)**:OPERATOR/SUPER_ADMIN 可建 / 改 / 核对 / 回滚;**删除限 SUPER_ADMIN**。

**本期不做(留切片 B/C):**
- `message` 站内信表 / 扇出 / 未读数 / 收件箱 / 红点(`BusUpdatedEvent` 本期无监听者)。
- 删除被收藏线路时给**订阅者发通知**(本期 `delete` 只软删 + 审计;通知语义留 B)。
- 匿名纠错上报(切片 C)。
- 推送实时性(SSE 等,Phase 2)。

**关键依赖判断:** 写入地基大半现成 —— `BusWriteMapper`(bus + 全子表 insert/update/delete)、`Canonicalizer.contentHash`、`SeedImporter` 已有「先删后插 + 算 hash + 存 content_hash」逻辑。本期把它抽进共享 `BusCommandService` 并加 diff / 快照 / 事件 / 乐观锁。

## 2. 数据模型(迁移每列带 COMMENT,表带 COMMENT)

**V5 —— `bus_route` 加列:**
```
version          INT          NOT NULL DEFAULT 0   COMMENT '乐观锁版本号/历史版本号,内容变化时+1'
last_verified_at DATETIME     NULL                 COMMENT '人工核对无误时间(与内容变更正交)'
last_verified_by VARCHAR(64)  NULL                 COMMENT '核对人'
```
> 三个时间语义就位:`last_updated`(数据日期,来自 data.json)/ `updated_at`(行更新时间)/ `last_verified_at`(人工核对时间)。

**V6 —— `audit_log`(只增写;含全套审计列 + 逻辑删除,遵约定):**
```
id, actor_id BIGINT NOT NULL, actor_type VARCHAR(16) NOT NULL,  -- 例 'ADMIN'
action VARCHAR(32) NOT NULL,         -- CREATE_BUS/UPDATE_BUS/DELETE_BUS/VERIFY_BUS/ROLLBACK_BUS
target_type VARCHAR(16) NOT NULL,    -- 'bus'
target_id VARCHAR(64) NULL,          -- source_id
summary VARCHAR(512) NULL,           -- 变更摘要/说明
ip VARCHAR(45) NULL,
created_by/created_at/updated_by/updated_at, deleted
KEY idx_audit_created (created_at), KEY idx_audit_actor (actor_id), KEY idx_audit_action (action)
```

**V7 —— `bus_route_version`(快照,只增写;含审计列 + 逻辑删除):**
```
id, bus_route_id BIGINT NOT NULL, version INT NOT NULL,
snapshot_json LONGTEXT NOT NULL,     -- 整条线路含全子表的【完整】编辑 DTO JSON(非 canonical 归一版,保证忠实回滚/展示)
content_hash CHAR(64) NOT NULL,
changed_summary VARCHAR(1024) NULL,  -- 字段级 diff 摘要 JSON(相对上一版本)
actor VARCHAR(64) NULL,
created_by/created_at/updated_by/updated_at, deleted
UNIQUE KEY uk_brv (bus_route_id, version, deleted),
KEY idx_brv_bus (bus_route_id),
CONSTRAINT fk_brv_bus FOREIGN KEY (bus_route_id) REFERENCES bus_route(id)
```

## 3. `com.airportbus.bus` —— BusCommandService(E5 共享)

新建 `bus/service/BusCommandService.java`,**独占 bus 写 + hash + 快照 + 事件**。导入器与 admin 都调它(E5:admin 不重复实现)。

**`BusInput`**(编辑 DTO,GET 返回同形):标量 `route/destination/operator/officialUrl/duration/price/operatingHours/lastUpdated`;子表 `stops[]{seq,direction,name}`、`schedules[]{direction,timeRange,intervalText,note}`、`alerts[]{type,message,startDate,endDate}`、`images[]{url,caption}`、`files[]{name,url}`。`fetchFailed` 非表单字段(默认 false)。

**`save(sourceId|null, BusInput, expectedVersion, actor, suppressEvents) → BusView`**:
1. 校验(必填 route;子表字段合法)。
2. 解析机场(airport code → id;沿用现有树/airport 查询)。
3. **乐观锁**:更新场景比对 `expectedVersion` 与库中 `version`,不匹配 → `409 BUS_VERSION_CONFLICT`。
4. 算新 `canonical`(走**同一** `Canonicalizer`,E2)+ 新 hash;读旧 hash。
5. 同一事务:upsert bus(子表**先删后插**,与导入一致),`version` 仅在 hash 变化时 +1。
6. hash **变化**时:写一条 `bus_route_version` 快照(完整 `BusInput` JSON + 新 hash + `changedSummary` + actor);算 `changedSummary`(旧/新 canonical 标量 diff `[{field,old,new}]` + 变更子表名列表);`!suppressEvents` 则 `publishEvent(BusUpdatedEvent(busRouteId, sourceId, oldHash, newHash, changedSummary))`。
7. hash **不变**(幂等保存)→ 不升 version、不快照、不发事件。
- **导入路径**:`SeedImporter` 重构为对每条线路调 `save(..., suppressEvents=true)`;首次导入即建 v1 快照作基线;之后幂等不变则跳过。靠 `SeedImporterIT` 兜回归。

**`verify(sourceId, actor)`**:更新 `last_verified_at/by` + 审计 `VERIFY_BUS`;**不动 hash / version、不快照、不发事件**。

**`delete(sourceId, actor)`**(SUPER_ADMIN):软删 `deleted=1` + 审计 `DELETE_BUS`。**订阅者通知留切片 B。**

**`rollback(sourceId, targetVersion, actor)`**(OPERATOR/SUPER_ADMIN):读该版本 `snapshot_json` → 反序列化为 `BusInput` → 调 `save(...)` 存为**新版本**(version 继续 +1、重算 hash、发事件、审计 `ROLLBACK_BUS`,summary 注明「回滚自 vN」)。历史只增不毁,可再回滚。

**`BusUpdatedEvent`**:`record(long busRouteId, String sourceId, String oldHash, String newHash, ChangedSummary summary)`,经 Spring `ApplicationEventPublisher` 发布。切片 B 用 `@TransactionalEventListener(AFTER_COMMIT) @Async` 监听;本期无监听者(发布即丢弃,无副作用)。

## 4. 操作审计(#7b)

- 新建模块包 `com.airportbus.audit`(design.md 把 audit 列为独立模块):`@Audited(action, target)` 注解 + `@Around` 切面 `AuditAspect` + `AuditMapper`/`audit_log` 写入 + `AuditQueryController`。
- 切面:actor 从 `CurrentUser`(E10 **绝不信请求体**),IP 从 `RequestContextHolder` 取请求;targetId 取方法的 `sourceId` 参数;方法成功返回后写 `audit_log`(失败抛异常则不记成功)。
- 注解挂在 admin 写端点(controller 方法):CREATE/UPDATE/DELETE/VERIFY/ROLLBACK_BUS。
- `GET /admin/audit` 列表 + 过滤(actor / action / 日期范围)+ 分页;只读,`requireAdmin()`。

## 5. API 契约(`/api/v1/admin/**`,需管理员;错误体沿用 `{code,message,details,traceId}` + 真实状态码)

| 方法 | 路径 | 行为 | 权限 |
|------|------|------|------|
| GET | `/admin/buses/tree` | 国家/城市/机场/线路 树 | requireAdmin |
| GET | `/admin/buses/{sourceId}` | 编辑用完整 `BusView`(含全子表 + version + 时间字段) | requireAdmin |
| POST | `/admin/buses` | 新建(body 带 airportCode + BusInput) → 200 资源 | requireAdmin |
| PUT | `/admin/buses/{sourceId}` | 更新(body 带 BusInput + version) → 200;version 不符 409 | requireAdmin |
| POST | `/admin/buses/{sourceId}/verify` | 核对无误 → 200 | requireAdmin |
| DELETE | `/admin/buses/{sourceId}` | 软删 → 200 | **requireSuperAdmin** |
| GET | `/admin/buses/{sourceId}/versions` | 版本列表(version/actor/时间/hash/摘要) | requireAdmin |
| GET | `/admin/buses/{sourceId}/versions/{version}` | 单版本完整快照 | requireAdmin |
| POST | `/admin/buses/{sourceId}/versions/{version}/rollback` | 回滚为新版本 → 200 | requireAdmin |
| GET | `/admin/audit` | 审计列表 + 过滤 | requireAdmin |

- 新增 `CurrentUser.requireSuperAdmin()`(role == SUPER_ADMIN,否则 403 `ADMIN_FORBIDDEN`)。
- 错误码新增:`BUS_VERSION_CONFLICT`(409)、`BUS_NOT_FOUND` 复用、`VALIDATION_FAILED` 复用。
- 对外 id 用 `source_id` / 机场 `code`,绝不暴露自增 id。资源词汇 `bus`。MyBatis 仅 `#{}`;读路径排除 `deleted=1`。

## 6. 前端(admin,复用 Element Plus + #7a 的 AdminLayout)

- **巴士维护页 `/admin/buses`**:左**树**(国家→城市→机场→线路,`el-tree` 或复用原型 details 结构)+ 右**编辑表单**:
  - 标量字段(route/destination/operator/officialUrl/duration/price/operatingHours/lastUpdated)。
  - **子表编辑器**(可增删行):stops(seq+direction+name)、schedules(direction+timeRange+intervalText+note)、alerts(type+message+startDate+endDate)、images(url+caption)、files(name+url)。
  - 按钮:**保存(触发推送)**(PUT/POST,带 version;409 → 提示「已被他人修改」+ 重载)、**核对无误**、**新建**、**删除**(仅 SUPER_ADMIN 显示)。
  - **版本历史面板**:列表(version/who/when/摘要)+ 查看某版本(与当前 diff 展示)+ **回滚**(二次确认)。
- **操作记录页 `/admin/audit`**:`el-table` + 过滤(actor/action/日期)+ 分页。
- 侧栏(AdminLayout)加「🚌 巴士维护」「🧾 操作记录」入口。
- XSS(E7):全 `{{ }}` 自动转义,禁 `v-html`;子表自由文本同。
- API 客户端 `api/admin-bus.ts`、`api/admin-audit.ts`(走现有 `client.ts`)。

## 7. 测试

**后端 IT**(Testcontainers;`*IT` 须 `-Dtest=` 点名):
- `save`:hash 变 → version+1 + 快照 + 发事件;hash 不变 → 不升版本/不快照/不发事件;子表先删后插正确;**乐观锁** version 不符 → 409。
- `verify`:更新核对时间、不升版本、不快照、不发事件。
- `delete`:软删、读路径不再可见、审计 DELETE_BUS;非 SUPER_ADMIN(OPERATOR)→ 403。
- `rollback`:还原旧内容为**新版本**(version 继续增)、发事件、审计 ROLLBACK_BUS;历史保留。
- 字段级 `changedSummary`:标量 old→new 列表 + 变更子表名正确。
- 审计切面:写端点产生 `audit_log` 行(actor 来自 token 非请求体、带 IP)。
- admin API RBAC 矩阵:匿名 401;OPERATOR 能改/核对/回滚、删除 403;SUPER_ADMIN 全可。
- **`SeedImporterIT` 仍绿**(导入重构无回归,幂等不重复快照)。

**前端 vitest**:
- 树渲染 + 选中加载编辑表单;保存成功 / 409 冲突处理;子表增删行;新建流程。
- 版本历史:列表渲染、查看版本、回滚二次确认。
- 删除按钮按角色显隐(SUPER_ADMIN 才显)。
- 审计页:列表 + 过滤。
- API 客户端 URL/参数正确。

## 8. 验收标准

- 管理员能在 `/admin/buses` 维护线路(含全部子表)、核对无误、删除(SUPER_ADMIN);保存即重算 content_hash。
- 并发编辑:后到的保存因 version 不符得到 409,不会静默覆盖。
- 每次内容变化产生一条 `bus_route_version` 快照;后台可查看历史、看某版本、**回滚**(回滚生成新版本,历史不丢)。
- 每次写操作(增/改/删/核对/回滚)有 `audit_log` 记录(谁/何时/动作/对象/IP)。
- 内容变化发出 `BusUpdatedEvent`(本期无监听者,为切片 B 备好闭环触发点)。
- 种子导入仍幂等、无回归(`SeedImporterIT` 绿);导入与运行时共用同一 Canonicalizer(无幽灵变更)。
- 后端 IT + 前端 vitest 全绿。
