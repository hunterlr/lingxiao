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
const { t } = usePortalI18n()

const articleId = String(route.params.id || '')
if (!/^\d+$/.test(articleId)) {
  throw createError({ statusCode: 404, statusMessage: t('article.notFound') })
}

const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: article } = await useAsyncData(`article-${articleId}`, () => api.fetchArticle(articleId))

if (!article.value) {
  throw createError({ statusCode: 404, statusMessage: t('article.notFound') })
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
    handleInteractionError(error, t('article.favoriteFailed'))
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
    handleInteractionError(error, t('article.likeFailed'))
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
    commentsError.value = memberErrorMessage(error, t('article.commentUnavailable'))
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
    commentFormError.value = t('article.commentRequired')
    return
  }
  if (content.length > 1000) {
    commentFormError.value = t('article.commentTooLong')
    return
  }
  if (!(await ensureMember())) return

  commentSubmitting.value = true
  try {
    await submitComment(articleId, content)
    commentContent.value = ''
    commentNotice.value = t('article.submitted')
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1002) {
      navigateTo(loginTarget())
      return
    }
    if (error instanceof MemberApiError && error.code === 1003) {
      commentsEnabled.value = false
      commentsError.value = memberErrorMessage(error, t('article.commentDisabled'))
      return
    }
    commentFormError.value = memberErrorMessage(error, t('article.commentFailed'))
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
const detailTemplate = computed(() => resolveDetailTemplate(article.value?.template))
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
  title: () => article.value?.title || t('article.defaultTitle'),
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
    <nav class="breadcrumb" :aria-label="t('common.breadcrumb')">
      <NuxtLink to="/">{{ t('common.home') }}</NuxtLink>
      <template v-for="item in trail" :key="item.categoryId">
        <span class="breadcrumb-sep" aria-hidden="true">/</span>
        <NuxtLink :to="`/category/${item.categoryKey}`">{{ item.categoryName }}</NuxtLink>
      </template>
    </nav>

    <!-- 标题 / 封面 / 正文由模板组件渲染；互动、评论与相关阅读留在页面级 -->
    <component :is="detailTemplate" :article="article" />

    <div class="interaction-bar" :aria-label="t('article.interaction')">
      <button
        type="button"
        class="interaction-button"
        :class="{ 'is-active': interaction.favorited }"
        :disabled="interactionPending"
        @click="toggleFavorite"
      >
        <span aria-hidden="true">{{ interaction.favorited ? '★' : '☆' }}</span>
        {{ interaction.favorited ? t('article.favorited') : t('article.favorite') }}
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
        {{ interaction.liked ? t('article.liked') : t('article.like') }}
        <span class="interaction-count">{{ formatCount(interaction.likeCount) }}</span>
      </button>
    </div>
    <p v-if="interactionError" class="form-alert error interaction-alert" role="alert">
      {{ interactionError }}
    </p>
    <p v-else-if="!isLoggedIn" class="form-hint interaction-hint">
      {{ t('article.loginHint') }}
    </p>

    <section class="comments" aria-labelledby="comments-title">
      <div class="section-head">
        <h2 id="comments-title" class="section-title">
          {{ t('article.comments') }}<span v-if="commentTotal" class="comments-total">{{ formatCount(commentTotal) }}</span>
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
                :alt="item.nickname || t('article.member')"
                referrerpolicy="no-referrer"
              />
              <span v-else aria-hidden="true">{{ (item.nickname || 'M').slice(0, 1) }}</span>
            </span>
            <div class="comment-body">
              <div class="comment-meta">
                <strong>{{ item.nickname || t('article.member') }}</strong>
                <time v-if="item.createTime" :datetime="item.createTime">{{ formatDate(item.createTime) }}</time>
              </div>
              <p class="comment-content">{{ item.content }}</p>
            </div>
          </li>
        </ul>
        <p v-else-if="!commentsLoading" class="comment-empty">{{ t('article.commentEmpty') }}</p>

        <div v-if="commentHasMore" class="comment-more">
          <button type="button" class="button ghost" :disabled="commentsLoading" @click="loadComments()">
            {{ commentsLoading ? t('common.loading') : t('article.loadMore') }}
          </button>
        </div>

        <form v-if="isLoggedIn" class="comment-form" @submit.prevent="handleSubmitComment">
          <label class="comment-label" for="comment-content">{{ t('article.formLabel') }}</label>
          <textarea
            id="comment-content"
            v-model="commentContent"
            maxlength="1000"
            rows="4"
            :placeholder="t('article.formPlaceholder')"
          />
          <div class="comment-form-footer">
            <span class="form-hint">{{ commentContent.length }} / 1000</span>
            <button type="submit" class="button" :disabled="commentSubmitting">
              {{ commentSubmitting ? t('article.submitting') : t('article.submit') }}
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
          {{ t('article.commentLogin') }}
          <NuxtLink :to="loginTarget()">{{ t('article.goLogin') }}</NuxtLink>
        </p>
      </template>
    </section>

    <footer class="article-footer">
      <p v-if="article.source">
        {{ t('article.source') }}
        <a v-if="sourceHref" :href="sourceHref" target="_blank" rel="noopener noreferrer">
          {{ article.source }}
        </a>
        <span v-else>{{ article.source }}</span>
      </p>
      <NuxtLink class="button ghost" to="/">{{ t('article.backHome') }}</NuxtLink>
    </footer>

    <section v-if="related.length" class="section related">
      <div class="section-head">
        <h2 class="section-title">{{ t('article.related') }}</h2>
      </div>
      <div class="article-grid">
        <ArticleCard v-for="item in related" :key="item.articleId" :article="item" />
      </div>
    </section>
  </article>
</template>
