import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { isAdminRole, adminGuard } from '../router/adminGuard'
import { useAuth } from '../stores/auth'

const to = { fullPath: '/admin' } as any

describe('isAdminRole', () => {
  it('accepts SUPER_ADMIN and OPERATOR, rejects others', () => {
    expect(isAdminRole('SUPER_ADMIN')).toBe(true)
    expect(isAdminRole('OPERATOR')).toBe(true)
    expect(isAdminRole('USER')).toBe(false)
    expect(isAdminRole(undefined)).toBe(false)
  })
})

describe('adminGuard', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('redirects anonymous to login with redirect', async () => {
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'login', query: { redirect: '/admin' } })
  })

  it('redirects logged-in non-admin to home', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = { username: 'u', email: 'u@x.com', locale: 'zh-CN', role: 'USER' }
    const res = await adminGuard(to)
    expect(res).toEqual({ name: 'home' })
  })

  it('allows admin', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'SUPER_ADMIN' }
    const res = await adminGuard(to)
    expect(res).toBe(true)
  })

  it('loads me when authed but user missing, then allows admin', async () => {
    const auth = useAuth()
    auth.accessToken = 'x'
    auth.user = null
    vi.spyOn(auth, 'loadMe').mockImplementation(async () => {
      auth.user = { username: 'a', email: 'a@x.com', locale: 'zh-CN', role: 'OPERATOR' }
    })
    const res = await adminGuard(to)
    expect(auth.loadMe).toHaveBeenCalled()
    expect(res).toBe(true)
  })
})
