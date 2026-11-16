import http from './request'

/** 公告接口，对应 /api/admin/system/notice。 */

export function listNotices(params) {
  return http.get('/admin/system/notice/list', { params })
}

export function getNotice(noticeId) {
  return http.get(`/admin/system/notice/${noticeId}`)
}

export function addNotice(data) {
  return http.post('/admin/system/notice', data)
}

export function updateNotice(noticeId, data) {
  return http.put(`/admin/system/notice/${noticeId}`, data)
}

export function deleteNotice(noticeId) {
  return http.delete(`/admin/system/notice/${noticeId}`)
}
