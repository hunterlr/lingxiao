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

/** 读取门户公开数据；业务错误统一转 502，避免把内部细节写进 XML。 */
export async function portalGet<T>(
  event: H3Event,
  path: string,
  query?: Record<string, unknown>
): Promise<T> {
  const { backendBase, siteKey } = feedConfig(event)
  const url = `${backendBase}/api/portal/cms/sites/${encodeURIComponent(siteKey)}${path}`
  const response = await $fetch<ApiEnvelope<T>>(url, { query })
  if (response.code !== 0) {
    throw createError({ statusCode: 502, statusMessage: '内容服务返回错误' })
  }
  return response.data
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
