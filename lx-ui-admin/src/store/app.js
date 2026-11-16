import { defineStore } from 'pinia'
import {
  DEFAULT_THEME,
  applyTheme,
  normalizeTheme,
  resolveDark,
  systemPrefersDark,
  watchSystemTheme
} from '@/utils/theme'

/**
 * 界面偏好。
 *
 * 存 localStorage，刷新后保持用户习惯；只存“纯 UI”数据，
 * 不放登录态（登录态在 store/auth.js）。
 *
 * 两份偏好：
 *   - 侧边栏折叠状态：老字段，保持兼容；
 *   - 主题（明暗 / 主色 / 侧边栏风格）：新增，键名 lx-admin-theme。
 *
 * 为什么主题不做成后端参数：主题是“个人视觉偏好”，不是全站策略。
 * 放 localStorage 零成本、即时生效；等到需要“换设备同步”时，
 * 再在 sys_user 加列并通过个人中心接口读写（见 docs/24-theme-switch.md）。
 */
const COLLAPSE_KEY = 'lx-admin-sidebar-collapsed'
const THEME_KEY = 'lx-admin-theme'

function loadTheme() {
  try {
    const raw = localStorage.getItem(THEME_KEY)
    return normalizeTheme(raw ? JSON.parse(raw) : DEFAULT_THEME)
  } catch (e) {
    // 手动改坏 localStorage 时不要让整个应用白屏
    return { ...DEFAULT_THEME }
  }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem(COLLAPSE_KEY) === '1',
    theme: loadTheme(),
    // 系统明暗单独存，auto 模式下它变化要驱动界面重绘
    systemDark: systemPrefersDark(),
    themeDrawerVisible: false
  }),

  getters: {
    /** 当前最终是不是暗色（auto 时看系统） */
    isDark: (state) => resolveDark(state.theme.mode, state.systemDark)
  },

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(COLLAPSE_KEY, this.sidebarCollapsed ? '1' : '0')
    },

    /** 应用启动时调用一次：落盘值写回 DOM，并监听系统明暗变化 */
    initTheme() {
      applyTheme(this.theme, this.systemDark)
      watchSystemTheme((dark) => {
        this.systemDark = dark
        if (this.theme.mode === 'auto') {
          applyTheme(this.theme, this.systemDark)
        }
      })
    },

    /** 统一入口：改任意字段都会立即生效并持久化 */
    setTheme(patch) {
      this.theme = normalizeTheme({ ...this.theme, ...patch })
      applyTheme(this.theme, this.systemDark)
      localStorage.setItem(THEME_KEY, JSON.stringify(this.theme))
    },

    setThemeMode(mode) {
      this.setTheme({ mode })
    },

    setPrimaryColor(color) {
      // 取色器清空时会传 null，忽略即可
      if (color) this.setTheme({ primaryColor: color })
    },

    setSidebarStyle(sidebarStyle) {
      this.setTheme({ sidebarStyle })
    },

    /** 顶栏月亮/太阳快捷键：只在亮色与暗色之间切，不动 auto */
    toggleDark() {
      this.setThemeMode(this.isDark ? 'light' : 'dark')
    },

    resetTheme() {
      this.theme = { ...DEFAULT_THEME }
      applyTheme(this.theme, this.systemDark)
      localStorage.removeItem(THEME_KEY)
    },

    openThemeDrawer() {
      this.themeDrawerVisible = true
    }
  }
})
