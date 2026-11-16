import http from './request'

/** 部门管理接口，对应 /api/admin/system/dept。 */

export function listDepts(params) {
  return http.get('/admin/system/dept/list', { params })
}

export function getDeptTree() {
  return http.get('/admin/system/dept/tree')
}

export function getDept(deptId) {
  return http.get(`/admin/system/dept/${deptId}`)
}

export function addDept(data) {
  return http.post('/admin/system/dept', data)
}

export function updateDept(deptId, data) {
  return http.put(`/admin/system/dept/${deptId}`, data)
}

export function deleteDept(deptId) {
  return http.delete(`/admin/system/dept/${deptId}`)
}
