import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api/admin', () => ({
  getOverview: vi.fn(() => Promise.resolve({
    totalUsers: 1284, newUsersThisWeek: 42, totalFavorites: 3907, newFavoritesThisWeek: 118,
  })),
  getRegistrations: vi.fn(() => Promise.resolve([
    { date: '2026-06-14', count: 18 }, { date: '2026-06-20', count: 42 },
  ])),
}))
vi.mock('echarts', () => ({
  init: () => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }),
}))

import AdminOverviewPage from '../pages/admin/AdminOverviewPage.vue'

describe('AdminOverviewPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders stat cards from overview data', async () => {
    const wrapper = mount(AdminOverviewPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('1284')
    expect(text).toContain('3907')
    expect(text).toContain('待处理工单')
    expect(text).toContain('—')
  })
})
