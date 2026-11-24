<script setup lang="ts">
import type { PortalArticleSummary, PortalCategory } from '~/types/portal'

/**
 * 默认列表模板（list.default）：三列卡片网格。
 *
 * <p>模板组件只负责文章区版式；面包屑、栏目标题与分页留在页面里，
 * 这样切换模板不会丢掉 SEO 头部与翻页状态。</p>
 */
defineProps<{
  category?: PortalCategory | null
  articles: PortalArticleSummary[]
}>()
</script>

<template>
  <div class="article-grid template-list-default">
    <ArticleCard v-for="article in articles" :key="article.articleId" :article="article" />
  </div>
</template>
