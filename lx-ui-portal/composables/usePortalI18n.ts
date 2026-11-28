import en from '~/i18n/en-US'
import zh from '~/i18n/zh-CN'

/**
 * 门户多语言。
 *
 * <p><b>语言从哪来：</b>由后台参数决定（`portal.i18n.mode`）：</p>
 * <ul>
 *   <li>{@code path}：`/en/article/1`，SEO 最好（默认）；</li>
 *   <li>{@code query}：`/article/1?lang=en`，兼容旧部署；</li>
 *   <li>{@code cookie}：URL 不变，只认 Cookie。</li>
 * </ul>
 *
 * <p><b>文案优先級：</b>后台已发布语言包 > 仓库内置文案；缺键回退中文，
 * 再缺返回键名，便于排查。</p>
 *
 * <p><b>内容翻译：</b>{@link contentLang} 只在“当前语言不是默认语言”时返回语言代码，
 * 供 CMS 接口带上 `lang` 取译文；默认语言传空，避免把主表内容当成“缺翻译”。</p>
 */

export const DEFAULT_PORTAL_LANG = 'zh-CN'

export interface PortalLangOption {
  code: string
  urlCode: string
  name: string
  nativeName: string
  direction: string
  isDefault: boolean
}

export interface PortalLangConfig {
  mode: 'path' | 'query' | 'cookie'
  defaultLang: string
  defaultLangInPath: boolean
  languages: PortalLangOption[]
}

const LANG_COOKIE = 'lx-portal-lang'
const BUILT_IN: Record<string, Record<string, string>> = {
  'zh-CN': zh,
  'en-US': en
}

const FALLBACK_CONFIG: PortalLangConfig = {
  mode: 'path',
  defaultLang: DEFAULT_PORTAL_LANG,
  defaultLangInPath: false,
  languages: [
    { code: 'zh-CN', urlCode: 'zh-cn', name: '简体中文', nativeName: '简体中文', direction: 'ltr', isDefault: true },
    { code: 'en-US', urlCode: 'en', name: 'English', nativeName: 'English', direction: 'ltr', isDefault: false }
  ]
}

/** 只认两种内置语言；`en` / `zh` 这类简写归一到完整 locale。 */
export function normalizeLang(value?: string | null): string {
  const raw = String(value || '').trim()
  if (!raw) return DEFAULT_PORTAL_LANG
  const lower = raw.toLowerCase()
  if (lower === 'en' || lower === 'en-us' || lower === 'en_us') return 'en-US'
  if (lower === 'zh' || lower === 'zh-cn' || lower === 'zh_cn') return 'zh-CN'
  return DEFAULT_PORTAL_LANG
}

export function usePortalI18n() {
  const route = useRoute()
  const cookie = useCookie<string | null>(LANG_COOKIE, {
    path: '/',
    sameSite: 'lax',
    maxAge: 365 * 24 * 60 * 60
  })
  const config = useState<PortalLangConfig>('portal-lang-config', () => FALLBACK_CONFIG)
  const lang = useState<string>('portal-lang', () => normalizeLang(cookie.value))
  const overrides = useState<Record<string, string>>('portal-i18n-overrides', () => ({}))
  const loadedFor = useState<string>('portal-i18n-loaded-for', () => '')
  const configLoaded = useState<boolean>('portal-lang-config-loaded', () => false)
  const requestFetch = useRequestFetch()

  /** 语言配置里的选项，供页头切换。 */
  const languages = computed(() =>
    config.value.languages.map((item) => ({
      code: item.code,
      label: item.nativeName || item.name,
      urlCode: item.urlCode
    }))
  )

  /** 内容语言：默认语言传空，避免把主表当成“缺翻译”。 */
  const contentLang = computed(() =>
    lang.value === config.value.defaultLang ? undefined : lang.value
  )

  function urlCodeOf(code: string): string {
    const matched = config.value.languages.find((item) => item.code === code)
    return matched ? matched.urlCode : code.toLowerCase()
  }

  function codeOfUrl(urlCode: string): string | null {
    const matched = config.value.languages.find(
      (item) => item.urlCode.toLowerCase() === urlCode.toLowerCase()
    )
    return matched ? matched.code : null
  }

  /** 取文案；缺键回退中文，再缺就把键名原样返回，方便排查。 */
  function t(key: string, params?: Record<string, string | number>): string {
    const dict = BUILT_IN[lang.value] || BUILT_IN[DEFAULT_PORTAL_LANG]
    const raw = overrides.value[key] ?? dict[key] ?? BUILT_IN[DEFAULT_PORTAL_LANG][key] ?? key
    if (!params) return raw
    return raw.replace(/\{(\w+)\}/g, (match, name: string) =>
      params[name] === undefined || params[name] === null ? match : String(params[name])
    )
  }

  /**
   * 从当前 URL 读语言（path / query 模式）。
   *
   * <p>path 模式下 `/en/...` 的第一段是语言短码；`/zh-cn/...` 同理。
   * 未知短码不在这里报 404——上层按“没匹配到就是默认语言”处理，
   * 真正的 404 交给页面数据（栏目 / 文章不存在）。</p>
   */
  function langFromUrl(): string {
    const mode = config.value.mode
    if (mode === 'query') {
      const value = route.query.lang
      if (typeof value === 'string' && value) {
        return codeOfUrl(value) || normalizeLang(value)
      }
      return ''
    }
    if (mode === 'path') {
      const segment = String(route.path || '').split('/').filter(Boolean)[0]
      if (!segment) return ''
      const code = codeOfUrl(segment)
      if (code) return code
    }
    return ''
  }

  /** SSR 拉配置；失败用内置默认（path / zh-CN）。 */
  async function loadConfig(): Promise<void> {
    if (configLoaded.value) return
    try {
      const response = await requestFetch<{ code: number; data?: PortalLangConfig }>(
        '/api/portal/i18n/config'
      )
      if (response?.code === 0 && response.data?.languages?.length) {
        config.value = response.data
      }
    } catch {
      config.value = FALLBACK_CONFIG
    } finally {
      configLoaded.value = true
    }
  }

  /** 应用当前 URL / Cookie 决定的语言；layout 在 SSR 里调用一次。 */
  async function applyLangFromUrl(): Promise<void> {
    await loadConfig()
    const fromUrl = langFromUrl()
    if (fromUrl) {
      lang.value = fromUrl
      cookie.value = fromUrl
      return
    }
    const fromCookie = normalizeLang(cookie.value)
    const allowed = config.value.languages.some((item) => item.code === fromCookie)
    lang.value = allowed ? fromCookie : config.value.defaultLang
  }

  /** SSR 拉一次后台已发布语言包；换语言后重新拉。 */
  async function loadPack(): Promise<void> {
    if (loadedFor.value === lang.value) return
    try {
      const response = await requestFetch<{
        code: number
        data?: { messages?: Record<string, string> }
      }>(`/api/portal/i18n/${encodeURIComponent(lang.value)}/portal`)
      if (response?.code === 0 && response.data?.messages) {
        overrides.value = { ...response.data.messages }
      } else {
        overrides.value = {}
      }
    } catch {
      overrides.value = {}
    } finally {
      loadedFor.value = lang.value
    }
  }

  /** 把当前路径换成目标语言的地址（保留后续路径与查询串）。 */
  function pathForLang(target: string): string {
    const mode = config.value.mode
    const segments = String(route.path || '/').split('/').filter(Boolean)
    const firstIsLang = segments.length > 0 && codeOfUrl(segments[0]) !== null
    const rest = firstIsLang ? segments.slice(1) : segments
    const suffix = rest.length ? `/${rest.join('/')}` : ''

    if (mode === 'path') {
      const isDefault = target === config.value.defaultLang
      if (isDefault && !config.value.defaultLangInPath) return suffix || '/'
      return `/${urlCodeOf(target)}${suffix}`
    }
    const query = { ...route.query }
    delete query.lang
    if (target === config.value.defaultLang) {
      delete query.lang
    } else {
      query.lang = urlCodeOf(target)
    }
    return { path: suffix || '/', query }
  }

  /**
   * 切换语言。
   *
   * <p>path / query 模式直接跳转到目标地址（保留当前页）；cookie 模式写 Cookie
   * 后整页刷新。切换后重新拉语言包，避免文案还是上一种语言。</p>
   */
  async function setLang(next: string): Promise<void> {
    const target = normalizeLang(next)
    cookie.value = target
    const destination = pathForLang(target)
    overrides.value = {}
    loadedFor.value = ''
    await navigateTo(destination, { replace: false })
  }

  return {
    lang,
    t,
    languages,
    contentLang,
    config,
    loadConfig,
    applyLangFromUrl,
    loadPack,
    setLang
  }
}