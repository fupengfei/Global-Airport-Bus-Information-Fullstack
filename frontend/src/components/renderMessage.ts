import type { MessageParams } from '../api/messages'

export interface RenderedDiff { label: string; oldValue: string | null; newValue: string | null }
export interface RenderedMessage { title: string; diffs: RenderedDiff[] }

type T = (key: string, named?: Record<string, unknown>) => string

/** 把 {templateCode, params} 按 locale 渲染成标题 + diff 行。未知 code 兜底(D6)。 */
export function renderMessage(templateCode: string, params: MessageParams, t: T): RenderedMessage {
  const route = params?.route ?? ''
  if (templateCode === 'BUS_UPDATED') {
    const diffs = (params?.changed ?? []).map((c) => ({
      label: t('msg.field.' + c.field), oldValue: c.oldValue, newValue: c.newValue,
    }))
    return { title: t('msg.busUpdated', { route }), diffs }
  }
  if (templateCode === 'BUS_OFFLINE') {
    return { title: t('msg.busOffline', { route }), diffs: [] }
  }
  return { title: t('msg.unknown'), diffs: [] }
}
