<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">RESET</p>
      <h1>{{ t('forgot.introTitle') }}</h1>
      <p>{{ t('forgot.introDesc') }}</p>
      <ul class="auth-points">
        <li>{{ t('forgot.point1') }}</li>
        <li>{{ t('forgot.point2') }}</li>
        <li>{{ t('forgot.point3') }}</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ step === 'request' ? t('forgot.stepRequest') : t('forgot.stepReset') }}</h2>
      <p class="auth-sub">
        <NuxtLink to="/login">{{ t('forgot.backToLogin') }}</NuxtLink>
      </p>

      <p v-if="memberDisabled" class="form-alert error" role="alert">{{ disabledMessage }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>
      <p v-if="infoMessage" class="form-alert info" role="status">{{ infoMessage }}</p>

      <form v-if="step === 'request'" @submit.prevent="onSendCode">
        <div class="form-field">
          <label for="forgot-account">{{ t('auth.common.account') }}</label>
          <input
            id="forgot-account"
            v-model="form.account"
            name="account"
            type="text"
            autocomplete="username"
            maxlength="128"
            :placeholder="t('auth.common.accountPlaceholder')"
            required
          />
        </div>
        <div v-if="captcha?.enabled" class="form-field">
          <label for="forgot-captcha">{{ t('auth.common.captcha') }}</label>
          <div class="captcha-row">
            <input
              id="forgot-captcha"
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
          <p class="form-hint">{{ t('auth.common.captchaHint') }}</p>
        </div>
        <p class="form-hint">{{ t('forgot.hintExpire') }}</p>
        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? t('forgot.sending') : t('forgot.sendCode') }}
          </button>
        </div>
      </form>

      <form v-else @submit.prevent="onReset">
        <div class="form-field">
          <label for="reset-account">{{ t('auth.common.account') }}</label>
          <input id="reset-account" :value="form.account" type="text" readonly />
        </div>
        <div class="form-field">
          <label for="reset-code">{{ t('forgot.code') }}</label>
          <input
            id="reset-code"
            v-model="form.code"
            name="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="16"
            :placeholder="t('forgot.codePlaceholder')"
            required
          />
        </div>
        <div class="form-field">
          <label for="reset-password">{{ t('forgot.newPassword') }}</label>
          <input
            id="reset-password"
            v-model="form.newPassword"
            name="newPassword"
            type="password"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            :placeholder="t('auth.common.passwordMinPlaceholder')"
            required
          />
        </div>
        <div class="form-field">
          <label for="reset-confirm">{{ t('forgot.confirmNewPassword') }}</label>
          <input
            id="reset-confirm"
            v-model="form.confirmPassword"
            name="confirmPassword"
            type="password"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            :placeholder="t('forgot.confirmNewPasswordPlaceholder')"
            required
          />
        </div>
        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? t('forgot.submitting') : t('forgot.submit') }}
          </button>
          <div class="form-links">
            <button type="button" class="link-button" @click="backToRequest">{{ t('forgot.resend') }}</button>
            <NuxtLink to="/login">{{ t('forgot.backToLogin') }}</NuxtLink>
          </div>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { MemberCaptcha } from '~/types/member'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const { t } = usePortalI18n()
const { forgotPassword, resetPassword, fetchCaptcha } = useMemberAuth()

const step = ref<'request' | 'reset'>('request')
const form = reactive({ account: '', code: '', newPassword: '', confirmPassword: '', captchaCode: '' })
const captcha = ref<MemberCaptcha | null>(null)
const captchaLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const infoMessage = ref('')
const memberDisabled = ref(false)
const disabledMessage = ref(t('auth.common.disabled'))

function handleError(error: unknown, fallback: string) {
  if (error instanceof MemberApiError && error.code === 1003) {
    memberDisabled.value = true
    disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
  }
  errorMessage.value = memberErrorMessage(error, fallback)
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    captcha.value = await fetchCaptcha('reset')
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1003) {
      memberDisabled.value = true
      disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
    } else {
      errorMessage.value = memberErrorMessage(error, t('forgot.captchaFailed'))
    }
  } finally {
    captchaLoading.value = false
  }
}

async function onSendCode() {
  errorMessage.value = ''
  infoMessage.value = ''
  const account = form.account.trim()
  if (!account) {
    errorMessage.value = t('forgot.accountRequired')
    return
  }
  if (captcha.value?.enabled && (!captcha.value.key || !form.captchaCode.trim())) {
    errorMessage.value = t('auth.common.captchaRequired')
    return
  }

  submitting.value = true
  try {
    await forgotPassword({
      account,
      captchaKey: captcha.value?.enabled ? captcha.value.key || undefined : undefined,
      captchaCode: captcha.value?.enabled ? form.captchaCode.trim() : undefined
    })
    form.account = account
    infoMessage.value = t('forgot.infoSent')
    step.value = 'reset'
  } catch (error) {
    handleError(error, t('forgot.sendFailed'))
    form.captchaCode = ''
    if (captcha.value?.enabled) await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

async function onReset() {
  errorMessage.value = ''
  infoMessage.value = ''
  if (!form.code.trim()) {
    errorMessage.value = t('forgot.codeRequired')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    errorMessage.value = t('forgot.newPasswordShort')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    errorMessage.value = t('forgot.newPasswordMismatch')
    return
  }

  submitting.value = true
  try {
    await resetPassword({
      account: form.account,
      code: form.code.trim(),
      newPassword: form.newPassword
    })
    await navigateTo('/login?reset=1', { replace: true })
  } catch (error) {
    handleError(error, t('forgot.resetFailed'))
  } finally {
    submitting.value = false
  }
}

function backToRequest() {
  step.value = 'request'
  form.code = ''
  form.newPassword = ''
  form.confirmPassword = ''
  errorMessage.value = ''
  infoMessage.value = ''
}

// 已登录也可以找回密码；不强制跳转，避免用户误入后无法操作
useSeoMeta({ title: () => t('forgot.seoTitle'), robots: 'noindex' })

onMounted(refreshCaptcha)
</script>
