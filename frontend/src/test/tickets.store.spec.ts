import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
vi.mock('../api/tickets', () => ({
  listTickets: vi.fn(() => Promise.resolve([{ id: 1, status: 'OPEN', relatedSourceId: null }])),
  getTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [{ id: 9, authorType: 'USER', body: 'hi' }] })),
  createTicket: vi.fn(() => Promise.resolve({ ticket: { id: 2, status: 'OPEN' }, replies: [] })),
  replyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [{ id: 9 }, { id: 10 }] })),
  closeTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'
import { useTickets } from '../stores/tickets'

describe('tickets store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('load fills list', async () => {
    const s = useTickets(); await s.load()
    expect(s.list).toHaveLength(1)
    expect(api.listTickets).toHaveBeenCalled()
  })
  it('open loads thread into threads map', async () => {
    const s = useTickets(); await s.open(1)
    expect(s.threads[1].replies).toHaveLength(1)
  })
  it('create prepends new ticket and reloads list', async () => {
    const s = useTickets(); await s.create({ body: 'new' })
    expect(api.createTicket).toHaveBeenCalledWith({ body: 'new' })
    expect(api.listTickets).toHaveBeenCalled()
  })
  it('reply updates thread + status in list', async () => {
    const s = useTickets(); await s.load(); await s.reply(1, 'more')
    expect(api.replyTicket).toHaveBeenCalledWith(1, 'more')
    expect(s.threads[1].replies).toHaveLength(2)
  })
  it('close sets status CLOSED in list', async () => {
    const s = useTickets(); await s.load(); await s.close(1)
    expect(s.list[0].status).toBe('CLOSED')
  })
})
