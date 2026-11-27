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
const { t } = usePortalI18n()

const tagKey = String(route.params.key || '')
const pageNum = Math.max(1, Number(route.query.page) || 1)
const pageSize = Number(config.public.pageSize) || 9
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: tags } = await useAsyncData('portal-tags', api.fetchTags)
const tag = computed(() => (tags.value || []).find((item) => item.tagKey === tagKey) || null)
if (!tag.value) {
  throw createError({ statusCode: 404, statusMessage: t('tag.notFound') })
}

const { data: result } = await useAsyncData(`tag-${tagKey}-${pageNum}`, () =>
  api.fetchArticles({ tagKey, pageNum, pageSize })
)

const articles = computed(() => result.value?.records || [])
const total = computed(() => Number(result.value?.total) || 0)

useSeoMeta({
  title: () => `#${tag.value?.tagName || tagKey}`,
  description: () => t('tag.seoDescription', { name: tag.value?.tagName || tagKey }),
  ogType: 'website',
  ogUrl: () => `${siteUrl}/tag/${encodeURIComponent(tagKey)}`
})

useHead({
  link: [{ rel: 'canonical', href: `${siteUrl}/tag/${encodeURIComponent(tagKey)}` }]
})
</script>

<template>
  <div class="list-page tag-page">
    <nav class="breadcrumb" :aria-label="t('common.breadcrumb')">
      <NuxtLink to="/">{{ t('common.home') }}</NuxtLink>
      <span class="breadcrumb-sep" aria-hidden="true">/</span>
      <span>{{ t('tag.breadcrumb') }}</span>
      <span class="breadcrumb-sep" aria-hidden="true">/</span>
      <span>{{ tag?.tagName }}</span>
    </nav>

    <header class="page-head">
      <p class="eyebrow">{{ t('tag.eyebrow') }}</p>
      <h1 class="page-title">#{{ tag?.tagName }}</h1>
      <p class="page-count">{{ t('tag.count', { count: total }) }}</p>
      <nav v-if="tags && tags.length > 1" class="tag-cloud" :aria-label="t('tag.all')">
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
      <template #title>{{ t('tag.empty.title') }}</template>
      <template #description>{{ t('tag.empty.desc') }}</template>
    </EmptyState>

    <PortalPager
      :page="pageNum"
      :page-size="pageSize"
      :total="total"
      :base-path="`/tag/${tagKey}`"
    />
  </div>
</template>
