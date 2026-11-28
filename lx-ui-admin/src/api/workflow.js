import http from './request'

/**
 * 工作流接口，对应 /api/admin/workflow/**。
 *
 * <p>模块关闭时后端整组 Controller 不装配，这些请求会得到 404；
 * 前端菜单也不会下发，正常不会触发。</p>
 */

/* ==================== 流程定义 ==================== */

export function listProcessDefs(params) {
  return http.get('/admin/workflow/process/list', { params })
}

export function listProcessCategories() {
  return http.get('/admin/workflow/process/categories')
}

export function importProcess(formData) {
  return http.post('/admin/workflow/process/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function changeProcessStatus(defId, status) {
  return http.put(`/admin/workflow/process/${defId}/status`, null, { params: { status } })
}

export function deleteProcess(defId) {
  return http.delete(`/admin/workflow/process/${defId}`)
}

/* ==================== 实例 ==================== */

export function startInstance(data) {
  return http.post('/admin/workflow/instance/start', data)
}

export function listMyInstances(params) {
  return http.get('/admin/workflow/instance/mine', { params })
}

export function getInstance(processInstanceId) {
  return http.get(`/admin/workflow/instance/${processInstanceId}`)
}

export function withdrawInstance(processInstanceId) {
  return http.put(`/admin/workflow/instance/${processInstanceId}/withdraw`)
}

/* ==================== 任务 ==================== */

export function listTodo(params) {
  return http.get('/admin/workflow/task/todo', { params })
}

export function listDone(params) {
  return http.get('/admin/workflow/task/done', { params })
}

export function completeTask(taskId, data) {
  return http.post(`/admin/workflow/task/${taskId}/complete`, data)
}

export function rejectTask(taskId, data) {
  return http.post(`/admin/workflow/task/${taskId}/reject`, data)
}