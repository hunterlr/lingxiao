/**
 * 门户接口数据类型。
 *
 * <p>ID 在 JSON 里按字符串返回（雪花 ID 超出 JS 安全整数），
 * 所以统一用 string；数字运算前先 Number()。</p>
 */

export interface PortalSite {
  siteKey: string
  siteName: string
  domain?: string | null
  logo?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
}

export interface PortalTag {
  tagId: string
  tagKey: string
  tagName: string
}

export interface PortalCategory {
  categoryId: string
  parentId?: string | null
  categoryKey: string
  categoryName: string
  slug?: string | null
  path?: string | null
  template?: string | null
  icon?: string | null
  cover?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
  sort?: number | null
  children?: PortalCategory[]
}

export interface PortalArticleSummary {
  articleId: string
  title: string
  slug?: string | null
  summary?: string | null
  cover?: string | null
  authorName?: string | null
  source?: string | null
  publishTime?: string | null
  viewCount?: number | string | null
  categoryId?: string | null
  categoryKey?: string | null
  categoryName?: string | null
  tags?: PortalTag[]
}

export interface PortalArticleDetail extends PortalArticleSummary {
  /** 服务端已清洗的 HTML，门户直接渲染 */
  content?: string | null
  sourceUrl?: string | null
  category?: PortalCategory | null
}

export interface PageResult<T> {
  total: number | string
  pageNum: number | string
  pageSize: number | string
  records: T[]
}

export interface ArticleQuery {
  pageNum?: number
  pageSize?: number
  categoryId?: string | number
  categoryKey?: string
  tagId?: string | number
  tagKey?: string
  keyword?: string
}
