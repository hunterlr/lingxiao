<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">OAUTH</p>
      <h1>{{ t('callback.introTitle') }}</h1>
      <p>{{ t('callback.introDesc') }}</p>
      <ul class="auth-points">
        <li>{{ t('callback.point1') }}</li>
        <li>{{ t('callback.point2') }}</li>
        <li>{{ t('callback.point3') }}</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ errorMessage ? t('callback.headingError') : t('callback.headingPending') }}</h2>
      <p v-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>
      <p v-else class="panel-description">{{ t('callback.pendingHint') }}</p>

      <div v-if="errorMessage" class="form-actions">
        <NuxtLink class="button" :to="loginTarget">{{ t('callback.backLogin') }}</NuxtLink>
        <NuxtLink class="button ghost" to="/">{{ t('callback.backHome') }}</NuxtLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { memberErrorMessage } from '~/composables/useMemberAuth'

/**
 * 第三方登录回调页。
 *
 * <p>后端回调成功后 302 到这里并带上一次性 ticket；本页在客户端 POST
 * `/api/app/auth/oauth/exchange` 换 Token，再写入门户 Cookie。放在客户端执行，
 * 避免 SSR 与 hydration 重复消费 ticket。</p>
 */
const route = useRoute()
const { t } = usePortalI18n()
const { exchangeOAuthTicket, oauthErrorText } = useMemberAuth()

const errorMessage = ref('')
const loginTarget = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/account'
  return `/login?redirect=${encodeURIComponent(redirect)}`
})

function readQuery(name: string): string {
  const value = route.query[name]
  return typeof value === 'string' ? value : ''
}

function safeRedirect(): string {
  const value = readQuery('redirect')
  return value.startsWith('/') && !value.startsWith('//') ? value : '/account'
}

onMounted(async () => {
  if (readQuery('oauthError')) {
    errorMessage.value = oauthErrorText(readQuery('oauthError'))
    return
  }
  const ticket = readQuery('ticket')
  if (!ticket) {
    errorMessage.value = t('callback.missingTicket')
    return
  }
  try {
    await exchangeOAuthTicket(ticket)
    await navigateTo(safeRedirect(), { replace: true })
  } catch (error) {
    errorMessage.value = memberErrorMessage(error, t('callback.failed'))
  }
})

useSeoMeta({ title: () => t('callback.seoTitle'), robots: 'noindex' })
</script>
