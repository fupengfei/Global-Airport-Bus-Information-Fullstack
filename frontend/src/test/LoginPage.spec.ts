import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const loginFn = vi.fn(() => Promise.resolve())
const registerFn = vi.fn(() => Promise.resolve())
vi.mock('../stores/auth', () => ({ useAuth: () => ({ login: loginFn, register: registerFn }) }))
vi.mock('../api/auth', () => ({ sendRegisterCode: vi.fn(), forgot: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))

import LoginPage from '../pages/LoginPage.vue'

function mountLogin() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(LoginPage, { global: { plugins: [i18n] } })
}

describe('LoginPage', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('switches to register tab and shows code field', async () => {
    const w = mountLogin()
    await w.findAll('.tabs button')[1].trigger('click')
    expect(w.text()).toContain(zhCN.auth.code)
  })

  it('shows a localized error (not the raw backend string) when register code is wrong', async () => {
    registerFn.mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { code: 'INVALID_CODE', message: 'bad code', details: [], traceId: 't' } },
    })
    const w = mountLogin()
    await w.findAll('.tabs button')[1].trigger('click')
    await w.find('input[type=email]').setValue('zoe@example.com')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(w.find('.authErr').text()).toBe(zhCN.errors.INVALID_CODE)
    expect(w.text()).not.toContain('bad code')
  })

  it('submits login form', async () => {
    const w = mountLogin()
    await w.find('input[type=text]').setValue('zoe')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(loginFn).toHaveBeenCalledWith('zoe', 'password123')
  })
})
