# 全球机场巴士信息(Global Airport Bus Information)

按 国家 / 城市 / 机场 搜索全球机场巴士信息;含用户系统(注册/登录/个人中心/收藏/站内信/建议工单)、界面多语言、以及管理后台。订阅(收藏)的巴士信息更新时,通过站内信及时推送。

## 技术栈

- 前端:Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-i18n + TanStack Query
- 后端:Spring Boot 3 + Spring MVC + MyBatis(模块化单体)
- 数据库:MySQL 8;缓存:Redis
- 数据源:维也纳、上海两市,用 [data.json](https://github.com/fupengfei/Global-Airport-Bus-Information/blob/main/data.json) 初始化(服务端抓取延后)

## 文档

- 设计文档:[docs/design.md](docs/design.md)(经多 agent 对抗评审修订)
- 数据库 schema:[db/migration/V1__init_schema.sql](db/migration/V1__init_schema.sql)

## 下一步

详见 design.md 的 Next Steps。第一步:用 Flyway 跑 V1 schema,写**幂等**种子导入器(以 `bus.source_id` / `country.code` / `(country_id,name)` upsert)把两城数据导入。
