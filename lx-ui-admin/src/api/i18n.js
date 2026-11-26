import http from './request'

/**
 * 多语言语言包接口，对应 /api/admin/i18n/**。
 *
 * <p>模块关闭时这些接口返回 404，菜单也不下发，因此页面不需要额外判空。</p>
 *
 * <p>约定：后端把雪花 ID 序列化为字符串，这里原样传递，不要 parseInt。</p>
 */

// ==================== 语言 ====================

export function listLanguages(params) {
  return http.get('/admin/i18n/language/list', { params })
}

export function languageOptions() {
  return http.get('/admin/i18n/language/options')
}

export function addLanguage(data) {
  return http.post('/admin/i18n/language', data)
}

export function updateLanguage(languageId, data) {
  return http.put(`/admin/i18n/language/${languageId}`, data)
}

export function deleteLanguage(languageId) {
  return http.delete(`/admin/i18n/language/${languageId}`)
}

export function changeLanguageStatus(languageId, status) {
  return http.put(`/admin/i18n/language/${languageId}/status`, null, { params: { status } })
}

// ==================== 翻译键 / 译文 ====================

export function listKeys(params) {
  return http.get('/admin/i18n/key/list', { params })
}

export function addKey(data) {
  return http.post('/admin/i18n/key', data)
}

export function updateKey(keyId, data) {
  return http.put(`/admin/i18n/key/${keyId}`, data)
}

export function deleteKey(keyId) {
  return http.delete(`/admin/i18n/key/${keyId}`)
}

export function batchSaveValues(items) {
  return http.post('/admin/i18n/value/batch', { items })
}

/** 导入 CSV：只写草稿，返回成功 / 跳过 / 失败明细。 */
export function importValues(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/admin/i18n/import', form, { timeout: 60000 })
}

/** 导出 CSV：responseType=blob 时拦截器原样返回 Blob。 */
export function exportValues(params) {
  return http.get('/admin/i18n/export', {
    params,
    responseType: 'blob',
    timeout: 60000
  })
}

export function getCoverage(params) {
  return http.get('/admin/i18n/coverage', { params })
}

export function listMissing(params) {
  return http.get('/admin/i18n/missing', { params })
}

export function clearMissing(params) {
  return http.delete('/admin/i18n/missing', { params })
}

// ==================== 语言包 ====================

export function listPacks(params) {
  return http.get('/admin/i18n/pack/list', { params })
}

export function currentPacks(params) {
  return http.get('/admin/i18n/pack/current', { params })
}

export function publishPack(data) {
  return http.post('/admin/i18n/pack/publish', data)
}

export function rollbackPack(packId) {
  return http.post('/admin/i18n/pack/rollback', { packId })
}
