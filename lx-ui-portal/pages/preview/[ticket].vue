<script setup lang="ts">
/**
 * 草稿 / 模板预览页。
 *
 * <p>票据一次性且 10 分钟内有效；预览页按票据覆盖的模板渲染，
 * 没有覆盖模板时使用目标自身的模板编码（文章详情由栏目列表模板推导）。
 * 预览页不渲染互动与评论，也不被搜索引擎收录。</p>
 */
const route = useRoute()
const api = usePortalApi()

const ticket = String(route.params.ticket || '')
const { data: preview } = await useAsyncData(`preview-${ticket}`, () => api.fetchPreview(ticket))

if (!preview.value) {
  throw createError({ statusCode: 404, statusMessage: '预览票据无效、已过期或已使用' })
}

const article = computed(() => preview.value?.article || null)
const page = computed(() => preview.value?.page || null)
const category = computed(() => preview.value?.category || null)
const articles = computed(() => preview.value?.articles || [])
const previewTemplate = computed(() => preview.value?.template || '')
const detailTemplate = computed(() => resolveDetailTemplate(previewTemplate.value || article.value?.template))
const pageTemplate = computed(() => resolvePageTemplate(previewTemplate.value || page.value?.template))
const listTemplate = computed(() => resolveListTemplate(previewTemplate.value || category.value?.template))

useSeoMeta({
  title: () => article.value?.title || page.value?.pageName || category.value?.categoryName || '内容预览',
  robots: 'noindex, nofollow'
})
</script>

<template>
  <div v-if="article" class="preview-page">
    <div class="preview-banner" role="status">草稿预览 · 票据仅可使用一次，关闭后需重新生成</div>
    <component :is="detailTemplate" :article="article" />
  </div>

  <div v-else-if="page" class="preview-page">
    <div class="preview-banner" role="status">草稿预览 · 票据仅可使用一次，关闭后需重新生成</div>
    <component :is="pageTemplate" :page="page" />
  </div>

  <div v-else-if="category" class="preview-page list-page">
    <div class="preview-banner" role="status">模板预览 · 票据仅可使用一次，关闭后需重新生成</div>
    <header class="page-head">
      <p class="eyebrow">栏目模板预览</p>
      <h1 class="page-title">{{ category.categoryName }}</h1>
      <p class="page-count">展示最近 {{ articles.length }} 篇已发布文章（最多 9 篇）</p>
    </header>

    <component :is="listTemplate" v-if="articles.length" :category="category" :articles="articles" />
    <EmptyState v-else>
      <template #title>该栏目暂无已发布文章</template>
      <template #description>草稿文章请使用文章预览票据；这里只展示已发布内容。</template>
    </EmptyState>
  </div>
</template>

<style scoped>
.preview-banner {
  margin-bottom: 20px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff4e5;
  color: #9a5b00;
  font-size: 14px;
}
</style>
