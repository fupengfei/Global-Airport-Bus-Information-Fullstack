import { defineStore } from 'pinia'
import * as api from '../api/tickets'
import type { Ticket, TicketThread } from '../api/tickets'

export const useTickets = defineStore('tickets', {
  state: () => ({
    list: [] as Ticket[],
    threads: {} as Record<number, TicketThread>,
  }),
  actions: {
    async load(status = '') { this.list = await api.listTickets(status) },
    async open(id: number) { this.threads[id] = await api.getTicket(id) },
    async create(body: { sourceId?: string; body: string }) {
      const th = await api.createTicket(body)
      this.threads[th.ticket.id] = th
      await this.load()
      return th
    },
    async reply(id: number, body: string) {
      const th = await api.replyTicket(id, body)
      this.threads[id] = th
      this.syncStatus(id, th.ticket.status)
    },
    async close(id: number) {
      const t = await api.closeTicket(id)
      this.syncStatus(id, t.status)
      if (this.threads[id]) this.threads[id].ticket.status = t.status
    },
    syncStatus(id: number, status: string) {
      const row = this.list.find((t) => t.id === id)
      if (row) row.status = status
    },
  },
})
