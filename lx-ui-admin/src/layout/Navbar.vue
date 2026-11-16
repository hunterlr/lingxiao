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
      <el-dropdown trigger="click" @command="handleLocale">
        <span class="lang-chip" :title="t('layout.language')">{{ localeStore.shortLabel }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="item in SUPPORTED_LOCALES"
              :key="item.value"
              :command="item.value"
              :disabled="item.value === localeStore.locale"
            >
              {{ item.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-tooltip :content="t('layout.fullscreen')" placement="bottom">
        <el-icon class="navbar-icon" @click="toggleFullscreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>

      <el-tooltip :content="appStore.isDark ? t('layout.lightMode') : t('layout.darkMode')" placement="bottom">
        <el-icon class="navbar-icon" @click="appStore.toggleDark()">
          <Sunny v-if="appStore.isDark" />
          <Moon v-else />
        </el-icon>
      </el-tooltip>

      <el-tooltip :content="t('layout.themeSetting')" placement="bottom">
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
              {{ t('layout.profile') }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              {{ t('layout.logout') }}
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
import { useI18n } from 'vue-i18n'
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
import { SUPPORTED_LOCALES } from '@/i18n'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import { useLocaleStore } from '@/store/locale'
import { resetDynamicRoutes } from '@/router/dynamic'

/**
 * 顶栏：折叠开关、面包屑、语言切换、全屏、用户菜单。
 *
 * <p>退出登录必须同时做三件事：调后端销毁 Token、清 Pinia、移除动态路由。
 * 少做最后一步的话，换账号登录后旧账号的菜单路由仍然存在，
 * 会出现“菜单看不到但 URL 能进页面”的假象。</p>
 */
const router = useRouter()
const { t } = useI18n()
const appStore = useAppStore()
const auth = useAuthStore()
const localeStore = useLocaleStore()

const initials = computed(() => {
  const name = auth.displayName || 'LX'
  return name.slice(0, 1).toUpperCase()
})

/** 头像地址：兼容外链、站内绝对路径和文件 ID。 */
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

function handleLocale(locale) {
  localeStore.setLocale(locale)
}

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm(t('layout.logoutConfirm'), t('layout.logoutTitle'), {
        type: 'warning',
        confirmButtonText: t('layout.logout'),
        cancelButtonText: t('common.cancel')
      })
    } catch (e) {
      return
    }
    await auth.logout()
    resetDynamicRoutes()
    ElMessage.success(t('layout.logoutDone'))
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

/* 语言切换：用文字胶囊而不是图标，中英文都一眼能认出来 */
.lang-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--lx-border);
  border-radius: 13px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lx-text);
  cursor: pointer;
  user-select: none;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.lang-chip:hover {
  color: var(--lx-primary);
  border-color: var(--lx-primary);
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
