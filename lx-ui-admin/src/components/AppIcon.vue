<template>
  <el-icon v-if="resolved" :size="size">
    <component :is="resolved" />
  </el-icon>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 菜单图标适配器。
 *
 * <p>后端菜单只存一个短名（setting / user / picture …），
 * 前端映射到 Element Plus 图标组件。找不到时退回 Menu，
 * 保证新增菜单即使图标名写错也不会出现空白。</p>
 *
 * <p>映射表故意保持“一个后端名对一个图标名”，不做花哨的智能推断：
 * 菜单图标是设计决策，应该显式可控。</p>
 */
const props = defineProps({
  name: { type: String, default: '' },
  size: { type: [Number, String], default: 18 }
})

const ICON_MAP = {
  setting: 'Setting',
  monitor: 'Monitor',
  tool: 'Tools',
  user: 'User',
  peoples: 'UserFilled',
  'tree-table': 'Grid',
  tree: 'Share',
  post: 'Postcard',
  dict: 'Notebook',
  edit: 'Edit',
  message: 'Bell',
  comment: 'ChatDotRound',
  online: 'Connection',
  logininfor: 'Key',
  form: 'Document',
  server: 'Cpu',
  redis: 'Coin',
  upload: 'UploadFilled',
  picture: 'Picture',
  component: 'Box',
  color: 'Grid',
  document: 'Document',
  delete: 'Delete',
  crop: 'Crop',
  'time-range': 'Timer',
  code: 'MagicStick',
  documentation: 'Document',
  home: 'HomeFilled',
  dashboard: 'Odometer',
  lock: 'Lock',
  userfilled: 'UserFilled',
  link: 'Link',
  language: 'ChatLineSquare'
}

const resolved = computed(() => {
  if (!props.name) return ''
  return ICON_MAP[props.name.toLowerCase()] || 'Menu'
})
</script>
