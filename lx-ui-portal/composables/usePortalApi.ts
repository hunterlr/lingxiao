import type {
  ArticleQuery,
  PageResult,
  PortalArticleDetail,
  PortalArticleSummary,
  PortalBanner,
  PortalBlock,
  PortalCategory,
  PortalNav,
  PortalPage,
  PortalPreview,
  PortalSite,
  PortalTag
} from '~/types/portal'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

/**
 * 门户数据访问。
 *
 * <p>只调用 `/api/portal/cms/**`，不碰后台接口。请求经 Nitro 同源代理，
 * 因此 SSR 与浏览器都可以用相对地址。业务错误码 1004（站点 / 栏目 /
 * 文章不可见）统一转成 404，避免把内部错误细节暴露给访问者。</p>
 */
export function usePortalApi() {
  const config = useRuntimeConfig()
  const siteKey = config.public.siteKey
  const requestFetch = useRequestFetch()
  const base = `/api/portal/cms/sites/${encodeURIComponent(siteKey)}`

  async function request<T>(path: string, query?: Record<string, unknown>): Promise<T> {
    return requestUrl<T>(base + path, query)
  }

  async function requestUrl<T>(url: string, query?: Record<string, unknown>): Promise<T> {
    let response: ApiEnvelope<T>
    try {
      response = await requestFetch<ApiEnvelope<T>>(url, { query })
    } catch (cause) {
      const statusCode = (cause as { statusCode?: number })?.statusCode || 502
      throw createError({ statusCode, statusMessage: '内容服务暂时不可用' })
    }
    if (response.code !== 0) {
      throw createError({
        statusCode: response.code === 1004 ? 404 : 502,
        statusMessage: response.msg || '请求内容失败'
      })
    }
    return response.data
  }

  return {
    siteKey,
    fetchSite: () => request<PortalSite>(''),
    fetchCategories: () => request<PortalCategory[]>('/categories'),
    fetchTags: () => request<PortalTag[]>('/tags'),
    fetchArticles: (query: ArticleQuery = {}) =>
      request<PageResult<PortalArticleSummary>>('/articles', query as Record<string, unknown>),
    fetchArticle: (articleId: string | number) =>
      request<PortalArticleDetail>(`/articles/${encodeURIComponent(String(articleId))}`),
    fetchArticleBySlug: (slug: string) =>
      request<PortalArticleDetail>(`/articles/by-slug/${encodeURIComponent(slug)}`),
    fetchArticleBatch: (ids: Array<string | number>) => {
      if (!ids.length) return Promise.resolve<PortalArticleSummary[]>([])
      return request<PortalArticleSummary[]>('/articles/batch', { ids: ids.join(',') })
    },
    search: (query: ArticleQuery = {}) =>
      request<PageResult<PortalArticleSummary>>('/search', query as Record<string, unknown>),
    fetchPage: (pageKey: string) =>
      request<PortalPage>(`/pages/${encodeURIComponent(pageKey)}`),
    fetchNav: (navKey: string) =>
      request<PortalNav>(`/navs/${encodeURIComponent(navKey)}`),
    fetchBlock: (blockKey: string) =>
      request<PortalBlock>(`/blocks/${encodeURIComponent(blockKey)}`),
    fetchBanners: (groupKey = 'home') =>
      request<PortalBanner[]>('/banners', { groupKey }),
    /** 预览不经过 siteKey 路径：票据自身绑定站点与目标。 */
    fetchPreview: (ticket: string) =>
      requestUrl<PortalPreview>(`/api/portal/cms/preview/${encodeURIComponent(ticket)}`)
  }
}
