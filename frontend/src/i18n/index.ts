import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'
import de from './locales/de'

const saved = localStorage.getItem('locale')
const fallback = navigator.language.startsWith('zh') ? 'zh-CN' : 'en'

export const i18n = createI18n({
  legacy: false,
  locale: saved ?? fallback,
  fallbackLocale: 'en',
  messages: { 'zh-CN': zhCN, en, de },
})

export function setLocale(locale: 'zh-CN' | 'en' | 'de') {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
}
