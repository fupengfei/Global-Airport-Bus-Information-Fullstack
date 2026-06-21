import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: { get: vi.fn(() => Promise.resolve({ data: 'OK' })) } }))
import { http } from '../api/client'
import * as api from '../api/admin-audit'

describe('admin-audit api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('listAudit default', async () => { await api.listAudit(); expect(http.get).toHaveBeenCalledWith('/admin/audit', { params: {} }) })
  it('listAudit with filters', async () => {
    await api.listAudit({ action: 'UPDATE_BUS', limit: 50 })
    expect(http.get).toHaveBeenCalledWith('/admin/audit', { params: { action: 'UPDATE_BUS', limit: 50 } })
  })
})
