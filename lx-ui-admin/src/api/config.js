import http from './request'

/** 系统参数接口，对应 /api/admin/system/config。 */

export function listConfigs(params) {
  return http.get('/admin/system/config/list', { params })
}

export function getConfig(configId) {
  return http.get(`/admin/system/config/${configId}`)
}

/** 按键名取值；失败不弹提示，调用方可给默认值。 */
export function getConfigValue(configKey) {
  return http.get('/admin/system/config/value', { params: { configKey }, silent: true })
}

/**
 * 读取公开参数（登录即可，不需要 config:list 权限）。
 *
 * <p>服务端用白名单限制可读 key（例如 `sys.editor.type`），
 * 非白名单 key 一律返回 null，不暴露参数是否存在。</p>
 */
export function getPublicConfig(configKey) {
  return http.get('/admin/system/config/public/value', { params: { configKey }, silent: true })
}

export function addConfig(data) {
  return http.post('/admin/system/config', data)
}

export function updateConfig(configId, data) {
  return http.put(`/admin/system/config/${configId}`, data)
}

export function deleteConfig(configId) {
  return http.delete(`/admin/system/config/${configId}`)
}
