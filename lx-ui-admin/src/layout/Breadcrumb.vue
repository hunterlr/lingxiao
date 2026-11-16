<template>
  <el-breadcrumb separator="/" class="breadcrumb">
    <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
    <el-breadcrumb-item v-for="(item, index) in trail" :key="index">
      <span v-if="index === trail.length - 1" class="current">{{ item.title }}</span>
      <router-link v-else-if="item.path" :to="item.path">{{ item.title }}</router-link>
      <span v-else>{{ item.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { findTrail } from '@/utils/tree'

/**
 * 面包屑：从菜单树里反查当前路径的祖先链。
 *
 * <p>不依赖路由嵌套关系：动态路由是扁平注册到 Layout 下的，
 * 路由层级表达不了“系统管理 / 用户管理”，菜单树才是唯一事实源。</p>
 */
const route = useRoute()
const auth = useAuthStore()

const trail = computed(() => {
  if (route.path === '/dashboard') {
    return []
  }
  const found = findTrail(auth.menus, (node) => node.type === 'C' && node.path === route.path)
  if (found && found.length) {
    return found.map((node) => ({
      title: node.name,
      path: node.type === 'C' ? node.path : ''
    }))
  }
  return route.meta && route.meta.title ? [{ title: route.meta.title, path: '' }] : []
})
</script>

<style scoped>
.breadcrumb {
  font-size: 13px;
}

.current {
  color: var(--lx-text-secondary);
}

@media (max-width: 640px) {
  .breadcrumb {
    display: none;
  }
}
</style>
