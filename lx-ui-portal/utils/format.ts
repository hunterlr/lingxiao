/**
 * 时间与数字格式化。
 *
 * <p>后端时间是 `yyyy-MM-dd'T'HH:mm:ss` 形式的本地时间，不带时区。
 * 不用 `new Date()` 解析，避免 SSR（服务器时区）与浏览器时区不一致导致
 * 水合前后文案不同。</p>
 */
export function formatDate(value?: string | null): string {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

export function formatDay(value?: string | null): string {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 10)
}

export function formatCount(value?: number | string | null): string {
  const count = Number(value ?? 0)
  if (!Number.isFinite(count) || count <= 0) return '0'
  if (count >= 10000) return `${(count / 10000).toFixed(1)} 万`
  return String(count)
}
