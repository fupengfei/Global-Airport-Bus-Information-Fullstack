# 巴士维护 + 审计 + 版本历史 —— 前端实现计划(#7 切片 A · 前端)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 前端交付 `/admin/buses` 巴士维护页(树 + 编辑表单含全部子表 + 版本历史 + 回滚)与 `/admin/audit` 操作记录页,接已落地的后端 API。

**Architecture:** 复用 #7a 的 AdminLayout/EP/路由懒加载;新增 2 个 api 客户端 + 2 个页面 + 2 个侧栏入口 + 2 条路由。删除按钮按 `auth.user.role==='SUPER_ADMIN'` 显隐;保存 409 走统一错误体提示并重载。

**Tech Stack:** Vue 3 + Pinia + vue-router + Element Plus + Vitest(jsdom + 已有 `src/test/setup.ts` 的 ResizeObserver polyfill)。

---

## 上游与约束
- spec:`docs/superpowers/specs/2026-06-20-bus-maintenance-audit-design.md` 第 6 节。
- 后端已落地 API(master):见下「API 契约」。
- 复用 #7a:`components/admin/AdminLayout.vue`(nav 数组)、`router/index.ts`(/admin 父路由 + 子路由)、`api/client.ts`(`http` + `asApiError`)、`stores/auth.ts`(`user.role`)、EP per-component import、`src/test/setup.ts`。
- XSS:全 `{{ }}` / EP 组件,禁 `v-html`。命令:`cd frontend && npx vitest run <file>`;`npm run build`(vue-tsc 严格,`beforeEach(() => fn())` 无大括号会 TS2322,用 `{ }`)。

## 后端 API 契约(本期消费)
- `GET /admin/buses/tree` → `AdminTreeRow[]`:`{countryCode,countryName,cityName,airportCode,airportName,busSourceId,busRoute}`(airport 无线路时 busSourceId/busRoute 为 null)。
- `GET /admin/buses/{sourceId}` → `BusView`:`{sourceId,airportCode,version,lastVerifiedAt,data:BusInput}`。
- `POST /admin/buses` body `{sourceId,airportCode,data:BusInput}` → `BusView`。
- `PUT /admin/buses/{sourceId}` body `{airportCode,version,data:BusInput}` → `BusView`;版本冲突 409 `{code:"BUS_VERSION_CONFLICT"}`。
- `POST /admin/buses/{sourceId}/verify` → 200。
- `DELETE /admin/buses/{sourceId}` → 200(仅 SUPER_ADMIN;OPERATOR 收 403 `ADMIN_FORBIDDEN`)。
- `GET /admin/buses/{sourceId}/versions` → `VersionMeta[]`:`{version,contentHash,changedSummary,actor,createdAt}`(version 倒序)。
- `GET /admin/buses/{sourceId}/versions/{version}` → `BusInput`(该版本快照)。
- `POST /admin/buses/{sourceId}/versions/{version}/rollback` → `BusView`。
- `GET /admin/audit?actor&action&limit` → `AuditRow[]`:`{id,actorId,actorType,action,targetType,targetId,summary,ip,createdAt}`。
- `BusInput`:`{route,destination,operator,officialUrl,duration,price,operatingHours,lastUpdated, stops:string[], schedules:{timeRange,intervalText,note}[], alerts:{type,message,startDate,endDate}[], images:{url,caption}[], files:{name,url}[]}`。

## 文件结构
- Create `frontend/src/api/admin-bus.ts`、`frontend/src/api/admin-audit.ts`
- Create `frontend/src/pages/admin/AdminAuditPage.vue`、`frontend/src/pages/admin/AdminBusesPage.vue`
- Modify `frontend/src/components/admin/AdminLayout.vue`(nav +2)、`frontend/src/router/index.ts`(+2 子路由)
- Tests:`frontend/src/test/admin-bus.api.spec.ts`、`admin-audit.api.spec.ts`、`AdminAuditPage.spec.ts`、`AdminBusesPage.spec.ts`

---

## Task 1: api 客户端 admin-bus + admin-audit

**Files:** Create `frontend/src/api/admin-bus.ts`、`frontend/src/api/admin-audit.ts`;Test `frontend/src/test/admin-bus.api.spec.ts`、`admin-audit.api.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/admin-bus.api.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  get: vi.fn(() => Promise.resolve({ data: 'OK' })),
  post: vi.fn(() => Promise.resolve({ data: 'OK' })),
  put: vi.fn(() => Promise.resolve({ data: 'OK' })),
  delete: vi.fn(() => Promise.resolve({ data: 'OK' })),
} }))
import { http } from '../api/client'
import * as api from '../api/admin-bus'

describe('admin-bus api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('getTree', async () => { await api.getTree(); expect(http.get).toHaveBeenCalledWith('/admin/buses/tree') })
  it('getBus', async () => { await api.getBus('vie-vab1'); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1') })
  it('createBus', async () => {
    const body = { sourceId: 'x', airportCode: 'VIE', data: {} as any }
    await api.createBus(body); expect(http.post).toHaveBeenCalledWith('/admin/buses', body)
  })
  it('updateBus', async () => {
    const data = {} as any
    await api.updateBus('vie-vab1', { airportCode: 'VIE', version: 2, data })
    expect(http.put).toHaveBeenCalledWith('/admin/buses/vie-vab1', { airportCode: 'VIE', version: 2, data })
  })
  it('verifyBus', async () => { await api.verifyBus('vie-vab1'); expect(http.post).toHaveBeenCalledWith('/admin/buses/vie-vab1/verify') })
  it('deleteBus', async () => { await api.deleteBus('vie-vab1'); expect(http.delete).toHaveBeenCalledWith('/admin/buses/vie-vab1') })
  it('listVersions', async () => { await api.listVersions('vie-vab1'); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions') })
  it('getVersion', async () => { await api.getVersion('vie-vab1', 2); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions/2') })
  it('rollback', async () => { await api.rollbackVersion('vie-vab1', 2); expect(http.post).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions/2/rollback') })
})
```
`frontend/src/test/admin-audit.api.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: { get: vi.fn(() => Promise.resolve({ data: 'OK' })) } }))
import { http } from '../api/client'
import * as api from '../api/admin-audit'

describe('admin-audit api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('listAudit default', async () => { await api.listAudit(); expect(http.get).toHaveBeenCalledWith('/admin/audit', { params: {} }) })
  it('listAudit with filters', async () => {
    await api.listAudit({ action: 'UPDATE_BUS', limit: 50 })
    expect(http.get).toHaveBeenCalledWith('/admin/audit', { params: { action: 'UPDATE_BUS', limit: 50 } })
  })
})
```

- [ ] **Step 2: 运行确认失败** `cd frontend && npx vitest run src/test/admin-bus.api.spec.ts src/test/admin-audit.api.spec.ts` → FAIL(模块不存在)。

- [ ] **Step 3: 写 `frontend/src/api/admin-bus.ts`**
```ts
import { http } from './client'

export interface BusInput {
  route: string; destination: string | null; operator: string | null; officialUrl: string | null
  duration: string | null; price: string | null; operatingHours: string | null; lastUpdated: string | null
  stops: string[]
  schedules: { timeRange: string | null; intervalText: string | null; note: string | null }[]
  alerts: { type: string; message: string; startDate: string | null; endDate: string | null }[]
  images: { url: string; caption: string | null }[]
  files: { name: string | null; url: string }[]
}
export interface BusView { sourceId: string; airportCode: string; version: number; lastVerifiedAt: string | null; data: BusInput }
export interface AdminTreeRow {
  countryCode: string; countryName: string; cityName: string
  airportCode: string; airportName: string; busSourceId: string | null; busRoute: string | null
}
export interface VersionMeta { version: number; contentHash: string; changedSummary: string | null; actor: string | null; createdAt: string }

export const getTree = () => http.get<AdminTreeRow[]>('/admin/buses/tree').then((r) => r.data)
export const getBus = (sourceId: string) => http.get<BusView>(`/admin/buses/${sourceId}`).then((r) => r.data)
export const createBus = (body: { sourceId: string; airportCode: string; data: BusInput }) =>
  http.post<BusView>('/admin/buses', body).then((r) => r.data)
export const updateBus = (sourceId: string, body: { airportCode: string; version: number; data: BusInput }) =>
  http.put<BusView>(`/admin/buses/${sourceId}`, body).then((r) => r.data)
export const verifyBus = (sourceId: string) => http.post(`/admin/buses/${sourceId}/verify`).then((r) => r.data)
export const deleteBus = (sourceId: string) => http.delete(`/admin/buses/${sourceId}`).then((r) => r.data)
export const listVersions = (sourceId: string) => http.get<VersionMeta[]>(`/admin/buses/${sourceId}/versions`).then((r) => r.data)
export const getVersion = (sourceId: string, version: number) =>
  http.get<BusInput>(`/admin/buses/${sourceId}/versions/${version}`).then((r) => r.data)
export const rollbackVersion = (sourceId: string, version: number) =>
  http.post<BusView>(`/admin/buses/${sourceId}/versions/${version}/rollback`).then((r) => r.data)
```

- [ ] **Step 4: 写 `frontend/src/api/admin-audit.ts`**
```ts
import { http } from './client'

export interface AuditRow {
  id: number; actorId: number; actorType: string; action: string
  targetType: string; targetId: string | null; summary: string | null; ip: string | null; createdAt: string
}
export const listAudit = (params: { actor?: number; action?: string; limit?: number } = {}) =>
  http.get<AuditRow[]>('/admin/audit', { params }).then((r) => r.data)
```

- [ ] **Step 5: 运行确认通过** → 两文件全绿。

- [ ] **Step 6: 提交**
```bash
git add frontend/src/api/admin-bus.ts frontend/src/api/admin-audit.ts \
        frontend/src/test/admin-bus.api.spec.ts frontend/src/test/admin-audit.api.spec.ts
git commit -m "feat(admin): admin-bus + admin-audit api clients (#7A 前端)"
```

---

## Task 2: 路由 + 侧栏入口

**Files:** Modify `frontend/src/router/index.ts`、`frontend/src/components/admin/AdminLayout.vue`;Test `frontend/src/test/AdminLayout.spec.ts`(已存在,追加断言)

- [ ] **Step 1: 给 AdminLayout 测试追加断言** —— 编辑现有 `frontend/src/test/AdminLayout.spec.ts`,在 `renders the three nav entries` 的断言后加(或新加一个 it):
```ts
    expect(text).toContain('巴士维护')
    expect(text).toContain('操作记录')
```

- [ ] **Step 2: 运行确认失败** `cd frontend && npx vitest run src/test/AdminLayout.spec.ts` → FAIL(缺两项)。

- [ ] **Step 3: AdminLayout nav 数组加两项**(`frontend/src/components/admin/AdminLayout.vue`)
```ts
const nav = [
  { to: { name: 'admin-overview' }, label: '概览', icon: '📊' },
  { to: { name: 'admin-subscriptions' }, label: '订阅统计', icon: '⭐' },
  { to: { name: 'admin-hotness' }, label: '热度榜单', icon: '🔥' },
  { to: { name: 'admin-buses' }, label: '巴士维护', icon: '🚌' },
  { to: { name: 'admin-audit' }, label: '操作记录', icon: '🧾' },
]
```

- [ ] **Step 4: router 加两条子路由**(`frontend/src/router/index.ts` 的 `/admin` children 数组末尾)
```ts
        { path: 'buses', name: 'admin-buses', component: () => import('../pages/admin/AdminBusesPage.vue') },
        { path: 'audit', name: 'admin-audit', component: () => import('../pages/admin/AdminAuditPage.vue') },
```
> 此刻页面文件未建,**不要** `npm run build`;只需 AdminLayout.spec 绿(它 stub 了 router-link/router-view)。

- [ ] **Step 5: 运行确认通过** → AdminLayout.spec 绿。

- [ ] **Step 6: 提交**
```bash
git add frontend/src/components/admin/AdminLayout.vue frontend/src/router/index.ts frontend/src/test/AdminLayout.spec.ts
git commit -m "feat(admin): 侧栏+路由加入 巴士维护/操作记录 (#7A 前端)"
```

---

## Task 3: 操作记录页 AdminAuditPage

**Files:** Create `frontend/src/pages/admin/AdminAuditPage.vue`;Test `frontend/src/test/AdminAuditPage.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/AdminAuditPage.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as api from '../api/admin-audit'

vi.mock('../api/admin-audit', () => ({
  listAudit: vi.fn(() => Promise.resolve([
    { id: 1, actorId: 1, actorType: 'ADMIN', action: 'UPDATE_BUS', targetType: 'bus', targetId: 'vie-vab1', summary: null, ip: '10.0.0.4', createdAt: '2026-06-21T09:00:00' },
  ])),
}))
import AdminAuditPage from '../pages/admin/AdminAuditPage.vue'

describe('AdminAuditPage', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders audit rows', async () => {
    const wrapper = mount(AdminAuditPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('UPDATE_BUS')
    expect(text).toContain('vie-vab1')
  })
  it('re-filters by action', async () => {
    const wrapper = mount(AdminAuditPage)
    await flushPromises()
    ;(wrapper.vm as any).action = 'DELETE_BUS'
    await (wrapper.vm as any).reload()
    expect(api.listAudit).toHaveBeenLastCalledWith({ action: 'DELETE_BUS' })
  })
})
```

- [ ] **Step 2: 运行确认失败** → 页面不存在。

- [ ] **Step 3: 写 `frontend/src/pages/admin/AdminAuditPage.vue`**
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElOption } from 'element-plus'
import { listAudit, type AuditRow } from '../../api/admin-audit'

const rows = ref<AuditRow[]>([])
const action = ref<string>('')
const actions = ['CREATE_BUS', 'UPDATE_BUS', 'DELETE_BUS', 'VERIFY_BUS', 'ROLLBACK_BUS']

async function reload() {
  rows.value = await listAudit(action.value ? { action: action.value } : {})
}
onMounted(reload)
defineExpose({ action, reload })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">操作记录</h1>
  <p class="pageDesc">管理端所有写操作全量记录(谁 / 何时 / 改了哪个对象)。</p>
  <div class="panel">
    <div style="margin-bottom: 14px">
      <ElSelect v-model="action" placeholder="全部动作" clearable size="small" style="width: 180px" @change="reload">
        <ElOption v-for="a in actions" :key="a" :label="a" :value="a" />
      </ElSelect>
    </div>
    <ElTable :data="rows" style="width: 100%">
      <ElTableColumn prop="createdAt" label="时间" width="180" />
      <ElTableColumn prop="actorId" label="操作人" width="90" />
      <ElTableColumn prop="action" label="动作" width="140" />
      <ElTableColumn prop="targetId" label="对象" />
      <ElTableColumn prop="ip" label="IP" width="140" />
    </ElTable>
  </div>
</template>
```

- [ ] **Step 4: 运行确认通过** → 2 例绿。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/pages/admin/AdminAuditPage.vue frontend/src/test/AdminAuditPage.spec.ts
git commit -m "feat(admin): 操作记录页(表+动作过滤) (#7A 前端)"
```

---

## Task 4: 巴士维护页 —— 树 + 选中加载

**Files:** Create `frontend/src/pages/admin/AdminBusesPage.vue`;Test `frontend/src/test/AdminBusesPage.spec.ts`

> 本任务建页面骨架:左树(按国家/城市/机场分组列出线路)+ 选中线路 → GET 加载到 `current: BusView`。编辑表单 + 版本面板在 Task 5/6 往同文件加。

- [ ] **Step 1: 写失败测试** `frontend/src/test/AdminBusesPage.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/admin-bus', () => ({
  getTree: vi.fn(() => Promise.resolve([
    { countryCode: 'AT', countryName: '奥地利', cityName: '维也纳', airportCode: 'VIE', airportName: '维也纳机场', busSourceId: 'vie-vab1', busRoute: 'VAB 1' },
    { countryCode: 'AT', countryName: '奥地利', cityName: '维也纳', airportCode: 'VIE', airportName: '维也纳机场', busSourceId: null, busRoute: null },
  ])),
  getBus: vi.fn(() => Promise.resolve({
    sourceId: 'vie-vab1', airportCode: 'VIE', version: 3, lastVerifiedAt: null,
    data: { route: 'VAB 1', destination: '西站', operator: 'ÖBB', officialUrl: null, duration: '40min', price: '€11', operatingHours: '03:00-24:00', lastUpdated: null, stops: ['A'], schedules: [], alerts: [], images: [], files: [] },
  })),
  listVersions: vi.fn(() => Promise.resolve([])),
}))
import * as api from '../api/admin-bus'
import AdminBusesPage from '../pages/admin/AdminBusesPage.vue'

describe('AdminBusesPage', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })
  it('renders tree routes and skips null buses', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    expect(wrapper.text()).toContain('VAB 1')
  })
  it('selecting a route loads it into the form', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    await (wrapper.vm as any).select('vie-vab1')
    await flushPromises()
    expect(api.getBus).toHaveBeenCalledWith('vie-vab1')
    expect((wrapper.vm as any).current.sourceId).toBe('vie-vab1')
    expect(wrapper.text()).toContain('€11')
  })
})
```

- [ ] **Step 2: 运行确认失败** → 页面不存在。

- [ ] **Step 3: 写 `frontend/src/pages/admin/AdminBusesPage.vue`**(骨架:树 + select + 表单只读展示占位;Task 5 填表单控件)
```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getTree, getBus, listVersions, type AdminTreeRow, type BusView, type VersionMeta } from '../../api/admin-bus'

const tree = ref<AdminTreeRow[]>([])
const current = ref<BusView | null>(null)
const versions = ref<VersionMeta[]>([])

// 按 国家>城市>机场 分组,仅保留有 busSourceId 的叶子
const grouped = computed(() => {
  const map = new Map<string, { airportName: string; routes: { sourceId: string; route: string }[] }>()
  for (const r of tree.value) {
    if (!r.busSourceId) continue
    const key = `${r.countryName} · ${r.cityName} · ${r.airportName} (${r.airportCode})`
    if (!map.has(key)) map.set(key, { airportName: r.airportName, routes: [] })
    map.get(key)!.routes.push({ sourceId: r.busSourceId, route: r.busRoute ?? r.busSourceId })
  }
  return map
})

async function loadTree() { tree.value = await getTree() }
async function select(sourceId: string) {
  current.value = await getBus(sourceId)
  versions.value = await listVersions(sourceId)
}
onMounted(loadTree)
defineExpose({ current, versions, select, loadTree })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">巴士信息维护</h1>
  <p class="pageDesc">树形选 国家 / 城市 / 机场 → 编辑线路。<strong>保存即触发变更检测</strong>(content_hash 无变化不计版本)。</p>
  <div style="display: grid; grid-template-columns: 280px 1fr; gap: 18px">
    <div class="panel" style="margin: 0">
      <div class="tree">
        <details v-for="[group, info] in grouped" :key="group" open>
          <summary>{{ group }}</summary>
          <div v-for="r in info.routes" :key="r.sourceId" class="leaf">
            <span>{{ r.route }}</span>
            <a href="#" @click.prevent="select(r.sourceId)">编辑</a>
          </div>
        </details>
      </div>
    </div>
    <div class="panel" style="margin: 0">
      <p v-if="!current" class="formNote">从左侧选择一条线路进行编辑。</p>
      <div v-else>
        <h3 style="margin-top: 0">编辑线路 · {{ current.data.route }} <span class="formNote">v{{ current.version }}</span></h3>
        <!-- Task 5 在此加表单控件;占位展示关键字段以通过骨架测试 -->
        <div class="formNote">价格:{{ current.data.price }}</div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 4: 运行确认通过** → 2 例绿。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/pages/admin/AdminBusesPage.vue frontend/src/test/AdminBusesPage.spec.ts
git commit -m "feat(admin): 巴士维护页骨架(树+选中加载) (#7A 前端)"
```

---

## Task 5: 编辑表单 —— 标量 + 子表编辑器 + 保存/核对/删除

**Files:** Modify `frontend/src/pages/admin/AdminBusesPage.vue`;Test 追加到 `AdminBusesPage.spec.ts`

- [ ] **Step 1: 追加失败测试**(在 `AdminBusesPage.spec.ts` 末尾,describe 内加;并在文件顶部 mock 里补 `createBus/updateBus/verifyBus/deleteBus`)

先把 `vi.mock('../api/admin-bus', ...)` 补全这些方法:
```ts
  createBus: vi.fn((b) => Promise.resolve({ ...b, version: 1, lastVerifiedAt: null })),
  updateBus: vi.fn((s, b) => Promise.resolve({ sourceId: s, airportCode: b.airportCode, version: b.version + 1, lastVerifiedAt: null, data: b.data })),
  verifyBus: vi.fn(() => Promise.resolve()),
  deleteBus: vi.fn(() => Promise.resolve()),
```
加测试(用 auth store 控制 role):
```ts
import { useAuth } from '../stores/auth'

  it('save calls updateBus with version + airportCode', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    await (wrapper.vm as any).save()
    expect(api.updateBus).toHaveBeenCalledWith('vie-vab1', expect.objectContaining({ airportCode: 'VIE', version: 3 }))
  })

  it('delete button visible only for SUPER_ADMIN', async () => {
    const auth = useAuth(); auth.accessToken = 'x'
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'OPERATOR' }
    const wrapper = mount(AdminBusesPage)
    await flushPromises(); await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    expect((wrapper.vm as any).canDelete).toBe(false)
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'SUPER_ADMIN' }
    expect((wrapper.vm as any).canDelete).toBe(true)
  })
```

- [ ] **Step 2: 运行确认失败** → `save`/`canDelete` 未定义。

- [ ] **Step 3: 给 AdminBusesPage 加表单 + 动作**

`<script setup>` 增补(import `ElMessage`、`useAuth`、`asApiError`,与 EP 表单控件):
```ts
import { ElMessage } from 'element-plus'
import { asApiError } from '../../api/client'
import { useAuth } from '../../stores/auth'
import { createBus, updateBus, verifyBus, deleteBus } from '../../api/admin-bus'

const auth = useAuth()
const canDelete = computed(() => auth.user?.role === 'SUPER_ADMIN')

async function save() {
  if (!current.value) return
  const c = current.value
  try {
    const saved = await updateBus(c.sourceId, { airportCode: c.airportCode, version: c.version, data: c.data })
    current.value = saved
    versions.value = await listVersions(c.sourceId)
    ElMessage.success('已保存')
  } catch (e) {
    const err = asApiError(e)
    if (err?.code === 'BUS_VERSION_CONFLICT') {
      ElMessage.warning('该线路已被他人修改,正在重新加载最新版本')
      await select(c.sourceId)
    } else { ElMessage.error(err?.message ?? '保存失败') }
  }
}
async function verify() {
  if (!current.value) return
  await verifyBus(current.value.sourceId)
  ElMessage.success('已标记核对无误')
}
async function removeBus() {
  if (!current.value) return
  await deleteBus(current.value.sourceId)
  current.value = null
  await loadTree()
  ElMessage.success('已下线')
}
defineExpose({ current, versions, select, loadTree, save, verify, removeBus, canDelete })
```
> 子表行的增删:为每个子表加 `addX()/removeX(i)` 简单方法,操作 `current.value.data.xxx` 数组。例:
```ts
function addStop() { current.value!.data.stops.push('') }
function removeStop(i: number) { current.value!.data.stops.splice(i, 1) }
function addSchedule() { current.value!.data.schedules.push({ timeRange: '', intervalText: '', note: '' }) }
function removeSchedule(i: number) { current.value!.data.schedules.splice(i, 1) }
function addAlert() { current.value!.data.alerts.push({ type: 'info', message: '', startDate: null, endDate: null }) }
function removeAlert(i: number) { current.value!.data.alerts.splice(i, 1) }
function addImage() { current.value!.data.images.push({ url: '', caption: null }) }
function removeImage(i: number) { current.value!.data.images.splice(i, 1) }
function addFile() { current.value!.data.files.push({ name: null, url: '' }) }
function removeFile(i: number) { current.value!.data.files.splice(i, 1) }
```
(把这些也加入 `defineExpose` 以便将来测试/模板引用。)

模板 `<div v-else>` 区替换为完整表单(标量用 `el-input`,子表用 v-for 行 + 增删按钮,底部动作条):
```vue
      <div v-else>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <h3 style="margin:0">编辑线路 · {{ current.data.route }}</h3>
          <span class="formNote">v{{ current.version }} · 乐观锁(冲突 409)</span>
        </div>
        <div class="formrow"><label>线路名 route</label><el-input v-model="current.data.route" /></div>
        <div class="formrow"><label>目的地 destination</label><el-input v-model="current.data.destination" /></div>
        <div class="formrow"><label>运营方 operator</label><el-input v-model="current.data.operator" /></div>
        <div class="formrow"><label>官网 officialUrl</label><el-input v-model="current.data.officialUrl" /></div>
        <div class="formrow"><label>时长 duration</label><el-input v-model="current.data.duration" /></div>
        <div class="formrow"><label>价格 price</label><el-input v-model="current.data.price" /></div>
        <div class="formrow"><label>运营时间 operatingHours</label><el-input v-model="current.data.operatingHours" /></div>
        <div class="formrow"><label>数据日期 lastUpdated</label><el-input v-model="current.data.lastUpdated" placeholder="YYYY-MM-DD" /></div>

        <h4>停靠站 stops</h4>
        <div v-for="(s, i) in current.data.stops" :key="'s'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="current.data.stops[i]" /><el-button @click="removeStop(i)">删</el-button>
        </div>
        <el-button size="small" @click="addStop">+ 停靠站</el-button>

        <h4>班次 schedules</h4>
        <div v-for="(s, i) in current.data.schedules" :key="'sc'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="s.timeRange" placeholder="时段" />
          <el-input v-model="s.intervalText" placeholder="间隔" />
          <el-input v-model="s.note" placeholder="备注" />
          <el-button @click="removeSchedule(i)">删</el-button>
        </div>
        <el-button size="small" @click="addSchedule">+ 班次</el-button>

        <h4>提示 alerts</h4>
        <div v-for="(a, i) in current.data.alerts" :key="'al'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="a.type" placeholder="类型" style="width:90px" />
          <el-input v-model="a.message" placeholder="内容" />
          <el-input v-model="a.startDate" placeholder="起" style="width:120px" />
          <el-input v-model="a.endDate" placeholder="止" style="width:120px" />
          <el-button @click="removeAlert(i)">删</el-button>
        </div>
        <el-button size="small" @click="addAlert">+ 提示</el-button>

        <h4>图片 images</h4>
        <div v-for="(m, i) in current.data.images" :key="'im'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="m.url" placeholder="url" /><el-input v-model="m.caption" placeholder="说明" />
          <el-button @click="removeImage(i)">删</el-button>
        </div>
        <el-button size="small" @click="addImage">+ 图片</el-button>

        <h4>文件 files</h4>
        <div v-for="(f, i) in current.data.files" :key="'fl'+i" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="f.name" placeholder="名称" /><el-input v-model="f.url" placeholder="url" />
          <el-button @click="removeFile(i)">删</el-button>
        </div>
        <el-button size="small" @click="addFile">+ 文件</el-button>

        <div style="display:flex;gap:10px;margin-top:16px">
          <el-button type="primary" @click="save">保存(触发推送)</el-button>
          <el-button @click="verify">核对无误</el-button>
          <el-button v-if="canDelete" type="danger" @click="removeBus">下线</el-button>
        </div>
      </div>
```
在 `<script setup>` 顶部 import 需要的 EP 组件:`import { ElInput, ElButton, ElMessage } from 'element-plus'`(ElMessage 已在上面;ElInput/ElButton 加上)。

- [ ] **Step 4: 运行确认通过** `cd frontend && npx vitest run src/test/AdminBusesPage.spec.ts` → 全绿(含 save/canDelete)。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/pages/admin/AdminBusesPage.vue frontend/src/test/AdminBusesPage.spec.ts
git commit -m "feat(admin): 巴士编辑表单(标量+子表增删+保存/核对/下线+409处理+角色显隐) (#7A 前端)"
```

---

## Task 6: 版本历史面板 + 回滚

**Files:** Modify `frontend/src/pages/admin/AdminBusesPage.vue`;Test 追加到 `AdminBusesPage.spec.ts`

- [ ] **Step 1: 追加失败测试**(mock 顶部补 `getVersion`/`rollbackVersion`;listVersions 改为返回 2 条)

把 mock 的 `listVersions` 改为:
```ts
  listVersions: vi.fn(() => Promise.resolve([
    { version: 2, contentHash: 'h2', changedSummary: '{"scalars":[{"field":"price","oldValue":"€11","newValue":"€13"}],"changedSubtables":[]}', actor: 'admin:1', createdAt: '2026-06-21T09:00:00' },
    { version: 1, contentHash: 'h1', changedSummary: null, actor: 'seed', createdAt: '2026-06-20T00:00:00' },
  ])),
  getVersion: vi.fn(() => Promise.resolve({ route: 'VAB 1', price: '€11', stops: [], schedules: [], alerts: [], images: [], files: [], destination: null, operator: null, officialUrl: null, duration: null, operatingHours: null, lastUpdated: null })),
  rollbackVersion: vi.fn((s, v) => Promise.resolve({ sourceId: s, airportCode: 'VIE', version: 99, lastVerifiedAt: null, data: { route: 'VAB 1', price: '€11', stops: [], schedules: [], alerts: [], images: [], files: [], destination: null, operator: null, officialUrl: null, duration: null, operatingHours: null, lastUpdated: null } })),
```
加测试:
```ts
  it('shows version history after selecting', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises(); await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    expect((wrapper.vm as any).versions.length).toBe(2)
    expect(wrapper.text()).toContain('v2')
  })
  it('rollback calls api and reloads', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises(); await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    await (wrapper.vm as any).doRollback(1)
    expect(api.rollbackVersion).toHaveBeenCalledWith('vie-vab1', 1)
    expect((wrapper.vm as any).current.version).toBe(99)
  })
```

- [ ] **Step 2: 运行确认失败** → `doRollback` 未定义 / 模板无 v2。

- [ ] **Step 3: 加版本面板 + doRollback**

`<script setup>` 增补:
```ts
import { getVersion, rollbackVersion } from '../../api/admin-bus'
// ElMessageBox 用于二次确认
import { ElMessageBox } from 'element-plus'

async function doRollback(version: number) {
  if (!current.value) return
  const saved = await rollbackVersion(current.value.sourceId, version)
  current.value = saved
  versions.value = await listVersions(saved.sourceId)
  ElMessage.success(`已回滚自 v${version}`)
}
async function confirmRollback(version: number) {
  try {
    await ElMessageBox.confirm(`确认把线路回滚到 v${version}?这会生成一个新版本。`, '回滚确认', { type: 'warning' })
    await doRollback(version)
  } catch { /* 用户取消 */ }
}
defineExpose({ /* …已有… */ doRollback, confirmRollback })
```
(把 `doRollback`/`confirmRollback` 并入现有 defineExpose 对象。)

模板:在编辑区 `<div v-else>` 末尾(动作条之后)加版本面板:
```vue
        <h4 style="margin-top:20px">版本历史</h4>
        <el-table :data="versions" style="width:100%">
          <el-table-column prop="version" label="版本" width="80">
            <template #default="{ row }">v{{ row.version }}</template>
          </el-table-column>
          <el-table-column prop="actor" label="操作人" width="120" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column prop="changedSummary" label="变更摘要" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="confirmRollback(row.version)">回滚</el-button>
            </template>
          </el-table-column>
        </el-table>
```
import 补 `ElTable, ElTableColumn`(若未引)。

- [ ] **Step 4: 运行确认通过** → 全绿(含 version 面板 + rollback)。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/pages/admin/AdminBusesPage.vue frontend/src/test/AdminBusesPage.spec.ts
git commit -m "feat(admin): 版本历史面板 + 回滚(二次确认) (#7A 前端)"
```

---

## Task 7: 全量验证 + 收尾

- [ ] **Step 1: 前端全测 + 构建**
Run: `cd frontend && npx vitest run && npm run build`
Expected: 全 spec 绿;`vue-tsc` 无类型错误(注意 `beforeEach(() => { ... })` 要大括号);构建出 admin chunk。
检查代码分割:`AdminBusesPage`/`AdminAuditPage` 在 admin 异步 chunk,公开入口不含 EP 新增。

- [ ] **Step 2: 手动全栈验证**(参考 testing-tooling-quirks 记忆)
`docker compose up -d mysql redis` → 后端 `SEED_ENABLED=true ... mvn spring-boot:run` → 前端 `npx vite`。用种子 admin 登录 → `/admin/buses`:树选线路 → 改价格保存 → 版本 +1、出现新版本行;核对无误;并发改(两标签)后到者 409 提示并重载;回滚某版本生成新版本;OPERATOR 登录看不到「下线」按钮、调 DELETE 403。`/admin/audit` 看到刚才的写操作记录。

- [ ] **Step 3: 更新记忆** —— 更新 `bus-maintenance-audit-backend-shipped.md` 旁补一条前端已交付,或新建 `…/memory/bus-maintenance-audit-frontend-shipped.md`(type: project);`MEMORY.md` 加指针。记:维护页/版本历史/审计页交付;follow-up(版本「查看/diff」目前只做回滚未做并排 diff 视图——如需再加)。

- [ ] **Step 4: 最终提交**
```bash
git add -A && git commit -m "chore(admin): #7A 前端 验证 + 记忆"
```

---

## 自审清单(写计划者已核对)
- **spec 覆盖**:api(T1)、路由+侧栏(T2)、审计页(T3)、维护页树+加载(T4)、编辑表单含全子表+保存/核对/下线+409+角色显隐(T5)、版本历史+回滚(T6)、验证(T7)。✅
- **类型一致**:`BusInput/BusView/AdminTreeRow/VersionMeta/AuditRow` 全程一致;与后端 JSON 字段对齐(已核对真实 API)。✅
- **占位**:无 TBD;子表增删/表单控件给了完整代码。`changedSummary` 在审计/版本里按字符串展示(后端存的是 JSON 串)——展示原串可接受,如需美化是 follow-up。
- **风险**:ElMessage/ElMessageBox 在 jsdom 测试里只要不在被断言路径触发即可(测试走 vm 方法不点真实按钮);`npm run build` 的 vue-tsc 对 `beforeEach` 箭头返回值敏感,已提示用大括号。
