# 管理后台独立登录页(/admin/login) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给管理后台加一个独立的登录入口 `/admin/login`(后台外观、仅登录表单),未登录访问 `/admin` 改跳这里,且在该入口拒绝非管理员账号。

**Architecture:** 纯前端改动。新建 `AdminLoginPage.vue`(只登录),复用现有 `auth` store 与设计 token;后端 `/auth/login` 不变,后端 admin 接口的 403 仍是真正的强约束,前端只负责体验。「拒绝非管理员」= 登录成功后查 `auth.user.role`,非管理员则 `logout()` 清 token 并报错。`adminGuard` 仅把未登录分支由跳 `login` 改为跳 `admin-login`。

**Tech Stack:** Vue 3 + `<script setup>` + TypeScript、Pinia、vue-router、vue-i18n、Vitest + @vue/test-utils。

参考规格:[docs/superpowers/specs/2026-06-23-admin-login-page-design.md](../specs/2026-06-23-admin-login-page-design.md)

---

## 文件清单

- 修改:`frontend/src/i18n/locales/zh-CN.ts`、`en.ts`、`de.ts` —— 新增 `adminAuth` 段
- 新建:`frontend/src/pages/admin/AdminLoginPage.vue` —— 后台登录页(仅登录)
- 新建:`frontend/src/test/AdminLoginPage.spec.ts` —— 组件测试
- 修改:`frontend/src/router/adminGuard.ts` —— 未登录跳 `admin-login`
- 修改:`frontend/src/test/adminGuard.spec.ts` —— 同步两条断言到 `admin-login`
- 修改:`frontend/src/router/index.ts` —— 注册 `/admin/login` 路由

所有命令均在 `frontend/` 目录下执行。

---

### Task 1: 三语新增 adminAuth 文案

**Files:**
- Modify: `frontend/src/i18n/locales/zh-CN.ts`(`auth: { ... }` 块之后)
- Modify: `frontend/src/i18n/locales/en.ts`
- Modify: `frontend/src/i18n/locales/de.ts`

- [ ] **Step 1: 在 zh-CN.ts 的 `auth` 块结束 `},` 之后、`msg:` 之前插入 adminAuth**

在 `frontend/src/i18n/locales/zh-CN.ts` 中,`auth` 对象的闭合 `},`(即 `genericError` 那行的下一行)之后,加入:

```ts
  adminAuth: {
    title: '管理后台登录',
    subtitle: '仅限管理员账号访问',
    noPermission: '此账号无管理员权限',
  },
```

- [ ] **Step 2: 在 en.ts 对应位置插入**

```ts
  adminAuth: {
    title: 'Admin Console Login',
    subtitle: 'Administrators only',
    noPermission: 'This account has no admin access',
  },
```

- [ ] **Step 3: 在 de.ts 对应位置插入**

```ts
  adminAuth: {
    title: 'Admin-Konsole Anmeldung',
    subtitle: 'Nur für Administratoren',
    noPermission: 'Dieses Konto hat keinen Admin-Zugriff',
  },
```

- [ ] **Step 4: 类型检查通过(三个 locale 形状一致)**

Run: `npx vue-tsc -b`
Expected: 类型检查无报错退出(0)。三个 locale 形状一致时通过。

- [ ] **Step 5: Commit**

```bash
git add src/i18n/locales/zh-CN.ts src/i18n/locales/en.ts src/i18n/locales/de.ts
git commit -m "i18n(admin): 新增 adminAuth 后台登录文案(三语)"
```

---

### Task 2: AdminLoginPage 组件(TDD)

**Files:**
- Create: `frontend/src/pages/admin/AdminLoginPage.vue`
- Test: `frontend/src/test/AdminLoginPage.spec.ts`

> 设计说明:组件用 `useAuth()`(`login`/`logout`/`user`/`isAuthed`)、`useRouter()`、`useRoute()`、`useI18n()`,并从 `../../router/adminGuard` 导入纯函数 `isAdminRole`。提交登录后:管理员 → `router.push(redirect ?? '/admin')`;非管理员 → `await auth.logout()` 并显示 `adminAuth.noPermission`;凭证错误 → `apiErrorMessage`。

- [ ] **Step 1: 写失败测试 `AdminLoginPage.spec.ts`**

创建 `frontend/src/test/AdminLoginPage.spec.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const push = vi.fn()
const auth = {
  isAuthed: false,
  user: null as { role: string } | null,
  login: vi.fn(async () => {}),
  logout: vi.fn(async () => {}),
}
vi.mock('../stores/auth', () => ({ useAuth: () => auth }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace: push }),
  useRoute: () => ({ query: {} }),
}))

import AdminLoginPage from '../pages/admin/AdminLoginPage.vue'

function mountPage() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(AdminLoginPage, { global: { plugins: [i18n] } })
}

describe('AdminLoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockClear(); auth.login.mockClear(); auth.logout.mockClear()
    auth.isAuthed = false; auth.user = null
    auth.login.mockImplementation(async () => {})
  })

  it('只有登录表单,没有注册/找回 Tab', () => {
    const w = mountPage()
    expect(w.text()).toContain(zhCN.adminAuth.title)
    expect(w.text()).not.toContain(zhCN.auth.register)
    expect(w.text()).not.toContain(zhCN.auth.forgot)
  })

  it('管理员登录成功 → 跳 /admin', async () => {
    auth.login.mockImplementation(async () => { auth.user = { role: 'SUPER_ADMIN' } })
    const w = mountPage()
    await w.find('input[type=text]').setValue('admin')
    await w.find('input[type=password]').setValue('admin12345')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(auth.login).toHaveBeenCalledWith('admin', 'admin12345')
    expect(push).toHaveBeenCalledWith('/admin')
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('非管理员登录 → logout 并报无权限,不跳转', async () => {
    auth.login.mockImplementation(async () => { auth.user = { role: 'USER' } })
    const w = mountPage()
    await w.find('input[type=text]').setValue('zoe')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(auth.logout).toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(w.find('.authErr').text()).toBe(zhCN.adminAuth.noPermission)
  })

  it('凭证错误 → 显示本地化错误,不跳转', async () => {
    auth.login.mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { code: 'INVALID_CREDENTIALS', message: 'bad credentials', details: [], traceId: 't' } },
    })
    const w = mountPage()
    await w.find('input[type=text]').setValue('admin')
    await w.find('input[type=password]').setValue('wrong')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
    expect(w.find('.authErr').text()).toBe(zhCN.errors.INVALID_CREDENTIALS)
  })
})
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `npx vitest run src/test/AdminLoginPage.spec.ts`
Expected: FAIL —— 报找不到 `../pages/admin/AdminLoginPage.vue`(模块不存在)。

- [ ] **Step 3: 实现 `AdminLoginPage.vue`**

创建 `frontend/src/pages/admin/AdminLoginPage.vue`:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../../stores/auth'
import { isAdminRole } from '../../router/adminGuard'
import { apiErrorMessage } from '../../api/client'

const { t, te } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuth()

// 仅接受站内绝对路径(/ 开头且非 //),默认进 /admin
function target(): string {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/admin'
}

// 已登录的管理员直接进后台,不必再登一次
onMounted(() => {
  if (auth.isAuthed && isAdminRole(auth.user?.role)) router.replace(target())
})

const account = ref(''); const password = ref('')
const err = ref(''); const busy = ref(false)

async function doLogin() {
  err.value = ''; busy.value = true
  try {
    await auth.login(account.value, password.value)
    if (isAdminRole(auth.user?.role)) {
      router.push(target())
    } else {
      await auth.logout()
      err.value = t('adminAuth.noPermission')
    }
  } catch (e) {
    err.value = apiErrorMessage(e, t, te)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="authCard">
    <h1 class="adminAuthTitle">{{ t('adminAuth.title') }}</h1>
    <p class="authNote">{{ t('adminAuth.subtitle') }}</p>

    <p v-if="err" class="authErr">{{ err }}</p>

    <form @submit.prevent="doLogin">
      <div class="formrow"><label>{{ t('auth.account') }}</label>
        <input class="input" type="text" v-model="account" :placeholder="t('auth.accountPh')" /></div>
      <div class="formrow"><label>{{ t('auth.password') }}</label>
        <input class="input" type="password" v-model="password" /></div>
      <button class="btn btn-primary btn-block" :disabled="busy">{{ t('auth.login') }}</button>
    </form>
  </div>
</template>

<style scoped>
.adminAuthTitle { font-family: var(--font-display, 'Sora'), sans-serif; font-size: 1.25rem; margin: 0 0 4px; }
</style>
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `npx vitest run src/test/AdminLoginPage.spec.ts`
Expected: PASS(4 个用例全绿)。

- [ ] **Step 5: Commit**

```bash
git add src/pages/admin/AdminLoginPage.vue src/test/AdminLoginPage.spec.ts
git commit -m "feat(admin): 新增管理后台独立登录页 AdminLoginPage(仅登录+拒绝非管理员)"
```

---

### Task 3: 注册路由 + 改 adminGuard 未登录跳转(TDD)

**Files:**
- Modify: `frontend/src/router/adminGuard.ts:12`
- Modify: `frontend/src/test/adminGuard.spec.ts`(两条断言)
- Modify: `frontend/src/router/index.ts`(新增顶层路由)

- [ ] **Step 1: 先改 guard 测试为期望 `admin-login`(失败)**

在 `frontend/src/test/adminGuard.spec.ts` 中,把两处对匿名/loadMe 失败的断言由 `name: 'login'` 改为 `name: 'admin-login'`:

第一处(`redirects anonymous to login with redirect`):
```ts
  it('redirects anonymous to admin-login with redirect', async () => {
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'admin-login', query: { redirect: '/admin' } })
  })
```

第二处(`falls back to login when loadMe throws`):
```ts
  it('falls back to admin-login when loadMe throws', async () => {
    const auth = useAuth()
    auth.accessToken = 'stale'
    auth.user = null
    vi.spyOn(auth, 'loadMe').mockRejectedValue(new Error('401'))
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'admin-login', query: { redirect: '/admin' } })
  })
```

> 注意:`redirects logged-in non-admin to home` 这条**保持不变**(非管理员仍静默跳 `home`)。

- [ ] **Step 2: 运行 guard 测试,确认失败**

Run: `npx vitest run src/test/adminGuard.spec.ts`
Expected: FAIL —— 两条断言期望 `admin-login` 但实际仍是 `login`。

- [ ] **Step 3: 改 `adminGuard.ts` 未登录分支**

在 `frontend/src/router/adminGuard.ts` 中,把两处 `{ name: 'login', query: { redirect: to.fullPath } }` 改为 `{ name: 'admin-login', query: { redirect: to.fullPath } }`(第 12 行的未登录分支,以及第 14 行 loadMe 失败的 catch 分支)。非管理员的 `{ name: 'home' }` 不动。

- [ ] **Step 4: 运行 guard 测试,确认通过**

Run: `npx vitest run src/test/adminGuard.spec.ts`
Expected: PASS。

- [ ] **Step 5: 注册 `/admin/login` 路由**

在 `frontend/src/router/index.ts` 的 `routes` 数组里,**在 `/admin` 那条之前**加入(确保独立顶层路由,不被 `/admin` children 抢匹配):

```ts
    {
      path: '/admin/login', name: 'admin-login',
      component: () => import('../pages/admin/AdminLoginPage.vue'),
    },
```

- [ ] **Step 6: 全量测试 + 类型检查通过**

Run: `npm run test`
Expected: 全部 PASS(含新增 AdminLoginPage 4 例与改后的 guard 例)。

Run: `npm run build`
Expected: `vue-tsc` 类型检查 + vite build 成功,无报错。

- [ ] **Step 7: Commit**

```bash
git add src/router/adminGuard.ts src/test/adminGuard.spec.ts src/router/index.ts
git commit -m "feat(admin): 未登录访问 /admin 改跳 /admin/login 并注册该路由"
```

---

### Task 4: 手工验收(可选,需本地起前端)

**Files:** 无(仅人工验证)

- [ ] **Step 1: 起前端 dev**

Run: `npm run dev`(默认 :5173,`/api` 代理到 :8080;需后端在跑)

- [ ] **Step 2: 验证四条路径**

1. 未登录直接访问 `http://localhost:5173/admin` → 自动跳到 `/admin/login?redirect=/admin`。
2. 在该页用 `admin` / `admin12345` 登录 → 进入 `/admin` 概览。
3. 用普通用户(如 `fpfos`)在该页登录 → 停留本页,显示「此账号无管理员权限」,且未保留登录态(顶栏仍是未登录)。
4. 已登录管理员手动访问 `/admin/login` → 直接被带到 `/admin`。

- [ ] **Step 3: 提交规格与计划文档(随实现一起)**

```bash
git add docs/superpowers/specs/2026-06-23-admin-login-page-design.md docs/superpowers/plans/2026-06-23-admin-login-page.md
git commit -m "docs(admin): 后台独立登录页 设计规格 + 实现计划"
```

---

## Self-Review 记录

- **Spec 覆盖**:独立 URL/后台外观(Task 2)、登录后直达后台 + redirect 防护(Task 2 Step 3)、拒绝非管理员=前端 logout+报错(Task 2 测试3+实现)、未登录跳 admin-login(Task 3)、非管理员静默回 home(Task 3 Step 1 保持不变)、三语 i18n(Task 1)、组件+guard 测试(Task 2/3)。公开页隐藏后台入口=现状已满足,无任务,符合规格 YAGNI 段。
- **占位符**:无 TBD/TODO,所有代码步骤给出完整代码。
- **类型/命名一致**:`isAdminRole` 来自现有 `adminGuard.ts`;`auth.login/logout/user/isAuthed` 与 store 实际签名一致;路由名 `admin-login` 在 guard、测试、路由注册三处一致;i18n 键 `adminAuth.title/subtitle/noPermission` 在三语与组件一致。
