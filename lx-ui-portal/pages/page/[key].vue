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

const pageTemplate = computed(() => resolvePageTemplate(page.value?.template))

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

    <component :is="pageTemplate" :page="page" />
  </article>
</template>
