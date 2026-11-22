<script setup lang="ts">
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()

const categoryKey = String(route.params.key || '')
const page = Math.max(1, Number(route.query.page) || 1)
const pageSize = Number(config.public.pageSize) || 9
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
const trail = findCategoryTrail(categories.value || [], categoryKey)
const category = trail[trail.length - 1]
if (!category) {
  throw createError({ statusCode: 404, statusMessage: '栏目不存在或已下线' })
}

const { data: result } = await useAsyncData(`category-${categoryKey}-${page}`, () =>
  api.fetchArticles({ categoryKey, pageNum: page, pageSize })
)

const articles = computed(() => result.value?.records || [])
const total = computed(() => Number(result.value?.total) || 0)

useSeoMeta({
  title: () => category.seoTitle || category.categoryName,
  description: () => category.seoDescription || `浏览「${category.categoryName}」栏目下的全部文章。`,
  keywords: () => category.seoKeywords || '',
  ogType: 'website',
  ogUrl: () => `${siteUrl}/category/${encodeURIComponent(categoryKey)}`
})

useHead({
  link: [{ rel: 'canonical', href: `${siteUrl}/category/${encodeURIComponent(categoryKey)}` }]
})
</script>

<template>
  <div class="list-page">
    <nav class="breadcrumb" aria-label="面包屑">
      <NuxtLink to="/">首页</NuxtLink>
      <template v-for="item in trail" :key="item.categoryId">
        <span class="breadcrumb-sep" aria-hidden="true">/</span>
        <NuxtLink :to="`/category/${item.categoryKey}`">{{ item.categoryName }}</NuxtLink>
      </template>
    </nav>

    <header class="page-head">
      <p class="eyebrow">栏目</p>
      <h1 class="page-title">{{ category.categoryName }}</h1>
      <p v-if="category.seoDescription" class="page-description">{{ category.seoDescription }}</p>
      <p class="page-count">共 {{ total }} 篇文章</p>
    </header>

    <div v-if="articles.length" class="article-grid">
      <ArticleCard v-for="article in articles" :key="article.articleId" :article="article" />
    </div>
    <EmptyState v-else>
      <template #title>该栏目暂无文章</template>
      <template #description>内容发布后会按时间倒序出现在这里。</template>
    </EmptyState>

    <PortalPager
      :page="page"
      :page-size="pageSize"
      :total="total"
      :base-path="`/category/${categoryKey}`"
    />
  </div>
</template>
