import { defineStore } from 'pinia'
import * as api from '../api/auth'
import type { MeView } from '../api/auth'

export const useAuth = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    user: null as MeView | null,
  }),
  getters: {
    isAuthed: (s) => !!s.accessToken,
  },
  actions: {
    setTokens(t: { accessToken: string; refreshToken: string }) {
      this.accessToken = t.accessToken
      this.refreshToken = t.refreshToken
      localStorage.setItem('accessToken', t.accessToken)
      localStorage.setItem('refreshToken', t.refreshToken)
    },
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      import('./favorites').then((m) => m.useFavorites().clear())
    },
    async login(account: string, password: string) {
      this.setTokens(await api.login(account, password))
      await this.loadMe()
    },
    async register(b: { username: string; email: string; code: string; password: string }) {
      this.setTokens(await api.register(b))
      await this.loadMe()
    },
    async loadMe() {
      this.user = await api.getMe()
      const { useFavorites } = await import('./favorites')
      try { await useFavorites().load() } catch { /* 收藏加载失败不阻塞登录 */ }
    },
    async logout() {
      if (this.refreshToken) {
        try { await api.logout(this.refreshToken) } catch { /* ignore */ }
      }
      this.clear()
    },
  },
})
