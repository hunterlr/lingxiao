import type { H3Event } from 'h3'

/**
 * sitemap / robots / RSS 的公共读取逻辑。
 *
 * <p>这三个文件是纯只读聚合，直接访问 Spring Boot 的门户接口，
 * 不经过数据库、不新增后端端点；站点标识与对外地址沿用 nuxt.config
 * 的运行时配置（NUXT_PUBLIC_SITE_KEY / NUXT_PUBLIC_SITE_URL）。</p>
 */

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

export function feedConfig(event: H3Event) {
  const config = useRuntimeConfig(event)
  return {
    backendBase: String(config.backendBase || '').replace(/\/$/, ''),
    siteKey: String(config.public.siteKey || 'main'),
    siteUrl: String(config.public.siteUrl || '').replace(/\/$/, '')
  }
}

/** 读取门户公开数据；内容不可见（1004）转 404，其余业务错误转 502。 */
export async function portalGet<T>(
  event: H3Event,
  path: string,
  query?: Record<string, unknown>
): Promise<T> {
  const { backendBase, siteKey } = feedConfig(event)
  const url = `${backendBase}/api/portal/cms/sites/${encodeURIComponent(siteKey)}${path}`
  const response = await $fetch<ApiEnvelope<T>>(url, { query })
  if (response.code !== 0) {
    // 1004 = 站点 / 栏目 / 内容不可见。sitemap 与 RSS 不能因此整站 502：
    // 运维还没建站点时，爬虫应该拿到一个空订阅源，而不是“服务故障”。
    throw createError({
      statusCode: response.code === 1004 ? 404 : 502,
      statusMessage: '内容服务返回错误'
    })
  }
  return response.data
}

/**
 * 读公开数据；内容不可见时用兜底值。
 *
 * <p>只吞 404（站点未配置 / 内容未发布），其它错误照常抛出，
 * 避免把后端故障伪装成“空内容”。</p>
 */
export async function portalGetOr<T>(
  event: H3Event,
  path: string,
  query: Record<string, unknown> | undefined,
  fallback: T
): Promise<T> {
  try {
    return await portalGet<T>(event, path, query)
  } catch (error) {
    if ((error as { statusCode?: number })?.statusCode === 404) {
      return fallback
    }
    throw error
  }
}

/** XML 文本转义：标题、摘要、URL 都可能包含 & < > " '。 */
export function escapeXml(value?: string | number | null): string {
  if (value == null) return ''
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/** 拼接对外绝对地址；path 以 / 开头。 */
export function absoluteUrl(siteUrl: string, path: string): string {
  return `${siteUrl}${path.startsWith('/') ? path : `/${path}`}`
}

/** 后端返回 `yyyy-MM-ddTHH:mm:ss`，RSS 需要 RFC 822 格式。 */
export function rssDate(value?: string | null): string | null {
  if (!value) return null
  const date = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date.toUTCString()
}
