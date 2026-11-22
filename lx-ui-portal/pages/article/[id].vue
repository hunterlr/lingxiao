<script setup lang="ts">
import type { MemberInteraction } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()
// 会员 composable 必须在首个 await 前初始化，避免异步 setup 丢上下文
const { isLoggedIn, fetchInteraction, setFavorite, setLike } = useMemberAuth()

const articleId = String(route.params.id || '')
if (!/^\d+$/.test(articleId)) {
  throw createError({ statusCode: 404, statusMessage: '文章不存在' })
}

const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: article } = await useAsyncData(`article-${articleId}`, () => api.fetchArticle(articleId))

if (!article.value) {
  throw createError({ statusCode: 404, statusMessage: '文章不存在或尚未发布' })
}

// 会员模块关闭 / 网络异常时只回落为 0，不影响正文阅读
const interaction = ref<MemberInteraction>({
  articleId,
  favoriteCount: 0,
  likeCount: 0,
  favorited: false,
  liked: false
})
try {
  interaction.value = await fetchInteraction(articleId)
} catch {
  // 匿名或会员功能关闭时保持默认值
}

const interactionPending = ref(false)
const interactionError = ref('')

function loginTarget(): string {
  return `/login?redirect=${encodeURIComponent(route.fullPath)}`
}

async function ensureMember(): Promise<boolean> {
  if (isLoggedIn.value) return true
  await navigateTo(loginTarget())
  return false
}

function handleInteractionError(error: unknown, fallback: string) {
  if (error instanceof MemberApiError && error.code === 1002) {
    // 会话已失效：回登录页，登录后回到当前文章
    navigateTo(loginTarget())
    return
  }
  interactionError.value = memberErrorMessage(error, fallback)
}

async function toggleFavorite() {
  if (interactionPending.value) return
  if (!(await ensureMember())) return
  interactionError.value = ''
  interactionPending.value = true
  try {
    interaction.value = await setFavorite(articleId, !interaction.value.favorited)
  } catch (error) {
    handleInteractionError(error, '收藏失败，请稍后再试')
  } finally {
    interactionPending.value = false
  }
}

async function toggleLike() {
  if (interactionPending.value) return
  if (!(await ensureMember())) return
  interactionError.value = ''
  interactionPending.value = true
  try {
    interaction.value = await setLike(articleId, !interaction.value.liked)
  } catch (error) {
    handleInteractionError(error, '点赞失败，请稍后再试')
  } finally {
    interactionPending.value = false
  }
}

const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
const { data: related } = await useAsyncData(`article-related-${articleId}`, async () => {
  const categoryKey = article.value?.category?.categoryKey
  if (!categoryKey) return []
  const result = await api.fetchArticles({ categoryKey, pageNum: 1, pageSize: 5 })
  return result.records.filter((item) => item.articleId !== articleId).slice(0, 3)
})

const cover = computed(() => mediaUrl(article.value?.cover, 'medium'))
const tags = computed(() => article.value?.tags || [])
const trail = computed(() =>
  article.value?.category ? findCategoryTrail(categories.value || [], article.value.category.categoryKey) : []
)
const absoluteCover = computed(() => {
  const value = mediaUrl(article.value?.cover)
  if (!value) return undefined
  return value.startsWith('http') ? value : `${siteUrl}${value}`
})
const sourceHref = computed(() => {
  const value = article.value?.sourceUrl?.trim()
  return value && /^https?:\/\//i.test(value) ? value : null
})

useSeoMeta({
  title: () => article.value?.title || '文章',
  description: () => article.value?.summary || site.value?.seoDescription || '',
  ogTitle: () => article.value?.title || '',
  ogDescription: () => article.value?.summary || '',
  ogType: 'article',
  ogUrl: () => `${siteUrl}/article/${articleId}`,
  ogImage: () => absoluteCover.value,
  articlePublishedTime: () => article.value?.publishTime || undefined,
  articleAuthor: () => (article.value?.authorName ? [article.value.authorName] : undefined),
  twitterCard: () => (absoluteCover.value ? 'summary_large_image' : 'summary')
})

useHead(() => {
  const current = article.value
  const jsonLd = current
    ? JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'NewsArticle',
        headline: current.title,
        description: current.summary || undefined,
        datePublished: current.publishTime || undefined,
        dateModified: current.publishTime || undefined,
        author: current.authorName ? { '@type': 'Person', name: current.authorName } : undefined,
        image: absoluteCover.value ? [absoluteCover.value] : undefined,
        mainEntityOfPage: `${siteUrl}/article/${articleId}`
      }).replace(/</g, '\\u003c')
    : ''

  return {
    link: [{ rel: 'canonical', href: `${siteUrl}/article/${articleId}` }],
    script: jsonLd
      ? [{ type: 'application/ld+json', innerHTML: jsonLd }]
      : []
  }
})
</script>

<template>
  <article v-if="article" class="article-page">
    <nav class="breadcrumb" aria-label="面包屑">
      <NuxtLink to="/">首页</NuxtLink>
      <template v-for="item in trail" :key="item.categoryId">
        <span class="breadcrumb-sep" aria-hidden="true">/</span>
        <NuxtLink :to="`/category/${item.categoryKey}`">{{ item.categoryName }}</NuxtLink>
      </template>
    </nav>

    <header class="article-header">
      <p v-if="article.category" class="eyebrow">
        <NuxtLink :to="`/category/${article.category.categoryKey}`">{{ article.category.categoryName }}</NuxtLink>
      </p>
      <h1 class="article-title">{{ article.title }}</h1>
      <p v-if="article.summary" class="article-summary">{{ article.summary }}</p>
      <div class="article-meta">
        <span v-if="article.authorName">{{ article.authorName }}</span>
        <time v-if="article.publishTime" :datetime="article.publishTime">{{ formatDate(article.publishTime) }}</time>
        <span v-if="article.viewCount != null">阅读 {{ formatCount(article.viewCount) }}</span>
      </div>
      <ul v-if="tags.length" class="tag-list" aria-label="文章标签">
        <li v-for="tag in tags" :key="tag.tagId" class="tag">{{ tag.tagName }}</li>
      </ul>
    </header>

    <figure v-if="cover" class="article-cover">
      <img :src="cover" :alt="article.title" />
    </figure>

    <!-- 正文已由后端 HtmlSanitizer 按白名单清洗，门户不再二次解析 -->
    <div class="article-content" v-html="article.content" />

    <div class="interaction-bar" aria-label="文章互动">
      <button
        type="button"
        class="interaction-button"
        :class="{ 'is-active': interaction.favorited }"
        :disabled="interactionPending"
        @click="toggleFavorite"
      >
        <span aria-hidden="true">{{ interaction.favorited ? '★' : '☆' }}</span>
        {{ interaction.favorited ? '已收藏' : '收藏' }}
        <span class="interaction-count">{{ formatCount(interaction.favoriteCount) }}</span>
      </button>
      <button
        type="button"
        class="interaction-button"
        :class="{ 'is-active': interaction.liked }"
        :disabled="interactionPending"
        @click="toggleLike"
      >
        <span aria-hidden="true">♥</span>
        {{ interaction.liked ? '已点赞' : '点赞' }}
        <span class="interaction-count">{{ formatCount(interaction.likeCount) }}</span>
      </button>
    </div>
    <p v-if="interactionError" class="form-alert error interaction-alert" role="alert">
      {{ interactionError }}
    </p>
    <p v-else-if="!isLoggedIn" class="form-hint interaction-hint">
      登录后可收藏与点赞，收藏内容可在个人中心查看。
    </p>

    <footer class="article-footer">
      <p v-if="article.source">
        来源：
        <a v-if="sourceHref" :href="sourceHref" target="_blank" rel="noopener noreferrer">
          {{ article.source }}
        </a>
        <span v-else>{{ article.source }}</span>
      </p>
      <NuxtLink class="button ghost" to="/">返回首页</NuxtLink>
    </footer>

    <section v-if="related.length" class="section related">
      <div class="section-head">
        <h2 class="section-title">同栏目更多</h2>
      </div>
      <div class="article-grid">
        <ArticleCard v-for="item in related" :key="item.articleId" :article="item" />
      </div>
    </section>
  </article>
</template>
