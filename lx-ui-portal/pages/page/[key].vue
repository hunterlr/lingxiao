<script setup lang="ts">
definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()

const pageKey = String(route.params.key || '')
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: page } = await useAsyncData(`page-${pageKey}`, () => api.fetchPage(pageKey))

if (!page.value) {
  throw createError({ statusCode: 404, statusMessage: '页面不存在或尚未发布' })
}

const cover = computed(() => mediaUrl(page.value?.cover, 'medium'))

useSeoMeta({
  title: () => page.value?.seoTitle || page.value?.pageName || '单页',
  description: () => page.value?.seoDescription || page.value?.summary || site.value?.seoDescription || '',
  keywords: () => page.value?.seoKeywords || '',
  ogType: 'website',
  ogUrl: () => `${siteUrl}/page/${encodeURIComponent(pageKey)}`
})

useHead({
  link: [{ rel: 'canonical', href: `${siteUrl}/page/${encodeURIComponent(pageKey)}` }]
})
</script>

<template>
  <article v-if="page" class="simple-page">
    <nav class="breadcrumb" aria-label="面包屑">
      <NuxtLink to="/">首页</NuxtLink>
      <span class="breadcrumb-sep" aria-hidden="true">/</span>
      <span>{{ page.pageName }}</span>
    </nav>

    <header class="page-head">
      <p class="eyebrow">单页</p>
      <h1 class="page-title">{{ page.title || page.pageName }}</h1>
      <p v-if="page.summary" class="page-description">{{ page.summary }}</p>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="page.title || page.pageName" />
    </figure>

    <!-- 正文已由后端 HtmlSanitizer 按白名单清洗 -->
    <div class="article-content" v-html="page.content" />
  </article>
</template>
