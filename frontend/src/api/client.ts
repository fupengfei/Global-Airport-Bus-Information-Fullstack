import axios, { AxiosError } from 'axios'

export interface ApiError {
  code: string
  message: string
  details: { field: string; issue: string }[]
  traceId: string
}

export const http = axios.create({ baseURL: '/api/v1' })

http.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = localStorage.getItem('locale') ?? 'en'
  return config
})

export function asApiError(err: unknown): ApiError | null {
  const e = err as AxiosError<ApiError>
  if (e.isAxiosError && e.response?.data?.code) return e.response.data
  return null
}
