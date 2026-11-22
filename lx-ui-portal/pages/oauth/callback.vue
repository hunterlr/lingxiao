<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">OAUTH</p>
      <h1>正在完成第三方登录</h1>
      <p>
        授权成功后门户会换取会员会话。Token 不会出现在地址栏，
        回调地址里只有一次性的临时凭证。
      </p>
      <ul class="auth-points">
        <li>临时凭证两分钟内有效，且只能使用一次</li>
        <li>会员会话与后台账号完全隔离</li>
        <li>如果授权失败，可以返回登录页重试</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ errorMessage ? '登录未完成' : '正在登录…' }}</h2>
      <p v-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>
      <p v-else class="panel-description">正在校验第三方授权，请稍候，不要关闭页面。</p>

      <div v-if="errorMessage" class="form-actions">
        <NuxtLink class="button" :to="loginTarget">返回登录</NuxtLink>
        <NuxtLink class="button ghost" to="/">返回首页</NuxtLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { memberErrorMessage, oauthErrorMessage } from '~/composables/useMemberAuth'

/**
 * 第三方登录回调页。
 *
 * <p>后端回调成功后 302 到这里并带上一次性 ticket；本页在客户端 POST
 * `/api/app/auth/oauth/exchange` 换 Token，再写入门户 Cookie。放在客户端执行，
 * 避免 SSR 与 hydration 重复消费 ticket。</p>
 */
const route = useRoute()
const { exchangeOAuthTicket } = useMemberAuth()

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
    errorMessage.value = oauthErrorMessage(readQuery('oauthError'))
    return
  }
  const ticket = readQuery('ticket')
  if (!ticket) {
    errorMessage.value = '登录凭证缺失，请重新发起第三方登录。'
    return
  }
  try {
    await exchangeOAuthTicket(ticket)
    await navigateTo(safeRedirect(), { replace: true })
  } catch (error) {
    errorMessage.value = memberErrorMessage(error, '第三方登录失败，请重试。')
  }
})

useSeoMeta({ title: '第三方登录', robots: 'noindex' })
</script>
