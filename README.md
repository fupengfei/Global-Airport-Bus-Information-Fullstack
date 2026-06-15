# 全球机场巴士信息(Global Airport Bus Information)

按 国家 / 城市 / 机场 搜索全球机场巴士信息;含用户系统(注册/登录/个人中心/收藏/站内信/建议工单)、界面多语言、以及管理后台。订阅(收藏)的巴士信息更新时,通过站内信及时推送。

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
