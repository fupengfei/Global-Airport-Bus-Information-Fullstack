# 修复记录(fix.md)

本文件按问题逐条记录排查与修复过程:**现象 → 根因 → 修复 → 验证**。新问题追加到文件末尾。

---

## #1 `docker compose up -d --build` 启动失败:拉不到基础镜像

- **日期**:2026-06-18
- **状态**:✅ 已修复(救急方案)

### 现象
```
=> ERROR [web internal] load metadata for docker.io/library/node:20-alpine            39.9s
=> ERROR [app internal] load metadata for docker.io/library/eclipse-temurin:21-jre    39.9s
=> ERROR [app internal] load metadata for docker.io/library/maven:3.9-eclipse-temurin-21
```
BuildKit 加载基础镜像 metadata 时约 40s 超时报错,构建无法开始。

### 根因
直接 `docker pull eclipse-temurin:21-jre` 暴露真因:
```
toomanyrequests: You have reached your unauthenticated pull rate limit.
```
1. **Docker Hub 匿名拉取限流**:未登录,触发 docker.io 匿名额度上限。
2. **daemon.json 里的镜像源大多失效**,导致拉取回退到 docker.io 直连并被限流 / 超时:
   - `registry.cn-hangzhou.aliyuncs.com` —— 是阿里云**自有镜像仓库**,不是 Docker Hub 加速器(正确形式是个性化的 `https://<id>.mirror.aliyuncs.com`)。
   - `mirror.ccs.tencentyun.com` —— 仅在腾讯云 VPC **内网**可用。
   - `docker.mirrors.ustc.edu.cn` —— 已转为**校内网**专用。
   - `registry.docker-cn.com` —— 多年前**已停服**。
3. BuildKit/buildx 的 `load metadata` 步骤**不一定走 daemon.json 的 registry-mirrors**,所以即便配了也可能直连 docker.io。

> 备注:开发机当时能本地构建成功,只是因为基础镜像还在 BuildKit 缓存里;换机器 / 清缓存 / 换 IP 即复现。

### 修复
**救急方案:`docker login`**。认证后匿名限流解除,三个基础镜像(`eclipse-temurin:21-jre`、`maven:3.9-eclipse-temurin-21`、`node:20-alpine`)均拉取成功。

```bash
docker login -u <DockerHub用户名>   # 提示 Password 处粘贴密码或 Personal Access Token
docker pull eclipse-temurin:21-jre
docker pull maven:3.9-eclipse-temurin-21
docker pull node:20-alpine
docker compose up -d --build
```

### 验证
- `docker pull eclipse-temurin:21-jre` → `Status: Downloaded newer image`(不再 `toomanyrequests`)。
- `docker compose up -d --build` 完整通过,四个容器全部 Up。

### 遗留 / 后续
- `docker login` 解的限流是**临时**的:换网络 / 换 IP / 额度耗尽会再撞。
- **彻底免登录方案(待选)**:把 daemon.json 的失效源替换为实测可用的镜像源,改完需重启 Docker Desktop 生效。实测 `/v2/` 握手正常(返回 401 即可用):
  - ✅ `https://docker.m.daocloud.io`
  - ✅ `https://docker.1ms.run`
  - ✅ `https://hub.rat.dev`
  - ❌ `https://dockerproxy.cn`(不可达)

---

## #2 app 缺少健康检查:容器「在跑」但服务其实挂了也不报错

- **日期**:2026-06-18
- **状态**:✅ 已修复

### 现象
`/actuator/health` 返回 500;`docker-compose.yml` 中 app 服务无 healthcheck,`web` 仅 `depends_on: ["app"]`(只等启动、不等健康)。结果:app 起不来时容器仍显示 `Up`,`docker compose up` 不会报失败,问题被静默掩盖。

### 根因
- 项目未引入 `spring-boot-starter-actuator`,`/actuator/health` 端点根本不存在(被当成静态资源 404→500)。
- compose 未给 app 配 healthcheck,`web` 也没等待 app 真正健康。

### 修复
1. [backend/pom.xml](backend/pom.xml) 增加 `spring-boot-starter-actuator` 依赖。
2. [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) 只暴露 health 端点:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health
     endpoint:
       health:
         show-details: never
   ```
3. [docker-compose.yml](docker-compose.yml) 给 app 加 healthcheck(镜像自带 `curl`),并让 `web` 等 app 健康后再起:
   ```yaml
   app:
     healthcheck:
       test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
       interval: 5s
       timeout: 3s
       retries: 20
       start_period: 20s
   web:
     depends_on:
       app: { condition: service_healthy }
   ```

### 验证
- `curl /actuator/health` → `{"status":"UP"}` HTTP 200。
- `docker compose ps` → app 状态由纯 `Up` 变为 **`Up (healthy)`**。
- 启动日志出现 `app ... Waiting ... Healthy`,确认 `up` 会等待并校验 app 健康。

---

## #3 查询主线 5 项用户反馈修复

- **日期**:2026-06-18
- **状态**:✅ 已修复(后端 22 测试 + 前端 9 测试 + 构建 + 真实浏览器端到端均通过)
- **设计/计划**:[docs/superpowers/specs/2026-06-18-query-mainline-bugfixes-design.md](docs/superpowers/specs/2026-06-18-query-mainline-bugfixes-design.md)、[docs/superpowers/plans/2026-06-18-query-mainline-bugfixes.md](docs/superpowers/plans/2026-06-18-query-mainline-bugfixes.md)

### #1 途经站点首节点必须是机场
- **现象**:线路卡片的途经站点时间轴首节点不一定是机场(维也纳线路机场画在末尾)。
- **根因**:`data.json` 的 `stops` 顺序不统一——维也纳「城市端→机场」、上海浦东「机场→城市端」;且部分机场端不含「机场」字样(如 `虹桥东交通中心`),无法靠简单反转或关键字判定。
- **修复**:新增纯函数 [AirportStopOrderer](backend/src/main/java/com/airportbus/bus/service/AirportStopOrderer.java),用「机场名去掉城市名+通用词后的特征 token」匹配两端点,机场端在尾则反转;特征打平时退化为机场关键词(机场/航站楼/airport/terminal)判定。`BusQueryService.detail` 读取期调用(新增 `selectRouteAirportCity` 取机场/城市名),不改库内 `seq`、不触发幽灵变更。
- **验证**:`AirportStopOrdererTest` 覆盖全 11 条种子线路;实跑 API `vie-vab1→维也纳机场`、`pvg-line4→浦东机场`、`sha-night-pvg→虹桥东交通中心`(正确避开浦东国际机场)、`pvg-shuttle-p4→T2 航站楼`。

### #4 选中机场后默认列出该机场全部线路
- **现象**:选中机场后默认只选中第一条、只显示一张卡片。
- **根因**:[HomePage.vue](frontend/src/pages/HomePage.vue) 有「线路加载后默认选中第一条」逻辑。
- **修复**:删除默认选中;默认 `routeId=''` → 用 `useQueries` 并发拉取该机场全部线路详情并渲染完整卡片;单选器保留为可选「收窄」(未选=全部,选中=仅该条)。
- **验证**:浏览器实测 VIE → 列出 3 张卡片(VAB 1 / VAB 2 / VAB 3)。

### #5 详情页打通
- **现象**:点不进详情页(无入口)。
- **根因**:`BusDetailPage.vue` 与路由 `/bus/:sourceId` 已存在,但卡片上没有内部链接。
- **修复**:`BusCard` 增加可选 `detailLink` prop,首页卡片页脚渲染「查询详情 →」`router-link`;面包屑「首页」改走 i18n。
- **验证**:浏览器实测点击「View details」→ 跳 `/bus/vie-vab1`,详情页正常渲染(面包屑 + 卡片 + 站点)。

### #6 按站点搜索
- **现象**:搜索框只能按机场/城市/IATA 过滤,按站点名搜不到。
- **根因**:原搜索是纯客户端、只过滤 tree 里的机场,站点数据不在 tree 中。
- **修复**:新增后端 `GET /api/v1/search?q=`(`searchAirports` + `searchRoutesByStop`,MyBatis 全程 `#{}` 防注入,排除 `deleted=1`,去重限量);前端搜索框改调该端点(防抖 250ms),建议列表给机场命中与站点命中,点站点命中跳详情页。
- **验证**:API `中央车站→vie-vab1`、`浦东→PVG+4 线`、空查询→空;浏览器实测建议框出现站点命中。

### #8 新增德语
- **现象**:只有中文 / EN。
- **修复**:新增 [de.ts](frontend/src/i18n/locales/de.ts) 全量德译,`i18n/index.ts` 注册 `de` 并放宽 `setLocale` 类型,`App.vue` 加 DE 按钮。
- **验证**:浏览器点 DE → 面包屑变 `Startseite`、`Offizieller Link`、站点命中标签 `HALTESTELLE`。

### 连带修复:IT 上下文因 actuator 崩溃
- **现象**:`mvn -Dtest=*IT test` 全部报 `Failed to load ApplicationContext` → `redisHealthContributor: Beans must not be empty`。
- **根因**:fix.md #2 引入 `spring-boot-starter-actuator` 后,actuator 的 reactive redis health 贡献器在 IT(redis 被 mock,无 reactive redis bean)下实例化失败。与本次 5 项修复无关,是潜伏回归。
- **修复**:在 `BusQueryServiceIT` / `SeedImporterIT` 的 `@SpringBootTest(properties=…)` 关闭 `management.health.redis.enabled`。
- **验证**:`mvn -Dtest=BusQueryServiceIT,SeedImporterIT,SearchHotnessServiceIT test` → 9 绿。
- **遗留**:`*IT` 不匹配 surefire 默认 include,也没配 failsafe,只能 `-Dtest=...IT` 手动触发;建议后续加 failsafe 插件。

### 关于「用 opencli 自动测试」
- `opencli`(@jackwener/opencli)首次跑崩在 `~/.opencli/package.json` 的 `EACCES`——该目录被 root 安装、属主是 root,普通用户无法写;用 `HOME` 指向可写副本可绕过。
- 但 `opencli browser` 的端到端依赖一个需手动加载并连接的 **Chrome 扩展**(`opencli doctor` 报 `Extension: not connected`),无法在无头/非交互环境自动完成。
- 因此端到端改用 gstack 无头浏览器:其 CLI 客户端 `dist/browse` 在本环境连不上自身守护进程(8s 超时),但守护进程本身健康,故直接走守护进程 HTTP `POST /command`(端口/令牌读自 `~/.claude/skills/gstack/.gstack/browse.json`)驱动完成上述全部浏览器验证。

---

## 已知但尚未修复

### N1 404 被伪装成 500
- **现象**:访问不存在的路径(如 `/api/v1/countries`)返回 `INTERNAL_ERROR` HTTP **500**,而非 404。
- **根因**:[backend/src/main/java/com/airportbus/common/GlobalExceptionHandler.java](backend/src/main/java/com/airportbus/common/GlobalExceptionHandler.java) 兜底捕获了 Spring 的 `NoResourceFoundException`,统一返回 500。
- **建议修复**:单独处理 `NoResourceFoundException`,返回真正的 404 错误信封。
