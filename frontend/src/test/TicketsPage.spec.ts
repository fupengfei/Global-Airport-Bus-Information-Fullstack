import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import zh from '../i18n/locales/zh-CN'
import TicketsPage from '../pages/TicketsPage.vue'

vi.mock('../api/tickets', () => ({
  listTickets: vi.fn(() => Promise.resolve([
    { id: 1, status: 'REPLIED', relatedSourceId: 'vie-vab1', lastReplyAt: '2026-06-20', createdAt: '2026-06-19' },
  ])),
  getTicket: vi.fn(() => Promise.resolve({
    ticket: { id: 1, status: 'REPLIED', relatedSourceId: 'vie-vab1' },
    replies: [
      { id: 1, authorType: 'USER', body: '价格变了', createdAt: '2026-06-19' },
      { id: 2, authorType: 'ADMIN', body: '已更新', createdAt: '2026-06-20' },
    ],
  })),
  createTicket: vi.fn(() => Promise.resolve({ ticket: { id: 2, status: 'OPEN' }, replies: [] })),
  replyTicket: vi.fn(() => Promise.resolve({ ticket: { id: 1, status: 'OPEN' }, replies: [] })),
  closeTicket: vi.fn(() => Promise.resolve({ id: 1, status: 'CLOSED' })),
}))
import * as api from '../api/tickets'

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zh } })
const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('TicketsPage', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('renders my tickets list with status badge', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    expect(api.listTickets).toHaveBeenCalled()
    expect(w.text()).toContain('已回复')
  })
  it('submitting new ticket calls createTicket', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    await w.find('[data-test="new-body"]').setValue('请新增一条线路')
    await w.find('[data-test="new-submit"]').trigger('click')
    expect(api.createTicket).toHaveBeenCalledWith({ sourceId: undefined, body: '请新增一条线路' })
  })
  it('opening a ticket loads thread', async () => {
    const w = mount(TicketsPage, { global: { plugins: [i18n], stubs } })
    await flushPromises()
    await w.find('[data-test="open-1"]').trigger('click')
    await flushPromises()
    expect(api.getTicket).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('已更新')
  })
})

async function flushPromises() { await new Promise((r) => setTimeout(r)) }
