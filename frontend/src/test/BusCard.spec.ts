import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { setActivePinia, createPinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ fullPath: '/bus/vie-vab1' }),
}))
vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve([])),
  favorite: vi.fn(() => Promise.resolve({ favorited: true })),
  unfavorite: vi.fn(() => Promise.resolve({ favorited: false })),
}))

import * as favApi from '../api/favorites'
import { useAuth } from '../stores/auth'
import BusCard from '../components/BusCard.vue'

const bus = {
  sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB', officialUrl: null,
  duration: '40 min', price: '€11', operatingHours: '03:00–24:00', lastUpdated: '2026-06-03', fetchFailed: false,
  stops: ['维也纳机场', 'Westbahnhof'], schedules: [], images: [], files: [], alerts: [],
}

function mountCard() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(BusCard, {
    props: { bus, detailLink: false },
    global: { plugins: [i18n], stubs: { 'router-link': true } },
  })
}

describe('BusCard favorite heart', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); vi.clearAllMocks() })

  it('anonymous click goes to login and does not call api', async () => {
    const w = mountCard()
    await w.find('.favBtn').trigger('click')
    expect(push).toHaveBeenCalled()
    expect(favApi.favorite).not.toHaveBeenCalled()
  })

  it('authed click toggles favorite via api', async () => {
    const auth = useAuth()
    auth.accessToken = 'tok' // isAuthed=true
    const w = mountCard()
    await w.find('.favBtn').trigger('click')
    expect(favApi.favorite).toHaveBeenCalledWith('vie-vab1')
  })
})
