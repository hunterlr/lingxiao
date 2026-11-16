import http from './request'

/** 菜单管理接口，对应 /api/admin/system/menu。 */

export function listMenus(params) {
  return http.get('/admin/system/menu/list', { params })
}

/** 菜单树；onlyMenu=true 时只含目录与菜单，不含按钮。 */
export function getMenuTree(onlyMenu = false) {
  return http.get('/admin/system/menu/tree', { params: { onlyMenu } })
}

export function getMenu(menuId) {
  return http.get(`/admin/system/menu/${menuId}`)
}

export function addMenu(data) {
  return http.post('/admin/system/menu', data)
}

export function updateMenu(menuId, data) {
  return http.put(`/admin/system/menu/${menuId}`, data)
}

export function deleteMenu(menuId) {
  return http.delete(`/admin/system/menu/${menuId}`)
}
