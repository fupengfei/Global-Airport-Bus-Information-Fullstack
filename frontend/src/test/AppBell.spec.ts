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
