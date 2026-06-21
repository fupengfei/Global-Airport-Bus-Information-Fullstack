import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('../api/client', () => ({ http: {
  get: vi.fn(() => Promise.resolve({ data: 'OK' })),
  post: vi.fn(() => Promise.resolve({ data: 'OK' })),
  put: vi.fn(() => Promise.resolve({ data: 'OK' })),
  delete: vi.fn(() => Promise.resolve({ data: 'OK' })),
} }))
import { http } from '../api/client'
import * as api from '../api/admin-bus'

describe('admin-bus api', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('getTree', async () => { await api.getTree(); expect(http.get).toHaveBeenCalledWith('/admin/buses/tree') })
  it('getBus', async () => { await api.getBus('vie-vab1'); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1') })
  it('createBus', async () => {
    const body = { sourceId: 'x', airportCode: 'VIE', data: {} as any }
    await api.createBus(body); expect(http.post).toHaveBeenCalledWith('/admin/buses', body)
  })
  it('updateBus', async () => {
    const data = {} as any
    await api.updateBus('vie-vab1', { airportCode: 'VIE', version: 2, data })
    expect(http.put).toHaveBeenCalledWith('/admin/buses/vie-vab1', { airportCode: 'VIE', version: 2, data })
  })
  it('verifyBus', async () => { await api.verifyBus('vie-vab1'); expect(http.post).toHaveBeenCalledWith('/admin/buses/vie-vab1/verify') })
  it('deleteBus', async () => { await api.deleteBus('vie-vab1'); expect(http.delete).toHaveBeenCalledWith('/admin/buses/vie-vab1') })
  it('listVersions', async () => { await api.listVersions('vie-vab1'); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions') })
  it('getVersion', async () => { await api.getVersion('vie-vab1', 2); expect(http.get).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions/2') })
  it('rollback', async () => { await api.rollbackVersion('vie-vab1', 2); expect(http.post).toHaveBeenCalledWith('/admin/buses/vie-vab1/versions/2/rollback') })
})
