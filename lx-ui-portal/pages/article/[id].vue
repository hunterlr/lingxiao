<script setup lang="ts">
import type { MemberCommentItem, MemberInteraction } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

definePageMeta({
  key: (route) => route.fullPath
})

const route = useRoute()
const api = usePortalApi()
const config = useRuntimeConfig()
// 会员 composable 必须在首个 await 前初始化，避免异步 setup 丢上下文
const { isLoggedIn, fetchInteraction, setFavorite, setLike, fetchComments, submitComment } = useMemberAuth()

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

// ==================== 评论 ====================

const COMMENT_PAGE_SIZE = 5
const comments = ref<MemberCommentItem[]>([])
const commentTotal = ref(0)
const commentPage = ref(0)
const commentsLoading = ref(false)
// 评论开关关闭或接口不可用时置为 false：隐藏评论框，只保留明确提示
const commentsEnabled = ref(true)
const commentsError = ref('')
const commentContent = ref('')
const commentSubmitting = ref(false)
const commentFormError = ref('')
const commentNotice = ref('')

const commentHasMore = computed(() => comments.value.length < commentTotal.value)

/**
 * 读取公开评论。
 *
 * <p>首屏在 SSR 里加载，客户端复用同一份状态；「加载更多」在同一页内追加，
 * 不改 URL，避免打开评论分页时把文章地址复制成一堆查询参数。</p>
 */
async function loadComments(reset = false) {
  if (commentsLoading.value) return
  commentsLoading.value = true
  commentsError.value = ''
  try {
    const targetPage = reset ? 1 : commentPage.value + 1
    const result = await fetchComments(articleId, targetPage, COMMENT_PAGE_SIZE)
    comments.value = reset ? result.records : [...comments.value, ...result.records]
    commentTotal.value = Number(result.total) || 0
    commentPage.value = targetPage
    commentsEnabled.value = true
  } catch (error) {
    // 评论开关关闭（1003）与网络异常都按“评论暂不可用”处理：
    // 不展示评论框，只显示后端或兜底文案。
    if (error instanceof MemberApiError && error.code === 1003) {
      commentsEnabled.value = false
    }
    commentsError.value = memberErrorMessage(error, '评论暂不可用，请稍后再试')
  } finally {
    commentsLoading.value = false
  }
}

await loadComments(true)

async function handleSubmitComment() {
  if (commentSubmitting.value) return
  commentFormError.value = ''
  commentNotice.value = ''

  const content = commentContent.value.trim()
  if (!content) {
    commentFormError.value = '评论内容不能为空'
    return
  }
  if (content.length > 1000) {
    commentFormError.value = '评论内容不能超过 1000 个字符'
    return
  }
  if (!(await ensureMember())) return

  commentSubmitting.value = true
  try {
    await submitComment(articleId, content)
    commentContent.value = ''
    commentNotice.value = '评论已提交，审核通过后会展示在下方。'
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1002) {
      navigateTo(loginTarget())
      return
    }
    if (error instanceof MemberApiError && error.code === 1003) {
      commentsEnabled.value = false
      commentsError.value = memberErrorMessage(error, '评论功能已关闭')
      return
    }
    commentFormError.value = memberErrorMessage(error, '评论提交失败，请稍后再试')
  } finally {
    commentSubmitting.value = false
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

    <section class="comments" aria-labelledby="comments-title">
      <div class="section-head">
        <h2 id="comments-title" class="section-title">
          评论<span v-if="commentTotal" class="comments-total">{{ formatCount(commentTotal) }}</span>
        </h2>
      </div>

      <p v-if="commentsError" class="form-alert error" role="alert">{{ commentsError }}</p>

      <template v-if="commentsEnabled">
        <ul v-if="comments.length" class="comment-list">
          <li v-for="item in comments" :key="item.commentId" class="comment-item">
            <span class="comment-avatar">
              <img
                v-if="mediaUrl(item.avatar, 'thumb')"
                :src="mediaUrl(item.avatar, 'thumb')!"
                :alt="item.nickname || '会员'"
              />
              <span v-else aria-hidden="true">{{ (item.nickname || '会').slice(0, 1) }}</span>
            </span>
            <div class="comment-body">
              <div class="comment-meta">
                <strong>{{ item.nickname || '会员' }}</strong>
                <time v-if="item.createTime" :datetime="item.createTime">{{ formatDate(item.createTime) }}</time>
              </div>
              <p class="comment-content">{{ item.content }}</p>
            </div>
          </li>
        </ul>
        <p v-else-if="!commentsLoading" class="comment-empty">还没有评论，来写下第一条吧。</p>

        <div v-if="commentHasMore" class="comment-more">
          <button type="button" class="button ghost" :disabled="commentsLoading" @click="loadComments()">
            {{ commentsLoading ? '加载中…' : '加载更多评论' }}
          </button>
        </div>

        <form v-if="isLoggedIn" class="comment-form" @submit.prevent="handleSubmitComment">
          <label class="comment-label" for="comment-content">发表评论</label>
          <textarea
            id="comment-content"
            v-model="commentContent"
            maxlength="1000"
            rows="4"
            placeholder="说点什么…（提交后需审核）"
          />
          <div class="comment-form-footer">
            <span class="form-hint">{{ commentContent.length }} / 1000</span>
            <button type="submit" class="button" :disabled="commentSubmitting">
              {{ commentSubmitting ? '提交中…' : '提交评论' }}
            </button>
          </div>
          <p v-if="commentFormError" class="form-alert error comment-feedback" role="alert">
            {{ commentFormError }}
          </p>
          <p v-else-if="commentNotice" class="form-alert success comment-feedback" role="status">
            {{ commentNotice }}
          </p>
        </form>
        <p v-else class="form-hint comment-login-hint">
          登录后可以发表评论。
          <NuxtLink :to="loginTarget()">去登录</NuxtLink>
        </p>
      </template>
    </section>

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
