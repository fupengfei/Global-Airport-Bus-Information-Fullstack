import { http } from './client'

export interface BusInput {
  route: string; destination: string | null; operator: string | null; officialUrl: string | null
  duration: string | null; price: string | null; operatingHours: string | null; lastUpdated: string | null
  stops: string[]
  schedules: { timeRange: string | null; intervalText: string | null; note: string | null }[]
  alerts: { type: string; message: string; startDate: string | null; endDate: string | null }[]
  images: { url: string; caption: string | null }[]
  files: { name: string | null; url: string }[]
}
export interface BusView { sourceId: string; airportCode: string; version: number; lastVerifiedAt: string | null; data: BusInput }
export interface AdminTreeRow {
  countryCode: string; countryName: string; cityName: string
  airportCode: string; airportName: string; busSourceId: string | null; busRoute: string | null
}
export interface VersionMeta { version: number; contentHash: string; changedSummary: string | null; actor: string | null; createdAt: string }

export const getTree = () => http.get<AdminTreeRow[]>('/admin/buses/tree').then((r) => r.data)
export const getBus = (sourceId: string) => http.get<BusView>(`/admin/buses/${sourceId}`).then((r) => r.data)
export const createBus = (body: { sourceId: string; airportCode: string; data: BusInput }) =>
  http.post<BusView>('/admin/buses', body).then((r) => r.data)
export const updateBus = (sourceId: string, body: { airportCode: string; version: number; data: BusInput }) =>
  http.put<BusView>(`/admin/buses/${sourceId}`, body).then((r) => r.data)
export const verifyBus = (sourceId: string) => http.post(`/admin/buses/${sourceId}/verify`).then((r) => r.data)
export const deleteBus = (sourceId: string) => http.delete(`/admin/buses/${sourceId}`).then((r) => r.data)
export const listVersions = (sourceId: string) => http.get<VersionMeta[]>(`/admin/buses/${sourceId}/versions`).then((r) => r.data)
export const getVersion = (sourceId: string, version: number) =>
  http.get<BusInput>(`/admin/buses/${sourceId}/versions/${version}`).then((r) => r.data)
export const rollbackVersion = (sourceId: string, version: number) =>
  http.post<BusView>(`/admin/buses/${sourceId}/versions/${version}/rollback`).then((r) => r.data)
