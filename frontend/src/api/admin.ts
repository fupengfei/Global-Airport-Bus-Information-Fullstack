import { http } from './client'

export interface Overview {
  totalUsers: number
  newUsersThisWeek: number
  totalFavorites: number
  newFavoritesThisWeek: number
}
export interface RegistrationPoint { date: string; count: number }
export interface RouteSub {
  busSourceId: string; route: string; destination: string
  airportCode: string; cityName: string; favoriteCount: number; notifyCount: number
}
export interface AirportSub { airportCode: string; airportName: string; cityName: string; favoriteCount: number }
export interface CitySub { cityName: string; countryName: string; favoriteCount: number }
export interface SubscriptionStats { topRoutes: RouteSub[]; topAirports: AirportSub[]; topCities: CitySub[] }
export interface HotnessRow { airportCode: string; airportName: string; cityName: string; views: number }

export const getOverview = () => http.get<Overview>('/admin/stats/overview').then((r) => r.data)
export const getRegistrations = (days = 7) =>
  http.get<RegistrationPoint[]>('/admin/stats/registrations', { params: { days } }).then((r) => r.data)
export const getSubscriptions = () =>
  http.get<SubscriptionStats>('/admin/stats/subscriptions').then((r) => r.data)
export const getHotness = (window = '7d') =>
  http.get<HotnessRow[]>('/admin/stats/hotness', { params: { window } }).then((r) => r.data)
