import http from './request'

/**
 * 认证接口。
 *
 * 路径不带 /api 前缀：axios baseURL 已经是 /api，
 * 与后端 /api/admin/auth/** 一一对应。
 */

export function getCaptcha() {
  return http.get('/admin/auth/captcha', { silent: true })
}

export function login(data) {
  return http.post('/admin/auth/login', data)
}

export function logout() {
  return http.post('/admin/auth/logout', null, { silent: true })
}

export function getInfo() {
  return http.get('/admin/auth/info')
}

export function getMenus() {
  return http.get('/admin/auth/menus')
}

/** 修改本人资料（昵称 / 邮箱 / 手机号），返回最新的用户信息。 */
export function updateProfile(data) {
  return http.put('/admin/auth/profile', data)
}

/**
 * 修改本人密码。
 *
 * 成功后后端会注销该账号的全部会话，前端必须清 Token 重新登录。
 */
export function changePassword(data) {
  return http.put('/admin/auth/password', data)
}
