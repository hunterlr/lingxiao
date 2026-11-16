import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import { setupGuard } from './guard'

/**
 * 静态路由：不依赖登录用户的页面。
 *
 * <p>业务页面（用户 / 角色 / 菜单 …）不写死在这里，全部由后端菜单
 * {@code /api/admin/auth/menus} 动态生成，见 router/dynamic.js。
 * 这样新增模块只需要加菜单数据，前端不用改路由文件。</p>
 */
export const staticRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', titleKey: 'login.title', public: true }
  },
  {
    path: '/',
    name: 'Layout',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', titleKey: 'layout.home', icon: 'HomeFilled', affix: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '没有权限', titleKey: 'errorPage.forbidden', public: true }
  },
  {
    path: '/404',
    name: 'NotFoundPage',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', titleKey: 'errorPage.notFound', public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', titleKey: 'errorPage.notFound' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

setupGuard(router)

export default router
