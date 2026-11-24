<script setup lang="ts">
import type { PortalPage } from '~/types/portal'

/**
 * 默认单页模板（page.default）：标题、封面、正文。
 *
 * <p>面包屑由页面维护，模板只负责正文区，便于后台按模板预览。</p>
 */
const props = defineProps<{ page: PortalPage }>()

const cover = computed(() => mediaUrl(props.page.cover, 'medium'))
</script>

<template>
  <div class="template-page-default">
    <header class="page-head">
      <p class="eyebrow">单页</p>
      <h1 class="page-title">{{ page.title || page.pageName }}</h1>
      <p v-if="page.summary" class="page-description">{{ page.summary }}</p>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="page.title || page.pageName" />
    </figure>

    <!-- 正文已由后端 HtmlSanitizer 按白名单清洗 -->
    <div class="article-content" v-html="page.content" />
  </div>
</template>
