import { http } from './client'

export interface TicketReply {
  id: number; authorType: 'USER' | 'ADMIN'; authorId: number; body: string; createdAt: string
}
export interface Ticket {
  id: number; userId: number; relatedSourceId: string | null
  status: string; lastReplyAt: string; createdAt: string
}
export interface TicketThread { ticket: Ticket; replies: TicketReply[] }

// 用户侧
export const listTickets = (status = '') =>
  http.get<Ticket[]>('/tickets', { params: { status } }).then((r) => r.data)
export const getTicket = (id: number) =>
  http.get<TicketThread>(`/tickets/${id}`).then((r) => r.data)
export const createTicket = (body: { sourceId?: string; body: string }) =>
  http.post<TicketThread>('/tickets', body).then((r) => r.data)
export const replyTicket = (id: number, body: string) =>
  http.post<TicketThread>(`/tickets/${id}/replies`, { body }).then((r) => r.data)
export const closeTicket = (id: number) =>
  http.post<Ticket>(`/tickets/${id}/close`, {}).then((r) => r.data)

// 管理员侧
export const adminListTickets = (status = '') =>
  http.get<Ticket[]>('/admin/tickets', { params: { status } }).then((r) => r.data)
export const adminGetTicket = (id: number) =>
  http.get<TicketThread>(`/admin/tickets/${id}`).then((r) => r.data)
export const adminReplyTicket = (id: number, body: string) =>
  http.post<TicketThread>(`/admin/tickets/${id}/replies`, { body }).then((r) => r.data)
export const adminCloseTicket = (id: number) =>
  http.post<Ticket>(`/admin/tickets/${id}/close`, {}).then((r) => r.data)
