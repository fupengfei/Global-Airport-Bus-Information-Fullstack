import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin, type VueQueryPluginOptions } from '@tanstack/vue-query'
import type { AxiosError } from 'axios'
import './styles/tokens.css'        // 设计稿全局样式(由 design/styles.css 复制而来)
import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'

// VueQuery 默认 retry 3 次:查不存在的线路(404)或网络出错时,用户要干等
// 数秒(累计退避 ~7s)才看到错误态。这里:4xx 不重试(资源不存在重试无意义),
// 其余(5xx/网络)最多重试 1 次,让错误/空态快速呈现。
const vueQueryOptions: VueQueryPluginOptions = {
  queryClientConfig: {
    defaultOptions: {
      queries: {
        retry: (failureCount: number, error: unknown) => {
          const status = (error as AxiosError)?.response?.status
          if (status !== undefined && status >= 400 && status < 500) return false
          return failureCount < 1
        },
      },
    },
  },
}

createApp(App)
  .use(createPinia())
  .use(router)
  .use(i18n)
  .use(VueQueryPlugin, vueQueryOptions)   // 不挂 Element Plus(公开页用设计稿手写组件)
  .mount('#app')
