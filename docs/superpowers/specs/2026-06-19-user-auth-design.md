# #2 登录注册(user 模块)— 设计

- **日期**:2026-06-19
- **范围**:用户鉴权模块。注册(邮箱验证码)、登录(JWT)、找回密码(邮箱重置链接)、个人中心。**不含**收藏=订阅(#3)、站内信/推送、admin 后台(#7)、第三方 OAuth。
- **排期**:大模块第 1 个(#2 → #3 → #7)。

## 事实源与冲突裁决

- design.md 称 MVP 不做邮箱验证/找回密码;但 **CLAUDE.md(项目指令,最高优先级)与 design/login.html 原型明确要求**「注册需邮箱验证码、登录支持邮箱找回密码」。按 CLAUDE.md「原型稿优先」规则,**本期实现邮箱验证码 + 找回密码**。
- design.md 已锁定且采纳:JWT access(短期、无状态)+ refresh(可撤销,E9);Redis 登录限流;用户名/邮箱重复 → 409;MyBatis 只用 `#{}`(E6);自由文本前端 `{{ }}` 转义、禁 `v-html`(E7);URL scheme 白名单(E8);每表带审计列 + 逻辑删除。

## 关键技术选型

- **手写 JWT 鉴权,不引入 spring-boot-starter-security**。理由:查询主线零登录(DS4),完整 security 默认全拦截、需到处开口子。改用:
  - `io.jsonwebtoken:jjwt`(jjwt-api/impl/jackson)签发与校验 access token。
  - `org.springframework.security:spring-security-crypto`(仅 crypto 包)的 `BCryptPasswordEncoder` 存密码哈希。
  - 一个 `OncePerRequestFilter`:仅当请求带 `Authorization: Bearer <jwt>` 时校验并把主体(userId/role)放入请求上下文;无 token 不拦截(公开端点照常)。需登录的端点在 Controller 层取上下文主体,缺失则抛 `UNAUTHORIZED`。
- **Token 前端存储**:access + refresh 存 localStorage(本项目取舍;XSS 由 E7 转义缓解)。axios 拦截器加 `Authorization`;401 时用 refresh 轮换,失败跳 `/login`。

## 后端(`com.airportbus.user`)

### 建表(`V3__user.sql`),均含 `created_by/created_at/updated_by/updated_at/deleted`
- `app_user`:`id`、`username VARCHAR(32)`、`email VARCHAR(255)`、`password_hash VARCHAR(100)`、`locale VARCHAR(8) DEFAULT 'zh-CN'`、`role VARCHAR(16) NOT NULL DEFAULT 'USER'`、`email_verified TINYINT(1) NOT NULL DEFAULT 0`。唯一键 `uk_user_username(username,deleted)`、`uk_user_email(email,deleted)`。
- `refresh_token`:`id`、`user_id`、`token_hash CHAR(64)`(SHA-256 存哈希)、`expires_at DATETIME`、`revoked TINYINT(1) NOT NULL DEFAULT 0`、外键 `user_id→app_user(id)`、`KEY idx_rt_user(user_id)`。

### Redis(键 + TTL)
- `evcode:<email>` → 6 位验证码,TTL 600s;另 `evcode:rl:<email>` 60s 重发限流。
- `pwreset:<token>` → userId,TTL 1800s(token 为随机 URL-safe 串)。
- `loginfail:<account>` → 失败计数,TTL 900s;阈值(如 ≥10)→ 429 `RATE_LIMITED`。

### 端点(前缀 `/api/v1`,成功返回资源体,失败用现有 `ApiError` 信封 + 真实 HTTP 状态)
| 方法 | 路径 | 入参 | 行为 / 状态 |
|---|---|---|---|
| POST | `/auth/register/code` | `{email}` | 校验邮箱格式 + 未占用;生成码存 Redis;dev 模式打印控制台、配置 SMTP 则发邮件;60s 重发限流(429)。返回 `{sent:true}` |
| POST | `/auth/register` | `{username,email,code,password}` | 校验码(错/过期→400 `INVALID_CODE`);用户名/邮箱占用→409;BCrypt 存;`email_verified=1`;签发 access+refresh |
| POST | `/auth/login` | `{account,password}` | account=用户名或邮箱;失败→401 `INVALID_CREDENTIALS` 并计数;超阈值→429;成功签发 access+refresh,清失败计数 |
| POST | `/auth/refresh` | `{refreshToken}` | 校验 DB 中存在且未撤销未过期;**轮换**(旧的置 revoked,发新 refresh)+ 新 access;失效→401 `INVALID_TOKEN` |
| POST | `/auth/logout` | `{refreshToken}` | 置 revoked;幂等返回 `{ok:true}` |
| POST | `/auth/password/forgot` | `{email}` | 邮箱存在才生成 reset token 存 Redis 并发链接;**无论是否存在都返回 `{sent:true}`**(不泄露账号是否注册) |
| POST | `/auth/password/reset` | `{token,newPassword}` | 校验 token(失效→400 `INVALID_TOKEN`);更新密码;**撤销该用户所有 refresh**;返回 `{ok:true}` |
| GET | `/me` | — | 需登录;返回 `{username,email,locale,role}` |
| PATCH | `/me` | `{locale}` | 需登录;更新 locale |
| POST | `/me/password` | `{oldPassword,newPassword}` | 需登录;校验旧密(错→400);更新;撤销其余 refresh |

- **Token 规格**:access JWT 含 `sub=userId`、`role`、`exp`(如 15 分钟),HS256,密钥来自 `JWT_SECRET` env(dev 有默认)。refresh 为随机串(原文返前端,DB 存 SHA-256),有效期如 14 天。
- **新 ErrorCode**:`USERNAME_TAKEN`(409)、`EMAIL_TAKEN`(409)、`INVALID_CODE`(400)、`INVALID_CREDENTIALS`(401)、`UNAUTHORIZED`(401)、`INVALID_TOKEN`(401/400)、`RATE_LIMITED`(429)、`INVALID_INPUT`(400)。
- **邮件抽象**:`Mailer` 接口;`ConsoleMailer`(默认,打印)与 `SmtpMailer`(`spring-boot-starter-mail`,`spring.mail.*` 配置存在时启用,用 `@ConditionalOnProperty`)。
- **种子管理员(D4)**:`SEED_ENABLED` 下若无 SUPER_ADMIN 则建一个(用户名 `admin`,随机或固定 dev 密码),**账号密码打印控制台 + 写入 README Quickstart**。幂等。

## 前端

- **路由**:`/login`(tab:登录/注册/找回密码)、`/reset-password`(读 query `token`)、`/me`(个人中心)。`/login`、`/reset-password` 轻量手写 + `tokens.css`,对齐 [login.html](design/login.html)。**不用 Element Plus**。
- **Pinia `auth` store**:`accessToken`/`refreshToken`/`user`;`login/register/logout/refresh/loadMe` actions;持久化 localStorage;`isAuthed` getter。
- **api/client.ts**:请求拦截器加 `Authorization: Bearer`;响应拦截器 401 → 尝试 refresh 一次,成功重放、失败清状态跳 `/login`。
- **api/auth.ts**:封装上述端点。
- **App.vue 顶栏**:`auth.isAuthed` → 显示用户名 + 「个人中心 / 登出」;否则「登录 / 注册」链接到 `/login`。
- **i18n**:zh-CN/en/de 新增 `auth.*`(标签、占位、错误提示、倒计时文案)。错误优先显示后端 `message`(D6:错误服务端本地化)。

## 错误处理

- 后端全部走 `ApiException`→`GlobalExceptionHandler`→`ApiError`,带真实状态码。`@Valid` 入参校验失败 → 400 `INVALID_INPUT` 带 `details`。
- 前端表单内联显示后端 message;429 显示「稍后再试」。

## 测试

- **单测**:`JwtService`(签发/校验/过期/篡改)、`PasswordEncoder` 集成、验证码/重置令牌服务(注入假时钟 + 内存/Testcontainers Redis)、登录限流计数。
- **集成(Testcontainers mysql+redis,沿用 #1 的 `management.health.redis.enabled=false` 修法)**:注册码→注册→登录→`/me`→refresh→logout 全链路;找回密码→重置→旧 refresh 失效;重复注册 409;错码 400;错密 401;限流 429。邮件用 `ConsoleMailer`,测试从其捕获验证码。
- **前端 vitest**:登录页三 tab 切换 + 表单校验 + 调用 mock api;auth store 的 login/refresh/logout;401 拦截器轮换。
- **端到端(opencli 驱动真实 Chrome)**:打开 `/login` → 获取验证码(从后端控制台读)→ 注册 → 自动登录后顶栏显示用户名 → `/me` 改 locale → 登出;找回密码走一遍。

## 验证标准(对齐 design.md UC1 后续)

- 注册/登录/个人中心全链路可用;未登录访问 `/me` 返回 401 信封。
- 查询主线仍全程零登录、不受影响(回归 #1/#4/#5/#6/#8)。
- 种子管理员可登录(为 #7 铺路)。
- fix.md/README 记录种子账号与运行方式。
