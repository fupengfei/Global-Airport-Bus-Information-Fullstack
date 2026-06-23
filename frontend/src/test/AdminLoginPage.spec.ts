import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import zhCN from '../i18n/locales/zh-CN'

const push = vi.fn()
const replace = vi.fn()
const auth = {
  isAuthed: false,
  user: null as { role: string } | null,
  login: vi.fn(async () => {}),
  logout: vi.fn(async () => {}),
}
vi.mock('../stores/auth', () => ({ useAuth: () => auth }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace }),
  useRoute: () => ({ query: {} }),
}))

import AdminLoginPage from '../pages/admin/AdminLoginPage.vue'

function mountPage() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } })
  return mount(AdminLoginPage, { global: { plugins: [i18n] } })
}

describe('AdminLoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockClear(); replace.mockClear(); auth.login.mockClear(); auth.logout.mockClear()
    auth.isAuthed = false; auth.user = null
    auth.login.mockImplementation(async () => {})
  })

  it('只有登录表单,没有注册/找回 Tab', () => {
    const w = mountPage()
    expect(w.text()).toContain(zhCN.adminAuth.title)
    expect(w.text()).not.toContain(zhCN.auth.register)
    expect(w.text()).not.toContain(zhCN.auth.forgot)
  })

  it('管理员登录成功 → 跳 /admin', async () => {
    auth.login.mockImplementation(async () => { auth.user = { role: 'SUPER_ADMIN' } })
    const w = mountPage()
    await w.find('input[type=text]').setValue('admin')
    await w.find('input[type=password]').setValue('admin12345')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(auth.login).toHaveBeenCalledWith('admin', 'admin12345')
    expect(push).toHaveBeenCalledWith('/admin')
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('OPERATOR 登录成功 → 跳 /admin', async () => {
    auth.login.mockImplementation(async () => { auth.user = { role: 'OPERATOR' } })
    const w = mountPage()
    await w.find('input[type=text]').setValue('op')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(push).toHaveBeenCalledWith('/admin')
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('非管理员登录 → logout 并报无权限,不跳转', async () => {
    auth.login.mockImplementation(async () => { auth.user = { role: 'USER' } })
    const w = mountPage()
    await w.find('input[type=text]').setValue('zoe')
    await w.find('input[type=password]').setValue('password123')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(auth.logout).toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(w.find('.authErr').text()).toBe(zhCN.adminAuth.noPermission)
  })

  it('已登录的管理员打开本页 → 直接跳 /admin', async () => {
    auth.isAuthed = true
    auth.user = { role: 'SUPER_ADMIN' }
    mountPage()
    await flushPromises()
    expect(replace).toHaveBeenCalledWith('/admin')
  })

  it('凭证错误 → 显示本地化错误,不跳转', async () => {
    auth.login.mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { code: 'INVALID_CREDENTIALS', message: 'bad credentials', details: [], traceId: 't' } },
    })
    const w = mountPage()
    await w.find('input[type=text]').setValue('admin')
    await w.find('input[type=password]').setValue('wrong')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
    expect(w.find('.authErr').text()).toBe(zhCN.errors.INVALID_CREDENTIALS)
  })
})
