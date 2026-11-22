/**
 * 图片地址解析。
 *
 * <p>后台 Logo / 封面存两种值：纯数字 `fileId` 或 http(s) 外链。
 * 数字走公开文件接口 `/api/file/{id}`，外链原样返回；其它协议一律忽略，
 * 防止后台脏数据把 `javascript:` 之类的地址带进页面。</p>
 */
export function mediaUrl(value?: string | null, size?: 'thumb' | 'medium'): string | null {
  if (!value) return null
  const text = String(value).trim()
  if (!text) return null
  if (/^https?:\/\//i.test(text)) return text
  if (/^\d+$/.test(text)) {
    return size ? `/api/file/${text}?size=${size}` : `/api/file/${text}`
  }
  if (text.startsWith('/api/file/')) return text
  return null
}
