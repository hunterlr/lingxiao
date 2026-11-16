import http from './request'

/**
 * 监控日志接口，对应后端：
 *   /api/admin/system/oper-log   （操作日志）
 *   /api/admin/system/login-log  （登录日志）
 *
 * <p>两类日志都只读：不提供新增 / 修改。清空是独立权限码，
 * 前端按钮用 v-hasPerm 控制，后端仍是最终防线。</p>
 */

export function listOperLogs(params) {
  return http.get('/admin/system/oper-log/list', { params })
}

export function getOperLog(operId) {
  return http.get(`/admin/system/oper-log/${operId}`)
}

export function cleanOperLogs() {
  return http.delete('/admin/system/oper-log/clean')
}

export function listLoginLogs(params) {
  return http.get('/admin/system/login-log/list', { params })
}

export function cleanLoginLogs() {
  return http.delete('/admin/system/login-log/clean')
}
