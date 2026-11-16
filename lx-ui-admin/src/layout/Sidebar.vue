<template>
  <aside class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="logo">
      <div class="logo-mark">LX</div>
      <transition name="logo-fade">
        <span v-show="!appStore.sidebarCollapsed" class="logo-text">{{ t('layout.brand') }}</span>
      </transition>
    </div>

    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
        unique-opened
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>{{ t('layout.home') }}</template>
        </el-menu-item>

        <SidebarItem v-for="menu in visibleMenus" :key="menu.id" :item="menu" />
      </el-menu>
    </el-scrollbar>

    <div v-show="!appStore.sidebarCollapsed" class="sidebar-footer">
      v1.0 · {{ t('layout.brand') }}
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeFilled } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import SidebarItem from './SidebarItem.vue'

/**
 * 侧边栏。
 *
 * <p>菜单数据来自 {@code /api/admin/auth/menus}，前端只渲染 M（目录）与 C（菜单）；
 * 隐藏菜单（visible=1）不展示，但仍然可以按 URL 访问（受后端权限保护）。</p>
 */
const route = useRoute()
const { t } = useI18n()
const appStore = useAppStore()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)

const visibleMenus = computed(() => filterVisible(auth.menus))

function filterVisible(menus) {
  return (menus || [])
    .filter((menu) => menu.visible !== '1' && menu.type !== 'F')
    .map((menu) => ({
      ...menu,
      children: filterVisible(menu.children)
    }))
    .filter((menu) => menu.type === 'C' || (menu.children && menu.children.length > 0))
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--lx-sidebar-width);
  background: var(--lx-sidebar-bg);
  display: flex;
  flex-direction: column;
  z-index: 1000;
  transition: width 0.22s ease, background-color 0.2s ease;
  overflow: hidden;
}

.sidebar.collapsed {
  width: var(--lx-sidebar-collapsed-width);
}

/* 浅色侧栏需要一条右边线，否则和内容区糊在一起 */
html.lx-sidebar-light .sidebar {
  border-right: 1px solid var(--lx-border);
}

.logo {
  height: var(--lx-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--lx-sidebar-divider);
  overflow: hidden;
}

.logo-mark {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--lx-primary-light), var(--lx-primary));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.5px;
}

.logo-text {
  color: var(--lx-sidebar-text-active);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
}

.logo-fade-enter-active,
.logo-fade-leave-active {
  transition: opacity 0.15s ease;
}

.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
}

.sidebar-scroll {
  flex: 1;
  padding-top: 6px;
}

.sidebar :deep(.el-menu) {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--lx-sidebar-text);
  --el-menu-hover-bg-color: var(--lx-sidebar-bg-hover);
  --el-menu-active-color: var(--lx-sidebar-active-text);
}

.sidebar :deep(.el-menu-item),
.sidebar :deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  margin: 2px 8px;
  border-radius: 6px;
}

.sidebar :deep(.el-menu-item:hover),
.sidebar :deep(.el-sub-menu__title:hover) {
  background-color: var(--lx-sidebar-bg-hover) !important;
}

.sidebar :deep(.el-menu-item.is-active) {
  background: var(--lx-sidebar-active-bg) !important;
  color: var(--lx-sidebar-active-text) !important;
}

.sidebar :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  height: 42px;
  line-height: 42px;
}

.sidebar-footer {
  flex-shrink: 0;
  padding: 12px 16px;
  font-size: 12px;
  color: var(--lx-sidebar-muted);
  border-top: 1px solid var(--lx-sidebar-divider);
  white-space: nowrap;
}

.sidebar.collapsed :deep(.el-menu--collapse .el-sub-menu__title span),
.sidebar.collapsed :deep(.el-menu--collapse .el-menu-item span) {
  display: none;
}
</style>
