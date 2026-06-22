import { http } from './client'

export interface FieldChange { field: string; oldValue: string | null; newValue: string | null }
export interface MessageParams { route?: string; sourceId?: string; changed?: FieldChange[]; changedSubtables?: string[] }
export interface MessageItem {
  id: number; templateCode: string; params: MessageParams
  relatedSourceId: string | null; isRead: boolean; createdAt: string
}

export const unreadCount = () => http.get<{ count: number }>('/messages/unread-count').then((r) => r.data.count)
export const listMessages = (limit = 20, offset = 0) =>
  http.get<MessageItem[]>('/messages', { params: { limit, offset } }).then((r) => r.data)
export const markRead = (ids: number[]) => http.post<{ updated: number }>('/messages/read', { ids }).then((r) => r.data)
export const deleteMessage = (id: number) => http.delete(`/messages/${id}`).then((r) => r.data)
