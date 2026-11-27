import type { H3Event } from 'h3'

/**
 * 门户 RSS 2.0。
 *
 * <p>只输出最近 30 篇已发布文章的标题、链接、发布时间与摘要；
 * 正文不重复下发，避免每次抓取都放大数据库压力。</p>
 */

interface Site {
  siteName?: string
  seoDescription?: string | null
}

interface Article {
  articleId: string | number
  title: string
  summary?: string | null
  publishTime?: string | null
}

interface PageResult<T> {
  records: T[]
}

export default defineEventHandler(async (event: H3Event) => {
  const { siteUrl } = feedConfig(event)
  // 与 sitemap 一致：站点未配置时输出空频道（200），不把 5xx 暴露给订阅者
  const [site, latest] = await Promise.all([
    portalGetOr<Site>(event, '', undefined, {}),
    portalGetOr<PageResult<Article>>(event, '/articles', { pageNum: 1, pageSize: 30 }, { records: [] })
  ])

  const items = latest.records.map((article) => {
    const link = absoluteUrl(siteUrl, `/article/${article.articleId}`)
    const pubDate = rssDate(article.publishTime)
    return `<item>
<title>${escapeXml(article.title)}</title>
<link>${escapeXml(link)}</link>
<guid isPermaLink="true">${escapeXml(link)}</guid>
${pubDate ? `<pubDate>${escapeXml(pubDate)}</pubDate>` : ''}
<description>${escapeXml(article.summary || '')}</description>
</item>`
  })

  setHeader(event, 'content-type', 'application/rss+xml; charset=utf-8')
  setHeader(event, 'cache-control', 'public, max-age=300')
  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
<channel>
<title>${escapeXml(site.siteName || '凌霄门户')}</title>
<link>${escapeXml(siteUrl)}</link>
<description>${escapeXml(site.seoDescription || '')}</description>
<language>zh-CN</language>
${items.join('\n')}
</channel>
</rss>`
})
