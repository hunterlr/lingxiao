<template>
  <div class="page-container">
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>{{ t('dashboard.hello', { greeting, name: auth.displayName }) }}</h2>
        <p class="welcome-sub">
          {{ t('dashboard.welcomePrefix') }} <b>{{ auth.username }}</b>
          <span v-if="auth.isSuperAdmin">{{ t('dashboard.superAdmin') }}</span>
        </p>
        <div class="welcome-tags">
          <el-tag v-for="role in auth.roles" :key="role" effect="dark" type="primary" size="small">
            {{ role }}
          </el-tag>
          <el-tag v-if="!auth.roles.length" type="info" size="small">{{ t('dashboard.noRole') }}</el-tag>
        </div>
      </div>
      <div class="welcome-illustration">
        <div class="bubble bubble-1"></div>
        <div class="bubble bubble-2"></div>
        <div class="bubble bubble-3"></div>
      </div>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="item in stats" :key="item.label" :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: item.bg, color: item.color }">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <div class="page-card">
          <h3 class="section-title">{{ t('dashboard.quickEntry') }}</h3>
          <div class="quick-grid">
            <div
              v-for="entry in quickEntries"
              :key="entry.path"
              class="quick-item"
              @click="router.push(entry.path)"
            >
              <AppIcon :name="entry.icon" :size="18" />
              <span class="quick-title">{{ entry.title }}</span>
            </div>
          </div>
          <el-empty
            v-if="!quickEntries.length"
            :description="t('dashboard.noMenu')"
            :image-size="80"
          />
        </div>
      </el-col>

      <el-col :xs="24" :lg="10">
        <div class="page-card">
          <h3 class="section-title">{{ t('dashboard.capability') }}</h3>
          <ul class="capability-list">
            <li>
              <el-icon color="#3fae6a"><CircleCheckFilled /></el-icon>
              {{ t('dashboard.cap1') }}
            </li>
            <li>
              <el-icon color="#3fae6a"><CircleCheckFilled /></el-icon>
              {{ t('dashboard.cap2') }}
            </li>
            <li>
              <el-icon color="#3fae6a"><CircleCheckFilled /></el-icon>
              {{ t('dashboard.cap3') }}
            </li>
            <li>
              <el-icon color="#3fae6a"><CircleCheckFilled /></el-icon>
              {{ t('dashboard.cap4') }}
            </li>
            <li>
              <el-icon color="#3fae6a"><CircleCheckFilled /></el-icon>
              {{ t('dashboard.cap5') }}
            </li>
            <li class="pending">
              <el-icon color="#d9a13b"><Clock /></el-icon>
              {{ t('dashboard.cap6') }}
            </li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  CircleCheckFilled,
  Clock,
  Document,
  Files,
  Menu as MenuIcon,
  UserFilled
} from '@element-plus/icons-vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/store/auth'

/**
 * 首页。
 *
 * <p>不放“假数据仪表盘”：没有统计接口就不编造数字。
 * 这里展示的是真实登录信息、菜单数量与可点进去的菜单入口。</p>
 */
const router = useRouter()
const { t, te } = useI18n()
const auth = useAuthStore()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return t('dashboard.greetingNight')
  if (hour < 12) return t('dashboard.greetingMorning')
  if (hour < 14) return t('dashboard.greetingNoon')
  if (hour < 18) return t('dashboard.greetingAfternoon')
  return t('dashboard.greetingEvening')
})

const pageMenus = computed(() => {
  const result = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.type === 'C' && node.path) result.push(node)
      if (node.children) walk(node.children)
    }
  }
  walk(auth.menus)
  return result
})

const stats = computed(() => [
  {
    label: t('dashboard.visibleMenus'),
    value: pageMenus.value.length,
    icon: MenuIcon,
    color: '#2f6fed',
    bg: 'rgba(47, 111, 237, 0.1)'
  },
  {
    label: t('dashboard.permissions'),
    value: auth.isSuperAdmin ? t('dashboard.allPermissions') : auth.permissions.length,
    icon: Document,
    color: '#7a5af8',
    bg: 'rgba(122, 90, 248, 0.1)'
  },
  {
    label: t('dashboard.roles'),
    value: auth.roles.length,
    icon: UserFilled,
    color: '#3fae6a',
    bg: 'rgba(63, 174, 106, 0.1)'
  },
  {
    label: t('dashboard.fileModule'),
    value: t('dashboard.fileEnabled'),
    icon: Files,
    color: '#e08a3c',
    bg: 'rgba(224, 138, 60, 0.12)'
  }
])

/**
 * 快捷入口。
 *
 * <p>后端菜单节点的字段是 {@code name}（不是 title），这里显式映射成
 * {@code title}，避免模板里出现“只有图标、没有文字”的空壳入口。</p>
 */
function menuLabel(menu) {
  const key = menu.menuKey ? `menu.${menu.menuKey}` : ''
  return key && te(key) ? t(key) : menu.name
}

const quickEntries = computed(() =>
  pageMenus.value.slice(0, 8).map((menu) => ({
    path: menu.path,
    title: menuLabel(menu),
    icon: menu.icon
  }))
)
</script>

<style scoped>
.welcome-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--lx-radius);
  padding: 26px 28px;
  background: linear-gradient(120deg, #1f3a7a, #2f6fed 55%, #4f8bff);
  color: #fff;
  box-shadow: 0 12px 30px rgba(47, 111, 237, 0.24);
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.welcome-sub {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
}

.welcome-tags {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.welcome-illustration {
  position: absolute;
  right: 20px;
  top: 0;
  bottom: 0;
  width: 220px;
  pointer-events: none;
}

.bubble {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.bubble-1 {
  width: 140px;
  height: 140px;
  right: 0;
  top: -40px;
}

.bubble-2 {
  width: 80px;
  height: 80px;
  right: 110px;
  bottom: -20px;
}

.bubble-3 {
  width: 40px;
  height: 40px;
  right: 60px;
  top: 30px;
}

.stat-row {
  margin-top: 16px;
}

.stat-card {
  background: var(--lx-card-bg);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-shadow);
  padding: 18px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--lx-text-secondary);
  margin-top: 2px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  cursor: pointer;
  color: var(--lx-text);
  transition: all 0.15s;
  font-size: 13px;
}

.quick-item:hover {
  border-color: rgba(47, 111, 237, 0.5);
  color: var(--lx-primary);
  background: rgba(47, 111, 237, 0.04);
}

.quick-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.capability-list {
  list-style: none;
  margin: 0;
  padding: 0;
  line-height: 2.3;
  font-size: 13px;
}

.capability-list li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.capability-list .pending {
  color: var(--lx-text-secondary);
}
</style>
