/* ==================== 内容多语言（人工翻译） ==================== */

function i18nBase(ownerType, ownerId) {
  return `/admin/cms/${ownerType === 'page' ? 'page' : 'article'}/${ownerId}/i18n`
}

export function listContentI18n(ownerType, ownerId) {
  return http.get(i18nBase(ownerType, ownerId))
}

export function saveContentI18n(ownerType, ownerId, lang, data) {
  return http.put(`${i18nBase(ownerType, ownerId)}/${encodeURIComponent(lang)}`, data)
}

export function publishContentI18n(ownerType, ownerId, lang) {
  return http.post(`${i18nBase(ownerType, ownerId)}/${encodeURIComponent(lang)}/publish`)
}

export function unpublishContentI18n(ownerType, ownerId, lang) {
  return http.post(`${i18nBase(ownerType, ownerId)}/${encodeURIComponent(lang)}/unpublish`)
}

export function deleteContentI18n(ownerType, ownerId, lang) {
  return http.delete(`${i18nBase(ownerType, ownerId)}/${encodeURIComponent(lang)}`)
}
