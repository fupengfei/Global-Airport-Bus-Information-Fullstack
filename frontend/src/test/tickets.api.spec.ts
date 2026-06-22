import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  post: vi.fn(() => Promise.resolve({ data: { ticket: { id: 1, status: 'OPEN' }, replies: [] } })),
  get: vi.fn(() => Promise.resolve({ data: [] })),
} }))
import { http } from '../api/client'
import * as api from '../api/tickets'

describe('tickets api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('create posts sourceId+body', async () => {
    await api.createTicket({ sourceId: 'vie-vab1', body: 'x' })
    expect(http.post).toHaveBeenCalledWith('/tickets', { sourceId: 'vie-vab1', body: 'x' })
  })
  it('list passes status', async () => {
    await api.listTickets('OPEN')
    expect(http.get).toHaveBeenCalledWith('/tickets', { params: { status: 'OPEN' } })
  })
  it('reply posts body to replies', async () => {
    await api.replyTicket(5, 'hi')
    expect(http.post).toHaveBeenCalledWith('/tickets/5/replies', { body: 'hi' })
  })
  it('close posts empty body', async () => {
    await api.closeTicket(5)
    expect(http.post).toHaveBeenCalledWith('/tickets/5/close', {})
  })
  it('admin reply hits admin path', async () => {
    await api.adminReplyTicket(5, 'ok')
    expect(http.post).toHaveBeenCalledWith('/admin/tickets/5/replies', { body: 'ok' })
  })
})
