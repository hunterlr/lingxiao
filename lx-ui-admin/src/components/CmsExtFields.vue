<template>
  <template v-for="field in fields" :key="field.fieldKey">
    <el-form-item :label="field.fieldName" :required="field.required === '1'">
      <!-- 单行文本 / 日期 / 数字 -->
      <el-input
        v-if="field.fieldType === 'text'"
        :model-value="stringValue(field)"
        maxlength="255"
        clearable
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <el-date-picker
        v-else-if="field.fieldType === 'date'"
        :model-value="stringValue(field)"
        type="date"
        value-format="YYYY-MM-DD"
        style="width: 100%"
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <el-input-number
        v-else-if="field.fieldType === 'number'"
        :model-value="numberValue(field)"
        controls-position="right"
        style="width: 100%"
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <!-- 多行文本 / 富文本 -->
      <el-input
        v-else-if="field.fieldType === 'textarea'"
        :model-value="stringValue(field)"
        type="textarea"
        :rows="3"
        maxlength="2000"
        show-word-limit
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <RichEditor
        v-else-if="field.fieldType === 'richtext'"
        :model-value="stringValue(field)"
        height="220px"
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <!-- 单选 / 多选 -->
      <el-select
        v-else-if="field.fieldType === 'select'"
        :model-value="stringValue(field)"
        clearable
        style="width: 100%"
        @update:model-value="setValue(field.fieldKey, $event)"
      >
        <el-option
          v-for="option in optionsOf(field)"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        v-else-if="field.fieldType === 'checkbox'"
        :model-value="arrayValue(field)"
        multiple
        clearable
        style="width: 100%"
        @update:model-value="setValue(field.fieldKey, $event)"
      >
        <el-option
          v-for="option in optionsOf(field)"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <!-- 单图 / 文件 -->
      <MediaPicker
        v-else-if="field.fieldType === 'image'"
        :model-value="stringValue(field)"
        :fallback="field.fieldName"
        placeholder="fileId 或 https:// 开头的地址"
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <el-input
        v-else-if="field.fieldType === 'file'"
        :model-value="stringValue(field)"
        maxlength="255"
        clearable
        placeholder="fileId 或 https:// 开头的地址"
        @update:model-value="setValue(field.fieldKey, $event)"
      />
      <!-- 多图：逐张选择，支持增删与排序 -->
      <div v-else-if="field.fieldType === 'images'" class="ext-images">
        <div v-for="(image, index) in arrayValue(field)" :key="index" class="ext-image-row">
          <MediaPicker
            :model-value="image"
            :fallback="`图 ${index + 1}`"
            placeholder="fileId 或 https:// 开头的地址"
            @update:model-value="updateImage(field.fieldKey, index, $event)"
          />
          <el-button link type="danger" @click="removeImage(field.fieldKey, index)">删除</el-button>
        </div>
        <el-button :icon="Plus" @click="addImage(field.fieldKey)">添加图片</el-button>
      </div>
      <el-input
        v-else
        :model-value="stringValue(field)"
        maxlength="255"
        clearable
        @update:model-value="setValue(field.fieldKey, $event)"
      />
    </el-form-item>
  </template>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import MediaPicker from '@/components/MediaPicker.vue'
import RichEditor from '@/components/RichEditor.vue'

/**
 * 模型扩展字段渲染器。
 *
 * <p>字段定义来自后端 cms_model_field，值统一提交到内容对象的 ext 对象。
 * 组件只负责控件与值形态，必填 / 类型校验仍由后端兜底。</p>
 */
const props = defineProps({
  fields: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

function current() {
  return props.modelValue || {}
}

function stringValue(field) {
  const value = current()[field.fieldKey]
  return value === undefined || value === null ? '' : String(value)
}

function numberValue(field) {
  const value = current()[field.fieldKey]
  return typeof value === 'number' ? value : value ? Number(value) : undefined
}

function arrayValue(field) {
  const value = current()[field.fieldKey]
  return Array.isArray(value) ? value : []
}

function setValue(key, value) {
  emit('update:modelValue', { ...current(), [key]: value })
}

function optionsOf(field) {
  if (!field.optionsJson) return []
  try {
    const parsed = JSON.parse(field.optionsJson)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function addImage(key) {
  setValue(key, [...(current()[key] || []), ''])
}

function updateImage(key, index, value) {
  const list = [...(current()[key] || [])]
  list[index] = value
  setValue(key, list)
}

function removeImage(key, index) {
  const list = [...(current()[key] || [])]
  list.splice(index, 1)
  setValue(key, list)
}
</script>

<style scoped>
.ext-images {
  width: 100%;
}

.ext-image-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
