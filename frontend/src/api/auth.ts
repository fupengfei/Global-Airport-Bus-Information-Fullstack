import { http } from './client'

export interface TokenPair { accessToken: string; refreshToken: string }
export interface MeView { username: string; email: string; locale: string; role: string }

export const sendRegisterCode = (email: string) =>
  http.post<{ sent: boolean }>('/auth/register/code', { email }).then((r) => r.data)
export const register = (b: { username: string; email: string; code: string; password: string }) =>
  http.post<TokenPair>('/auth/register', b).then((r) => r.data)
export const login = (account: string, password: string) =>
  http.post<TokenPair>('/auth/login', { account, password }).then((r) => r.data)
export const refresh = (refreshToken: string) =>
  http.post<TokenPair>('/auth/refresh', { refreshToken }).then((r) => r.data)
export const logout = (refreshToken: string) =>
  http.post('/auth/logout', { refreshToken }).then((r) => r.data)
export const forgot = (email: string) =>
  http.post<{ sent: boolean }>('/auth/password/forgot', { email }).then((r) => r.data)
export const reset = (token: string, newPassword: string) =>
  http.post('/auth/password/reset', { token, newPassword }).then((r) => r.data)
export const getMe = () => http.get<MeView>('/me').then((r) => r.data)
export const updateMe = (locale: string) => http.patch<MeView>('/me', { locale }).then((r) => r.data)
export const changePassword = (oldPassword: string, newPassword: string) =>
  http.post('/me/password', { oldPassword, newPassword }).then((r) => r.data)
