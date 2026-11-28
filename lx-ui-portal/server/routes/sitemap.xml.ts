import type { H3Event } from 'h3'

/**
 * 门户 sitemap。
 *
 * <p>收录首页、栏目、标签与已发布文章；单页没有公开的“列表”接口，
 * 因此不在这里枚举（单页仍可通过站内链接访问，见 docs/29-portal-api）。
 * 文章最多读取 500 篇（10 页 × 50），超出部分不在 sitemap 内。</p>
 *
 * <p><b>多语言：</b>path 模式下每个地址按语言各出一条 `&lt;url&gt;`，并用
 * `xhtml:link` 互相标注 hreflang（含 x-default）；query / cookie 模式只出一条。</p>
 */

interface Site {
  siteName?: string
}

interface Category {
  categoryKey: string
  children?: Category[]
}

interface Tag {
  tagKey: string
}

interface Article {
  articleId: string | number
  publishTime?: string | null
}

interface PageResult<T> {
  records: T[]
}

function flatten(nodes: Category[] = []): Category[] {
  const result: Category[] = []
  for (const node of nodes) {
    result.push(node)
    if (node.children?.length) result.push(...flatten(node.children))
  }
  return result
}

export default defineEventHandler(async (event: H3Event) => {
  const { siteUrl } = feedConfig(event)
  const langConfig = await portalLangConfig(event)
  // 站点还没建好时返回空 sitemap（200），而不是 502：爬虫会把 5xx 当成整站故障
  const categories = await portalGetOr<Category[]>(event, '/categories', undefined, [])
  const tags = await portalGetOr<Tag[]>(event, '/tags', undefined, [])

  const articles: Article[] = []
  for (let pageNum = 1; pageNum <= 10; pageNum += 1) {
    const result = await portalGetOr<PageResult<Article>>(
      event,
      '/articles',
      { pageNum, pageSize: 50 },
      { records: [] }
    )
    articles.push(...result.records)
    if (result.records.length < 50) break
  }

  /** 一条地址的所有语言版本，带 hreflang 互指。 */
  function localizedUrls(path: string, extra = ''): string {
    const codes = langConfig.mode === 'path' ? langConfig.languages.map((item) => item.code) : [langConfig.defaultLang]
    const alternates = codes
      .map((code) => {
        const href = localizeUrl(langConfig, siteUrl, path, code)
        return `<xhtml:link rel="alternate" hreflang="${code}" href="${escapeXml(href)}"/>`
      })
      .join('')
    const defaultHref = localizeUrl(langConfig, siteUrl, path, langConfig.defaultLang)
    const withDefault = alternates + (alternates ? `<xhtml:link rel="alternate" hreflang="x-default" href="${escapeXml(defaultHref)}"/>` : '')
    return codes
      .map((code) => {
        const href = localizeUrl(langConfig, siteUrl, path, code)
        return `<url><loc>${escapeXml(href)}</loc>${extra}${withDefault}</url>`
      })
      .join('\n')
  }

  const urls: string[] = [localizedUrls('/', '<changefreq>daily</changefreq><priority>1.0</priority>')]
  for (const category of flatten(categories)) {
    urls.push(localizedUrls(`/category/${encodeURIComponent(category.categoryKey)}`))
  }
  for (const tag of tags) {
    urls.push(localizedUrls(`/tag/${encodeURIComponent(tag.tagKey)}`))
  }
  for (const article of articles) {
    const lastmod = article.publishTime
      ? `<lastmod>${escapeXml(String(article.publishTime).slice(0, 10))}</lastmod>`
      : ''
    urls.push(localizedUrls(`/article/${article.articleId}`, lastmod))
  }

  setHeader(event, 'content-type', 'application/xml; charset=utf-8')
  setHeader(event, 'cache-control', 'public, max-age=300')
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urls.join('\n')}
</urlset>`
})
