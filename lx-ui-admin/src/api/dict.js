import http from './request'

/**
 * 字典接口，对应 /api/admin/system/dict。
 *
 * 类型与数据分成 /type/** 与 /data/** 两组，避免一个接口做两件事。
 */

export function listDictTypes(params) {
  return http.get('/admin/system/dict/type/list', { params })
}

export function listDictTypeOptions() {
  return http.get('/admin/system/dict/type/options')
}

export function getDictType(dictId) {
  return http.get(`/admin/system/dict/type/${dictId}`)
}

export function addDictType(data) {
  return http.post('/admin/system/dict/type', data)
}

export function updateDictType(dictId, data) {
  return http.put(`/admin/system/dict/type/${dictId}`, data)
}

export function deleteDictType(dictId) {
  return http.delete(`/admin/system/dict/type/${dictId}`)
}

export function listDictData(params) {
  return http.get('/admin/system/dict/data/list', { params })
}

export function listDictDataOptions(dictType) {
  return http.get('/admin/system/dict/data/options', { params: { dictType } })
}

export function getDictData(dictCode) {
  return http.get(`/admin/system/dict/data/${dictCode}`)
}

export function addDictData(data) {
  return http.post('/admin/system/dict/data', data)
}

export function updateDictData(dictCode, data) {
  return http.put(`/admin/system/dict/data/${dictCode}`, data)
}

export function deleteDictData(dictCode) {
  return http.delete(`/admin/system/dict/data/${dictCode}`)
}
