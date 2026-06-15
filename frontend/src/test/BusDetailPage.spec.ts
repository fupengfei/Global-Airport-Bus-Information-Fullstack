import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('../api/bus', () => ({
  getBusDetail: vi.fn().mockResolvedValue({
    sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB',
    officialUrl: 'https://example.com', duration: '40min', price: '€11', operatingHours: '03:00-24:00',
    lastUpdated: '2026-06-03', fetchFailed: false,
    stops: ['Westbahnhof', 'Hauptbahnhof', 'Airport'],
    schedules: [{ timeRange: 'all day', intervalText: '30min', note: '' }],
    images: [], files: [],
    alerts: [{ type: 'info', message: 'live', startDate: null, endDate: '2099-01-01' }],
  }),
}))

import BusDetailPage from '../pages/BusDetailPage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('BusDetailPage', () => {
  it('shows decision bar fields and stops', async () => {
    const wrapper = mount(BusDetailPage, {
      props: { sourceId: 'vie-vab1' },
      global: { plugins: [i18n, VueQueryPlugin], stubs },
    })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('€11')
    expect(text).toContain('Westbahnhof')
    expect(text).toContain('live') // 未过期 alert 展示
  })
})
