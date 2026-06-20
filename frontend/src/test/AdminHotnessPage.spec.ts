import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as admin from '../api/admin'

vi.mock('../api/admin', () => ({
  getHotness: vi.fn(() => Promise.resolve([
    { airportCode: 'VIE', airportName: '维也纳国际机场', cityName: '维也纳', views: 1200 },
    { airportCode: 'PVG', airportName: '浦东国际机场', cityName: '上海', views: 900 },
  ])),
}))

import AdminHotnessPage from '../pages/admin/AdminHotnessPage.vue'

describe('AdminHotnessPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders hotness rows and defaults to 7d window', async () => {
    const wrapper = mount(AdminHotnessPage)
    await flushPromises()
    expect(admin.getHotness).toHaveBeenCalledWith('7d')
    const text = wrapper.text()
    expect(text).toContain('VIE')
    expect(text).toContain('1200')
  })

  it('re-fetches when window changes to 30d', async () => {
    const wrapper = mount(AdminHotnessPage)
    await flushPromises()
    ;(wrapper.vm as any).window = '30d'
    await (wrapper.vm as any).reload()
    expect(admin.getHotness).toHaveBeenLastCalledWith('30d')
  })
})
