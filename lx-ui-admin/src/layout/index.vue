<template>
  <div class="app-layout">
    <Sidebar />
    <div class="app-body" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
      <Navbar />
      <main class="app-main">
        <router-view v-slot="{ Component, route }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
    <ThemeDrawer />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useAppStore } from '@/store/app'
import { loadEditorEngine } from '@/composables/useEditorEngine'
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
import ThemeDrawer from '@/components/ThemeDrawer.vue'

/**
 * 后台主布局：左侧固定导航 + 顶部栏 + 内容区。
 *
 * <p>内容区用 {@code route.path} 作 key：切换菜单时销毁旧页面，
 * 避免不同页面的搜索条件、分页状态互相污染；代价是切换时重新拉数据，
 * 后台管理可以接受。</p>
 */
const appStore = useAppStore()

// 进入后台后预取平台默认编辑器，避免用户打开公告弹窗时编辑器再“换一次”
onMounted(() => {
  loadEditorEngine()
})
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-body {
  margin-left: var(--lx-sidebar-width);
  transition: margin-left 0.22s ease;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-body.is-collapsed {
  margin-left: var(--lx-sidebar-collapsed-width);
}

.app-main {
  flex: 1;
  padding: 0;
  overflow-x: hidden;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.16s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .app-body {
    margin-left: var(--lx-sidebar-collapsed-width);
  }
}
</style>
