<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">MEMBER</p>
      <h1>{{ t('auth.login.introTitle') }}</h1>
      <p>{{ t('auth.login.introDesc') }}</p>
      <ul class="auth-points">
        <li>{{ t('auth.login.point1') }}</li>
        <li>{{ t('auth.login.point2') }}</li>
        <li>{{ t('auth.login.point3') }}</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ t('auth.login.title') }}</h2>
      <p class="auth-sub">
        {{ t('auth.login.noAccount') }}
        <NuxtLink to="/register">{{ t('auth.login.registerNow') }}</NuxtLink>
      </p>

      <p v-if="notice" class="form-alert info" role="status">{{ notice }}</p>
      <p v-if="bindNotice" class="form-alert info" role="status">{{ bindNotice }}</p>
      <p v-if="memberDisabled" class="form-alert error" role="alert">{{ disabledMessage }}</p>
      <p v-else-if="oauthError" class="form-alert error" role="alert">{{ oauthError }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="onSubmit">
        <div class="form-field">
          <label for="login-account">{{ t('auth.common.account') }}</label>
          <input
            id="login-account"
            v-model="form.account"
            name="account"
            type="text"
            autocomplete="username"
            maxlength="128"
            :placeholder="t('auth.login.accountPlaceholder')"
            required
          />
        </div>

        <div class="form-field">
          <label for="login-password">{{ t('auth.common.password') }}</label>
          <input
            id="login-password"
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            maxlength="64"
            :placeholder="t('auth.login.passwordPlaceholder')"
            required
          />
        </div>

        <div v-if="captcha?.enabled" class="form-field">
          <label for="login-captcha">{{ t('auth.common.captcha') }}</label>
          <div class="captcha-row">
            <input
              id="login-captcha"
              v-model="form.captchaCode"
              name="captchaCode"
              type="text"
              inputmode="text"
              autocomplete="off"
              maxlength="8"
              :placeholder="t('auth.common.captchaPlaceholder')"
              required
            />
            <button
              type="button"
              class="captcha-image"
              :aria-label="captchaLoading ? t('auth.common.captchaLoadingLabel') : t('auth.common.captchaRefreshLabel')"
              @click="refreshCaptcha"
            >
              <img v-if="captcha.image" :src="captcha.image" :alt="t('auth.common.captchaAlt')" />
              <span v-else>{{ t('auth.common.refresh') }}</span>
            </button>
          </div>
          <p class="form-hint">{{ t('auth.login.captchaHint') }}</p>
        </div>

        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? t('auth.common.submitting') : t('auth.login.submit') }}
          </button>
          <div class="form-links">
            <NuxtLink to="/forgot-password">{{ t('auth.common.forgot') }}</NuxtLink>
            <NuxtLink to="/register">{{ t('auth.common.createAccount') }}</NuxtLink>
          </div>
        </div>
      </form>

      <MemberOAuthButtons :redirect="redirectTarget()" @error="onOAuthError" />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { MemberCaptcha } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const { t } = usePortalI18n()
const { login, token, profile, ensureProfile, fetchCaptcha, bindOAuthTicket, oauthErrorText } = useMemberAuth()

const form = reactive({ account: '', password: '', captchaCode: '' })
const captcha = ref<MemberCaptcha | null>(null)
const captchaLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const memberDisabled = ref(false)
const disabledMessage = ref(t('auth.login.disabled'))
const notice = computed(() => {
  if (route.query.reset === '1') return t('auth.login.resetNotice')
  if (route.query.changed === '1') return t('auth.login.changedNotice')
  return ''
})
const oauthError = computed(() => {
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorText(code) : ''
})

function onOAuthError(message: string) {
  errorMessage.value = message
}

function redirectTarget(): string {
  const value = route.query.redirect
  const text = typeof value === 'string' ? value : ''
  return text.startsWith('/') && !text.startsWith('//') ? text : '/account'
}

/**
 * 第三方账号命中已有会员时的待绑定凭证。
 *
 * <p>后端不下发会话，只把 ticket 拼到登录页；用户必须先用原账号登录，
 * 登录成功后再调用 bind-ticket 完成绑定。</p>
 */
const bindTicket = computed(() => {
  const value = route.query.oauthBindTicket
  return typeof value === 'string' && value ? value : ''
})
const bindProvider = computed(() => {
  const value = route.query.provider
  return typeof value === 'string' ? value : ''
})
const bindNotice = computed(() =>
  bindTicket.value && !token.value ? t('auth.login.bindNotice') : ''
)

/** 登录态就绪后尝试完成绑定；失败返回 false，由调用方决定提示方式。 */
async function tryBindPendingTicket(): Promise<boolean> {
  if (!bindTicket.value) return false
  try {
    await bindOAuthTicket(bindTicket.value)
    return true
  } catch {
    return false
  }
}

function accountAfterBind(bound: boolean): string {
  return bound
    ? `/account?oauthBound=${encodeURIComponent(bindProvider.value)}`
    : '/account?oauthBindError=1'
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    captcha.value = await fetchCaptcha('login')
    memberDisabled.value = false
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1003) {
      memberDisabled.value = true
      disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
    } else {
      errorMessage.value = memberErrorMessage(error, t('auth.login.captchaFailed'))
    }
  } finally {
    captchaLoading.value = false
  }
}

async function onSubmit() {
  errorMessage.value = ''
  const account = form.account.trim()
  if (!account || !form.password) {
    errorMessage.value = t('auth.login.accountRequired')
    return
  }
  if (captcha.value?.enabled && (!captcha.value.key || !form.captchaCode.trim())) {
    errorMessage.value = t('auth.login.captchaRequired')
    return
  }

  submitting.value = true
  try {
    await login({
      account,
      password: form.password,
      captchaKey: captcha.value?.enabled ? captcha.value.key || undefined : undefined,
      captchaCode: captcha.value?.enabled ? form.captchaCode.trim() : undefined
    })
    if (bindTicket.value) {
      // 先用原账号登录成功，再完成第三方绑定；失败也不影响本次登录
      const bound = await tryBindPendingTicket()
      await navigateTo(accountAfterBind(bound), { replace: true })
      return
    }
    await navigateTo(redirectTarget(), { replace: true })
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1003) {
      memberDisabled.value = true
      disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
    }
    errorMessage.value = memberErrorMessage(error, t('auth.login.failed'))
    form.captchaCode = ''
    if (captcha.value?.enabled) await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

await ensureProfile(true)
if (token.value && profile.value) {
  if (bindTicket.value) {
    // 已经登录：直接完成绑定，不用再走登录表单
    const bound = await tryBindPendingTicket()
    await navigateTo(accountAfterBind(bound), { replace: true })
  } else {
    await navigateTo(redirectTarget(), { replace: true })
  }
}

useSeoMeta({ title: () => t('auth.login.seoTitle'), robots: 'noindex' })

onMounted(refreshCaptcha)
</script>
