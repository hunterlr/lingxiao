<script setup lang="ts">
/**
 * 标签独立页。
 *
 * <p>复用公开的 `/tags` 与 `/articles?tagKey=` 接口，不新增后端端点；
 * 标签标识不存在或已停用时按 404 处理，避免空页面被收录。</p>
 */
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()

const tagKey = String(route.params.key || '')
const pageNum = Math.max(1, Number(route.query.page) || 1)
const pageSize = Number(config.public.pageSize) || 9
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: tags } = await useAsyncData('portal-tags', api.fetchTags)
const tag = computed(() => (tags.value || []).find((item) => item.tagKey === tagKey) || null)
if (!tag.value) {
  throw createError({ statusCode: 404, statusMessage: '标签不存在或已下线' })
}

const { data: result } = await useAsyncData(`tag-${tagKey}-${pageNum}`, () =>
  api.fetchArticles({ tagKey, pageNum, pageSize })
)

const articles = computed(() => result.value?.records || [])
const total = computed(() => Number(result.value?.total) || 0)

useSeoMeta({
  title: () => `#${tag.value?.tagName || tagKey}`,
  description: () => `浏览标签「${tag.value?.tagName || tagKey}」下的全部文章。`,
  ogType: 'website',
  ogUrl: () => `${siteUrl}/tag/${encodeURIComponent(tagKey)}`
})

useHead({
  link: [{ rel: 'canonical', href: `${siteUrl}/tag/${encodeURIComponent(tagKey)}` }]
})
</script>

<template>
  <div class="list-page tag-page">
    <nav class="breadcrumb" aria-label="面包屑">
      <NuxtLink to="/">首页</NuxtLink>
      <span class="breadcrumb-sep" aria-hidden="true">/</span>
      <span>标签</span>
      <span class="breadcrumb-sep" aria-hidden="true">/</span>
      <span>{{ tag?.tagName }}</span>
    </nav>

    <header class="page-head">
      <p class="eyebrow">标签</p>
      <h1 class="page-title">#{{ tag?.tagName }}</h1>
      <p class="page-count">共 {{ total }} 篇文章</p>
      <nav v-if="tags && tags.length > 1" class="tag-cloud" aria-label="全部标签">
        <NuxtLink
          v-for="item in tags"
          :key="item.tagId"
          :to="`/tag/${item.tagKey}`"
          :class="{ 'is-active': item.tagKey === tagKey }"
        >
          #{{ item.tagName }}
        </NuxtLink>
      </nav>
    </header>

    <div v-if="articles.length" class="article-grid">
      <ArticleCard v-for="article in articles" :key="article.articleId" :article="article" />
    </div>
    <EmptyState v-else>
      <template #title>该标签暂无文章</template>
      <template #description>文章发布并绑定该标签后会自动出现在这里。</template>
    </EmptyState>

    <PortalPager
      :page="pageNum"
      :page-size="pageSize"
      :total="total"
      :base-path="`/tag/${tagKey}`"
    />
  </div>
</template>
