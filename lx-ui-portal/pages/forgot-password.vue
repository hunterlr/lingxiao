<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">RESET</p>
      <h1>找回密码</h1>
      <p>
        输入注册时的用户名、手机号或邮箱获取验证码。账号不存在时接口也会返回成功，
        避免被用来探测会员账号。
      </p>
      <ul class="auth-points">
        <li>手机号走短信通道，邮箱走邮件通道</li>
        <li>验证码一次性使用，过期需重新获取</li>
        <li>重置成功后所有旧会话立即失效</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>{{ step === 'request' ? '获取验证码' : '设置新密码' }}</h2>
      <p class="auth-sub">
        <NuxtLink to="/login">返回登录</NuxtLink>
      </p>

      <p v-if="memberDisabled" class="form-alert error" role="alert">{{ disabledMessage }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>
      <p v-if="infoMessage" class="form-alert info" role="status">{{ infoMessage }}</p>

      <form v-if="step === 'request'" @submit.prevent="onSendCode">
        <div class="form-field">
          <label for="forgot-account">账号</label>
          <input
            id="forgot-account"
            v-model="form.account"
            name="account"
            type="text"
            autocomplete="username"
            maxlength="128"
            placeholder="用户名 / 手机号 / 邮箱"
            required
          />
        </div>
        <p class="form-hint">验证码有效期由站点会员设置决定，通常 10 分钟。</p>
        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? '发送中…' : '发送验证码' }}
          </button>
        </div>
      </form>

      <form v-else @submit.prevent="onReset">
        <div class="form-field">
          <label for="reset-account">账号</label>
          <input id="reset-account" :value="form.account" type="text" readonly />
        </div>
        <div class="form-field">
          <label for="reset-code">验证码</label>
          <input
            id="reset-code"
            v-model="form.code"
            name="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="16"
            placeholder="请输入短信或邮件中的验证码"
            required
          />
        </div>
        <div class="form-field">
          <label for="reset-password">新密码</label>
          <input
            id="reset-password"
            v-model="form.newPassword"
            name="newPassword"
            type="password"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            placeholder="至少 6 位"
            required
          />
        </div>
        <div class="form-field">
          <label for="reset-confirm">确认新密码</label>
          <input
            id="reset-confirm"
            v-model="form.confirmPassword"
            name="confirmPassword"
            type="password"
            autocomplete="new-password"
            minlength="6"
            maxlength="64"
            placeholder="再次输入新密码"
            required
          />
        </div>
        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? '提交中…' : '重置密码' }}
          </button>
          <div class="form-links">
            <button type="button" class="link-button" @click="backToRequest">重新获取验证码</button>
            <NuxtLink to="/login">返回登录</NuxtLink>
          </div>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const { forgotPassword, resetPassword } = useMemberAuth()

const step = ref<'request' | 'reset'>('request')
const form = reactive({ account: '', code: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)
const errorMessage = ref('')
const infoMessage = ref('')
const memberDisabled = ref(false)
const disabledMessage = ref('会员功能已关闭，请稍后再试。')

function handleError(error: unknown, fallback: string) {
  if (error instanceof MemberApiError && error.code === 1003) {
    memberDisabled.value = true
    disabledMessage.value = memberErrorMessage(error, disabledMessage.value)
  }
  errorMessage.value = memberErrorMessage(error, fallback)
}

async function onSendCode() {
  errorMessage.value = ''
  infoMessage.value = ''
  const account = form.account.trim()
  if (!account) {
    errorMessage.value = '请输入账号'
    return
  }

  submitting.value = true
  try {
    await forgotPassword(account)
    form.account = account
    infoMessage.value = '如果该账号存在，验证码已发送，请查收短信或邮件。'
    step.value = 'reset'
  } catch (error) {
    handleError(error, '验证码发送失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function onReset() {
  errorMessage.value = ''
  infoMessage.value = ''
  if (!form.code.trim()) {
    errorMessage.value = '请输入验证码'
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    errorMessage.value = '新密码至少 6 位'
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    errorMessage.value = '两次输入的新密码不一致'
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
    handleError(error, '密码重置失败，请稍后重试')
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
useSeoMeta({ title: '找回密码', robots: 'noindex' })
</script>
