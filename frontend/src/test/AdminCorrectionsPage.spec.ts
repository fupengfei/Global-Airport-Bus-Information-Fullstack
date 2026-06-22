import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as api from '../api/corrections'
import AdminCorrectionsPage from '../pages/admin/AdminCorrectionsPage.vue'

vi.mock('../api/corrections', () => ({
  listCorrections: vi.fn(() => Promise.resolve([
    { id: 1, relatedSourceId: 'vie-vab1', description: '末班车是23:30', contact: 'a@b.com', status: 'OPEN', resolutionNote: null, reporterIp: '1.1.1.1', createdAt: '2026-06-22T09:00:00' },
  ])),
  updateCorrection: vi.fn(() => Promise.resolve({ id: 1, status: 'RESOLVED' })),
}))

describe('AdminCorrectionsPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('loads and renders a report row', async () => {
    const w = mount(AdminCorrectionsPage)
    await flushPromises()
    expect(api.listCorrections).toHaveBeenCalled()
    expect(w.text()).toContain('末班车是23:30')
  })
  it('resolve calls updateCorrection', async () => {
    const w = mount(AdminCorrectionsPage)
    await flushPromises()
    await (w.vm as any).setStatus(1, 'RESOLVED')
    expect(api.updateCorrection).toHaveBeenCalledWith(1, { status: 'RESOLVED', resolutionNote: '' })
  })
})
