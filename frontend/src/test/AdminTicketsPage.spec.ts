import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AdminTicketsPage from '../pages/admin/AdminTicketsPage.vue'

vi.mock('../api/tickets', () => ({
  adminListTickets: vi.fn(() => Promise.resolve([
    { id: 1, status: 'OPEN', relatedSourceId: 'vie-vab1', userId: 5, lastReplyAt: '2026-06-20', createdAt: '2026-06-19' },
  ])),
  adminGetTicket: vi.fn(() => Promise.resolve({
    ticket: { id: 1, status: 'OPEN', userId: 5 },
    replies: [{ id: 1, authorType: 'USER', body: '请核对', createdAt: '2026-06-19' }],
  })),
  adminReplyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'REPLIED' }, replies: [] })),
  adminCloseTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'

describe('AdminTicketsPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('loads queue on mount', async () => {
    const w = mount(AdminTicketsPage)
    await flushPromises()
    expect(api.adminListTickets).toHaveBeenCalled()
    expect(w.text()).toContain('vie-vab1')
  })
  it('replying as admin calls adminReplyTicket', async () => {
    const w = mount(AdminTicketsPage)
    await flushPromises()
    await (w.vm as any).openThread(1)
    await flushPromises()
    ;(w.vm as any).replyDraft[1] = '已更新'
    await (w.vm as any).sendReply(1)
    expect(api.adminReplyTicket).toHaveBeenCalledWith(1, '已更新')
  })
})
