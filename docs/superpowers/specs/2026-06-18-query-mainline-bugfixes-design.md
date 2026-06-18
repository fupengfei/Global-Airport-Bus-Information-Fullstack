# 查询主线 Bug 修复(5 项)— 设计

- **日期**:2026-06-18
- **范围**:仅修复现有「查询主线」的 5 个问题。鉴权(#2)、收藏(#3)、admin(#7)为后续独立模块,不在本 spec。
- **排期决定**:先修这 5 个 Bug 并测试,再做大模块。

## 背景

当前仅实现查询主线:后端 `bus`+`common` 包,前端 首页 / 机场线路 / 详情 三页。原型 `home.html` 与 `bus-detail.html` 对 stops 顺序画法不一致——以用户要求「首节点一定是机场」为准(即 bus-detail 画法,全局统一)。

种子 `data.json` 的 `stops` 顺序不统一:维也纳机场在末尾,上海浦东机场在开头;且部分机场端不含「机场」字样(如 `虹桥东交通中心`)。机场必为线路的一个**端点**。

## 修复项

### #1 途径站点首节点 = 机场(后端归一化,可测)

- **位置**:后端 `BusQueryService.detail(sourceId)`。
- **做法**:detail 查询带上该线路所属机场名(`airport.name`)与城市名(`city.name`)。对 `stops` 的首尾两端点,各自与「机场名 + 城市名」做相似度比较——先剔除通用词(`机场 国际 航站楼 T1 T2 T3 站 中心 交通 枢纽 Airport Terminal`),再比较剩余**特征 token** 的重合度。命中度高的端点视为机场端;若机场端在 `stops` 末尾,则整体反转列表,保证 `stops[0]` 是机场端。两端点都无法判定时保持原序(兜底)。
- **测试**:单测覆盖全部 11 条种子线路,断言每条 `detail.stops` 的首元素是该线路机场端(逐条列期望值)。
- **前端**:`BusCard` 末站实心(`stopDotEnd`)逻辑不变;反转后末站=城市端,符合设计。

### #4 选中机场后,该机场全部线路完整列出(前端)

- **位置**:`HomePage.vue`。
- **做法**:删除「自动选中第一条线路」(L71-73)。选中机场后,默认**渲染该机场全部线路的完整 `BusCard`**(用 `@tanstack/vue-query` 的 `useQueries` 并发拉取各线路 detail;种子里单机场最多约 6 条)。保留单选器作为可选「收窄」:未选中 = 全部展示;选中某条 = 仅展示该条。空机场仍走空态。

### #5 详情页打通(前端)

- 详情页 `BusDetailPage.vue` 与路由 `/bus/:sourceId` 已存在,缺入口。
- 在 `BusCard` 页脚新增内部链接「查询详情 →」指向 `/bus/:sourceId`(通过新增可选 prop 控制:仅首页列表卡片显示,详情页内不重复)。
- 顺带把 `BusDetailPage.vue` / `AirportBusesPage.vue` 面包屑硬编码的「首页」「共 N 条」改走 i18n。

### #6 按站点搜索(后端新端点 + 前端接入)

- **后端**:新增 `GET /api/v1/search?q=`,返回
  ```json
  { "airports": [{"code","name","cityName","countryCode"}],
    "routes":   [{"sourceId","route","destination","airportCode","matchedStop"}] }
  ```
  `routes` 来自对 `bus_stop.name`(及 `route` / `destination` / `operator`)的 `LIKE` 匹配。MyBatis 全程 `#{}` 传参(`LIKE CONCAT('%', #{q}, '%')`),排除 `deleted=1`,结果去重并限量(如 ≤10)。`q` 为空或过短返回空结果。
- **前端**:`HomePage.vue` 搜索框改调该端点(防抖 ~250ms),建议列表分「机场命中 / 站点命中」两类;点机场命中 → 级联选中该机场;点站点命中 → 跳 `/bus/:sourceId`。
- **测试**:控制器/服务测试覆盖「按站点名命中」「空查询返回空」「注入字符安全」。

### #8 德语(前端)

- 新增 `frontend/src/i18n/locales/de.ts`(对 `zh-CN`/`en` 现有 key 全量德译)。
- `i18n/index.ts` 注册 `de`,放宽 `setLocale` 类型为 `'zh-CN' | 'en' | 'de'`。
- `App.vue` 语言切换组新增「DE」按钮。
- 涉及「中国台湾省」约定的文案,德语同样**不单列 Taiwan**。

## 不做(本期)

- 鉴权 / 登录注册(#2)、收藏=订阅(#3)、admin 后台(#7)。
- 不改 `content_hash` 相关逻辑(#1 只在读取期重排展示顺序,不改库内 `seq` / 不触发幽灵变更)。

## 验证

1. 后端:`mvn test`(新增 #1、#6 测试通过)。
2. 前端:`vitest`(更新 HomePage / BusCard 相关用例)。
3. 端到端:修复 `~/.opencli` 权限后用 `opencli` 跑自动测试;辅以本地起服务手测搜索/详情/语言切换。
4. 全程记录到 `fix.md`(现象 → 根因 → 修复 → 验证)。
