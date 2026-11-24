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

export function scheduleArticle(articleId, data) {
  return http.put(`/admin/cms/article/${articleId}/schedule`, data)
}

export function cancelScheduleArticle(articleId) {
  return http.put(`/admin/cms/article/${articleId}/schedule/cancel`)
}

export function deleteArticle(articleId) {
  return http.delete(`/admin/cms/article/${articleId}`)
}

/* ==================== 文章内容版本 ==================== */

export function listArticleVersions(articleId, params) {
  return http.get(`/admin/cms/article/${articleId}/versions`, { params })
}

export function getArticleVersion(articleId, versionId) {
  return http.get(`/admin/cms/article/${articleId}/versions/${versionId}`)
}

export function rollbackArticleVersion(articleId, versionId) {
  return http.put(`/admin/cms/article/${articleId}/versions/${versionId}/rollback`)
}

/* ==================== 文章回收站 ==================== */

export function listRecycleArticles(params) {
  return http.get('/admin/cms/article/recycle/list', { params })
}

export function restoreArticle(articleId) {
  return http.put(`/admin/cms/article/${articleId}/restore`)
}

export function purgeArticle(articleId) {
  return http.delete(`/admin/cms/article/${articleId}/purge`)
}

/* ==================== 内容模型 / 字段 ==================== */

export function listModels(params) {
  return http.get('/admin/cms/model/list', { params })
}

export function listModelOptions(siteId) {
  return http.get('/admin/cms/model/options', { params: { siteId } })
}

export function getModel(modelId) {
  return http.get(`/admin/cms/model/${modelId}`)
}

export function addModel(data) {
  return http.post('/admin/cms/model', data)
}

export function updateModel(modelId, data) {
  return http.put(`/admin/cms/model/${modelId}`, data)
}

export function deleteModel(modelId) {
  return http.delete(`/admin/cms/model/${modelId}`)
}

export function listModelFields(modelId) {
  return http.get(`/admin/cms/model/${modelId}/fields`)
}

export function addModelField(modelId, data) {
  return http.post(`/admin/cms/model/${modelId}/field`, data)
}

export function updateModelField(modelId, fieldId, data) {
  return http.put(`/admin/cms/model/${modelId}/field/${fieldId}`, data)
}

export function deleteModelField(modelId, fieldId) {
  return http.delete(`/admin/cms/model/${modelId}/field/${fieldId}`)
}

/* ==================== 单页 ==================== */

export function listPages(params) {
  return http.get('/admin/cms/page/list', { params })
}

export function getPage(pageId) {
  return http.get(`/admin/cms/page/${pageId}`)
}

export function addPage(data) {
  return http.post('/admin/cms/page', data)
}

export function updatePage(pageId, data) {
  return http.put(`/admin/cms/page/${pageId}`, data)
}

export function deletePage(pageId) {
  return http.delete(`/admin/cms/page/${pageId}`)
}

export function publishPage(pageId) {
  return http.put(`/admin/cms/page/${pageId}/publish`)
}

export function offlinePage(pageId) {
  return http.put(`/admin/cms/page/${pageId}/offline`)
}

/* ==================== 导航 ==================== */

export function listNavs(siteId) {
  return http.get('/admin/cms/nav/list', { params: { siteId } })
}

export function getNav(navId) {
  return http.get(`/admin/cms/nav/${navId}`)
}

export function addNav(data) {
  return http.post('/admin/cms/nav', data)
}

export function updateNav(navId, data) {
  return http.put(`/admin/cms/nav/${navId}`, data)
}

export function deleteNav(navId) {
  return http.delete(`/admin/cms/nav/${navId}`)
}

export function listNavItems(navId) {
  return http.get(`/admin/cms/nav/${navId}/items`)
}

export function addNavItem(navId, data) {
  return http.post(`/admin/cms/nav/${navId}/items`, data)
}

export function updateNavItem(navId, itemId, data) {
  return http.put(`/admin/cms/nav/${navId}/items/${itemId}`, data)
}

export function deleteNavItem(navId, itemId) {
  return http.delete(`/admin/cms/nav/${navId}/items/${itemId}`)
}

/* ==================== 区块 / 轮播 ==================== */

export function listBlocks(params) {
  return http.get('/admin/cms/block/list', { params })
}

export function getBlock(blockId) {
  return http.get(`/admin/cms/block/${blockId}`)
}

export function addBlock(data) {
  return http.post('/admin/cms/block', data)
}

export function updateBlock(blockId, data) {
  return http.put(`/admin/cms/block/${blockId}`, data)
}

export function deleteBlock(blockId) {
  return http.delete(`/admin/cms/block/${blockId}`)
}

export function listBanners(params) {
  return http.get('/admin/cms/banner/list', { params })
}

export function getBanner(bannerId) {
  return http.get(`/admin/cms/banner/${bannerId}`)
}

export function addBanner(data) {
  return http.post('/admin/cms/banner', data)
}

export function updateBanner(bannerId, data) {
  return http.put(`/admin/cms/banner/${bannerId}`, data)
}

export function deleteBanner(bannerId) {
  return http.delete(`/admin/cms/banner/${bannerId}`)
}

/* ==================== 草稿预览票据 ==================== */

export function createArticlePreviewTicket(articleId) {
  return http.post(`/admin/cms/article/${articleId}/preview-ticket`)
}

export function createPagePreviewTicket(pageId) {
  return http.post(`/admin/cms/page/${pageId}/preview-ticket`)
}
