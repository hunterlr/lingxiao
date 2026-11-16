import { useAuthStore } from '@/store/auth'
import i18n from '@/i18n'
import { installDynamicRoutes, resetDynamicRoutes } from './dynamic'

/**
 * 路由守卫。
 *
 * <p>流程：白名单直达 → 无 Token 去登录 → 有 Token 但用户信息未加载
 * （刷新 / 刚登录）先拉 info + menus、注册动态路由，再重新进入目标路由。</p>
 *
 * <p>用“重新进入”而不是直接放行：动态路由是在本次导航之后才注册的，
 * 不重进会命中 404。</p>
 */

const WHITE_LIST = ['/login', '/403', '/404']

export function setupGuard(router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (to.meta.public || WHITE_LIST.includes(to.path)) {
      // 已登录再访问登录页，直接回首页，避免出现“两个身份”的错觉
      if (to.path === '/login' && auth.token) {
        return { path: '/' }
      }
      return true
    }

    if (!auth.token) {
      return {
        path: '/login',
        query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : {}
      }
    }

    if (!auth.loaded) {
      try {
        await auth.loadUser()
        resetDynamicRoutes()
        installDynamicRoutes(auth.menus)
        return { path: to.fullPath, replace: true }
      } catch (e) {
        // 1002 已由 axios 拦截器整页跳登录；这里兜住其余异常，避免白屏
        auth.reset()
        return { path: '/login' }
      }
    }

    return true
  })

  router.afterEach((to) => {
    const { t, te } = i18n.global
    const meta = to.meta || {}
    const pageTitle = meta.titleKey && te(meta.titleKey) ? t(meta.titleKey) : meta.title
    document.title = pageTitle ? `${pageTitle} · ${t('layout.brand')}` : t('layout.brand')
  })
}
