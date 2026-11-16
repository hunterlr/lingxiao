import { defineStore } from 'pinia'
import i18n, {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  isSupportedLocale,
  resolveInitialLocale
} from '@/i18n'

/**
 * 语言偏好。
 *
 * <p>与 {@code store/app.js} 的主题一样，语言是“个人偏好”，
 * 存 localStorage、即时生效，不占数据库字段。</p>
 *
 * <p>为什么状态要放 Pinia：顶栏下拉、Element Plus 的 locale、
 * 请求头都要读同一个值；只放 i18n 实例里的话，组件拿不到响应式状态。</p>
 */
export const useLocaleStore = defineStore('locale', {
  state: () => ({
    locale: resolveInitialLocale()
  }),

  getters: {
    isEnglish: (state) => state.locale === 'en-US',
    /** 顶栏按钮上显示的短标签，如“中” / “EN” */
    shortLabel: (state) => (state.locale === 'en-US' ? 'EN' : '中')
  },

  actions: {
    /** 应用启动时调用一次：把初始语言写回 i18n 与 html.lang */
    init() {
      this.apply(this.locale, false)
    },

    /** 顶栏切换：立即生效并持久化 */
    setLocale(locale) {
      this.apply(locale, true)
    },

    apply(locale, persist) {
      const next = isSupportedLocale(locale) ? locale : DEFAULT_LOCALE
      this.locale = next
      i18n.global.locale.value = next
      document.documentElement.lang = next
      if (persist) {
        localStorage.setItem(LOCALE_STORAGE_KEY, next)
      }
    }
  }
})
