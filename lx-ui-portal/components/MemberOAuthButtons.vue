<template>
  <div v-if="visible" class="oauth-block">
    <div class="oauth-divider">
      <span>{{ dividerText }}</span>
    </div>

    <div class="oauth-grid">
      <button
        v-for="item in visibleProviders"
        :key="item.provider"
        type="button"
        class="oauth-button"
        :disabled="Boolean(loadingProvider)"
        @click="start(item)"
      >
        <span class="oauth-badge" aria-hidden="true">{{ initial(item.displayName) }}</span>
        <span class="oauth-name">{{ item.displayName }}</span>
        <span v-if="loadingProvider === item.provider" class="oauth-loading" aria-hidden="true">…</span>
      </button>
    </div>

    <p v-if="errorText" class="form-alert error" role="alert">{{ errorText }}</p>
  </div>
</template>

<script setup lang="ts">
import type { MemberOAuthProvider } from '~/types/member'
import { memberErrorMessage } from '~/composables/useMemberAuth'

/**
 * 第三方登录入口。
 *
 * <p>登录 / 注册页用 login 模式，个人中心用 bind 模式。按钮点击后先向后端
 * 换取一次性 state 的授权地址，再整页跳转到平台；Token 不会出现在 URL 上。</p>
 *
 * <p>后端只下发“已启用且已配置密钥”的平台；总开关关闭时整块入口自动隐藏。</p>
 */
const props = withDefaults(defineProps<{
  mode?: 'login' | 'bind'
  redirect?: string
  boundProviders?: string[]
}>(), {
  mode: 'login',
  redirect: '',
  boundProviders: () => []
})

const emit = defineEmits<{ error: [message: string] }>()

const { t } = usePortalI18n()
const { fetchOAuthProviders, fetchOAuthAuthorizeUrl } = useMemberAuth()

const providers = ref<MemberOAuthProvider[]>([])
const loadingProvider = ref('')
const errorText = ref('')

const visibleProviders = computed(() => {
  if (props.mode !== 'bind') return providers.value
  const bound = new Set(props.boundProviders)
  return providers.value.filter((item) => !bound.has(item.provider))
})

const visible = computed(() => visibleProviders.value.length > 0)

const dividerText = computed(() =>
  props.mode === 'bind' ? t('memberOauth.dividerBind') : t('memberOauth.dividerLogin')
)

function initial(name: string): string {
  return (name || '?').trim().slice(0, 1).toUpperCase()
}

async function start(item: MemberOAuthProvider) {
  if (loadingProvider.value) return
  loadingProvider.value = item.provider
  errorText.value = ''
  try {
    const result = await fetchOAuthAuthorizeUrl(item.provider, props.mode, props.redirect || undefined)
    window.location.assign(result.authorizeUrl)
  } catch (error) {
    errorText.value = memberErrorMessage(error, t('memberOauth.failed'))
    emit('error', errorText.value)
    loadingProvider.value = ''
  }
}

onMounted(async () => {
  try {
    const result = await fetchOAuthProviders()
    providers.value = result?.enabled ? result.providers || [] : []
  } catch {
    // 第三方登录只是辅助入口，加载失败不打断账号密码登录
    providers.value = []
  }
})
</script>
