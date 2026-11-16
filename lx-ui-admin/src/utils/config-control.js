/**
 * 参数控件（sys_config.control_type / config_options）的前端解析。
 *
 * <p>格式与后端 `SysConfigService#normalizeAndValidateControl` 保持一致：</p>
 *
 * | 控件 | 选项格式 | 示例 |
 * |---|---|---|
 * | text   | 忽略 | - |
 * | number | 最小值:最大值，可留空一侧 | `1:3650` |
 * | switch | 开值:关值，默认 true:false | `true:false` |
 * | select | 值:显示名，逗号 / 换行分隔 | `wangeditor-next:wangEditor Next,ueditor-plus:UEditor Plus` |
 *
 * <p>解析失败时不抛异常，返回安全默认值：页面先把数据展示出来，
 * 真正的拒绝发生在保存时（后端校验）。</p>
 */

import i18n from '@/i18n'

export const CONTROL_TYPES = [
  { value: 'text' },
  { value: 'number' },
  { value: 'switch' },
  { value: 'select' }
]

/**
 * 控件类型文案走语言包（{@code configControl.*}）。
 *
 * <p>不在模块加载时把 label 固化进数组：那样切换语言后下拉框还是旧语言。
 * 这里每次调用都读当前语言。</p>
 */
export function controlTypeLabel(type) {
  const { t, te } = i18n.global
  const key = `configControl.${type || 'text'}`
  return te(key) ? t(key) : type || ''
}

/** 解析下拉选项，返回 [{ value, label }]；空值与重复值跳过。 */
export function parseSelectOptions(text) {
  const result = []
  const seen = new Set()
  String(text || '')
    .split(/[,，\r\n]/)
    .forEach((raw) => {
      const item = raw.trim()
      if (!item) return
      const idx = item.indexOf(':')
      const value = (idx < 0 ? item : item.slice(0, idx)).trim()
      const label = idx < 0 ? value : item.slice(idx + 1).trim() || value
      if (!value || seen.has(value)) return
      seen.add(value)
      result.push({ value, label })
    })
  return result
}

/** 解析数字范围，返回 { min, max }；缺省一侧为 undefined。 */
export function parseNumberRange(text) {
  const raw = String(text || '').trim()
  if (!raw) return { min: undefined, max: undefined }
  const parts = raw.split(':')
  if (parts.length !== 2) return { min: undefined, max: undefined }
  const toNumber = (value) => {
    const trimmed = value.trim()
    if (!trimmed) return undefined
    const number = Number(trimmed)
    return Number.isFinite(number) ? number : undefined
  }
  const min = toNumber(parts[0])
  const max = toNumber(parts[1])
  if (min !== undefined && max !== undefined && min > max) {
    return { min: undefined, max: undefined }
  }
  return { min, max }
}

/** 解析开关值，返回 { on, off }；非法或缺失时回退 true/false。 */
export function parseSwitchValues(text) {
  const parts = String(text || '').split(':')
  if (parts.length === 2 && parts[0].trim() && parts[1].trim() && parts[0].trim() !== parts[1].trim()) {
    return { on: parts[0].trim(), off: parts[1].trim() }
  }
  return { on: 'true', off: 'false' }
}

/**
 * 把参数值转成列表里可读的文案。
 *
 * <p>下拉显示选项名而不是原始值（`wangeditor-next` → `wangEditor Next`）；
 * 开关显示“开启 / 关闭”；数字与文本原样返回。</p>
 */
export function displayConfigValue(config) {
  const value = config.configValue == null ? '' : String(config.configValue)
  const type = config.controlType || 'text'
  if (type === 'switch') {
    const { on, off } = parseSwitchValues(config.configOptions)
    const { t } = i18n.global
    if (value === on) return t('configControl.on')
    if (value === off) return t('configControl.off')
    return value
  }
  if (type === 'select') {
    const hit = parseSelectOptions(config.configOptions).find((item) => item.value === value)
    return hit ? hit.label : value
  }
  return value
}
