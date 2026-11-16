import http from './request'

/** 角色管理接口，对应 /api/admin/system/role。 */

export function listRoles(params) {
  return http.get('/admin/system/role/list', { params })
}

/** 下拉选项：只返回启用角色。 */
export function listRoleOptions() {
  return http.get('/admin/system/role/options')
}

export function getRole(roleId) {
  return http.get(`/admin/system/role/${roleId}`)
}

export function addRole(data) {
  return http.post('/admin/system/role', data)
}

export function updateRole(roleId, data) {
  return http.put(`/admin/system/role/${roleId}`, data)
}

export function deleteRole(roleId) {
  return http.delete(`/admin/system/role/${roleId}`)
}
