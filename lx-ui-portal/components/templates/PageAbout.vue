<script setup lang="ts">
import type { PortalPage } from '~/types/portal'

/**
 * 「关于我们」单页模板（page.about）：窄栏、引文式摘要与留白。
 *
 * <p>适合企业介绍、团队、联系方式等单页；正文仍复用统一富文本样式。</p>
 */
const props = defineProps<{ page: PortalPage }>()

const cover = computed(() => mediaUrl(props.page.cover, 'medium'))
</script>

<template>
  <div class="template-page-about about-page">
    <header class="page-head">
      <p class="eyebrow">关于</p>
      <h1 class="page-title">{{ page.title || page.pageName }}</h1>
      <p v-if="page.publishTime" class="article-meta">
        <time :datetime="page.publishTime">更新于 {{ formatDate(page.publishTime) }}</time>
      </p>
    </header>

    <p v-if="page.summary" class="about-lead">{{ page.summary }}</p>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="page.title || page.pageName" />
    </figure>

    <div class="article-content" v-html="page.content" />
  </div>
</template>
