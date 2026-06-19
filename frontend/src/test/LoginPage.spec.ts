import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const loginFn = vi.fn(() => Promise.resolve())
vi.mock('../stores/auth', () => ({ useAuth: () => ({ login: loginFn, register: vi.fn() }) }))
vi.mock('../api/auth', () => ({ sendRegisterCode: vi.fn(), forgot: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

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

  it('submits login form', async () => {
    const w = mountLogin()
    await w.find('input[type=text]').setValue('zoe')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(loginFn).toHaveBeenCalledWith('zoe', 'password123')
  })
})
