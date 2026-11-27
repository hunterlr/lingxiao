<script setup lang="ts">
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()
const { t } = usePortalI18n()

const keyword = computed(() => String(route.query.keyword || '').trim())
const page = Math.max(1, Number(route.query.page) || 1)
const pageSize = Number(config.public.pageSize) || 9
const input = ref(keyword.value)

const { data: result } = await useAsyncData(
  `search-${keyword.value}-${page}`,
  () =>
    keyword.value
      ? api.search({ keyword: keyword.value, pageNum: page, pageSize })
      : Promise.resolve({ total: 0, pageNum: page, pageSize, records: [] })
)

const articles = computed(() => result.value?.records || [])
const total = computed(() => Number(result.value?.total) || 0)

function submitSearch() {
  const text = input.value.trim()
  if (!text) return
  return navigateTo({ path: '/search', query: { keyword: text } })
}

useSeoMeta({
  title: () => (keyword.value ? t('search.seoTitle', { keyword: keyword.value }) : t('search.title')),
  description: () =>
    keyword.value
      ? t('search.seoDescription', { keyword: keyword.value })
      : t('search.seoDescriptionEmpty'),
  robots: 'noindex,follow'
})
</script>

<template>
  <div class="search-page">
    <header class="page-head">
      <p class="eyebrow">{{ t('search.eyebrow') }}</p>
      <h1 class="page-title">{{ keyword ? t('search.titleWithKeyword') : t('search.title') }}</h1>
      <form class="search-form" role="search" @submit.prevent="submitSearch">
        <input v-model="input" type="search" :placeholder="t('search.placeholder')" :aria-label="t('search.label')" />
        <button type="submit" class="button">{{ t('common.search') }}</button>
      </form>
      <p v-if="keyword" class="page-count">{{ t('search.count', { keyword, count: total }) }}</p>
      <p v-else class="page-description">{{ t('search.hint') }}</p>
    </header>

    <div v-if="articles.length" class="article-grid">
      <ArticleCard v-for="article in articles" :key="article.articleId" :article="article" />
    </div>
    <EmptyState v-else-if="keyword">
      <template #title>{{ t('search.empty.title') }}</template>
      <template #description>{{ t('search.empty.desc') }}</template>
    </EmptyState>

    <PortalPager
      :page="page"
      :page-size="pageSize"
      :total="total"
      base-path="/search"
      :extra-query="{ keyword }"
    />
  </div>
</template>
