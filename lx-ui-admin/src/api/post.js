import http from './request'

/** 岗位管理接口，对应 /api/admin/system/post。 */

export function listPosts(params) {
  return http.get('/admin/system/post/list', { params })
}

export function listPostOptions() {
  return http.get('/admin/system/post/options')
}

export function getPost(postId) {
  return http.get(`/admin/system/post/${postId}`)
}

export function addPost(data) {
  return http.post('/admin/system/post', data)
}

export function updatePost(postId, data) {
  return http.put(`/admin/system/post/${postId}`, data)
}

export function deletePost(postId) {
  return http.delete(`/admin/system/post/${postId}`)
}
