<template>
  <div class="page-container">
    <div class="page-card placeholder">
      <div class="placeholder-icon">
        <el-icon :size="34"><Tools /></el-icon>
      </div>
      <h2>{{ title }}</h2>
      <p class="placeholder-desc">
        菜单已经接进来了，但这个页面还没有实现。前端不需要改路由：
        在 <code>src/views/{{ componentPath }}.vue</code> 创建文件后，刷新即可生效。
      </p>
      <el-descriptions :column="1" border class="placeholder-meta">
        <el-descriptions-item label="当前路径">{{ route.path }}</el-descriptions-item>
        <el-descriptions-item label="组件路径">
          <span class="text-mono">{{ componentPath || '未配置' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="权限码">{{ perms || '未配置' }}</el-descriptions-item>
        <el-descriptions-item label="计划版本">{{ milestone }}</el-descriptions-item>
      </el-descriptions>
      <el-button style="margin-top: 20px" @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 占位页。
 *
 * <p>v1.0 的后台菜单都已经有真实页面；这里保留为“菜单先加、组件没跟上”的兜底：
 * 新增模块只加 sys_menu 而不建 Vue 文件时，动态路由落到这里并显示缺的文件路径，
 * 比直接 404 更容易排查。</p>
 */
const route = useRoute()
const router = useRouter()

const title = computed(() => (route.meta && route.meta.title) || '功能建设中')
const componentPath = computed(() => (route.meta && route.meta.component) || '')
const perms = computed(() => (route.meta && route.meta.perms) || '')
const milestone = computed(() => {
  const path = route.path
  if (path.startsWith('/monitor')) return 'W4 在线用户 / 服务器 / 缓存监控'
  if (path.startsWith('/tool/job')) return 'W4 定时任务'
  if (path.startsWith('/tool/gen')) return 'W5 代码生成'
  if (path.startsWith('/tool/api-doc')) return 'W5 接口文档'
  return '后续版本'
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
