<template>
  <div class="message-page">
    <header class="message-head">
      <div>
        <p class="eyebrow">{{ t('messages.eyebrow') }}</p>
        <h1 class="message-title">{{ t('messages.title') }}</h1>
        <p class="message-sub">{{ t('messages.sub') }}</p>
      </div>
      <div class="message-head-actions">
        <NuxtLink to="/account" class="button ghost">{{ t('messages.back') }}</NuxtLink>
        <button type="button" class="button" :disabled="unreadCount === 0 || markingAll" @click="markAll">
          {{ t('messages.markAll') }}
        </button>
      </div>
    </header>

    <p v-if="error" class="message-tip">{{ error }}</p>
    <p v-else-if="loading" class="message-tip">{{ t('messages.loading') }}</p>
    <p v-else-if="!items.length" class="message-tip">{{ t('messages.empty') }}</p>

    <ul v-else class="message-list">
      <li
        v-for="item in items"
        :key="item.msgId"
        class="message-item"
        :class="{ 'is-unread': item.readFlag === '0' }"
      >
        <div class="message-item-head">
          <strong>{{ item.title }}</strong>
          <span class="message-time">{{ formatTime(item.createTime) }}</span>
        </div>
        <p class="message-content">{{ item.content }}</p>
        <div class="message-item-foot">
          <NuxtLink
            v-if="item.bizType === 'article' && item.bizId"
            :to="`/article/${item.bizId}`"
            class="message-action"
          >
            {{ t('messages.viewArticle') }}
          </NuxtLink>
          <button
            v-if="item.readFlag === '0'"
            type="button"
            class="message-action"
            :disabled="readingId === String(item.msgId)"
            @click="markOne(item)"
          >
            {{ t('messages.markRead') }}
          </button>
        </div>
      </li>
    </ul>

    <div v-if="total > pageSize" class="message-pager">
      <button type="button" class="button ghost" :disabled="page === 1 || loading" @click="changePage(-1)">{{ t('messages.prev') }}</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button type="button" class="button ghost" :disabled="page >= totalPages || loading" @click="changePage(1)">
        {{ t('messages.next') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MemberMessageItem } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

/**
 * 我的消息。
 *
 * <p>需要登录；未登录直接跳登录页并带回跳地址。消息正文是纯文本，
 * 用 `{{ }}` 输出由 Vue 转义，不拼 HTML。</p>
 */

const route = useRoute()
const { t } = usePortalI18n()
const { isLoggedIn, ensureProfile, unreadCount, fetchMessages, markMessageRead, markAllMessagesRead } =
  useMemberAuth()

await ensureProfile(true)
if (!isLoggedIn.value) {
  await navigateTo(`/login?redirect=${encodeURIComponent('/messages')}`, { replace: true })
}

const pageSize = 10
const page = ref(1)
const total = ref(0)
const items = ref<MemberMessageItem[]>([])
const loading = ref(false)
const error = ref('')
const readingId = ref('')
const markingAll = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load(target = page.value) {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchMessages(target, pageSize)
    items.value = result.records || []
    total.value = Number(result.total || 0)
    page.value = target
  } catch (cause) {
    if (cause instanceof MemberApiError && cause.code === 1003) {
      error.value = t('messages.disabled')
    } else if (cause instanceof MemberApiError && (cause.code === 1002 || cause.code === 2002)) {
      await navigateTo(`/login?redirect=${encodeURIComponent('/messages')}`, { replace: true })
    } else {
      error.value = memberErrorMessage(cause, t('messages.loadFailed'))
    }
  } finally {
    loading.value = false
  }
}

async function markOne(item: MemberMessageItem) {
  readingId.value = String(item.msgId)
  try {
    await markMessageRead(item.msgId)
    item.readFlag = '1'
  } catch (cause) {
    error.value = memberErrorMessage(cause, t('messages.markFailed'))
  } finally {
    readingId.value = ''
  }
}

async function markAll() {
  markingAll.value = true
  try {
    await markAllMessagesRead()
    items.value = items.value.map((item) => ({ ...item, readFlag: '1' }))
  } catch (cause) {
    error.value = memberErrorMessage(cause, t('messages.actionFailed'))
  } finally {
    markingAll.value = false
  }
}

function changePage(delta: number) {
  const next = page.value + delta
  if (next < 1 || next > totalPages.value) return
  load(next)
}

function formatTime(value?: string | null) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

await load(1)

useSeoMeta({ title: () => t('messages.seoTitle'), robots: 'noindex' })
</script>