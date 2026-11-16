import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

/**
 * 前端多语言装配。
 *
 * <p>设计约定（与 docs/23 一致）：</p>
 * <ul>
 *   <li>{@code zh-CN} 是默认语言与兜底语言：缺翻译时退回中文，绝不显示 {@code menu.xxx} 这种键名；</li>
 *   <li>语言只存 {@code localStorage['lx-admin-locale']}，不新增数据库列；</li>
 *   <li>切换语言后 Element Plus 组件文案通过 {@code ElConfigProvider} 一起变；</li>
 *   <li>请求头 {@code X-Lang} 由 {@code api/request.js} 读取当前语言后带上，后端据此翻译 msg。</li>
 * </ul>
 *
 * <p>这里的语言标签只用 {@code zh-CN} / {@code en-US} 两种，
 * 与后端 {@code LingxiaoLocaleResolver} 的白名单保持一致。</p>
 */

/** 语言持久化键，改名会让老用户的语言偏好丢失。 */
export const LOCALE_STORAGE_KEY = 'lx-admin-locale'

/** 默认语言：中文。 */
export const DEFAULT_LOCALE = 'zh-CN'

/** 顶栏语言下拉的数据源；顺序即展示顺序。 */
export const SUPPORTED_LOCALES = [
  { value: 'zh-CN', label: '简体中文', short: '中' },
  { value: 'en-US', label: 'English', short: 'EN' }
]

export function isSupportedLocale(locale) {
  return SUPPORTED_LOCALES.some((item) => item.value === locale)
}

/**
 * 初始语言：localStorage → 浏览器语言 → zh-CN。
 *
 * <p>浏览器是 {@code en-GB} 这类英文变体时也归到 {@code en-US}，
 * 其它语言（日文、法文…）一律回退中文——目前只维护两套文案。</p>
 */
export function resolveInitialLocale() {
  try {
    const saved = localStorage.getItem(LOCALE_STORAGE_KEY)
    if (isSupportedLocale(saved)) return saved
  } catch (e) {
    // 隐私模式下 localStorage 可能不可读，忽略即可
  }
  const browser = (navigator.language || '').toLowerCase()
  return browser.startsWith('en') ? 'en-US' : DEFAULT_LOCALE
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  },
  // 缺 key 时静默回退：产品要求“不白屏、不显示键名”，
  // 覆盖率由 scripts/check-i18n-keys.mjs 在开发期兜住。
  missingWarn: false,
  fallbackWarn: false
})

export default i18n
