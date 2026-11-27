<script setup lang="ts">
import type { PortalArticleDetail } from '~/types/portal'

const { t } = usePortalI18n()

/**
 * 默认详情模板（detail.default）：标题、封面、富文本正文依次排列。
 *
 * <p>正文已由后端按白名单清洗，模板只做展示，不再解析或信任原始 HTML。
 * 互动、评论与相关阅读属于页面级功能，留在文章页，不随模板切换。</p>
 */
const props = defineProps<{ article: PortalArticleDetail }>()

const cover = computed(() => mediaUrl(props.article.cover, 'medium'))
const tags = computed(() => props.article.tags || [])
</script>

<template>
  <div class="template-detail-default">
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
      <ul v-if="tags.length" class="tag-list" :aria-label="t('article.tags')">
        <li v-for="tag in tags" :key="tag.tagId" class="tag">
          <NuxtLink :to="`/tag/${tag.tagKey}`">{{ tag.tagName }}</NuxtLink>
        </li>
      </ul>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="article.title" />
    </figure>

    <div class="article-content" v-html="article.content" />
  </div>
</template>
