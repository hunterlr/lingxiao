import http from './request'

/**
 * 代码生成器接口，对应 /api/admin/tool/gen。
 *
 * <p>流程：db/list 选表 → import → 详情里改表 / 字段配置 →
 * preview 预览 → download 下载 ZIP。后端不直接写工程目录，
 * 生成物由开发者自行放入目标模块。</p>
 */

export function listGenTables(params) {
  return http.get('/admin/tool/gen/list', { params })
}

/** 当前数据源里可导入的表；已导入的返回 imported=true。 */
export function listDbTables(keyword) {
  return http.get('/admin/tool/gen/db/list', { params: { keyword } })
}

export function getGenTable(tableId) {
  return http.get(`/admin/tool/gen/${tableId}`)
}

/** tables 是表名数组；部分失败不抛异常，结果里带 skipped 原因。 */
export function importGenTables(tables) {
  return http.post('/admin/tool/gen/import', { tables })
}

export function updateGenTable(tableId, data) {
  return http.put(`/admin/tool/gen/${tableId}`, data)
}

export function updateGenColumns(tableId, columns) {
  return http.put(`/admin/tool/gen/${tableId}/columns`, columns)
}

export function deleteGenTable(tableId) {
  return http.delete(`/admin/tool/gen/${tableId}`)
}

/** 返回数组：[{ name, path, language, content }] */
export function previewGenCode(tableId) {
  return http.get(`/admin/tool/gen/preview/${tableId}`)
}

/**
 * 下载 ZIP。
 *
 * <p>responseType=blob 时 request.js 的拦截器会把 Blob 原样返回
 * （Blob 上没有 code 字段，不会走业务错误分支）。</p>
 */
export function downloadGenCode(tableId) {
  return http.get(`/admin/tool/gen/download/${tableId}`, {
    responseType: 'blob',
    timeout: 60000
  })
}
