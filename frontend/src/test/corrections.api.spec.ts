import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  post: vi.fn(() => Promise.resolve({ data: { id: 1, status: 'OPEN' } })),
  get: vi.fn(() => Promise.resolve({ data: [] })),
  patch: vi.fn(() => Promise.resolve({ data: { id: 1, status: 'RESOLVED' } })),
} }))
import { http } from '../api/client'
import * as api from '../api/corrections'

describe('corrections api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('submit posts body', async () => {
    await api.submitCorrection({ sourceId: 'vie-vab1', description: 'x', contact: '' })
    expect(http.post).toHaveBeenCalledWith('/corrections', { sourceId: 'vie-vab1', description: 'x', contact: '' })
  })
  it('admin list with status', async () => {
    await api.listCorrections('OPEN')
    expect(http.get).toHaveBeenCalledWith('/admin/corrections', { params: { status: 'OPEN', limit: 50, offset: 0 } })
  })
  it('admin update patches', async () => {
    await api.updateCorrection(1, { status: 'RESOLVED', resolutionNote: 'ok' })
    expect(http.patch).toHaveBeenCalledWith('/admin/corrections/1', { status: 'RESOLVED', resolutionNote: 'ok' })
  })
})
