import { http } from './client'

export interface Tree {
  countries: { code: string; name: string; cities: { name: string; airports: { code: string; name: string }[] }[] }[]
}
export interface BusSummary {
  sourceId: string; route: string; destination: string | null; operator: string | null
  duration: string | null; price: string | null; lastUpdated: string | null; fetchFailed: boolean
}
export interface Alert { type: string; message: string; startDate: string | null; endDate: string | null }
export interface BusDetail {
  sourceId: string; route: string; destination: string | null; operator: string | null; officialUrl: string | null
  duration: string | null; price: string | null; operatingHours: string | null
  lastUpdated: string | null; fetchFailed: boolean
  countryName?: string | null; cityName?: string | null; airportName?: string | null; airportCode?: string | null
  stops: string[]
  schedules: { timeRange: string | null; intervalText: string | null; note: string | null }[]
  images: { url: string; caption: string | null }[]
  files: { name: string | null; url: string }[]
  alerts: Alert[]
}

export const getTree = () => http.get<Tree>('/tree').then((r) => r.data)
export const getAirportBuses = (code: string) =>
  http.get<BusSummary[]>(`/airports/${encodeURIComponent(code)}/buses`).then((r) => r.data)
export const getBusDetail = (sourceId: string) =>
  http.get<BusDetail>(`/buses/${encodeURIComponent(sourceId)}`).then((r) => r.data)

export interface SearchResult {
  airports: { code: string; name: string; cityName: string; countryCode: string }[]
  routes: { sourceId: string; route: string; destination: string | null; airportCode: string; matchedStop: string }[]
}
export const search = (q: string) =>
  http.get<SearchResult>('/search', { params: { q } }).then((r) => r.data)
