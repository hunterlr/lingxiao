<template>
  <div class="rich-editor">
    <div class="editor-bar">
      <div class="editor-bar-left">
        <el-tag size="small" effect="plain" type="info">{{ currentLabel }}</el-tag>
        <span class="editor-hint">{{ t('editor.hint') }}</span>
      </div>
      <el-dropdown trigger="click" placement="bottom-end" @command="handleSwitch">
        <el-button link type="primary" size="small">
          {{ t('editor.switch') }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="item in EDITOR_ENGINES"
              :key="item.value"
              :command="item.value"
              :disabled="item.value === engine"
            >
              <div class="engine-option">
                <span class="engine-option-label">{{ item.label }}</span>
                <span class="engine-option-hint">{{ t('editor.' + item.hintKey) }}</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided command="__default__" :disabled="!overrideActive">
              {{ t('editor.followDefault') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <WangEditorNext
      v-if="engine === 'wangeditor-next'"
      v-model="inner"
      :height="height"
      :placeholder="resolvedPlaceholder"
    />
    <UEditorPlus v-else v-model="inner" :height="height" :placeholder="resolvedPlaceholder" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import WangEditorNext from '@/components/editor/WangEditorNext.vue'
import UEditorPlus from '@/components/editor/UEditorPlus.vue'
import {
  EDITOR_ENGINES,
  editorEngine,
  engineLabel,
  hasEditorOverride,
  loadEditorEngine,
  setEditorEngine
} from '@/composables/useEditorEngine'

/**
 * 富文本编辑器外壳（业务页面只用这一个组件）。
 *
 * <p>两种引擎，切换方式分三层：</p>
 * <ul>
 *   <li>平台默认：系统参数 {@code sys.editor.type}，在「参数设置」里改，影响新用户 / 新设备；</li>
 *   <li>本机偏好：右上角「切换编辑器」，写 localStorage，只影响当前浏览器；</li>
 *   <li>构建兜底：{@code VITE_RICH_EDITOR}，平台参数读不到时使用。</li>
 * </ul>
 *
 * <p>切换时组件会重新挂载，内容以父级 {@code v-model} 为准，不会丢正文；
 * 但撤销栈会重置，这是可接受的代价（换引擎本来就是换实现）。</p>
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  height: { type: String, default: '360px' },
  placeholder: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const engine = editorEngine
const currentLabel = computed(() => engineLabel(engine.value))
const overrideActive = computed(() => hasEditorOverride())
const resolvedPlaceholder = computed(() => props.placeholder || t('editor.placeholder'))

const inner = computed({
  get: () => props.modelValue || '',
  set: (value) => emit('update:modelValue', value)
})

onMounted(() => {
  // 平台默认值只在首次真正用编辑器时加载，失败静默回落到构建默认值
  loadEditorEngine()
})

function handleSwitch(command) {
  const before = engine.value
  const next = setEditorEngine(command)
  if (next === before) return
  ElMessage.success(
    command === '__default__'
      ? t('editor.restoredDefault', { engine: engineLabel(next) })
      : t('editor.switchedTo', { engine: engineLabel(next) })
  )
}
</script>

<style scoped>
.rich-editor {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
  background: var(--lx-card-bg);
  z-index: 1;
}

.editor-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 10px;
  background: var(--lx-fill-bg);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.editor-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.editor-hint {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.engine-option {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.engine-option-label {
  font-size: 13px;
}

.engine-option-hint {
  font-size: 11px;
  color: #909399;
}
</style>
