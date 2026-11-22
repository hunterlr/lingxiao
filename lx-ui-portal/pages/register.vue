<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">JOIN</p>
      <h1>注册会员，收藏你的阅读轨迹</h1>
      <p>
        注册后自动登录。手机号和邮箱是否必填由站点会员设置决定，
        提交时若缺失会给出明确提示。
      </p>
      <ul class="auth-points">
        <li>用户名用于登录，注册后不可修改</li>
        <li>昵称会展示在个人中心与后续评论中</li>
        <li>密码至少 6 位，具体复杂度以站点策略为准</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>注册新账号</h2>
      <p class="auth-sub">已有账号？<NuxtLink to="/login">直接登录</NuxtLink></p>

      <p v-if="memberDisabled" class="form-alert error" role="alert">{{ disabledMessage }}</p>
      <p v-else-if="oauthError" class="form-alert error" role="alert">{{ oauthError }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="onSubmit">
        <div class="form-field">
          <label for="register-username">用户名</label>
          <input
            id="register-username"
            v-model="form.username"
            name="username"
            type="text"
            autocomplete="username"
            maxlength="64"
            placeholder="用于登录，注册后不可修改"
            required
          />
        </div>

        <div class="form-field">
          <label for="register-nickname">昵称</label>
          <input
            id="register-nickname"
            v-model="form.nickname"
            name="nickname"
            type="text"
            autocomplete="nickname"
            maxlength="64"
            placeholder="不填时默认使用用户名"
          />
        </div>

        <div class="form-row">
          <div class="form-field">
            <label for="register-mobile">手机号</label>
            <input
              id="register-mobile"
              v-model="form.mobile"
              name="mobile"
              type="tel"
              autocomplete="tel"
              maxlength="32"
              placeholder="选填"
            />
          </div>
          <div class="form-field">
            <label for="register-email">邮箱</label>
            <input
              id="register-email"
              v-model="form.email"
              name="email"
              type="email"
              autocomplete="email"
              maxlength="128"
              placeholder="选填"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-field">
            <label for="register-password">密码</label>
            <input
              id="register-password"
              v-model="form.password"
              name="password"
              type="password"
              autocomplete="new-password"
              minlength="6"
              maxlength="64"
              placeholder="至少 6 位"
              required
            />
          </div>
          <div class="form-field">
            <label for="register-password-confirm">确认密码</label>
            <input
              id="register-password-confirm"
              v-model="form.confirmPassword"
              name="confirmPassword"
              type="password"
              autocomplete="new-password"
              minlength="6"
              maxlength="64"
              placeholder="再次输入密码"
              required
            />
          </div>
        </div>

        <div v-if="captcha?.enabled" class="form-field">
          <label for="register-captcha">图形验证码</label>
          <div class="captcha-row">
            <input
              id="register-captcha"
              v-model="form.captchaCode"
              name="captchaCode"
              type="text"
              autocomplete="off"
              maxlength="8"
              placeholder="请输入图片中的字符"
              required
            />
            <button
              type="button"
              class="captcha-image"
              :aria-label="captchaLoading ? '正在刷新验证码' : '点击刷新验证码'"
              @click="refreshCaptcha"
            >
              <img v-if="captcha.image" :src="captcha.image" alt="图形验证码" />
              <span v-else>刷新</span>
            </button>
          </div>
        </div>

        <div class="form-field">
          <label class="checkbox-field">
            <input v-model="form.agreement" type="checkbox" name="agreement" />
            <span>我已阅读并同意用户协议与隐私说明</span>
          </label>
        </div>

        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? '注册中…' : '注册并登录' }}
          </button>
          <div class="form-links">
            <NuxtLink to="/login">已有账号</NuxtLink>
            <NuxtLink to="/forgot-password">忘记密码</NuxtLink>
          </div>
        </div>
      </form>

      <MemberOAuthButtons redirect="/account" @error="onOAuthError" />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { MemberCaptcha } from '~/types/member'
import { MemberApiError, memberErrorMessage, oauthErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const { register, token, profile, ensureProfile, fetchCaptcha } = useMemberAuth()

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
const disabledMessage = ref('会员功能已关闭，请稍后再试。')
const oauthError = computed(() => {
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorMessage(code) : ''
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
      errorMessage.value = memberErrorMessage(error, '验证码加载失败，请稍后重试')
    }
  } finally {
    captchaLoading.value = false
  }
}

async function onSubmit() {
  errorMessage.value = ''
  const username = form.username.trim()
  if (!username) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!form.password || form.password.length < 6) {
    errorMessage.value = '密码至少 6 位'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  if (!form.agreement) {
    errorMessage.value = '请先阅读并同意用户协议'
    return
  }
  if (captcha.value?.enabled && (!captcha.value.key || !form.captchaCode.trim())) {
    errorMessage.value = '请输入图形验证码'
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
    errorMessage.value = memberErrorMessage(error, '注册失败，请稍后重试')
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

useSeoMeta({ title: '注册会员', robots: 'noindex' })

onMounted(refreshCaptcha)
</script>
