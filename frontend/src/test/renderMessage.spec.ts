import { describe, it, expect } from 'vitest'
import { renderMessage } from '../components/renderMessage'

const t = (key: string, named?: Record<string, unknown>) => {
  const map: Record<string, string> = {
    'msg.busUpdated': '线路 {route} 已更新',
    'msg.busOffline': '线路 {route} 已下线',
    'msg.unknown': '您有一条新通知',
    'msg.field.price': '价格',
    'msg.ticketReplied': '您的工单 #{ticketId} 有新回复',
  }
  let s = map[key] ?? key
  if (named) for (const k of Object.keys(named)) s = s.replace(`{${k}}`, String(named[k]))
  return s
}

describe('renderMessage', () => {
  it('BUS_UPDATED title + diff(字段名走 i18n)', () => {
    const r = renderMessage('BUS_UPDATED', { route: 'VAB 1', changed: [{ field: 'price', oldValue: '€11', newValue: '€13' }] }, t as any)
    expect(r.title).toBe('线路 VAB 1 已更新')
    expect(r.diffs).toEqual([{ label: '价格', oldValue: '€11', newValue: '€13' }])
  })
  it('BUS_OFFLINE title, no diff', () => {
    const r = renderMessage('BUS_OFFLINE', { route: 'VAB 1' }, t as any)
    expect(r.title).toBe('线路 VAB 1 已下线')
    expect(r.diffs).toEqual([])
  })
  it('unknown code falls back', () => {
    const r = renderMessage('WHATEVER', {}, t as any)
    expect(r.title).toBe('您有一条新通知')
    expect(r.diffs).toEqual([])
  })
  it('unknown diff field uses raw field name', () => {
    const r = renderMessage('BUS_UPDATED', { route: 'X', changed: [{ field: 'weird', oldValue: 'a', newValue: 'b' }] }, t as any)
    expect(r.diffs[0].label).toBe('msg.field.weird') // t 兜底返回 key,前端展示原 key 亦可接受
  })
  it('TICKET_REPLIED title + ticket link', () => {
    const r = renderMessage('TICKET_REPLIED', { ticketId: 1042 } as any, t as any)
    expect(r.title).toBe('您的工单 #1042 有新回复')
    expect(r.diffs).toEqual([])
    expect(r.link).toBe('/tickets/1042')
  })
})
