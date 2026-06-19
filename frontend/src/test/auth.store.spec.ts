import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/auth', () => ({
  login: vi.fn(() => Promise.resolve({ accessToken: 'a1', refreshToken: 'r1' })),
  getMe: vi.fn(() => Promise.resolve({ username: 'zoe', email: 'z@x.com', locale: 'zh-CN', role: 'USER' })),
  logout: vi.fn(() => Promise.resolve({})),
}))

import { useAuth } from '../stores/auth'

describe('auth store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('login stores tokens + loads user + isAuthed', async () => {
    const s = useAuth()
    expect(s.isAuthed).toBe(false)
    await s.login('zoe', 'password123')
    expect(s.isAuthed).toBe(true)
    expect(localStorage.getItem('accessToken')).toBe('a1')
    expect(s.user?.username).toBe('zoe')
  })

  it('logout clears state', async () => {
    const s = useAuth()
    await s.login('zoe', 'password123')
    await s.logout()
    expect(s.isAuthed).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})
