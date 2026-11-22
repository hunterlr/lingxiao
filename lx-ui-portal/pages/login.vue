<template>
  <div class="auth-page">
    <section class="auth-intro">
      <p class="eyebrow">MEMBER</p>
      <h1>登录会员，继续你的阅读</h1>
      <p>
        用用户名、手机号或邮箱登录。会员资料与后台内容账号完全隔离，
        门户不会读取后台管理数据。
      </p>
      <ul class="auth-points">
        <li>登录后可维护昵称、头像与性别</li>
        <li>支持自助修改密码与找回密码</li>
        <li>会话过期后只需重新登录，不影响已发布内容</li>
      </ul>
    </section>

    <section class="auth-card">
      <h2>会员登录</h2>
      <p class="auth-sub">还没有账号？<NuxtLink to="/register">立即注册</NuxtLink></p>

      <p v-if="notice" class="form-alert info" role="status">{{ notice }}</p>
      <p v-if="bindNotice" class="form-alert info" role="status">{{ bindNotice }}</p>
      <p v-if="memberDisabled" class="form-alert error" role="alert">
        {{ disabledMessage }}
      </p>
      <p v-else-if="oauthError" class="form-alert error" role="alert">{{ oauthError }}</p>
      <p v-else-if="errorMessage" class="form-alert error" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="onSubmit">
        <div class="form-field">
          <label for="login-account">账号</label>
          <input
            id="login-account"
            v-model="form.account"
            name="account"
            type="text"
            autocomplete="username"
            maxlength="128"
            placeholder="用户名 / 手机号 / 邮箱"
            required
          />
        </div>

        <div class="form-field">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            maxlength="64"
            placeholder="请输入密码"
            required
          />
        </div>

        <div v-if="captcha?.enabled" class="form-field">
          <label for="login-captcha">图形验证码</label>
          <div class="captcha-row">
            <input
              id="login-captcha"
              v-model="form.captchaCode"
              name="captchaCode"
              type="text"
              inputmode="text"
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
          <p class="form-hint">看不清可以点击图片刷新，验证码一次性使用。</p>
        </div>

        <div class="form-actions">
          <button type="submit" class="button" :disabled="submitting || memberDisabled">
            {{ submitting ? '登录中…' : '登录' }}
          </button>
          <div class="form-links">
            <NuxtLink to="/forgot-password">忘记密码</NuxtLink>
            <NuxtLink to="/register">注册新账号</NuxtLink>
          </div>
        </div>
      </form>

      <MemberOAuthButtons :redirect="redirectTarget()" @error="onOAuthError" />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { MemberCaptcha } from '~/types/member'
import { MemberApiError, memberErrorMessage, oauthErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const { login, token, profile, ensureProfile, fetchCaptcha, bindOAuthTicket } = useMemberAuth()

const form = reactive({ account: '', password: '', captchaCode: '' })
const captcha = ref<MemberCaptcha | null>(null)
const captchaLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const memberDisabled = ref(false)
const disabledMessage = ref('会员功能已关闭，请稍后再试。')
const notice = computed(() => {
  if (route.query.reset === '1') return '密码已重置，请使用新密码登录。'
  if (route.query.changed === '1') return '密码已修改，请使用新密码登录。'
  return ''
})
const oauthError = computed(() => {
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorMessage(code) : ''
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
  bindTicket.value && !token.value
    ? '该第三方账号关联的邮箱或手机号已注册会员。请先用原账号登录，登录后会自动完成绑定。'
    : ''
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
      errorMessage.value = memberErrorMessage(error, '验证码加载失败，请稍后重试')
    }
  } finally {
    captchaLoading.value = false
  }
}

async function onSubmit() {
  errorMessage.value = ''
  const account = form.account.trim()
  if (!account || !form.password) {
    errorMessage.value = '请输入账号和密码'
    return
  }
  if (captcha.value?.enabled && (!captcha.value.key || !form.captchaCode.trim())) {
    errorMessage.value = '请输入图形验证码'
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
    errorMessage.value = memberErrorMessage(error, '登录失败，请稍后重试')
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

useSeoMeta({ title: '会员登录', robots: 'noindex' })

onMounted(refreshCaptcha)
</script>
