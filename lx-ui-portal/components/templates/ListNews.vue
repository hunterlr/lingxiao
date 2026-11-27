<script setup lang="ts">
import type { PortalArticleSummary, PortalCategory } from '~/types/portal'

const { t } = usePortalI18n()

/**
 * 新闻列表模板（list.news）：左侧日期、右侧标题与摘要的紧凑列表。
 *
 * <p>适合更新频繁的栏目；摘要与标签只展示已有字段，不额外请求接口。</p>
 */
defineProps<{
  category?: PortalCategory | null
  articles: PortalArticleSummary[]
}>()
</script>

<template>
  <ul class="news-list template-list-news">
    <li v-for="article in articles" :key="article.articleId" class="news-item">
      <time class="news-date" :datetime="article.publishTime || undefined">
        {{ formatDay(article.publishTime) || t('list.unpublished') }}
      </time>
      <div class="news-body">
        <h2 class="news-title">
          <NuxtLink :to="`/article/${article.articleId}`">{{ article.title }}</NuxtLink>
        </h2>
        <p v-if="article.summary" class="news-summary">{{ article.summary }}</p>
        <div class="news-meta">
          <span v-if="article.authorName">{{ article.authorName }}</span>
          <span v-if="article.publishTime">{{ formatDate(article.publishTime) }}</span>
          <span>{{ t('list.readCount', { count: formatCount(article.viewCount) }) }}</span>
          <NuxtLink
            v-for="tag in (article.tags || []).slice(0, 3)"
            :key="tag.tagId"
            :to="`/tag/${tag.tagKey}`"
          >
            #{{ tag.tagName }}
          </NuxtLink>
        </div>
      </div>
    </li>
  </ul>
</template>
