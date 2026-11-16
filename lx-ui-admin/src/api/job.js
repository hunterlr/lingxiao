import http from './request'

/**
 * 定时任务接口，对应 /api/admin/monitor/job。
 *
 * <p>任务与日志分两组接口：日志是排查依据，单独一个权限码，
 * 因此不塞进任务详情里返回。</p>
 */

export function listJobs(params) {
  return http.get('/admin/monitor/job/list', { params })
}

export function getJob(jobId) {
  return http.get(`/admin/monitor/job/${jobId}`)
}

/** 可选执行器；任务表单必须用下拉，避免手填一个不存在的执行器。 */
export function listJobHandlers() {
  return http.get('/admin/monitor/job/handlers')
}

export function addJob(data) {
  return http.post('/admin/monitor/job', data)
}

export function updateJob(jobId, data) {
  return http.put(`/admin/monitor/job/${jobId}`, data)
}

export function deleteJob(jobId) {
  return http.delete(`/admin/monitor/job/${jobId}`)
}

/** 启停；status：0 正常 / 1 暂停。 */
export function changeJobStatus(jobId, status) {
  return http.put(`/admin/monitor/job/${jobId}/status`, null, { params: { status } })
}

/** 立即执行一次（异步，结果去日志里看）。 */
export function runJob(jobId) {
  return http.post(`/admin/monitor/job/${jobId}/run`)
}

export function listJobLogs(params) {
  return http.get('/admin/monitor/job/log/list', { params })
}

export function cleanJobLogs() {
  return http.delete('/admin/monitor/job/log/clean')
}
