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
