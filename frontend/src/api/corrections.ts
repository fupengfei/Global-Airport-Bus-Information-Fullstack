import { http } from './client'

export interface CorrectionReport {
  id: number; relatedSourceId: string | null; description: string
  contact: string | null; status: string; resolutionNote: string | null
  reporterIp: string | null; createdAt: string
}
export interface SubmitCorrection { sourceId?: string; description: string; contact?: string }

export const submitCorrection = (body: SubmitCorrection) =>
  http.post<CorrectionReport>('/corrections', body).then((r) => r.data)

export const listCorrections = (status = '', limit = 50, offset = 0) =>
  http.get<CorrectionReport[]>('/admin/corrections', { params: { status, limit, offset } }).then((r) => r.data)

export const updateCorrection = (id: number, body: { status: string; resolutionNote?: string }) =>
  http.patch<CorrectionReport>(`/admin/corrections/${id}`, body).then((r) => r.data)
