# message 站内信 —— 前端实现计划(#7 切片 B · 前端)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 前端交付站内信收件箱 `/inbox`(列表 + diff 渲染 + 批量已读 + 删除)与顶栏 bell 红点(登录后轮询未读数),按 locale 渲染模板(D6)。

**Architecture:** 复用 App.vue 共享顶栏(加 bell);`stores/messages.ts` 持未读数 + 轮询生命周期(登录起、登出停),接入 auth store;`renderMessage` 纯函数把 `{templateCode, params}` 按 i18n 渲染成标题 + diff。

**Tech Stack:** Vue 3 + Pinia + vue-router + vue-i18n + Element Plus(列表用设计类即可,轻交互用 EP)+ Vitest。

---

## 上游与约束
- spec:`docs/superpowers/specs/2026-06-21-message-push-loop-design.md` §6。
- 后端已落地 API(master):`GET /messages/unread-count`→`{count}`;`GET /messages?limit&offset`→`[{id,userId,templateCode,params,relatedSourceId,isRead,createdAt}]`(**params 是对象**,@JsonRawValue);`POST /messages/read {ids}`→`{updated}`;`DELETE /messages/{id}`。
- 复用:`App.vue`(共享顶栏 `.actions`)、`stores/auth.ts`(login/loadMe/logout/clear;favorites 已接同款生命周期)、`api/client.ts`(`http`)、`i18n/locales/{zh-CN,en,de}.ts`、`components/StateBlock.vue`(空态)、EP per-component import + `src/test/setup.ts`。
- XSS:全 `{{ }}`,禁 v-html。`beforeEach(() => { ... })` 带大括号(vue-tsc 严格)。

## params 形状(后端)
- BUS_UPDATED:`{route, sourceId, changed:[{field,oldValue,newValue}], changedSubtables:[...]}`
- BUS_OFFLINE:`{route, sourceId}`

## 文件结构
- Create `frontend/src/api/messages.ts`、`frontend/src/stores/messages.ts`、`frontend/src/components/renderMessage.ts`、`frontend/src/pages/InboxPage.vue`
- Modify `frontend/src/stores/auth.ts`(loadMe→start、clear→stop)、`frontend/src/App.vue`(bell+红点 + onMounted 起轮询)、`frontend/src/router/index.ts`(/inbox)、`frontend/src/i18n/locales/{zh-CN,en,de}.ts`(msg 段)
- Tests:`messages.api.spec.ts`、`messages.store.spec.ts`、`renderMessage.spec.ts`、`InboxPage.spec.ts`、`AppBell.spec.ts`

---

## Task 1: api/messages.ts

**Files:** Create `frontend/src/api/messages.ts`;Test `frontend/src/test/messages.api.spec.ts`

- [ ] **Step 1: 写失败测试**
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  get: vi.fn(() => Promise.resolve({ data: 'OK' })),
  post: vi.fn(() => Promise.resolve({ data: 'OK' })),
  delete: vi.fn(() => Promise.resolve({ data: 'OK' })),
} }))
import { http } from '../api/client'
import * as api from '../api/messages'

describe('messages api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('unreadCount', async () => { await api.unreadCount(); expect(http.get).toHaveBeenCalledWith('/messages/unread-count') })
  it('list default', async () => { await api.listMessages(); expect(http.get).toHaveBeenCalledWith('/messages', { params: { limit: 20, offset: 0 } }) })
  it('markRead', async () => { await api.markRead([1,2]); expect(http.post).toHaveBeenCalledWith('/messages/read', { ids: [1,2] }) })
  it('deleteMessage', async () => { await api.deleteMessage(5); expect(http.delete).toHaveBeenCalledWith('/messages/5') })
})
```

- [ ] **Step 2: 运行确认失败** `cd frontend && npx vitest run src/test/messages.api.spec.ts` → FAIL。

- [ ] **Step 3: 写 `frontend/src/api/messages.ts`**
```ts
import { http } from './client'

export interface FieldChange { field: string; oldValue: string | null; newValue: string | null }
export interface MessageParams { route?: string; sourceId?: string; changed?: FieldChange[]; changedSubtables?: string[] }
export interface MessageItem {
  id: number; templateCode: string; params: MessageParams
  relatedSourceId: string | null; isRead: boolean; createdAt: string
}

export const unreadCount = () => http.get<{ count: number }>('/messages/unread-count').then((r) => r.data.count)
export const listMessages = (limit = 20, offset = 0) =>
  http.get<MessageItem[]>('/messages', { params: { limit, offset } }).then((r) => r.data)
export const markRead = (ids: number[]) => http.post<{ updated: number }>('/messages/read', { ids }).then((r) => r.data)
export const deleteMessage = (id: number) => http.delete(`/messages/${id}`).then((r) => r.data)
```

- [ ] **Step 4: 运行确认通过** → 4 例绿。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/api/messages.ts frontend/src/test/messages.api.spec.ts
git commit -m "feat(message): messages api client (#7B 前端)"
```

---

## Task 2: stores/messages.ts(未读 + 轮询生命周期)+ 接入 auth

**Files:** Create `frontend/src/stores/messages.ts`;Modify `frontend/src/stores/auth.ts`;Test `frontend/src/test/messages.store.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/messages.store.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/messages', () => ({
  unreadCount: vi.fn(() => Promise.resolve(3)),
  listMessages: vi.fn(() => Promise.resolve([
    { id: 1, templateCode: 'BUS_UPDATED', params: { route: 'VAB 1', changed: [] }, relatedSourceId: 'vie-vab1', isRead: false, createdAt: '2026-06-22T09:00:00' },
  ])),
  markRead: vi.fn(() => Promise.resolve({ updated: 1 })),
  deleteMessage: vi.fn(() => Promise.resolve()),
}))
import * as api from '../api/messages'
import { useMessages } from '../stores/messages'

describe('messages store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('refreshUnread sets unread count', async () => {
    const s = useMessages()
    await s.refreshUnread()
    expect(s.unread).toBe(3)
  })
  it('loadList fills list', async () => {
    const s = useMessages()
    await s.loadList()
    expect(s.list).toHaveLength(1)
    expect(s.list[0].templateCode).toBe('BUS_UPDATED')
  })
  it('markRead marks items read locally + refreshes unread', async () => {
    const s = useMessages()
    await s.loadList()
    await s.markRead([1])
    expect(api.markRead).toHaveBeenCalledWith([1])
    expect(s.list[0].isRead).toBe(true)
  })
  it('remove deletes from list', async () => {
    const s = useMessages()
    await s.loadList()
    await s.remove(1)
    expect(api.deleteMessage).toHaveBeenCalledWith(1)
    expect(s.list).toHaveLength(0)
  })
  it('startPolling sets interval, stopPolling clears + resets', async () => {
    const s = useMessages()
    s.startPolling()
    expect(s.polling).toBe(true)
    s.stopPolling()
    expect(s.polling).toBe(false)
    expect(s.unread).toBe(0)
  })
})
```

- [ ] **Step 2: 运行确认失败** → FAIL。

- [ ] **Step 3: 写 `frontend/src/stores/messages.ts`**
```ts
import { defineStore } from 'pinia'
import * as api from '../api/messages'
import type { MessageItem } from '../api/messages'

const POLL_MS = 30000

export const useMessages = defineStore('messages', {
  state: () => ({
    unread: 0,
    list: [] as MessageItem[],
    polling: false,
    timer: 0 as unknown as ReturnType<typeof setInterval> | 0,
  }),
  actions: {
    async refreshUnread() {
      try { this.unread = await api.unreadCount() } catch { /* 忽略轮询错误 */ }
    },
    async loadList() { this.list = await api.listMessages(50, 0) },
    async markRead(ids: number[]) {
      await api.markRead(ids)
      for (const m of this.list) if (ids.includes(m.id)) m.isRead = true
      await this.refreshUnread()
    },
    async remove(id: number) {
      await api.deleteMessage(id)
      this.list = this.list.filter((m) => m.id !== id)
      await this.refreshUnread()
    },
    startPolling() {
      if (this.polling) return
      this.polling = true
      this.refreshUnread()
      this.timer = setInterval(() => this.refreshUnread(), POLL_MS)
    },
    stopPolling() {
      if (this.timer) clearInterval(this.timer)
      this.timer = 0
      this.polling = false
      this.unread = 0
      this.list = []
    },
  },
})
```

- [ ] **Step 4: 接入 auth store** —— `frontend/src/stores/auth.ts`:
  - `loadMe()` 末尾(favorites.load 之后)加:`import('./messages').then((m) => m.useMessages().startPolling())`(动态 import 避免循环依赖,与 favorites 同款)。
  - `clear()` 里(favorites().clear() 旁)加:`import('./messages').then((m) => m.useMessages().stopPolling())`。

- [ ] **Step 5: 运行确认通过** → 5 例绿。

- [ ] **Step 6: 提交**
```bash
git add frontend/src/stores/messages.ts frontend/src/stores/auth.ts frontend/src/test/messages.store.spec.ts
git commit -m "feat(message): messages store(未读+轮询生命周期)接入 auth (#7B 前端)"
```

---

## Task 3: renderMessage + i18n 模板

**Files:** Create `frontend/src/components/renderMessage.ts`;Modify `frontend/src/i18n/locales/{zh-CN,en,de}.ts`;Test `frontend/src/test/renderMessage.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/renderMessage.spec.ts`
```ts
import { describe, it, expect } from 'vitest'
import { renderMessage } from '../components/renderMessage'

const t = (key: string, named?: Record<string, unknown>) => {
  const map: Record<string, string> = {
    'msg.busUpdated': '线路 {route} 已更新',
    'msg.busOffline': '线路 {route} 已下线',
    'msg.unknown': '您有一条新通知',
    'msg.field.price': '价格',
  }
  let s = map[key] ?? key
  if (named) for (const k of Object.keys(named)) s = s.replace(`{${k}}`, String(named[k]))
  return s
}

describe('renderMessage', () => {
  it('BUS_UPDATED title + diff(字段名走 i18n)', () => {
    const r = renderMessage('BUS_UPDATED', { route: 'VAB 1', changed: [{ field: 'price', oldValue: '€11', newValue: '€13' }] }, t as any)
    expect(r.title).toBe('线路 VAB 1 已更新')
    expect(r.diffs).toEqual([{ label: '价格', oldValue: '€11', newValue: '€13' }])
  })
  it('BUS_OFFLINE title, no diff', () => {
    const r = renderMessage('BUS_OFFLINE', { route: 'VAB 1' }, t as any)
    expect(r.title).toBe('线路 VAB 1 已下线')
    expect(r.diffs).toEqual([])
  })
  it('unknown code falls back', () => {
    const r = renderMessage('WHATEVER', {}, t as any)
    expect(r.title).toBe('您有一条新通知')
    expect(r.diffs).toEqual([])
  })
  it('unknown diff field uses raw field name', () => {
    const r = renderMessage('BUS_UPDATED', { route: 'X', changed: [{ field: 'weird', oldValue: 'a', newValue: 'b' }] }, t as any)
    expect(r.diffs[0].label).toBe('msg.field.weird') // t 兜底返回 key,前端展示原 key 亦可接受
  })
})
```

- [ ] **Step 2: 运行确认失败** → FAIL。

- [ ] **Step 3: 写 `frontend/src/components/renderMessage.ts`**
```ts
import type { MessageParams } from '../api/messages'

export interface RenderedDiff { label: string; oldValue: string | null; newValue: string | null }
export interface RenderedMessage { title: string; diffs: RenderedDiff[] }

type T = (key: string, named?: Record<string, unknown>) => string

/** 把 {templateCode, params} 按 locale 渲染成标题 + diff 行。未知 code 兜底(D6)。 */
export function renderMessage(templateCode: string, params: MessageParams, t: T): RenderedMessage {
  const route = params?.route ?? ''
  if (templateCode === 'BUS_UPDATED') {
    const diffs = (params?.changed ?? []).map((c) => ({
      label: t('msg.field.' + c.field), oldValue: c.oldValue, newValue: c.newValue,
    }))
    return { title: t('msg.busUpdated', { route }), diffs }
  }
  if (templateCode === 'BUS_OFFLINE') {
    return { title: t('msg.busOffline', { route }), diffs: [] }
  }
  return { title: t('msg.unknown'), diffs: [] }
}
```

- [ ] **Step 4: i18n 加 `msg` 段** —— 三个 locale 文件各加(放进顶层对象):

`zh-CN.ts`:
```ts
  msg: {
    title: '站内信', empty: '暂无消息', markRead: '标记已读', delete: '删除', viewDetail: '查看详情',
    busUpdated: '线路 {route} 已更新', busOffline: '线路 {route} 已下线', unknown: '您有一条新通知',
    field: { route: '线路名', destination: '目的地', operator: '运营方', officialUrl: '官网',
             duration: '时长', price: '价格', operatingHours: '运营时间', lastUpdated: '数据日期' },
  },
```
`en.ts`:
```ts
  msg: {
    title: 'Messages', empty: 'No messages', markRead: 'Mark read', delete: 'Delete', viewDetail: 'View',
    busUpdated: 'Route {route} updated', busOffline: 'Route {route} discontinued', unknown: 'You have a new notification',
    field: { route: 'Route', destination: 'Destination', operator: 'Operator', officialUrl: 'Official site',
             duration: 'Duration', price: 'Price', operatingHours: 'Operating hours', lastUpdated: 'Data date' },
  },
```
`de.ts`:
```ts
  msg: {
    title: 'Nachrichten', empty: 'Keine Nachrichten', markRead: 'Als gelesen', delete: 'Löschen', viewDetail: 'Ansehen',
    busUpdated: 'Linie {route} aktualisiert', busOffline: 'Linie {route} eingestellt', unknown: 'Sie haben eine neue Benachrichtigung',
    field: { route: 'Linie', destination: 'Ziel', operator: 'Betreiber', officialUrl: 'Offizielle Seite',
             duration: 'Dauer', price: 'Preis', operatingHours: 'Betriebszeiten', lastUpdated: 'Datenstand' },
  },
```
(加在各文件顶层对象内,与现有 `app`/`home` 等并列。注意尾逗号与括号匹配。)

- [ ] **Step 5: 运行确认通过** → 4 例绿。

- [ ] **Step 6: 提交**
```bash
git add frontend/src/components/renderMessage.ts frontend/src/i18n/locales/zh-CN.ts \
        frontend/src/i18n/locales/en.ts frontend/src/i18n/locales/de.ts \
        frontend/src/test/renderMessage.spec.ts
git commit -m "feat(message): renderMessage(模板+diff,locale)+ i18n msg 段 (#7B 前端)"
```

---

## Task 4: 顶栏 bell + 红点

**Files:** Modify `frontend/src/App.vue`;Test `frontend/src/test/AppBell.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/AppBell.spec.ts`
```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import zhCN from '../i18n/locales/zh-CN'
import App from '../App.vue'
import { useAuth } from '../stores/auth'
import { useMessages } from '../stores/messages'

vi.mock('../api/favorites', () => ({ listFavoriteIds: vi.fn(() => Promise.resolve([])), favorite: vi.fn(), unfavorite: vi.fn() }))
vi.mock('../api/messages', () => ({ unreadCount: vi.fn(() => Promise.resolve(0)), listMessages: vi.fn(() => Promise.resolve([])), markRead: vi.fn(), deleteMessage: vi.fn() }))

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const mountApp = () => mount(App, { global: { plugins: [i18n], stubs: { 'router-link': RouterLinkStub, 'router-view': { template: '<div/>' } } } })

describe('App bell', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('no bell when anonymous', () => {
    const w = mountApp()
    expect(w.find('[data-test=bell]').exists()).toBe(false)
  })
  it('bell shows when authed; red dot when unread>0', async () => {
    const auth = useAuth(); auth.accessToken = 'x'
    auth.user = { username: 'u', email: 'u@x.com', locale: 'zh-CN', role: 'USER' }
    const w = mountApp()
    expect(w.find('[data-test=bell]').exists()).toBe(true)
    expect(w.find('[data-test=bell-dot]').exists()).toBe(false)
    useMessages().unread = 5
    await w.vm.$nextTick()
    expect(w.find('[data-test=bell-dot]').exists()).toBe(true)
    expect(w.find('[data-test=bell-dot]').text()).toContain('5')
  })
})
```

- [ ] **Step 2: 运行确认失败** → FAIL。

- [ ] **Step 3: 改 `App.vue`** —— `<script setup>` 加 messages store;`.actions` 里(登录态分支)加 bell。

script 增补:
```ts
import { useMessages } from './stores/messages'
const messages = useMessages()
onMounted(() => {
  if (auth.isAuthed) { useFavorites().load().catch(() => {}); messages.startPolling() }
})
```
(把原 onMounted 合并成上面这个;原本只有 favorites.load。)

模板 `.actions` 里,把登录态那行 `router-link to="/me"` 前面加 bell:
```vue
        <router-link v-if="auth.isAuthed" data-test="bell" class="bell" to="/inbox" :aria-label="t('msg.title')">
          🔔<span v-if="messages.unread > 0" data-test="bell-dot" class="dot">{{ messages.unread }}</span>
        </router-link>
```
(`.bell`/`.dot` 类在 design/styles.css→tokens.css 已有(inbox.html 用过);若无则加内联样式:红点 `style="color:#fff;background:var(--accent);border-radius:999px;padding:0 6px;font-size:11px;margin-left:2px"`。)

- [ ] **Step 4: 运行确认通过** → 2 例绿。

- [ ] **Step 5: 提交**
```bash
git add frontend/src/App.vue frontend/src/test/AppBell.spec.ts
git commit -m "feat(message): 顶栏 bell + 未读红点(登录后轮询) (#7B 前端)"
```

---

## Task 5: 收件箱页 InboxPage + 路由

**Files:** Create `frontend/src/pages/InboxPage.vue`;Modify `frontend/src/router/index.ts`;Test `frontend/src/test/InboxPage.spec.ts`

- [ ] **Step 1: 写失败测试** `frontend/src/test/InboxPage.spec.ts`
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import zhCN from '../i18n/locales/zh-CN'
import * as api from '../api/messages'

vi.mock('../api/messages', () => ({
  unreadCount: vi.fn(() => Promise.resolve(2)),
  listMessages: vi.fn(() => Promise.resolve([
    { id: 1, templateCode: 'BUS_UPDATED', params: { route: 'VAB 1', changed: [{ field: 'price', oldValue: '€11', newValue: '€13' }] }, relatedSourceId: 'vie-vab1', isRead: false, createdAt: '2026-06-22T09:00:00' },
    { id: 2, templateCode: 'BUS_OFFLINE', params: { route: '机场四线' }, relatedSourceId: null, isRead: false, createdAt: '2026-06-21T09:00:00' },
  ])),
  markRead: vi.fn(() => Promise.resolve({ updated: 1 })),
  deleteMessage: vi.fn(() => Promise.resolve()),
}))
import InboxPage from '../pages/InboxPage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const mountPage = () => mount(InboxPage, { global: { plugins: [i18n], stubs: { 'router-link': { template: '<a><slot/></a>' } } } })

describe('InboxPage', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('renders BUS_UPDATED diff + BUS_OFFLINE title', async () => {
    const w = mountPage(); await flushPromises()
    const text = w.text()
    expect(text).toContain('线路 VAB 1 已更新')
    expect(text).toContain('价格')         // diff 字段名
    expect(text).toContain('€11')
    expect(text).toContain('€13')
    expect(text).toContain('线路 机场四线 已下线')
  })
  it('delete removes a message', async () => {
    const w = mountPage(); await flushPromises()
    await (w.vm as any).remove(2)
    expect(api.deleteMessage).toHaveBeenCalledWith(2)
  })
  it('markAllRead calls markRead with unread ids', async () => {
    const w = mountPage(); await flushPromises()
    await (w.vm as any).markAllRead()
    expect(api.markRead).toHaveBeenCalledWith([1, 2])
  })
  it('empty state when no messages', async () => {
    ;(api.listMessages as any).mockResolvedValueOnce([])
    const w = mountPage(); await flushPromises()
    expect(w.text()).toContain('暂无消息')
  })
})
```

- [ ] **Step 2: 运行确认失败** → 页面不存在。

- [ ] **Step 3: 写 `frontend/src/pages/InboxPage.vue`**
```vue
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMessages } from '../stores/messages'
import { renderMessage } from '../components/renderMessage'

const { t } = useI18n()
const store = useMessages()

onMounted(() => store.loadList())

const rendered = computed(() => store.list.map((m) => ({
  raw: m, view: renderMessage(m.templateCode, m.params, t as any),
})))

async function remove(id: number) { await store.remove(id) }
async function markAllRead() {
  const ids = store.list.filter((m) => !m.isRead).map((m) => m.id)
  if (ids.length) await store.markRead(ids)
}
defineExpose({ remove, markAllRead })
</script>

<template>
  <h1 class="pageH2" style="margin-top: 0">{{ t('msg.title') }}</h1>
  <div style="margin: 8px 0 16px">
    <button class="btn btn-ghost btn-sm" @click="markAllRead">{{ t('msg.markRead') }}</button>
  </div>

  <div v-if="rendered.length === 0" class="panel">{{ t('msg.empty') }}</div>

  <div v-for="r in rendered" :key="r.raw.id" class="panel" :style="{ opacity: r.raw.isRead ? 0.6 : 1 }">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px">
      <div>
        <div class="msgTitle" style="font-weight:700">{{ r.view.title }}</div>
        <div v-for="(d, i) in r.view.diffs" :key="i" class="diffRow" style="font-size:13px;margin-top:4px">
          <span class="diffField">{{ d.label }}</span>
          <span class="diffOld" style="color:var(--ink-faint)"> {{ d.oldValue }}</span>
          <span class="diffArrow"> → </span>
          <span class="diffNew">{{ d.newValue }}</span>
        </div>
        <div class="msgMeta formNote" style="margin-top:6px">{{ r.raw.createdAt }}</div>
      </div>
      <div style="display:flex;gap:8px;flex-shrink:0">
        <router-link v-if="r.raw.relatedSourceId" class="btn btn-ghost btn-sm" :to="`/bus/${r.raw.relatedSourceId}`">{{ t('msg.viewDetail') }}</router-link>
        <button class="btn btn-ghost btn-sm" @click="remove(r.raw.id)">{{ t('msg.delete') }}</button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 4: 路由加 /inbox(需登录)** —— `frontend/src/router/index.ts`:
在文件顶部加 `import { useAuth } from '../stores/auth'`(ESM;Pinia 在 main.ts 先于 router use,守卫内 `useAuth()` 安全)。在 `routes` 数组里(`/me` 附近)加:
```ts
    {
      path: '/inbox', name: 'inbox',
      component: () => import('../pages/InboxPage.vue'),
      beforeEnter: (to) => {
        const auth = useAuth()
        return auth.isAuthed ? true : { name: 'login', query: { redirect: to.fullPath } }
      },
    },
```

- [ ] **Step 5: 运行确认通过** `cd frontend && npx vitest run src/test/InboxPage.spec.ts` → 4 例绿。

- [ ] **Step 6: 提交**
```bash
git add frontend/src/pages/InboxPage.vue frontend/src/router/index.ts frontend/src/test/InboxPage.spec.ts
git commit -m "feat(message): 收件箱页(diff渲染+批量已读+删除+空态)+ /inbox 路由 (#7B 前端)"
```

---

## Task 6: 全量验证 + 收尾

- [ ] **Step 1: 前端全测 + 构建**
Run: `cd frontend && npx vitest run && npm run build`
Expected: 全 spec 绿;vue-tsc 无错(`beforeEach` 带大括号);构建成功。

- [ ] **Step 2: 手动全栈验证**(参考 testing-tooling-quirks 记忆)
`docker compose up -d mysql redis` → 后端 `SEED_ENABLED=true ... mvn spring-boot:run` → 前端 `npx vite`。
- 普通用户登录并收藏 vie-vab1;另开管理员改该线路价格保存 → 几十秒内(轮询)普通用户顶栏红点 +1 → 进 /inbox 看到「线路 VAB 1 已更新 · 价格 €11→€13」→ 标记已读红点消失 → 删除消失。
- 管理员删除某被收藏线路 → 订阅者收「已下线」、其收藏消失。
- 登出后红点消失、轮询停止。

- [ ] **Step 3: 更新记忆** —— 更新 `message-push-loop-shipped.md` 标注前端已交付(收件箱/红点/i18n);`MEMORY.md` 指针。

- [ ] **Step 4: 最终提交**
```bash
git add -A && git commit -m "chore(message): #7B 前端 验证 + 记忆"
```

---

## 自审清单(写计划者已核对)
- **spec 覆盖**:api(T1)、store 轮询生命周期接 auth(T2)、renderMessage+i18n(T3)、顶栏红点(T4)、收件箱页 diff/已读/删除/空态 + 路由守卫(T5)、验证(T6)。✅
- **类型一致**:`MessageItem/MessageParams/FieldChange/RenderedMessage` 全程一致;params 是对象(后端 @JsonRawValue)。✅
- **占位**:无。全部完整代码。
- **风险**:轮询 setInterval 在测试里只断言 polling 标志 + 不跑真实定时(不用 fake timers);App.vue onMounted 合并 favorites+messages 启动;红点类 `.dot` 若 tokens.css 无则用内联样式兜底。
