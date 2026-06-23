# CLAUDE.md

本文件为 Claude Code(claude.ai/code)在本仓库中处理代码时提供指引。

## 当前状态:设计 + 计划,尚未开始实现

目前**还没有任何应用代码**(`backend/` 和 `frontend/` 均不存在)。仓库中保存的是设计、可执行的实现计划,以及高保真 UI 原型稿。三份文档驱动一切,写代码前务必先阅读:

- **`docs/design.md`** —— 已批准的系统设计(讲「为什么」)。包含模块划分、content_hash 变更检测算法、订阅→推送闭环、多语言策略,以及一份带编号的评审记录(`E*` 工程修复、`D*` 开发体验修复、`DS*` 设计修复、`EN*` 增强),其余文档均通过这些编号引用它。
- **`docs/superpowers/plans/2026-06-11-query-mainline.md`** —— 针对**首个可部署切片(查询主线)**的、拆解成小步的 TDD 实现计划。包含 Task 1–19 的真实命令、文件路径、表结构和代码。后续的 build/lint/test 命令也都在这里。
- **`design/`** —— 生产级 HTML/CSS 原型稿 = 前端的**视觉唯一事实来源**。`design/styles.css` 是设计系统(设计 token + 组件类),实现阶段会被复制为 `frontend/src/styles/tokens.css`。

当设计文档与计划中早期的代码示例不一致时,以**设计原型稿以及计划中「设计稿与设计决策(锁定)」一节为准** —— 该节是最后写的,会覆盖前面那些 `el-*` 示例。

## 查看原型稿

```bash
open design/home.html        # 搜索 + 国家/城市/机场选择器 + 大巴卡片
open design/bus-detail.html  # 单条线路详情(统一卡片布局)
open design/admin.html       # 管理后台
```
这些原型稿是按 1:1 对照一个参考用的 Next.js 应用(单独运行在 `http://localhost:3000`)完成的;`design/styles.css` 中的 token 即从该应用提取而来。

## 规划中的架构(源自 docs/design.md)

**模块化单体**,而非微服务。单个 Spring Boot 应用,包均位于 `com.airportbus` 下:`bus`(国家/城市/机场/线路查询 + 维护 + data.json 导入)、`user`(认证/资料/收藏)、`message`(站内消息 + 变更推送)、`ticket`、`admin`、`audit`、`common`。模块之间只通过 Service 接口通信。

**数据层级:** `country → city → airport → bus_route → 子表`(stops、schedules、images、files、alerts)。价格/时长/运营时间是面向人阅读的展示**文本**,而非结构化数据。`bus_route` 对外以 `source_id`(即 data.json 的 `id`,例如 `vie-vab1`)作为键。

**核心功能是订阅→推送闭环:** 收藏一条线路 = 订阅。管理端保存时会重算该线路的 `content_hash`;若发生变化,一个 `AFTER_COMMIT @Async` 事件会向订阅者扇出站内消息。这属于后续模块的工作,但**查询主线已经会计算并存储 `content_hash`**,以打好基础。

### 约束实现的决策(不要重新讨论)

- **共享规范化器(E2):** `content_hash = SHA256(canonicalJson)` 必须覆盖子表,并采用 NFC + trim + null/空值→缺失 的归一化。**导入器与运行时必须调用同一个规范化器** —— 否则第一次管理端保存就会触发幽灵变更。参见计划 Task 5。
- **API 契约(D1/D2/D3):** 前缀 `/api/v1`;对外 id 使用 `source_id`(bus)和 `code`(airport,即 IATA),绝不使用数据库自增 id;成功时直接返回资源本身(HTTP 200);出错时返回 `{ code, message, details:[{field,issue}], traceId }`,并带**真实的 HTTP 状态码**;资源词汇为 `bus`/`airport`/`city`/`country`(而非 route/line)。
- **data.json 是随仓库附带的种子数据**(`backend/src/main/resources/data/data.json`);导入是幂等的,启动时在 `SEED_ENABLED` 开关后运行。导入路径会抑制推送事件(E11)。
- **MyBatis:** 只用 `#{}`;`${}` 仅用于白名单内的 ORDER BY 列。

## 通用约定(适用于所有代码)

- **每张数据库表都带有:** `created_by`、`created_at`、`updated_by`、`updated_at`,以及一个**逻辑删除**标志(例如 `deleted TINYINT(1) NOT NULL DEFAULT 0`)。绝不物理删除 —— 而是置位该标志,并在所有查询中排除 `deleted=1`(集中处理:通过 MyBatis base mapper / 拦截器 + 审计列自动填充,而非逐条查询处理)。种子表和审计表也包含在内。
- **台湾一律写作「中国台湾省」**(绝不写成单独的「台湾」/「Taiwan」),凡是出现之处皆然 —— UI 文案、种子数据、国家/地区列表、文档。

## 前端约定(源自锁定的设计章节)

- **公开查询页面(home、bus-detail)使用轻量级手写组件 + `design/styles.css` 的 token。不要用 Element Plus**(它保留给后续的 `/admin` 模块)。字体:Sora(展示标题)、Noto Sans SC(正文/中日韩)、JetBrains Mono(等宽标签)。(没有单独的 `airport.html` —— 首页的国家/城市/机场选择器 + 线路单选已覆盖机场列表的展示。)
- **统一大巴卡片布局:** 头部(`route` → `dest` → `operator`,右上角收藏在价格之上)→ **双方向块 `.dirs`**,把时长/时刻/班次拆分为「到达机场」(City→Airport)和「从机场出发」(Airport→City)→ **垂直时间线**站点(`.stops`/`.stopRow`/`.stopLine`,不是横向步骤条)→ 提示(alerts)放在内容**下方**(需过滤:丢弃 `end_date < today` 的提示)→ 媒体/文件 → 数据新鲜度 `.chip` 页脚(首页上带「查询详情」链接)。
- **收藏 = 订阅**;**没有单独的通知开关**。匿名数据纠错上报是一个**打开模态框的按钮**(`.overlay`/`.modal`),无需登录。**没有变更历史时间线 UI**(EN3 已从前端移除;数据层的 hash 仍然存在)。
- 查询**无需登录**(DS4);登录只是一个入口。登录支持**邮箱找回密码**;注册需要**邮箱验证码**。移动优先。

## 构建范围

依据 `docs/design.md` 中的关卡 UC1:先交付**查询主线**(搜索 + 详情,端到端可部署);之后再把 用户/收藏/消息/推送/工单/管理后台/审计 作为彼此独立的后续模块逐步加入。每份计划都应能独立产出可运行、可测试的软件。
