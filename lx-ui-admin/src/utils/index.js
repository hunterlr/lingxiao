/**
 * 通用小工具。
 */

/** 是否为空（null / undefined / 空串 / 空数组）。 */
export function isEmpty(value) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

/** 取第一个非空值，常用于兼容字段别名。 */
export function firstNotEmpty(...values) {
  for (const value of values) {
    if (!isEmpty(value)) return value
  }
  return ''
}

/** 秒数转“x 小时 y 分钟”。 */
export function formatSeconds(seconds) {
  const total = Number(seconds) || 0
  if (total <= 0) return '-'
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

/** 文件大小可读化。 */
export function formatFileSize(size) {
  const value = Number(size) || 0
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

/** 下载文本文件（导出 / 复制兜底）。 */
export function downloadText(filename, text) {
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
