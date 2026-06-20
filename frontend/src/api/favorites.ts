import { http } from './client'
import type { BusDetail } from './bus'

export const listFavoriteIds = () => http.get<string[]>('/favorites/ids').then((r) => r.data)
export const listFavorites = () => http.get<BusDetail[]>('/favorites').then((r) => r.data)
export const favorite = (sourceId: string) =>
  http.put<{ favorited: boolean }>(`/buses/${encodeURIComponent(sourceId)}/favorite`).then((r) => r.data)
export const unfavorite = (sourceId: string) =>
  http.delete<{ favorited: boolean }>(`/buses/${encodeURIComponent(sourceId)}/favorite`).then((r) => r.data)
