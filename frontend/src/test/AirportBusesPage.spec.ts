import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { VueQueryPlugin } from '@tanstack/vue-query'
import zhCN from '../i18n/locales/zh-CN'

vi.mock('../api/bus', () => ({
  getAirportBuses: vi.fn().mockResolvedValue([
    { sourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', operator: 'ÖBB',
      duration: '40min', price: '€11', lastUpdated: '2026-06-03', fetchFailed: false },
  ]),
}))

import AirportBusesPage from '../pages/AirportBusesPage.vue'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('AirportBusesPage', () => {
  it('lists buses for the airport code', async () => {
    const wrapper = mount(AirportBusesPage, {
      props: { code: 'VIE' },
      global: { plugins: [i18n, VueQueryPlugin], stubs },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('VAB 1')
    expect(wrapper.text()).toContain('Westbahnhof')
  })
})
