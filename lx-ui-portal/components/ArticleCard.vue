<script setup lang="ts">
import type { PortalArticleSummary } from '~/types/portal'

const props = defineProps<{
  article: PortalArticleSummary
  featured?: boolean
}>()

const { t } = usePortalI18n()

const to = computed(() => `/article/${props.article.articleId}`)
const cover = computed(() => mediaUrl(props.article.cover, 'medium'))
const initial = computed(() => props.article.title?.trim().slice(0, 1) || 'A')
</script>

<template>
  <article class="article-card" :class="{ 'is-featured': featured }">
    <NuxtLink :to="to" class="card-link" :aria-label="article.title">
      <div class="card-cover">
        <img v-if="cover" :src="cover" :alt="article.title" loading="lazy" />
        <span v-else class="cover-placeholder" aria-hidden="true">{{ initial }}</span>
      </div>
      <div class="card-body">
        <p class="card-kicker">
          <span v-if="article.categoryName" class="card-category">{{ article.categoryName }}</span>
          <time v-if="article.publishTime" :datetime="article.publishTime">
            {{ formatDay(article.publishTime) }}
          </time>
        </p>
        <h3 class="card-title">{{ article.title }}</h3>
        <p v-if="article.summary" class="card-summary">{{ article.summary }}</p>
        <p class="card-meta">
          <span v-if="article.authorName">{{ article.authorName }}</span>
          <span v-if="article.viewCount != null">{{ t('list.readCount', { count: formatCount(article.viewCount) }) }}</span>
        </p>
      </div>
    </NuxtLink>
  </article>
</template>
