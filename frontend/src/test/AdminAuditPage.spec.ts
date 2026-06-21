import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as api from '../api/admin-audit'

vi.mock('../api/admin-audit', () => ({
  listAudit: vi.fn(() => Promise.resolve([
    { id: 1, actorId: 1, actorType: 'ADMIN', action: 'UPDATE_BUS', targetType: 'bus', targetId: 'vie-vab1', summary: null, ip: '10.0.0.4', createdAt: '2026-06-21T09:00:00' },
  ])),
}))
import AdminAuditPage from '../pages/admin/AdminAuditPage.vue'

describe('AdminAuditPage', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders audit rows', async () => {
    const wrapper = mount(AdminAuditPage)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('UPDATE_BUS')
    expect(text).toContain('vie-vab1')
  })
  it('re-filters by action', async () => {
    const wrapper = mount(AdminAuditPage)
    await flushPromises()
    ;(wrapper.vm as any).action = 'DELETE_BUS'
    await (wrapper.vm as any).reload()
    expect(api.listAudit).toHaveBeenLastCalledWith({ action: 'DELETE_BUS' })
  })
})
