import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  get: vi.fn(() => Promise.resolve({ data: 'OK' })),
  post: vi.fn(() => Promise.resolve({ data: 'OK' })),
  delete: vi.fn(() => Promise.resolve({ data: 'OK' })),
} }))
import { http } from '../api/client'
import * as api from '../api/messages'

describe('messages api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('unreadCount', async () => { await api.unreadCount(); expect(http.get).toHaveBeenCalledWith('/messages/unread-count') })
  it('list default', async () => { await api.listMessages(); expect(http.get).toHaveBeenCalledWith('/messages', { params: { limit: 20, offset: 0 } }) })
  it('markRead', async () => { await api.markRead([1,2]); expect(http.post).toHaveBeenCalledWith('/messages/read', { ids: [1,2] }) })
  it('deleteMessage', async () => { await api.deleteMessage(5); expect(http.delete).toHaveBeenCalledWith('/messages/5') })
})
