<template>
  <el-sub-menu v-if="hasChildren" :index="`menu-${item.id}`">
    <template #title>
      <AppIcon :name="item.icon" />
      <span class="menu-title">{{ item.name }}</span>
    </template>
    <SidebarItem v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>

  <el-menu-item v-else-if="item.type === 'C' && item.path" :index="item.path">
    <AppIcon :name="item.icon" />
    <template #title>{{ item.name }}</template>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

/**
 * 递归菜单项。
 *
 * <p>目录（M）用 {@code menu-{id}} 作为 el-sub-menu 的 index：
 * 目录的 path 可能和某个子菜单路径相同，用 ID 保证唯一；
 * 菜单（C）直接用自己的路由地址，配合 el-menu 的 router 模式跳转。</p>
 */
const props = defineProps({
  item: { type: Object, required: true }
})

const hasChildren = computed(() => Array.isArray(props.item.children) && props.item.children.length > 0)
</script>

<style scoped>
.menu-title {
  margin-left: 4px;
}
</style>
