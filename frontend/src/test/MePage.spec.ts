import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { setActivePinia, createPinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('../api/auth', () => ({
  getMe: vi.fn(() => Promise.resolve({ username: 'zoe', email: 'z@x.com', locale: 'zh-CN', role: 'USER' })),
  changePassword: vi.fn(),
}))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve(['vie-vab1'])),
  listFavorites: vi.fn(() => Promise.resolve([{
    sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB', officialUrl: null,
    duration: null, price: null, operatingHours: null, lastUpdated: null, fetchFailed: false,
    countryName: '奥地利', cityName: '维也纳', airportName: '维也纳国际机场', airportCode: 'VIE',
    stops: [], schedules: [], images: [], files: [], alerts: [],
  }])),
  favorite: vi.fn(), unfavorite: vi.fn(),
}))

import { useAuth } from '../stores/auth'
import MePage from '../pages/MePage.vue'

function mountMe() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(MePage, { global: { plugins: [i18n], stubs: { 'router-link': RouterLinkStub } } })
}

describe('MePage favorites', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('renders my favorites list', async () => {
    const auth = useAuth()
    auth.accessToken = 'tok'
    auth.user = { username: 'zoe', email: 'z@x.com', locale: 'zh-CN', role: 'USER' }
    const w = mountMe()
    await flushPromises()
    expect(w.text()).toContain('我的收藏')
    expect(w.text()).toContain('VAB 1')
    expect(w.text()).toContain('维也纳国际机场 (VIE)')
  })
})
