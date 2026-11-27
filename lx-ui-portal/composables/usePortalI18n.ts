import en from '~/i18n/en-US'
import zh from '~/i18n/zh-CN'

/**
 * 门户多语言。
 *
 * <p><b>优先级：</b>后台已发布语言包（`/api/portal/i18n/{lang}/portal`）>
 * 仓库内置文案（`i18n/*.ts`）。i18n 模块关闭或该键没发布时，页面仍能完整渲染。</p>
 *
 * <p><b>语言选择：</b>Cookie `lx-portal-lang`，SSR 首屏按它渲染；没有 Cookie 时
 * 默认 `zh-CN`（不猜 `Accept-Language`，避免同一链接对不同人渲染不同内容，
 * 影响缓存与 SEO）。</p>
 *
 * <p><b>为什么没有 URL 前缀：</b>这一刀只做文案；`/en/` 路由、hreflang 与
 * 分语言 sitemap 在下一刀统一处理（见 `docs/13` §12.34）。</p>
 */

export const PORTAL_LANGUAGES = [
  { code: 'zh-CN', label: '中文' },
  { code: 'en-US', label: 'English' }
] as const

export const DEFAULT_PORTAL_LANG = 'zh-CN'

const LANG_COOKIE = 'lx-portal-lang'
const BUILT_IN: Record<string, Record<string, string>> = {
  'zh-CN': zh,
  'en-US': en
}

/** 只认内置语言；`en` / `zh` 这类简写归一到完整 locale。 */
export function normalizeLang(value?: string | null): string {
  const raw = String(value || '').trim()
  if (!raw) return DEFAULT_PORTAL_LANG
  const lower = raw.toLowerCase()
  if (lower === 'en' || lower === 'en-us' || lower === 'en_us') return 'en-US'
  if (lower === 'zh' || lower === 'zh-cn' || lower === 'zh_cn') return 'zh-CN'
  return DEFAULT_PORTAL_LANG
}

export function usePortalI18n() {
  const cookie = useCookie<string | null>(LANG_COOKIE, {
    path: '/',
    sameSite: 'lax',
    maxAge: 365 * 24 * 60 * 60
  })
  const lang = useState<string>('portal-lang', () => normalizeLang(cookie.value))
  const overrides = useState<Record<string, string>>('portal-i18n-overrides', () => ({}))
  const loaded = useState<boolean>('portal-i18n-loaded', () => false)
  const requestFetch = useRequestFetch()

  /** 取文案；缺键回退中文，再缺就把键名原样返回，方便排查。 */
  function t(key: string, params?: Record<string, string | number>): string {
    const dict = BUILT_IN[lang.value] || BUILT_IN[DEFAULT_PORTAL_LANG]
    const raw = overrides.value[key] ?? dict[key] ?? BUILT_IN[DEFAULT_PORTAL_LANG][key] ?? key
    if (!params) return raw
    return raw.replace(/\{(\w+)\}/g, (match, name: string) =>
      params[name] === undefined || params[name] === null ? match : String(params[name])
    )
  }

  /** SSR 拉一次后台已发布语言包；拿不到就用内置文案，不阻断渲染。 */
  async function loadPack(): Promise<void> {
    if (loaded.value) return
    try {
      const response = await requestFetch<{
        code: number
        data?: { messages?: Record<string, string> }
      }>(`/api/portal/i18n/${encodeURIComponent(lang.value)}/portal`)
      if (response?.code === 0 && response.data?.messages) {
        overrides.value = { ...response.data.messages }
      }
    } catch {
      // i18n 模块关闭 / 后端未启动：保持内置文案
      overrides.value = {}
    } finally {
      loaded.value = true
    }
  }

  /**
   * 切换语言：写 Cookie 后整页刷新。
   *
   * <p>不做客户端局部替换：文案散落在 SSR 生成的 HTML 与 `<head>` 里，
   * 整页刷新最省事也最不容易出现“正文换了、标题没换”。下一刀加 URL 前缀后
   * 改为跳转 `/{lang}/...`。</p>
   */
  function setLang(next: string): void {
    const normalized = normalizeLang(next)
    cookie.value = normalized
    lang.value = normalized
    loaded.value = false
    if (import.meta.client) {
      window.location.reload()
    }
  }

  return { lang, t, loadPack, setLang, languages: PORTAL_LANGUAGES }
}