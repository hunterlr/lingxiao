<script setup lang="ts">
import type { PortalArticleSummary, PortalCategory } from '~/types/portal'

/**
 * 图集列表模板（list.gallery）：封面优先的网格。
 *
 * <p>没有封面的文章用标题首字占位，避免出现破图；
 * 卡片整体可点击，标题仍是真实链接文本，利于 SEO。</p>
 */
defineProps<{
  category?: PortalCategory | null
  articles: PortalArticleSummary[]
}>()
</script>

<template>
  <div class="gallery-grid template-list-gallery">
    <NuxtLink
      v-for="article in articles"
      :key="article.articleId"
      :to="`/article/${article.articleId}`"
      class="gallery-card"
    >
      <div class="gallery-media">
        <img v-if="mediaUrl(article.cover, 'medium')" :src="mediaUrl(article.cover, 'medium')!" :alt="article.title" />
        <span v-else class="gallery-placeholder" aria-hidden="true">{{ article.title.slice(0, 1) }}</span>
      </div>
      <div class="gallery-caption">
        <h2>{{ article.title }}</h2>
        <p v-if="article.summary">{{ article.summary }}</p>
        <div class="news-meta">
          <span v-if="article.publishTime">{{ formatDay(article.publishTime) }}</span>
          <span>阅读 {{ formatCount(article.viewCount) }}</span>
        </div>
      </div>
    </NuxtLink>
  </div>
</template>
