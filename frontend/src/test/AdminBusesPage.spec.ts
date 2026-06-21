import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { useAuth } from '../stores/auth'

vi.mock('../api/admin-bus', () => ({
  getTree: vi.fn(() => Promise.resolve([
    { countryCode: 'AT', countryName: '奥地利', cityName: '维也纳', airportCode: 'VIE', airportName: '维也纳机场', busSourceId: 'vie-vab1', busRoute: 'VAB 1' },
    { countryCode: 'AT', countryName: '奥地利', cityName: '维也纳', airportCode: 'VIE', airportName: '维也纳机场', busSourceId: null, busRoute: null },
  ])),
  getBus: vi.fn(() => Promise.resolve({
    sourceId: 'vie-vab1', airportCode: 'VIE', version: 3, lastVerifiedAt: null,
    data: { route: 'VAB 1', destination: '西站', operator: 'ÖBB', officialUrl: null, duration: '40min', price: '€11', operatingHours: '03:00-24:00', lastUpdated: null, stops: ['A'], schedules: [], alerts: [], images: [], files: [] },
  })),
  listVersions: vi.fn(() => Promise.resolve([])),
  createBus: vi.fn((b) => Promise.resolve({ ...b, version: 1, lastVerifiedAt: null })),
  updateBus: vi.fn((s, b) => Promise.resolve({ sourceId: s, airportCode: b.airportCode, version: b.version + 1, lastVerifiedAt: null, data: b.data })),
  verifyBus: vi.fn(() => Promise.resolve()),
  deleteBus: vi.fn(() => Promise.resolve()),
}))
import * as api from '../api/admin-bus'
import AdminBusesPage from '../pages/admin/AdminBusesPage.vue'

describe('AdminBusesPage', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })
  it('renders tree routes and skips null buses', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    expect(wrapper.text()).toContain('VAB 1')
  })
  it('selecting a route loads it into the form', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    await (wrapper.vm as any).select('vie-vab1')
    await flushPromises()
    expect(api.getBus).toHaveBeenCalledWith('vie-vab1')
    expect((wrapper.vm as any).current.sourceId).toBe('vie-vab1')
    expect(wrapper.text()).toContain('编辑线路 · VAB 1')
  })

  it('save calls updateBus with version + airportCode', async () => {
    const wrapper = mount(AdminBusesPage)
    await flushPromises()
    await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    await (wrapper.vm as any).save()
    expect(api.updateBus).toHaveBeenCalledWith('vie-vab1', expect.objectContaining({ airportCode: 'VIE', version: 3 }))
  })

  it('delete button visible only for SUPER_ADMIN', async () => {
    const auth = useAuth(); auth.accessToken = 'x'
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'OPERATOR' }
    const wrapper = mount(AdminBusesPage)
    await flushPromises(); await (wrapper.vm as any).select('vie-vab1'); await flushPromises()
    expect((wrapper.vm as any).canDelete).toBe(false)
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'SUPER_ADMIN' }
    expect((wrapper.vm as any).canDelete).toBe(true)
  })
})
