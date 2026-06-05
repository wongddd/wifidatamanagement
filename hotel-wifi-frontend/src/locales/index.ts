import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'

// 获取浏览器语言
const savedLocale = localStorage.getItem('locale') || navigator.language?.split('-')[0] || 'zh'

const i18n = createI18n({
  legacy: false,           // Composition API 模式
  locale: savedLocale === 'en' ? 'en' : 'zh',
  fallbackLocale: 'zh',
  messages: {
    zh: zhCN,
    en: enUS,
  },
})

export default i18n
