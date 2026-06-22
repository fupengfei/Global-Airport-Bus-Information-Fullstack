import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/messages', () => ({
  unreadCount: vi.fn(() => Promise.resolve(3)),
  listMessages: vi.fn(() => Promise.resolve([
    { id: 1, templateCode: 'BUS_UPDATED', params: { route: 'VAB 1', changed: [] }, relatedSourceId: 'vie-vab1', isRead: false, createdAt: '2026-06-22T09:00:00' },
  ])),
  markRead: vi.fn(() => Promise.resolve({ updated: 1 })),
  deleteMessage: vi.fn(() => Promise.resolve()),
}))
import * as api from '../api/messages'
import { useMessages } from '../stores/messages'

describe('messages store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('refreshUnread sets unread count', async () => {
    const s = useMessages()
    await s.refreshUnread()
    expect(s.unread).toBe(3)
  })
  it('loadList fills list', async () => {
    const s = useMessages()
    await s.loadList()
    expect(s.list).toHaveLength(1)
    expect(s.list[0].templateCode).toBe('BUS_UPDATED')
  })
  it('markRead marks items read locally + refreshes unread', async () => {
    const s = useMessages()
    await s.loadList()
    await s.markRead([1])
    expect(api.markRead).toHaveBeenCalledWith([1])
    expect(s.list[0].isRead).toBe(true)
  })
  it('remove deletes from list', async () => {
    const s = useMessages()
    await s.loadList()
    await s.remove(1)
    expect(api.deleteMessage).toHaveBeenCalledWith(1)
    expect(s.list).toHaveLength(0)
  })
  it('startPolling sets interval, stopPolling clears + resets', async () => {
    const s = useMessages()
    s.startPolling()
    expect(s.polling).toBe(true)
    s.stopPolling()
    expect(s.polling).toBe(false)
    expect(s.unread).toBe(0)
  })
})
