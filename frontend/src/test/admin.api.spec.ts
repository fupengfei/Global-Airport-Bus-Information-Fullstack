import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../api/client', () => ({
  http: { get: vi.fn(() => Promise.resolve({ data: 'OK' })) },
}))

import { http } from '../api/client'
import * as admin from '../api/admin'

describe('admin api client', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getOverview hits /admin/stats/overview', async () => {
    await admin.getOverview()
    expect(http.get).toHaveBeenCalledWith('/admin/stats/overview')
  })

  it('getRegistrations passes days param', async () => {
    await admin.getRegistrations(7)
    expect(http.get).toHaveBeenCalledWith('/admin/stats/registrations', { params: { days: 7 } })
  })

  it('getSubscriptions hits /admin/stats/subscriptions', async () => {
    await admin.getSubscriptions()
    expect(http.get).toHaveBeenCalledWith('/admin/stats/subscriptions')
  })

  it('getHotness passes window param', async () => {
    await admin.getHotness('30d')
    expect(http.get).toHaveBeenCalledWith('/admin/stats/hotness', { params: { window: '30d' } })
  })
})
