import http from './request'

/**
 * CMS 接口，对应 /api/admin/cms/**。
 *
 * <p>CMS 关闭时后端整组 Controller 不装配，这些请求会得到 404；
 * 前端菜单也不会下发，正常不会触发。</p>
 */

/* ==================== 站点 ==================== */

export function listSites(params) {
  return http.get('/admin/cms/site/list', { params })
}

export function listSiteOptions() {
  return http.get('/admin/cms/site/options')
}

export function getSite(siteId) {
  return http.get(`/admin/cms/site/${siteId}`)
}

export function addSite(data) {
  return http.post('/admin/cms/site', data)
}

export function updateSite(siteId, data) {
  return http.put(`/admin/cms/site/${siteId}`, data)
}

export function deleteSite(siteId) {
  return http.delete(`/admin/cms/site/${siteId}`)
}

/* ==================== 栏目 ==================== */

export function getCategoryTree(params) {
  return http.get('/admin/cms/category/tree', { params })
}

export function listCategories(siteId) {
  return http.get('/admin/cms/category/list', { params: { siteId } })
}

export function getCategory(categoryId) {
  return http.get(`/admin/cms/category/${categoryId}`)
}

export function addCategory(data) {
  return http.post('/admin/cms/category', data)
}

export function updateCategory(categoryId, data) {
  return http.put(`/admin/cms/category/${categoryId}`, data)
}

export function deleteCategory(categoryId) {
  return http.delete(`/admin/cms/category/${categoryId}`)
}

/* ==================== 标签 ==================== */

export function listTags(params) {
  return http.get('/admin/cms/tag/list', { params })
}

export function listTagOptions(siteId) {
  return http.get('/admin/cms/tag/options', { params: { siteId } })
}

export function addTag(data) {
  return http.post('/admin/cms/tag', data)
}

export function updateTag(tagId, data) {
  return http.put(`/admin/cms/tag/${tagId}`, data)
}

export function deleteTag(tagId) {
  return http.delete(`/admin/cms/tag/${tagId}`)
}

/* ==================== 文章 ==================== */

export function listArticles(params) {
  return http.get('/admin/cms/article/list', { params })
}

export function getArticle(articleId) {
  return http.get(`/admin/cms/article/${articleId}`)
}

export function addArticle(data) {
  return http.post('/admin/cms/article', data)
}

export function updateArticle(articleId, data) {
  return http.put(`/admin/cms/article/${articleId}`, data)
}

export function publishArticle(articleId) {
  return http.put(`/admin/cms/article/${articleId}/publish`)
}

export function offlineArticle(articleId) {
  return http.put(`/admin/cms/article/${articleId}/offline`)
}

export function deleteArticle(articleId) {
  return http.delete(`/admin/cms/article/${articleId}`)
}
