<script setup lang="ts">
import type { PortalArticleDetail } from '~/types/portal'

const { t } = usePortalI18n()

/**
 * 图集详情模板（detail.gallery）：封面优先，正文前展示扩展图集。
 *
 * <p>图集图片来自模型扩展字段 `images`（fileId 或外链），
 * 没有扩展字段时退化为普通详情，不显示空网格。</p>
 */
const props = defineProps<{ article: PortalArticleDetail }>()

const cover = computed(() => mediaUrl(props.article.cover, 'medium'))
const tags = computed(() => props.article.tags || [])
const images = computed(() => galleryImages(props.article.ext?.images))
</script>

<template>
  <div class="template-detail-gallery detail-gallery">
    <header class="article-header">
      <p v-if="article.category" class="eyebrow">
        <NuxtLink :to="`/category/${article.category.categoryKey}`">{{ article.category.categoryName }}</NuxtLink>
      </p>
      <h1 class="article-title">{{ article.title }}</h1>
      <p v-if="article.summary" class="article-summary">{{ article.summary }}</p>
      <div class="article-meta">
        <span v-if="article.authorName">{{ article.authorName }}</span>
        <time v-if="article.publishTime" :datetime="article.publishTime">{{ formatDate(article.publishTime) }}</time>
        <span v-if="article.viewCount != null">{{ t('common.readCount', { count: formatCount(article.viewCount) }) }}</span>
      </div>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="article.title" />
    </figure>

    <div v-if="images.length" class="gallery-ext" :aria-label="t('article.gallery')">
      <img v-for="(image, index) in images" :key="`${image}-${index}`" :src="image" :alt="t('article.galleryAlt', { title: article.title, index: index + 1 })" />
    </div>

    <div class="article-content" v-html="article.content" />

    <ul v-if="tags.length" class="tag-list" :aria-label="t('article.tags')">
      <li v-for="tag in tags" :key="tag.tagId" class="tag">
        <NuxtLink :to="`/tag/${tag.tagKey}`">{{ tag.tagName }}</NuxtLink>
      </li>
    </ul>
  </div>
</template>
