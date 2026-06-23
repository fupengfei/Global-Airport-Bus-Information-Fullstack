import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import AdminLayout from '../components/admin/AdminLayout.vue'

describe('AdminLayout', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('renders the three nav entries', () => {
    const wrapper = mount(AdminLayout, {
      global: {
        stubs: { 'router-link': RouterLinkStub, 'router-view': { template: '<div />' } },
      },
    })
    const text = wrapper.text()
    expect(text).toContain('概览')
    expect(text).toContain('订阅统计')
    expect(text).toContain('热度榜单')
    expect(text).toContain('巴士维护')
    expect(text).toContain('操作记录')
  })

  it('shows a logout button', () => {
    const wrapper = mount(AdminLayout, {
      global: {
        stubs: { 'router-link': RouterLinkStub, 'router-view': { template: '<div />' } },
      },
    })
    expect(wrapper.text()).toContain('退出登录')
  })
})
