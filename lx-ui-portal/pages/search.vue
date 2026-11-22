<script setup lang="ts">
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()

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
  title: () => (keyword.value ? `搜索：${keyword.value}` : '搜索'),
  description: () => (keyword.value ? `站内搜索「${keyword.value}」的结果。` : '站内搜索'),
  robots: 'noindex,follow'
})
</script>

<template>
  <div class="search-page">
    <header class="page-head">
      <p class="eyebrow">站内搜索</p>
      <h1 class="page-title">{{ keyword ? '搜索结果' : '搜索文章' }}</h1>
      <form class="search-form" role="search" @submit.prevent="submitSearch">
        <input v-model="input" type="search" placeholder="输入标题或摘要关键字" aria-label="搜索关键字" />
        <button type="submit" class="button">搜索</button>
      </form>
      <p v-if="keyword" class="page-count">「{{ keyword }}」找到 {{ total }} 篇文章</p>
      <p v-else class="page-description">输入关键字，按标题与摘要匹配已发布内容。</p>
    </header>

    <div v-if="articles.length" class="article-grid">
      <ArticleCard v-for="article in articles" :key="article.articleId" :article="article" />
    </div>
    <EmptyState v-else-if="keyword">
      <template #title>没有找到相关内容</template>
      <template #description>换一个关键字，或从栏目导航继续浏览。</template>
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
