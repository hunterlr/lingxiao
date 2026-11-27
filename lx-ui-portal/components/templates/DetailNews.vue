<script setup lang="ts">
import type { PortalArticleDetail } from '~/types/portal'

const { t } = usePortalI18n()

/**
 * 新闻详情模板（detail.news）：更紧凑的标题层级与引文式摘要。
 *
 * <p>由栏目使用 `list.news` 时自动推导，保证列表与详情版式一致。</p>
 */
const props = defineProps<{ article: PortalArticleDetail }>()

const cover = computed(() => mediaUrl(props.article.cover, 'medium'))
const tags = computed(() => props.article.tags || [])
</script>

<template>
  <div class="template-detail-news detail-news">
    <header class="article-header">
      <p v-if="article.category" class="eyebrow">
        <NuxtLink :to="`/category/${article.category.categoryKey}`">{{ article.category.categoryName }}</NuxtLink>
      </p>
      <h1 class="article-title">{{ article.title }}</h1>
      <div class="article-meta">
        <span v-if="article.authorName">{{ article.authorName }}</span>
        <time v-if="article.publishTime" :datetime="article.publishTime">{{ formatDate(article.publishTime) }}</time>
        <span v-if="article.viewCount != null">{{ t('common.readCount', { count: formatCount(article.viewCount) }) }}</span>
      </div>
    </header>

    <p v-if="article.summary" class="article-summary">{{ article.summary }}</p>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="article.title" />
    </figure>

    <div class="article-content" v-html="article.content" />

    <ul v-if="tags.length" class="tag-list" :aria-label="t('article.tags')">
      <li v-for="tag in tags" :key="tag.tagId" class="tag">
        <NuxtLink :to="`/tag/${tag.tagKey}`">{{ tag.tagName }}</NuxtLink>
      </li>
    </ul>
  </div>
</template>
