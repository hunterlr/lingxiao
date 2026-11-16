<template>
  <header class="navbar">
    <div class="navbar-left">
      <el-icon class="hamburger" @click="appStore.toggleSidebar()">
        <Expand v-if="appStore.sidebarCollapsed" />
        <Fold v-else />
      </el-icon>
      <Breadcrumb />
    </div>

    <div class="navbar-right">
      <el-tooltip content="全屏 / 退出全屏" placement="bottom">
        <el-icon class="navbar-icon" @click="toggleFullscreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <el-tooltip :content="appStore.isDark ? '切换到亮色' : '切换到暗色'" placement="bottom">
        <el-icon class="navbar-icon" @click="appStore.toggleDark()">
          <Sunny v-if="appStore.isDark" />
          <Moon v-else />
        </el-icon>
      </el-tooltip>

      <el-tooltip content="主题设置" placement="bottom">
        <el-icon class="navbar-icon" @click="appStore.openThemeDrawer()">
          <Setting />
        </el-icon>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-chip">
          <el-avatar :size="30" :src="avatarUrl" class="user-avatar">
            {{ initials }}
          </el-avatar>
          <div class="user-meta">
            <span class="user-name">{{ auth.displayName }}</span>
            <span class="user-role">{{ auth.roleLabel }}</span>
          </div>
          <el-icon class="user-arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Expand,
  Fold,
  FullScreen,
  Moon,
  Setting,
  Sunny,
  SwitchButton,
  User
} from '@element-plus/icons-vue'
import Breadcrumb from './Breadcrumb.vue'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import { resetDynamicRoutes } from '@/router/dynamic'

/**
 * 顶栏：折叠开关、面包屑、全屏、用户菜单。
 *
 * <p>退出登录必须同时做三件事：调后端销毁 Token、清 Pinia、移除动态路由。
 * 少做最后一步的话，换账号登录后旧账号的菜单路由仍然存在，
 * 会出现“菜单看不到但 URL 能进页面”的假象。</p>
 */
const router = useRouter()
const appStore = useAppStore()
const auth = useAuthStore()

const initials = computed(() => {
  const name = auth.displayName || 'LX'
  return name.slice(0, 1).toUpperCase()
})

const avatarUrl = computed(() => {
  const avatar = auth.avatar
  if (!avatar) return ''
  if (avatar.startsWith('http') || avatar.startsWith('/')) return avatar
  return `/api/file/${avatar}`
})

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen()
  }
}

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    await auth.logout()
    resetDynamicRoutes()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}
</script>

<style scoped>
.navbar {
  height: var(--lx-header-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--lx-card-bg);
  border-bottom: 1px solid var(--lx-border);
  position: sticky;
  top: 0;
  z-index: 900;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.navbar-left,
.navbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.hamburger,
.navbar-icon {
  /* Element Plus 的 .el-icon 固定 width/height: 1em，只调 font-size 不会扩大点击区域，
     这里显式给 38x38，图标 22px，兼顾可见性与可点性 */
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: var(--lx-text);
  cursor: pointer;
  padding: 0;
  border-radius: 8px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}

.hamburger:hover,
.navbar-icon:hover {
  color: var(--lx-primary);
  background: var(--lx-hover-bg);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-chip:hover {
  background: var(--lx-hover-bg);
}

.user-avatar {
  background: var(--lx-primary);
  color: #fff;
  font-size: 14px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text);
}

.user-role {
  font-size: 11px;
  color: var(--lx-text-secondary);
}

.user-arrow {
  font-size: 12px;
  color: var(--lx-text-secondary);
}

@media (max-width: 768px) {
  .user-meta {
    display: none;
  }
}
</style>
