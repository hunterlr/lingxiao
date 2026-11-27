<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">JOIN</p>
      <h1>{{ t('register.introTitle') }}</h1>
      <p>{{ t('register.introDesc') }}</p>
      <ul class="auth-points">
        <li>{{ t('register.point1') }}</li>
        <li>{{ t('register.point2') }}</li>
        <li>{{ t('register.point3') }}</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ t('register.title') }}</h2>
      <p class="auth-sub">
        {{ t('register.hasAccount') }}
        <NuxtLink to="/login">{{ t('register.loginNow') }}</NuxtLink>
      </p>

      <p v-if="memberDisabled" class="form-alert error" role="alert">{{ disabledMessage }}</p>
      <p v-else-if="oauthError" class="form-alert error" role="alert">{{ oauthError }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="onSubmit">
        <div class="form-field">
          <label for="register-username">{{ t('register.username') }}</label>
          <input
            id="register-username"
            v-model="form.username"
            name="username"
            type="text"
            autocomplete="username"
            maxlength="64"
            :placeholder="t('register.usernamePlaceholder')"
            required
          />
        </div>

        <div class="form-field">
          <label for="register-nickname">{{ t('register.nickname') }}</label>
          <input
            id="register-nickname"
            v-model="form.nickname"
            name="nickname"
            type="text"
            autocomplete="nickname"
            maxlength="64"
            :placeholder="t('register.nicknamePlaceholder')"
          />
        </div>

        <div class="form-row">
          <div class="form-field">
            <label for="register-mobile">{{ t('register.mobile') }}</label>
            <input
              id="register-mobile"
              v-model="form.mobile"
              name="mobile"
              type="tel"
              autocomplete="tel"
              maxlength="32"
              :placeholder="t('auth.common.optional')"
            />
          </div>
          <div class="form-field">
            <label for="register-email">{{ t('register.email') }}</label>
            <input
              id="register-email"
              v-model="form.email"
              name="email"
              type="email"
              autocomplete="email"
              maxlength="128"
              :placeholder="t('auth.common.optional')"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-field">
            <label for="register-password">{{ t('auth.common.password') }}</label>
            <input
              id="register-password"
              v-model="form.password"
              name="password"
              type="password"
              autocomplete="new-password"
              minlength="6"
              maxlength="64"
              :placeholder="t('auth.common.passwordMinPlaceholder')"
              required
            />
          </div>
          <div class="form-field">
            <label for="register-password-confirm">{{ t('register.confirmPassword') }}</label>
            <input
              id="register-password-confirm"
              v-model="form.confirmPassword"
              name="confirmPassword"
              type="password"
              autocomplete="new-password"
              minlength="6"
              maxlength="64"
              :placeholder="t('register.confirmPlaceholder')"
              required
            />
          </div>
        </div>

        <div v-if="captcha?.enabled" class="form-field">
          <label for="register-captcha">{{ t('auth.common.captcha') }}</label>
          <div class="captcha-row">
            <input
              id="register-captcha"
              v-model="form.captchaCode"
              name="captchaCode"
              type="text"
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
        </div>

        <div class="form-field">
          <label class="checkbox-field">
            <input v-model="form.agreement" type="checkbox" name="agreement" />
            <span>{{ t('register.agreement') }}</span>
          </label>
        </div>

        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? t('register.submitting') : t('register.submit') }}
          </button>
          <div class="form-links">
            <NuxtLink to="/login">{{ t('register.haveAccount') }}</NuxtLink>
            <NuxtLink to="/forgot-password">{{ t('auth.common.forgot') }}</NuxtLink>
          </div>
        </div>
      </form>

      <MemberOAuthButtons redirect="/account" @error="onOAuthError" />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { MemberCaptcha } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const { t } = usePortalI18n()
const { register, token, profile, ensureProfile, fetchCaptcha, oauthErrorText } = useMemberAuth()

const form = reactive({
  username: '',
  nickname: '',
  mobile: '',
  email: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
  agreement: false
})
const captcha = ref<MemberCaptcha | null>(null)
const captchaLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const memberDisabled = ref(false)
const disabledMessage = ref(t('auth.common.disabled'))
const oauthError = computed(() => {
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorText(code) : ''
})

function onOAuthError(message: string) {
  errorMessage.value = message
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    captcha.value = await fetchCaptcha('register')
    memberDisabled.value = false
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1003) {
      memberDisabled.value = true
      disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
    } else {
      errorMessage.value = memberErrorMessage(error, t('auth.common.captchaFailed'))
    }
  } finally {
    captchaLoading.value = false
  }
}

async function onSubmit() {
  errorMessage.value = ''
  const username = form.username.trim()
  if (!username) {
    errorMessage.value = t('register.usernameRequired')
    return
  }
  if (!form.password || form.password.length < 6) {
    errorMessage.value = t('register.passwordShort')
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMessage.value = t('register.passwordMismatch')
    return
  }
  if (!form.agreement) {
    errorMessage.value = t('register.agreementRequired')
    return
  }
  if (captcha.value?.enabled && (!captcha.value.key || !form.captchaCode.trim())) {
    errorMessage.value = t('auth.common.captchaRequired')
    return
  }

  submitting.value = true
  try {
    await register({
      username,
      password: form.password,
      nickname: form.nickname.trim() || undefined,
      mobile: form.mobile.trim() || undefined,
      email: form.email.trim() || undefined,
      captchaKey: captcha.value?.enabled ? captcha.value.key || undefined : undefined,
      captchaCode: captcha.value?.enabled ? form.captchaCode.trim() : undefined,
      agreement: true
    })
    await navigateTo('/account', { replace: true })
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1003) {
      memberDisabled.value = true
      disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
    }
    errorMessage.value = memberErrorMessage(error, t('register.failed'))
    form.captchaCode = ''
    if (captcha.value?.enabled) await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

await ensureProfile(true)
if (token.value && profile.value) {
  await navigateTo('/account', { replace: true })
}

useSeoMeta({ title: () => t('register.seoTitle'), robots: 'noindex' })

onMounted(refreshCaptcha)
</script>
