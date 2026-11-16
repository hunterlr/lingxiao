import router from './index'

/**
 * 把后端菜单树转成路由。
 *
 * <p>映射规则：菜单（type=C）的 {@code component} 字段是相对 views 的路径，
 * 例如 {@code system/user/index} → {@code src/views/system/user/index.vue}。
 * 组件文件不存在时挂到占位页，避免“菜单加了、前端没跟上”导致整页白屏。</p>
 *
 * <p>路径直接使用后端给的绝对路径（{@code /system/user}），
 * 全部作为 Layout 的子路由注册；侧边栏层级由菜单树独立渲染，
 * 两者解耦，菜单移动位置不用改路由结构。</p>
 */

const viewModules = import.meta.glob('../views/**/*.vue')
const PLACEHOLDER = '../views/placeholder/index.vue'

/** 已注册的动态路由名，退出登录时按名单精确移除。 */
const dynamicRouteNames = []

function resolveComponent(component) {
  if (!component) {
    return viewModules[PLACEHOLDER]
  }
  const key = `../views/${component.replace(/^\/+/, '')}.vue`
  return viewModules[key] || viewModules[PLACEHOLDER]
}

/** 深度优先收集所有 C 类型菜单（F 按钮不生成路由）。 */
function collectPageMenus(menus, result = []) {
  for (const menu of menus || []) {
    if (menu.type === 'C') {
      result.push(menu)
    }
    if (menu.children && menu.children.length) {
      collectPageMenus(menu.children, result)
    }
  }
  return result
}

export function installDynamicRoutes(menus) {
  const pages = collectPageMenus(menus)
  for (const menu of pages) {
    if (!menu.path) continue
    const name = `menu-${menu.id}`
    if (router.hasRoute(name)) continue
    router.addRoute('Layout', {
      path: menu.path,
      name,
      component: resolveComponent(menu.component),
      meta: {
        title: menu.name,
        icon: menu.icon,
        perms: menu.perms,
        menuId: menu.id,
        component: menu.component,
        dynamic: true,
        placeholder: !menu.component || !viewModules[`../views/${menu.component}.vue`]
      }
    })
    dynamicRouteNames.push(name)
  }
}

/** 移除全部动态路由；切换账号 / 退出登录时调用，防止权限串台。 */
export function resetDynamicRoutes() {
  while (dynamicRouteNames.length) {
    const name = dynamicRouteNames.pop()
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
}
