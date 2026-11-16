/**
 * 后台主题工具。
 * ---------------------------------------------------------------------
 * 设计目标：
 *   1. 只依赖 CSS 变量 + Element Plus 官方暗色变量，不引入额外主题库；
 *   2. 所有计算都是纯函数，方便单元测试，也方便将来把偏好同步到后端；
 *   3. 主题状态由 store/app.js 持有，本文件只负责“把状态写到 DOM”。
 *
 * 约定：
 *   - 亮 / 暗由 <html class="dark"> 控制（Element Plus 暗色规范）；
 *   - 侧边栏深浅由 <html class="lx-sidebar-light"> 控制，走自己的 --lx-sidebar-* 变量；
 *   - 主色同时写入 --lx-primary（项目变量）和 --el-color-primary（Element Plus 变量），
 *     否则按钮 / 链接 / 表格选中态会出现“一半新色、一半旧色”。
 */

/** 主题色预设：够用即可，用户仍可用取色器选任意颜色；名称走 theme.color.<key> 译文 */
export const THEME_PRESETS = [
  { key: 'blue', value: '#2f6fed' },
  { key: 'green', value: '#0e9f6e' },
  { key: 'violet', value: '#7c3aed' },
  { key: 'rose', value: '#e11d48' },
  { key: 'amber', value: '#f59e0b' },
  { key: 'cyan', value: '#0ea5e9' }
]

/** 默认偏好：与 v1.0 现有视觉保持一致，老用户升级后无感 */
export const DEFAULT_THEME = {
  /** light | dark | auto */
  mode: 'light',
  /** 必须是 #RRGGBB */
  primaryColor: '#2f6fed',
  /** dark | light */
  sidebarStyle: 'dark'
}

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/
const MODES = ['light', 'dark', 'auto']
const SIDEBAR_STYLES = ['dark', 'light']

/**
 * 把任意来源（localStorage / 后端）的主题对象收敛成合法值。
 * 任何字段不合法都回退默认值，避免一条脏数据把整个界面搞成不可用。
 */
export function normalizeTheme(input) {
  const theme = input && typeof input === 'object' ? input : {}
  return {
    mode: MODES.includes(theme.mode) ? theme.mode : DEFAULT_THEME.mode,
    primaryColor: HEX_COLOR.test(theme.primaryColor || '')
      ? theme.primaryColor.toLowerCase()
      : DEFAULT_THEME.primaryColor,
    sidebarStyle: SIDEBAR_STYLES.includes(theme.sidebarStyle)
      ? theme.sidebarStyle
      : DEFAULT_THEME.sidebarStyle
  }
}

/** 系统是否处于暗色（auto 模式用） */
export function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

/** 计算最终生效的明暗：auto 时跟随系统 */
export function resolveDark(mode, systemDark = systemPrefersDark()) {
  if (mode === 'dark') return true
  if (mode === 'auto') return systemDark
  return false
}

/**
 * 颜色混合：weight 是 target 的占比（0~1）。
 * 例：mixColor('#2f6fed', '#ffffff', 0.3) = 30% 白 + 70% 主色。
 * 只支持 #RRGGBB，内部足够用，不引入 tinycolor 之类的依赖。
 */
export function mixColor(hex, target, weight) {
  const a = parseHex(hex)
  const b = parseHex(target)
  const w = Math.min(1, Math.max(0, weight))
  const channel = (x, y) => Math.round(x * (1 - w) + y * w)
  return (
    '#' +
    [channel(a.r, b.r), channel(a.g, b.g), channel(a.b, b.b)]
      .map((n) => n.toString(16).padStart(2, '0'))
      .join('')
  )
}

function parseHex(hex) {
  const value = HEX_COLOR.test(hex || '') ? hex.slice(1) : '2f6fed'
  return {
    r: parseInt(value.slice(0, 2), 16),
    g: parseInt(value.slice(2, 4), 16),
    b: parseInt(value.slice(4, 6), 16)
  }
}

/**
 * 把主题写到 DOM。
 * 注意：这里不读写 localStorage，持久化由 store 负责，职责单一。
 */
export function applyTheme(theme, systemDark = systemPrefersDark()) {
  if (typeof document === 'undefined') return
  const normalized = normalizeTheme(theme)
  const root = document.documentElement
  const dark = resolveDark(normalized.mode, systemDark)
  const primary = normalized.primaryColor

  root.classList.toggle('dark', dark)
  root.classList.toggle('lx-sidebar-light', normalized.sidebarStyle === 'light')
  root.dataset.theme = dark ? 'dark' : 'light'
  // 让浏览器原生控件（滚动条、输入框、日期选择器）也跟随明暗
  root.style.colorScheme = dark ? 'dark' : 'light'

  // 项目自己的变量
  root.style.setProperty('--lx-primary', primary)
  root.style.setProperty('--lx-primary-dark', mixColor(primary, '#000000', 0.18))
  root.style.setProperty('--lx-primary-light', mixColor(primary, '#ffffff', 0.18))

  // Element Plus 主色及其派生色（light-3/5/7/8/9 与 dark-2）
  root.style.setProperty('--el-color-primary', primary)
  root.style.setProperty('--el-color-primary-dark-2', mixColor(primary, '#000000', 0.2))
  root.style.setProperty('--el-color-primary-light-3', mixColor(primary, '#ffffff', 0.3))
  root.style.setProperty('--el-color-primary-light-5', mixColor(primary, '#ffffff', 0.5))
  root.style.setProperty('--el-color-primary-light-7', mixColor(primary, '#ffffff', 0.7))
  root.style.setProperty('--el-color-primary-light-8', mixColor(primary, '#ffffff', 0.8))
  root.style.setProperty('--el-color-primary-light-9', mixColor(primary, '#ffffff', 0.9))
}

/**
 * 监听系统明暗变化，返回取消监听函数。
 * 只有 mode=auto 时 store 才会真正重绘，但监听本身常驻，切到 auto 无需重新注册。
 */
export function watchSystemTheme(callback) {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {}
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const handler = (event) => callback(event.matches)
  if (media.addEventListener) {
    media.addEventListener('change', handler)
    return () => media.removeEventListener('change', handler)
  }
  // Safari < 14 的旧 API，保留兜底
  media.addListener(handler)
  return () => media.removeListener(handler)
}
