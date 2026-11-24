import type { Component } from 'vue'
import DetailDefault from '~/components/templates/DetailDefault.vue'
import DetailGallery from '~/components/templates/DetailGallery.vue'
import DetailNews from '~/components/templates/DetailNews.vue'
import ListDefault from '~/components/templates/ListDefault.vue'
import ListGallery from '~/components/templates/ListGallery.vue'
import ListNews from '~/components/templates/ListNews.vue'
import PageAbout from '~/components/templates/PageAbout.vue'
import PageDefault from '~/components/templates/PageDefault.vue'

/**
 * 模板编码 → 组件的映射。
 *
 * <p>后端只返回稳定编码（list.* / detail.* / page.*），门户在这里集中维护
 * 编码与 `.vue` 组件的对应关系。未知编码一律回退默认版式，保证后台即使
 * 写入了未来版本新增的编码，旧门户也不会白屏。</p>
 *
 * <p>这里用显式 import 而不是 Nuxt 自动注册名 + `resolveComponent`：
 * 映射在 `computed` 里求值，SSR 时可能不在渲染上下文，按名字解析会拿到
 * 未注册的字符串并渲染成空标签。</p>
 */
const LIST_COMPONENTS: Record<string, Component> = {
  'list.default': ListDefault,
  'list.news': ListNews,
  'list.gallery': ListGallery
}

const DETAIL_COMPONENTS: Record<string, Component> = {
  'detail.default': DetailDefault,
  'detail.news': DetailNews,
  'detail.gallery': DetailGallery
}

const PAGE_COMPONENTS: Record<string, Component> = {
  'page.default': PageDefault,
  'page.about': PageAbout
}

function pick(map: Record<string, Component>, code: string | null | undefined, fallback: Component): Component {
  if (!code) return fallback
  return map[code] || fallback
}

/**
 * 把模型扩展字段 `images` 归一化为图片地址列表。
 *
 * <p>后台可能存数组、JSON 字符串，或 `{ url }` / `{ fileId }` 对象；
 * 门户只接受能被 `mediaUrl` 解析的值，其余静默丢弃。</p>
 */
export function galleryImages(value: unknown): string[] {
  let raw = value
  if (typeof raw === 'string') {
    try {
      raw = JSON.parse(raw)
    } catch {
      raw = [raw]
    }
  }
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (typeof item === 'string') return item
      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>
        return String(record.url || record.fileId || record.path || '')
      }
      return ''
    })
    .map((item) => mediaUrl(item, 'medium'))
    .filter((item): item is string => Boolean(item))
}

/** 栏目列表模板。 */
export function resolveListTemplate(code?: string | null) {
  return pick(LIST_COMPONENTS, code, ListDefault)
}

/** 文章详情模板。 */
export function resolveDetailTemplate(code?: string | null) {
  return pick(DETAIL_COMPONENTS, code, DetailDefault)
}

/** 单页模板。 */
export function resolvePageTemplate(code?: string | null) {
  return pick(PAGE_COMPONENTS, code, PageDefault)
}
