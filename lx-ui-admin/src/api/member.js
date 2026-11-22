import http from './request'

/**
 * 会员管理接口，对应 /api/admin/member/**。
 *
 * <p>只供后台运营使用；会员端（门户）不调用这些接口，改用
 * {@code /api/app/auth/**} 与 {@code /api/app/profile/**}。</p>
 *
 * <p>会员模块关闭时后端整组 Controller 不装配，这些请求会得到 404；
 * 前端菜单也不会下发，正常不会触发。</p>
 */

export function listMembers(params) {
  return http.get('/admin/member/list', { params })
}

export function getMember(memberId) {
  return http.get(`/admin/member/${memberId}`)
}

export function listLevelOptions() {
  return http.get('/admin/member/level-options')
}

export function addMember(data) {
  return http.post('/admin/member', data)
}

export function updateMember(memberId, data) {
  return http.put(`/admin/member/${memberId}`, data)
}

export function changeMemberStatus(memberId, status) {
  return http.put(`/admin/member/${memberId}/status`, { status })
}

export function resetMemberPassword(memberId, newPassword) {
  return http.put(`/admin/member/${memberId}/password`, { newPassword })
}

export function deleteMember(memberId) {
  return http.delete(`/admin/member/${memberId}`)
}

/* ==================== 会员消息通道 ==================== */

export function listMemberChannels() {
  return http.get('/admin/member/channel')
}

export function updateMemberChannel(configId, data) {
  return http.put(`/admin/member/channel/${configId}`, data)
}

export function testMemberChannel(configId, target) {
  return http.post(`/admin/member/channel/${configId}/test`, { target })
}

/* ==================== 会员评论审核 ==================== */

export function listMemberComments(params) {
  return http.get('/admin/member/comment/list', { params })
}

export function auditMemberComment(commentId, data) {
  return http.put(`/admin/member/comment/${commentId}/audit`, data)
}

export function deleteMemberComment(commentId) {
  return http.delete(`/admin/member/comment/${commentId}`)
}
