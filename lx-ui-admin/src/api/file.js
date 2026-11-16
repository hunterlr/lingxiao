import http from './request'

/**
 * 文件中心接口，对应 /api/admin/file。
 *
 * <p>上传走 multipart，仍复用同一个 axios 实例：
 * 拦截器会自动带 Token，也会统一处理业务错误。</p>
 */

export function uploadFile(file, { bizType = 'common', remark = '', onProgress } = {}) {
  const form = new FormData()
  form.append('file', file)
  form.append('bizType', bizType)
  if (remark) {
    form.append('remark', remark)
  }
  return http.post('/admin/file/upload', form, {
    timeout: 60000,
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
  })
}

export function listFiles(params) {
  return http.get('/admin/file/list', { params })
}

export function getFile(fileId) {
  return http.get(`/admin/file/${fileId}`)
}

export function renameFile(fileId, displayName) {
  return http.put(`/admin/file/${fileId}/rename`, { displayName })
}

export function listFileNameHistory(fileId) {
  return http.get(`/admin/file/${fileId}/name-history`)
}
