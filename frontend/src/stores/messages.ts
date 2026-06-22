import { defineStore } from 'pinia'
import * as api from '../api/messages'
import type { MessageItem } from '../api/messages'

const POLL_MS = 30000

export const useMessages = defineStore('messages', {
  state: () => ({
    unread: 0,
    list: [] as MessageItem[],
    polling: false,
    timer: 0 as unknown as ReturnType<typeof setInterval> | 0,
  }),
  actions: {
    async refreshUnread() {
      try { this.unread = await api.unreadCount() } catch { /* 忽略轮询错误 */ }
    },
    async loadList() { this.list = await api.listMessages(50, 0) },
    async markRead(ids: number[]) {
      await api.markRead(ids)
      for (const m of this.list) if (ids.includes(m.id)) m.isRead = true
      await this.refreshUnread()
    },
    async remove(id: number) {
      await api.deleteMessage(id)
      this.list = this.list.filter((m) => m.id !== id)
      await this.refreshUnread()
    },
    startPolling() {
      if (this.polling) return
      this.polling = true
      this.refreshUnread()
      this.timer = setInterval(() => this.refreshUnread(), POLL_MS)
    },
    stopPolling() {
      if (this.timer) clearInterval(this.timer)
      this.timer = 0
      this.polling = false
      this.unread = 0
      this.list = []
    },
  },
})
