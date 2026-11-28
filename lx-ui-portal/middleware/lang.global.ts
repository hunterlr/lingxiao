/**
 * 语言路由守卫（全局）。
 *
 * <p>path / query 模式下把 URL 里的语言写进 `portal-lang` state；默认语言带了前缀时
 * 301 到不带前缀的地址，保证同一内容只有一个规范 URL。</p>
 *
 * <p>cookie 模式什么都不做：语言由 Cookie 决定，URL 不参与。</p>
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const { config, loadConfig, lang } = usePortalI18n()
  await loadConfig()
  const current = config.value

  if (current.mode === 'path') {
    const segments = to.path.split('/').filter(Boolean)
    const first = segments[0]
    if (!first) return
    const matched = current.languages.find(
      (item) => item.urlCode.toLowerCase() === first.toLowerCase()
    )
    if (!matched) return
    if (matched.code === current.defaultLang && !current.defaultLangInPath) {
      const rest = '/' + segments.slice(1).join('/')
      return navigateTo(rest === '/' ? '/' : rest, { redirectCode: 301 })
    }
    lang.value = matched.code
    return
  }

  if (current.mode === 'query') {
    const value = to.query.lang
    if (typeof value !== 'string' || !value) return
    const matched = current.languages.find(
      (item) =>
        item.urlCode.toLowerCase() === value.toLowerCase() ||
        item.code.toLowerCase() === value.toLowerCase()
    )
    if (matched) lang.value = matched.code
  }
})