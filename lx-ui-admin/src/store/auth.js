import { defineStore } from 'pinia'
import i18n from '@/i18n'
import * as authApi from '@/api/auth'
import { clearToken, getToken, getTokenName, setToken } from '@/utils/auth'

/**
 * 登录态与权限。
 *
 * <p>这里只存“数据”，不碰路由跳转：路由编排放在 router/guard.js，
 * 避免 store ←→ router 循环依赖。</p>
 *
 * <p>{@code loaded} 表示“用户信息 + 菜单”是否已拉到；刷新页面后
 * 它是 false，守卫会重新拉一次再放行。</p>
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    tokenName: getTokenName(),
    userId: '',
    username: '',
    nickname: '',
    avatar: '',
    email: '',
    phone: '',
    roles: [],
    permissions: [],
    menus: [],
    loaded: false
  }),

  getters: {
    isLogin: (state) => Boolean(state.token),
    // 读一下 locale.value，切语言时 getter 才会重新求值（否则一直用首次渲染的文案）
    displayName(state) {
      void i18n.global.locale.value
      return state.nickname || state.username || i18n.global.t('layout.notLoggedIn')
    },
    isSuperAdmin: (state) => state.permissions.includes('*:*:*'),
    roleLabel(state) {
      void i18n.global.locale.value
      return state.roles.length ? state.roles.join(' / ') : i18n.global.t('layout.normalUser')
    }
  },

  actions: {
    /**
     * 登录：后端返回 tokenName / tokenValue / expiresIn。
     * 不在这里拉菜单，交给路由守卫统一处理，保证“刷新”和“刚登录”走同一条路。
     */
    async login(form) {
      const data = await authApi.login(form)
      setToken(data.tokenValue, data.tokenName)
      this.token = data.tokenValue
      this.tokenName = data.tokenName
      this.loaded = false
      return data
    },

    /** 拉取当前用户信息与菜单（两者并行，任一个失败都视为登录态失效）。 */
    async loadUser() {
      const [info, menus] = await Promise.all([authApi.getInfo(), authApi.getMenus()])
      this.applyUserInfo(info)
      this.menus = menus || []
      this.loaded = true
      return info
    },

    /** 把后端返回的用户信息写进 store（登录后 / 刷新 / 改资料后共用）。 */
    applyUserInfo(info) {
      this.userId = info.userId
      this.username = info.username
      this.nickname = info.nickname
      this.avatar = info.avatar || ''
      this.email = info.email || ''
      this.phone = info.phone || ''
      this.roles = info.roles || []
      this.permissions = info.permissions || []
    },

    /** 修改本人资料；后端返回最新信息，直接覆盖 store，顶部昵称立即更新。 */
    async updateProfile(form) {
      const info = await authApi.updateProfile(form)
      this.applyUserInfo(info)
      return info
    },

    /** 退出：先通知后端销毁 Token，无论成功失败都清本地状态。 */
    async logout() {
      try {
        if (this.token) {
          await authApi.logout()
        }
      } catch (e) {
        // 退出接口失败不影响本地清理，避免用户被困在已过期的会话里
      } finally {
        this.reset()
      }
    },

    /** 清空本地登录态（不调用后端）。 */
    reset() {
      clearToken()
      this.token = ''
      this.tokenName = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      this.email = ''
      this.phone = ''
      this.roles = []
      this.permissions = []
      this.menus = []
      this.loaded = false
    },

    /**
     * 权限判断。
     * @param {string|string[]|undefined} value 权限码；数组表示任一命中；空表示不限制
     */
    hasPermission(value) {
      if (!value || (Array.isArray(value) && value.length === 0)) return true
      if (this.permissions.includes('*:*:*')) return true
      const list = Array.isArray(value) ? value : [value]
      return list.some((item) => this.permissions.includes(item))
    },

    /** 角色判断，规则同上。 */
    hasRole(value) {
      if (!value || (Array.isArray(value) && value.length === 0)) return true
      const list = Array.isArray(value) ? value : [value]
      return list.some((item) => this.roles.includes(item))
    }
  }
})
