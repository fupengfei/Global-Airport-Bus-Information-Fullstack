import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import './styles/tokens.css'        // 设计稿全局样式(由 design/styles.css 复制而来)
import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(i18n)
  .use(VueQueryPlugin)               // 不挂 Element Plus(公开页用设计稿手写组件)
  .mount('#app')
