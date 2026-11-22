/**
 * 素材地址解析。
 *
 * <p>后端只保存两种值：文件中心的 fileId（纯数字）或 http(s) 外链。
 * 页面渲染前统一在这里转成可直接放进 {@code src} 的地址，避免每个页面
 * 各写一份判断，也避免把外链误拼成 {@code /api/file/http://...}。</p>
 */

/**
 * @param {string|number|null|undefined} value fileId、相对路径、http(s) 外链
 * @param {{ size?: string }} options size 可传 thumb / medium，仅对 fileId 生效
 * @returns {string} 可直接用于 img / el-image 的地址
 */
export function resolveAssetUrl(value, { size = '' } = {}) {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  if (!text) return ''

  // 外链、协议相对地址、站点内绝对路径、内联图片都原样使用
  if (
    text.startsWith('http://') ||
    text.startsWith('https://') ||
    text.startsWith('//') ||
    text.startsWith('/') ||
    text.startsWith('data:') ||
    text.startsWith('blob:')
  ) {
    return text
  }

  const query = size ? `?size=${encodeURIComponent(size)}` : ''
  return `/api/file/${encodeURIComponent(text)}${query}`
}
