import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from '../i18n/locales/zh-CN'
import * as api from '../api/corrections'
import ReportModal from '../components/ReportModal.vue'

vi.mock('../api/corrections', () => ({ submitCorrection: vi.fn(() => Promise.resolve({ id: 1, status: 'OPEN' })) }))
const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
const mountModal = () => mount(ReportModal, { props: { sourceId: 'vie-vab1' }, global: { plugins: [i18n] } })

describe('ReportModal', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('opens on trigger click', async () => {
    const w = mountModal()
    expect(w.find('.overlay.open').exists()).toBe(false)
    await w.find('[data-test=report-trigger]').trigger('click')
    expect(w.find('.overlay.open').exists()).toBe(true)
  })
  it('blocks submit when description empty', async () => {
    const w = mountModal()
    await w.find('[data-test=report-trigger]').trigger('click')
    await w.find('[data-test=report-submit]').trigger('click')
    expect(api.submitCorrection).not.toHaveBeenCalled()
  })
  it('submits with description + sourceId then shows sent', async () => {
    const w = mountModal()
    await w.find('[data-test=report-trigger]').trigger('click')
    await w.find('[data-test=report-desc]').setValue('末班车是23:30')
    await w.find('[data-test=report-submit]').trigger('click')
    await new Promise((r) => setTimeout(r))
    expect(api.submitCorrection).toHaveBeenCalledWith({ sourceId: 'vie-vab1', description: '末班车是23:30', contact: '' })
    expect(w.text()).toContain('已收到')
  })
})
