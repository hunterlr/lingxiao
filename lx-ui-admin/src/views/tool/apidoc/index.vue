<template>
  <div class="page-container">
    <div class="page-card apidoc-card">
      <div class="api-header">
        <div class="api-title">
          <h3 class="section-title">{{ t('page.apidoc.title') }}</h3>
          <p class="text-secondary">
            {{ t('page.apidoc.hint') }}
          </p>
        </div>
        <div class="api-actions">
          <el-button :icon="CopyDocument" @click="copyToken">{{ t('page.apidoc.copyToken') }}</el-button>
          <el-button :icon="Refresh" @click="reload">{{ t('common.refresh') }}</el-button>
          <el-button type="primary" :icon="TopRight" @click="openNewWindow">{{ t('page.apidoc.newWindow') }}</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="api-tip"
        :title="t('page.apidoc.tipTitle')"
        :description="t('page.apidoc.tipDesc')"
      />

      <div class="token-row">
        <span class="token-label">{{ t('page.apidoc.tokenName') }}</span>
        <el-tag size="small" effect="plain" class="text-mono">{{ auth.tokenName || 'lx-platform-token' }}</el-tag>
        <span class="token-label">{{ t('page.apidoc.tokenValue') }}</span>
        <span class="token-value text-mono">{{ maskedToken }}</span>
        <span class="text-secondary">{{ t('page.apidoc.maskedHint') }}</span>
      </div>

      <div class="api-frame-wrap">
        <iframe
          :key="frameKey"
          class="api-frame"
          :src="docUrl"
          :title="t('page.apidoc.docTitle')"
          referrerpolicy="no-referrer"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, TopRight } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/store/auth'

/**
 * 接口文档页。
 *
 * <p>用 iframe 内嵌后端 /doc.html：文档是 Knife4j 的静态资源，不需要也不应该
 * 由 Vue 再实现一遍。开发期由 Vite 代理 /doc.html、/v3/api-docs、/webjars；
 * 生产期由 Nginx 转发到后端，前端代码不区分环境。</p>
 *
 * <p>文档接口本身不走 /api/admin/**，因此不受平台域拦截器约束；
 * 如果以后把文档挪到 /api/admin 下，必须在 SaTokenConfigure 里放行。</p>
 */
const { t } = useI18n()

const auth = useAuthStore()

// 可用环境变量覆盖，默认同源 /doc.html
const docUrl = import.meta.env.VITE_API_DOC_URL || '/doc.html'
const frameKey = ref(0)

const maskedToken = computed(() => {
  const token = getToken()
  if (!token) return t('page.apidoc.notLoggedIn')
  return `${token.slice(0, 8)}${'*'.repeat(Math.max(4, Math.min(token.length - 8, 16)))}`
})

async function copyToken() {
  const token = getToken()
  if (!token) {
    ElMessage.warning(t('page.apidoc.noToken'))
    return
  }
  try {
    await navigator.clipboard.writeText(token)
    ElMessage.success(t('page.apidoc.copied'))
  } catch (e) {
    // 非 HTTPS / 浏览器拒绝剪贴板权限时降级为 textarea + execCommand
    const textarea = document.createElement('textarea')
    textarea.value = token
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)
    if (copied) {
      ElMessage.success(t('page.apidoc.copied'))
    } else {
      ElMessage.error(t('page.apidoc.copyFailed'))
    }
  }
}

function reload() {
  frameKey.value += 1
}

function openNewWindow() {
  window.open(docUrl, '_blank', 'noopener')
}
</script>

<style scoped>
.apidoc-card {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 150px);
}

.api-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.api-title {
  max-width: 720px;
}

.api-title .section-title {
  margin: 0 0 6px;
}

.api-title p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}

.api-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.api-tip {
  margin-top: 14px;
}

.token-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
}

.token-label {
  color: var(--lx-text-secondary);
}

.token-value {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--lx-fill-bg);
  color: var(--lx-text);
}

.api-frame-wrap {
  flex: 1;
  min-height: 620px;
  border: 1px solid var(--lx-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--lx-card-bg);
}

.api-frame {
  display: block;
  width: 100%;
  height: calc(100vh - 330px);
  min-height: 620px;
  border: 0;
}
</style>
