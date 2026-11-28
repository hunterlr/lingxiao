<script setup lang="ts">
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()
const { t } = usePortalI18n()

const categoryKey = String(route.params.key || '')
const page = Math.max(1, Number(route.query.page) || 1)
const pageSize = Number(config.public.pageSize) || 9
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
const trail = findCategoryTrail(categories.value || [], categoryKey)
const category = trail[trail.length - 1]
if (!category) {
  throw createError({ statusCode: 404, statusMessage: t('category.notFound') })
}

const { data: result } = await useAsyncData(`category-${categoryKey}-${page}`, () =>
  api.fetchArticles({ categoryKey, pageNum: page, pageSize })
)

const articles = computed(() => result.value?.records || [])
const total = computed(() => Number(result.value?.total) || 0)
// 栏目未设置模板或编码未知时回退 list.default，保证旧数据可访问
const listTemplate = computed(() => resolveListTemplate(category.template))

useSeoMeta({
  title: () => category.seoTitle || category.categoryName,
  description: () => category.seoDescription || t('category.seoDescription', { name: category.categoryName }),
  keywords: () => category.seoKeywords || '',
  ogType: 'website',
  ogUrl: () => `${siteUrl}/category/${encodeURIComponent(categoryKey)}`
})

</script>

<template>
  <div class="list-page">
    <nav class="breadcrumb" :aria-label="t('common.breadcrumb')">
      <NuxtLink to="/">{{ t('common.home') }}</NuxtLink>
      <template v-for="item in trail" :key="item.categoryId">
        <span class="breadcrumb-sep" aria-hidden="true">/</span>
        <NuxtLink :to="`/category/${item.categoryKey}`">{{ item.categoryName }}</NuxtLink>
      </template>
    </nav>

    <header class="page-head">
      <p class="eyebrow">{{ t('category.eyebrow') }}</p>
      <h1 class="page-title">{{ category.categoryName }}</h1>
      <p v-if="category.seoDescription" class="page-description">{{ category.seoDescription }}</p>
      <p class="page-count">{{ t('category.count', { count: total }) }}</p>
    </header>

    <component :is="listTemplate" v-if="articles.length" :category="category" :articles="articles" />
    <EmptyState v-else>
      <template #title>{{ t('category.empty.title') }}</template>
      <template #description>{{ t('category.empty.desc') }}</template>
    </EmptyState>

    <PortalPager
      :page="page"
      :page-size="pageSize"
      :total="total"
      :base-path="`/category/${categoryKey}`"
    />
  </div>
</template>
