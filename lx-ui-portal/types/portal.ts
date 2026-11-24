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
  /** 门户主题编码：default / magazine / minimal */
  theme?: string | null
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
  /** 详情模板编码：detail.default / detail.news / detail.gallery */
  template?: string | null
  /** 模型扩展字段，值形态由字段类型决定 */
  ext?: Record<string, unknown> | null
}

export interface PortalPage {
  pageId: string
  pageKey: string
  pageName: string
  title?: string | null
  summary?: string | null
  cover?: string | null
  content?: string | null
  template?: string | null
  seoTitle?: string | null
  seoKeywords?: string | null
  seoDescription?: string | null
  publishTime?: string | null
  ext?: Record<string, unknown> | null
}

export interface PortalNavItem {
  itemId: string
  itemName: string
  targetType: string
  targetId?: string | null
  url: string
  icon?: string | null
  sort?: number | null
  children?: PortalNavItem[]
}

export interface PortalNav {
  navId: string
  navKey: string
  navName: string
  position?: string | null
  items: PortalNavItem[]
}

export interface PortalBlock {
  blockId: string
  blockKey: string
  blockName: string
  blockType: string
  content?: string | null
  cover?: string | null
  linkUrl?: string | null
  sort?: number | null
}

export interface PortalBanner {
  bannerId: string
  bannerGroup: string
  title?: string | null
  image?: string | null
  linkUrl?: string | null
  sort?: number | null
}

export interface PortalPreview {
  /** article / page / category */
  targetType: string
  /** 票据覆盖的模板编码；空值回退目标自身模板 */
  template?: string | null
  article?: PortalArticleDetail | null
  page?: PortalPage | null
  /** targetType=category 时的栏目与已发布文章 */
  category?: PortalCategory | null
  articles?: PortalArticleSummary[] | null
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
