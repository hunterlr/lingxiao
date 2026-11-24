import type { H3Event } from 'h3'

/**
 * 门户 sitemap。
 *
 * <p>收录首页、栏目、标签与已发布文章；单页没有公开的“列表”接口，
 * 因此不在这里枚举（单页仍可通过站内链接访问，见 docs/29-portal-api）。
 * 文章最多读取 500 篇（10 页 × 50），超出部分不在 sitemap 内。</p>
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
  const categories = await portalGet<Category[]>(event, '/categories')
  const tags = await portalGet<Tag[]>(event, '/tags')

  const articles: Article[] = []
  for (let pageNum = 1; pageNum <= 10; pageNum += 1) {
    const result = await portalGet<PageResult<Article>>(event, '/articles', {
      pageNum,
      pageSize: 50
    })
    articles.push(...result.records)
    if (result.records.length < 50) break
  }

  const urls: string[] = [
    `<url><loc>${escapeXml(siteUrl)}</loc><changefreq>daily</changefreq><priority>1.0</priority></url>`
  ]
  for (const category of flatten(categories)) {
    urls.push(
      `<url><loc>${escapeXml(absoluteUrl(siteUrl, `/category/${encodeURIComponent(category.categoryKey)}`))}</loc></url>`
    )
  }
  for (const tag of tags) {
    urls.push(`<url><loc>${escapeXml(absoluteUrl(siteUrl, `/tag/${encodeURIComponent(tag.tagKey)}`))}</loc></url>`)
  }
  for (const article of articles) {
    const lastmod = article.publishTime ? `<lastmod>${escapeXml(String(article.publishTime).slice(0, 10))}</lastmod>` : ''
    urls.push(
      `<url><loc>${escapeXml(absoluteUrl(siteUrl, `/article/${article.articleId}`))}</loc>${lastmod}</url>`
    )
  }

  setHeader(event, 'content-type', 'application/xml; charset=utf-8')
  setHeader(event, 'cache-control', 'public, max-age=300')
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.join('\n')}
</urlset>`
})
