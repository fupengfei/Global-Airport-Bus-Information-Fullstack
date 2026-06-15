# 全球机场巴士信息(Global Airport Bus Information)

按 国家 / 城市 / 机场 搜索全球机场巴士信息;含用户系统(注册/登录/个人中心/收藏/站内信/建议工单)、界面多语言、以及管理后台。订阅(收藏)的巴士信息更新时,通过站内信及时推送。

## 实现过程(AI Vibe Coding)

本项目全程用 [Claude Code](https://claude.com/claude-code)(Opus)以 vibe coding 方式构建,配合 [superpowers](https://github.com/obra/superpowers) 与 gstack 技能栈,按「先想清楚、再设计、后实现」的顺序推进:

1. **头脑风暴与需求** — `superpowers:brainstorming` 厘清意图、范围与边界。
2. **系统设计** — 产出 [docs/design.md](docs/design.md),经多 agent 对抗评审(gstack 的 CEO / 工程经理 / 设计 / DX 等视角 + `autoplan` 四阶段)反复打磨,所有修订以编号评审日志(`E*`/`D*`/`DS*`/`EN*`)留痕。
3. **高保真设计稿(Frontend Design)** — 在 [design/](design/) 完成 8 页生产级 HTML/CSS 稿,经 `frontend-design` + gstack 设计工具生成、`plan-design-review` 多轮评审(含对比度达 WCAG AA、44px 触控目标、骨架与卡片对齐等修复)。`design/styles.css` 作为前端样式 **tokens 单一事实源**,实现期复制为 `frontend/src/styles/tokens.css`,Vue 组件直接复用其 class。
4. **实施计划** — `superpowers:writing-plans` 把「查询主线」拆成 19 个 **TDD bite-sized** 任务([docs/superpowers/plans/](docs/superpowers/plans/)),每个任务自带可运行代码、验证命令与提交点。
5. **实现** — `superpowers:subagent-driven-development`:每个任务派发**全新上下文的子代理**实现,严格 **TDD(先写失败测试 red → 实现 green)**,实现后过**两阶段评审**(先 spec 合规、再代码质量;核心任务用更强模型 opus 深评)。评审抓出并修复了多处真实问题(Redis 缓存命中 500、charset 混淆、`@MapperScan` 切片回归、NFC 测试缺口等)。
6. **收尾** — 整体 final code review 确认集成一致性与可合并性,再用 `superpowers:finishing-a-development-branch` 合并。

> 全部后端 9 单测 + 7 集成测试(Testcontainers 真 MySQL/Redis)、前端 9 测试 + 类型检查零错通过;`docker compose up` 一键起全栈端到端验证。

## 技术栈

- 前端:Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-i18n + TanStack Query
- 后端:Spring Boot 3 + Spring MVC + MyBatis(模块化单体)
- 数据库:MySQL 8;缓存:Redis
- 数据源:维也纳、上海两市,用 [data.json](https://github.com/fupengfei/Global-Airport-Bus-Information/blob/main/data.json) 初始化(服务端抓取延后)

## 文档

- 设计文档:[docs/design.md](docs/design.md)(纯设计层,不含数据库 schema;经多 agent 对抗评审修订)

数据库 schema 不在设计文档内,留待实现环节(基于 design.md「系统核心数据结构」落地)。

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

## 下一步

详见 design.md 的 The Assignment。实现第一步:基于「系统核心数据结构」设计具体 schema + 写幂等种子导入器,把维也纳/上海两城数据导入并跑通查询主线。
