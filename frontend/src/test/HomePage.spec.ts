import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'
import en from '../i18n/locales/en'

vi.mock('../api/bus', () => ({
  getTree: vi.fn(() =>
    Promise.resolve({
      countries: [
        {
          code: 'AT',
          name: '奥地利',
          cities: [{ name: 'Vienna', airports: [{ code: 'VIE', name: '维也纳国际机场' }] }],
        },
      ],
    }),
  ),
  getAirportBuses: vi.fn(() =>
    Promise.resolve([
      { sourceId: 'vie-vab1', route: 'VAB 1', destination: '维也纳西站 Westbahnhof', operator: null, duration: null, price: null, lastUpdated: null, fetchFailed: false },
    ]),
  ),
  getBusDetail: vi.fn(() =>
    Promise.resolve({
      sourceId: 'vie-vab1', route: 'VAB 1', destination: '维也纳西站 Westbahnhof', operator: 'ÖBB Postbus', officialUrl: null,
      duration: '约 40 分钟', price: '€11', operatingHours: '03:00–24:00', lastUpdated: '2026-06-03', fetchFailed: false,
      stops: ['维也纳机场', '维也纳西站 Westbahnhof'], schedules: [{ timeRange: '全天', intervalText: '每 30 分钟', note: '固定班距' }],
      images: [], files: [], alerts: [],
    }),
  ),
  search: vi.fn(() => Promise.resolve({ airports: [], routes: [] })),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import HomePage from '../pages/HomePage.vue'

function mountHome() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN, en } })
  return mount(HomePage, {
    global: {
      plugins: [i18n, VueQueryPlugin],
      stubs: { 'router-link': true },
    },
  })
}

describe('HomePage', () => {
  it('renders the selector and the country/city/airport from the tree', async () => {
    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.find('.selector').exists()).toBe(true)
    expect(wrapper.find('.searchWrap').exists()).toBe(true)

    const selects = wrapper.findAll('.field select')
    expect(selects).toHaveLength(3)

    // 国家选项已渲染
    expect(selects[0].text()).toContain('奥地利')

    // 级联:选中国家 → 城市,选中城市 → 机场
    await selects[0].setValue('AT')
    expect(selects[1].text()).toContain('Vienna')

    await selects[1].setValue('Vienna')
    expect(selects[2].text()).toContain('维也纳国际机场')
    expect(selects[2].text()).toContain('VIE')
  })

  it('shows the page header title', async () => {
    const wrapper = mountHome()
    await flushPromises()
    expect(wrapper.find('.title').text()).toBe('机场巴士信息')
  })

  it('lists ALL route cards for the picked airport by default (#4)', async () => {
    const wrapper = mountHome()
    await flushPromises()

    const selects = wrapper.findAll('.field select')
    await selects[0].setValue('AT')
    await selects[1].setValue('Vienna')
    await selects[2].setValue('VIE')
    await flushPromises()
    await flushPromises()

    // 默认不选中具体线路,直接渲染该机场全部线路卡片
    expect(wrapper.find('.routePick').exists()).toBe(true)
    expect(wrapper.findAll('.card').length).toBeGreaterThanOrEqual(1)
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('约 40 分钟')
    expect(text).toContain('每 30 分钟')
  })
})
