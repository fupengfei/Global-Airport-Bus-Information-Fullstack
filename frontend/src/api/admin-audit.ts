import { http } from './client'

export interface AuditRow {
  id: number; actorId: number; actorType: string; action: string
  targetType: string; targetId: string | null; summary: string | null; ip: string | null; createdAt: string
}
export const listAudit = (params: { actor?: number; action?: string; limit?: number } = {}) =>
  http.get<AuditRow[]>('/admin/audit', { params }).then((r) => r.data)
