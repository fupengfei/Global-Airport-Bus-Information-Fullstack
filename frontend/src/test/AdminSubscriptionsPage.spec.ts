import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api/admin', () => ({
  getSubscriptions: vi.fn(() => Promise.resolve({
    topRoutes: [{ busSourceId: 'vie-vab1', route: 'VAB 1', destination: 'Westbahnhof', airportCode: 'VIE', cityName: '维也纳', favoriteCount: 612, notifyCount: 612 }],
    topAirports: [{ airportCode: 'VIE', airportName: '维也纳国际机场', cityName: '维也纳', favoriteCount: 989 }],
    topCities: [{ cityName: '维也纳', countryName: '奥地利', favoriteCount: 989 }],
  })),
}))

import AdminSubscriptionsPage from '../pages/admin/AdminSubscriptionsPage.vue'

describe('AdminSubscriptionsPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders top routes / airports / cities', async () => {
    const wrapper = mount(AdminSubscriptionsPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('VIE')
    expect(text).toContain('维也纳')
    expect(text).toContain('612')
  })
})
