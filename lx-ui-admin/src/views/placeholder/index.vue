<template>
  <div class="page-container">
    <div class="page-card placeholder">
      <div class="placeholder-icon">
        <el-icon :size="34"><Tools /></el-icon>
      </div>
      <h2>{{ title }}</h2>
      <p class="placeholder-desc">
        {{ t('placeholder.descPrefix') }} <code>src/views/{{ componentPath }}.vue</code>
        {{ t('placeholder.descSuffix') }}
      </p>
      <el-descriptions :column="1" border class="placeholder-meta">
        <el-descriptions-item :label="t('placeholder.currentPath')">{{ route.path }}</el-descriptions-item>
        <el-descriptions-item :label="t('placeholder.componentPath')">
          <span class="text-mono">{{ componentPath || t('placeholder.notConfigured') }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('placeholder.perms')">{{ perms || t('placeholder.notConfigured') }}</el-descriptions-item>
        <el-descriptions-item :label="t('placeholder.milestone')">{{ milestone }}</el-descriptions-item>
      </el-descriptions>
      <el-button style="margin-top: 20px" @click="router.push('/')">{{ t('errorPage.backHome') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

/**
 * 占位页。
 *
 * <p>v1.0 的后台菜单都已经有真实页面；这里保留为“菜单先加、组件没跟上”的兜底：
 * 新增模块只加 sys_menu 而不建 Vue 文件时，动态路由落到这里并显示缺的文件路径，
 * 比直接 404 更容易排查。</p>
 */
const route = useRoute()
const router = useRouter()
const { t, te } = useI18n()

const title = computed(() => {
  const meta = route.meta || {}
  if (meta.titleKey && te(meta.titleKey)) return t(meta.titleKey)
  return meta.title || t('placeholder.underConstruction')
})
const componentPath = computed(() => (route.meta && route.meta.component) || '')
const perms = computed(() => (route.meta && route.meta.perms) || '')
const milestone = computed(() => {
  const path = route.path
  if (path.startsWith('/monitor')) return t('placeholder.milestoneMonitor')
  if (path.startsWith('/tool/job')) return t('placeholder.milestoneJob')
  if (path.startsWith('/tool/gen')) return t('placeholder.milestoneGen')
  if (path.startsWith('/tool/api-doc')) return t('placeholder.milestoneApiDoc')
  return t('placeholder.milestoneLater')
})
</script>

<style scoped>
.placeholder {
  max-width: 720px;
  margin: 40px auto;
  text-align: center;
  padding: 40px 32px;
}

.placeholder-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 20px;
  background: rgba(47, 111, 237, 0.08);
  color: var(--lx-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.placeholder-desc {
  color: var(--lx-text-secondary);
  font-size: 13px;
  line-height: 1.8;
  max-width: 520px;
  margin: 0 auto 20px;
}

.placeholder-desc code {
  background: var(--lx-fill-strong);
  padding: 1px 5px;
  border-radius: 3px;
}

.placeholder-meta {
  text-align: left;
}
</style>
