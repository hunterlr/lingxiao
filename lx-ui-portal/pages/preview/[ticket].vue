<script setup lang="ts">
const route = useRoute()
const api = usePortalApi()

const ticket = String(route.params.ticket || '')
const { data: preview } = await useAsyncData(`preview-${ticket}`, () => api.fetchPreview(ticket))

if (!preview.value) {
  throw createError({ statusCode: 404, statusMessage: '预览票据无效、已过期或已使用' })
}

const article = computed(() => preview.value?.article || null)
const page = computed(() => preview.value?.page || null)
const cover = computed(() => mediaUrl(article.value?.cover || page.value?.cover, 'medium'))

useSeoMeta({
  title: () => article.value?.title || page.value?.pageName || '内容预览',
  robots: 'noindex, nofollow'
})
</script>

<template>
  <article v-if="article" class="article-page">
    <div class="preview-banner" role="status">草稿预览 · 票据仅可使用一次，关闭后需重新生成</div>
    <header class="article-header">
      <p v-if="article.category" class="eyebrow">{{ article.category.categoryName }}</p>
      <h1 class="article-title">{{ article.title }}</h1>
      <p v-if="article.summary" class="article-summary">{{ article.summary }}</p>
      <div class="article-meta">
        <span v-if="article.authorName">{{ article.authorName }}</span>
        <time v-if="article.publishTime" :datetime="article.publishTime">{{ formatDate(article.publishTime) }}</time>
      </div>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="article.title" />
    </figure>

    <div class="article-content" v-html="article.content" />
  </article>

  <article v-else-if="page" class="simple-page">
    <div class="preview-banner" role="status">草稿预览 · 票据仅可使用一次，关闭后需重新生成</div>
    <header class="page-head">
      <p class="eyebrow">单页</p>
      <h1 class="page-title">{{ page.title || page.pageName }}</h1>
      <p v-if="page.summary" class="page-description">{{ page.summary }}</p>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="page.title || page.pageName" />
    </figure>

    <div class="article-content" v-html="page.content" />
  </article>
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
