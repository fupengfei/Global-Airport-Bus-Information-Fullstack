# 管理后台独立登录页(/admin/login)设计

> 状态:已批准设计,待写实现计划
> 日期:2026-06-23
> 范围:仅前端。无后端改动。

## 背景与动机

当前 `/login` 是面向公开用户的统一页(登录 / 注册 / 找回密码三个 Tab),而 `/admin` 的路由守卫 [`adminGuard`](../../../frontend/src/router/adminGuard.ts) 在未登录时把人重定向到这个**公开**登录页。管理员希望后台有自己独立的登录入口。

期望(用户已确认):

1. 独立 URL(`/admin/login`)+ 后台外观,登录后直达后台。
2. 在这个入口拒绝非管理员。
3. 公开页不暴露后台入口。

**现状已满足项**:公开顶栏 [`App.vue`](../../../frontend/src/App.vue) 本来就没有任何 `/admin` 链接,`/admin` 只能手输网址进入。因此第 3 点无需删任何东西,只需保证后续不新增此类链接。

## 决策:拒绝非管理员 = 前端拦截

后端 `/auth/login` 对任何合法用户都签发 token,与角色无关;真正的强约束是 admin 接口的服务端 403(`requireAdmin`)。

采用**前端拦截**:后台登录页提交成功后检查角色,非 `SUPER_ADMIN`/`OPERATOR` 即刻 `logout()` 清掉刚签发的 token 并报错。不新增后端端点(避免多一套契约,且安全增益相对 403 已有约束很小)。这与既有注释「前端守卫只是体验,后端 403 才是强约束」([`adminGuard.ts`](../../../frontend/src/router/adminGuard.ts) 第 7 行)一致。

`isAdminRole(role) = role === 'SUPER_ADMIN' || role === 'OPERATOR'`(复用 `adminGuard.ts` 已导出的判定,不重复实现)。

## 组件与改动

### 1. 新路由 `/admin/login`

- 在 [`router/index.ts`](../../../frontend/src/router/index.ts) 中**于 `/admin` 之前**注册 `{ path: '/admin/login', name: 'admin-login', component: AdminLoginPage }`,本身**不挂** `adminGuard`。
- 路由顺序须保证 `/admin/login` 不被 `/admin` 的 children 抢匹配(显式独立顶层路由即可)。

### 2. 新组件 `pages/admin/AdminLoginPage.vue`

- **只有登录表单**:account + password 两个字段,无注册/找回 Tab(管理员是种子账号)。
- 后台主题外观:复用现有设计 token(`.authCard` / `.input` / `.btn` / `.btn-primary` / `.authErr`),标题用 i18n「管理后台登录」+ 一行副标题,使其读起来是后台入口而非公开登录。
- 进入页时:若 `auth.isAuthed && isAdminRole(auth.user?.role)` → 直接 `router.replace(redirect ?? '/admin')`(已登录管理员不必再登一次)。已登录非管理员 → 停留本页,允许换号登录。

### 3. 登录提交流程

```
err = ''
try {
  await auth.login(account, password)      // 失败 → catch,走 apiErrorMessage
  if (isAdminRole(auth.user?.role)) {
    router.push(safeRedirect ?? '/admin')   // safeRedirect 同 LoginPage:仅接受 / 开头、非 // 的站内路径
  } else {
    await auth.logout()                      // 清掉刚签发的 token
    err = t('adminAuth.noPermission')
  }
} catch (e) {
  err = apiErrorMessage(e, t, te)            // 凭证错误等,沿用现有本地化
}
```

`safeRedirect` 复用 [`LoginPage.vue`](../../../frontend/src/pages/LoginPage.vue) 已有的开放重定向防护(`startsWith('/') && !startsWith('//')`),默认目标 `/admin`。

### 4. `adminGuard` 改动

唯一改动:未登录分支由跳 `login` 改为跳 `admin-login`(保留 `redirect` 回跳)。

- 未登录 → `{ name: 'admin-login', query: { redirect: to.fullPath } }`
- 已登录非管理员 → **保持现状**:`{ name: 'home' }`(静默跳回公开首页,用户已确认)
- 管理员 → 放行

### 5. i18n

zh-CN / en / de 各新增 `adminAuth` 段:`title`(管理后台登录)、`subtitle`、`noPermission`(此账号无管理员权限)。account/password/login 等标签复用现有 `auth.*` 键。

## 测试

- `AdminLoginPage` 组件测试三条:
  1. 管理员凭证 → 登录成功并 `router.push('/admin')`(或 redirect 目标)。
  2. 非管理员凭证 → 调用 `auth.logout()` 且渲染 `noPermission` 错误,未跳转。
  3. 凭证错误 → 渲染 `apiErrorMessage` 文案,未跳转。
- 守卫测试:未登录访问 `/admin` → 重定向到 `/admin/login` 且带 `redirect` query。

## 不做(YAGNI)

- 不动后端(无 `/auth/admin-login` 端点)。
- 后台登录页不放注册 / 找回(管理员为种子账号)。
- 不动公开 `/login` 页。
- 不在公开导航新增后台链接。
