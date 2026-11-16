<template>
  <el-drawer
    v-model="appStore.themeDrawerVisible"
    title="主题设置"
    size="340px"
    append-to-body
    class="theme-drawer"
  >
    <div class="theme-block">
      <p class="theme-label">外观模式</p>
      <div class="mode-grid">
        <button
          v-for="item in modes"
          :key="item.value"
          type="button"
          class="mode-card"
          :class="{ active: appStore.theme.mode === item.value }"
          @click="appStore.setThemeMode(item.value)"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>
      <p class="theme-hint">「跟随系统」读取操作系统深浅色设置，系统切换时页面自动跟随。</p>
    </div>

    <el-divider />

    <div class="theme-block">
      <p class="theme-label">主题色</p>
      <div class="color-grid">
        <button
          v-for="preset in THEME_PRESETS"
          :key="preset.value"
          type="button"
          class="color-dot"
          :class="{ active: appStore.theme.primaryColor === preset.value }"
          :style="{ background: preset.value }"
          :title="preset.name"
          @click="appStore.setPrimaryColor(preset.value)"
        >
          <el-icon v-if="appStore.theme.primaryColor === preset.value" :size="14">
            <Check />
          </el-icon>
        </button>
        <el-color-picker
          :model-value="appStore.theme.primaryColor"
          :predefine="THEME_PRESETS.map((item) => item.value)"
          @change="appStore.setPrimaryColor"
        />
      </div>
      <p class="theme-hint">主色会同步到按钮、链接、菜单选中态与 Element Plus 派生色。</p>
    </div>

    <el-divider />

    <div class="theme-block">
      <p class="theme-label">侧边栏风格</p>
      <div class="sidebar-grid">
        <button
          v-for="item in sidebarStyles"
          :key="item.value"
          type="button"
          class="sidebar-card"
          :class="{ active: appStore.theme.sidebarStyle === item.value }"
          @click="appStore.setSidebarStyle(item.value)"
        >
          <span class="sidebar-preview" :class="item.value">
            <span class="preview-bar" />
            <span class="preview-body">
              <i /><i /><i />
            </span>
          </span>
          <span>{{ item.label }}</span>
        </button>
      </div>
    </div>

    <el-divider />

    <div class="theme-footer">
      <el-button plain @click="appStore.resetTheme()">恢复默认</el-button>
      <p class="theme-note">
        偏好仅保存在当前浏览器（localStorage），不写入账号。清除浏览器数据后会回到默认主题。
      </p>
    </div>
  </el-drawer>
</template>

<script setup>
import { Check, Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import { THEME_PRESETS } from '@/utils/theme'

/**
 * 主题设置抽屉。
 *
 * <p>放在 layout 下而不是全局 App.vue：登录页、403/404 不需要设置入口，
 * 但它们仍然读取同一份主题，所以深色模式下不会突然变全白。</p>
 *
 * <p>所有交互只改 store，DOM 更新由 utils/theme.js 统一负责，
 * 避免“抽屉里改了颜色，却忘了改 Element Plus 变量”这类漏改。</p>
 */
const appStore = useAppStore()

const modes = [
  { label: '亮色', value: 'light', icon: Sunny },
  { label: '暗色', value: 'dark', icon: Moon },
  { label: '跟随系统', value: 'auto', icon: Monitor }
]

const sidebarStyles = [
  { label: '深色侧栏', value: 'dark' },
  { label: '浅色侧栏', value: 'light' }
]
</script>

<style scoped>
.theme-block {
  padding: 2px 0;
}

.theme-label {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text);
}

.theme-hint {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--lx-text-secondary);
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.mode-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 4px;
  font-size: 12px;
  color: var(--lx-text-secondary);
  background: var(--lx-fill-bg);
  border: 1px solid var(--lx-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.mode-card:hover {
  color: var(--lx-primary);
  border-color: var(--lx-primary-light);
}

.mode-card.active {
  color: var(--lx-primary);
  border-color: var(--lx-primary);
  background: var(--el-color-primary-light-9);
}

.color-grid {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.color-dot {
  width: 26px;
  height: 26px;
  border: 2px solid transparent;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  outline: none;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08) inset;
}

.color-dot.active {
  border-color: var(--lx-text);
}

.sidebar-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.sidebar-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 10px;
  font-size: 12px;
  color: var(--lx-text-secondary);
  background: transparent;
  border: 1px solid var(--lx-border);
  border-radius: 8px;
  cursor: pointer;
}

.sidebar-card.active {
  color: var(--lx-primary);
  border-color: var(--lx-primary);
}

.sidebar-preview {
  width: 96px;
  height: 56px;
  display: flex;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--lx-border);
}

.sidebar-preview .preview-bar {
  width: 26px;
  background: #131c31;
}

.sidebar-preview .preview-body {
  flex: 1;
  background: #f2f4f8;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.sidebar-preview .preview-body i {
  height: 6px;
  border-radius: 2px;
  background: #d8deea;
}

.sidebar-preview.light .preview-bar {
  background: #ffffff;
  border-right: 1px solid #e4e8f0;
}

.theme-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.theme-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--lx-text-secondary);
}
</style>
