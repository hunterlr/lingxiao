import http from './request'

/** 用户管理接口，对应 /api/admin/system/user。 */

export function listUsers(params) {
  return http.get('/admin/system/user/list', { params })
}

export function getUser(userId) {
  return http.get(`/admin/system/user/${userId}`)
}

export function addUser(data) {
  return http.post('/admin/system/user', data)
}

export function updateUser(userId, data) {
  return http.put(`/admin/system/user/${userId}`, data)
}

export function resetUserPassword(userId, newPassword) {
  return http.put(`/admin/system/user/${userId}/password`, { newPassword })
}

export function changeUserStatus(userId, status) {
  return http.put(`/admin/system/user/${userId}/status`, null, { params: { status } })
}

/** 手动解锁：清空连续失败次数与锁定截止时间；未锁定时调用也安全。 */
export function unlockUser(userId) {
  return http.put(`/admin/system/user/${userId}/unlock`)
}

export function deleteUser(userId) {
  return http.delete(`/admin/system/user/${userId}`)
}
