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
  const token = localStorage.getItem('accessToken')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// 401 → 用 refresh 轮换一次,成功重放原请求;失败清状态跳登录。
// 用裸 axios 调刷新端点,避免再次经过本拦截器造成循环。
http.interceptors.response.use(
  (r) => r,
  async (err: AxiosError) => {
    const original = err.config as (typeof err.config & { _retried?: boolean }) | undefined
    const status = err.response?.status
    const rt = localStorage.getItem('refreshToken')
    if (status === 401 && rt && original && !original._retried && !original.url?.includes('/auth/')) {
      original._retried = true
      try {
        const resp = await axios.post('/api/v1/auth/refresh', { refreshToken: rt })
        localStorage.setItem('accessToken', resp.data.accessToken)
        localStorage.setItem('refreshToken', resp.data.refreshToken)
        original.headers = original.headers ?? {}
        ;(original.headers as Record<string, string>)['Authorization'] = `Bearer ${resp.data.accessToken}`
        return http(original)
      } catch {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        if (location.pathname !== '/login') location.assign('/login')
      }
    }
    return Promise.reject(err)
  },
)

export function asApiError(err: unknown): ApiError | null {
  const e = err as AxiosError<ApiError>
  if (e.isAxiosError && e.response?.data?.code) return e.response.data
  return null
}

// 后端错误 message 是开发用英文串(如 "bad code")。这里按 code 映射到本地化文案,
// 有对应 i18n 键(errors.<CODE>)则用之,否则回落后端 message,再回落通用提示。
export function apiErrorMessage(
  err: unknown,
  t: (k: string) => string,
  te: (k: string) => boolean,
): string {
  const ae = asApiError(err)
  if (ae && te(`errors.${ae.code}`)) return t(`errors.${ae.code}`)
  return ae?.message || t('auth.genericError')
}
