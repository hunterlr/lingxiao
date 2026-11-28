<template>
  <div class="wf-form-renderer">
    <template v-if="readonly">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
          {{ display(field) }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
    <el-form v-else label-width="110px" class="wf-form">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :required="field.required">
        <el-input
          v-if="field.type === 'input'"
          v-model="model[field.key]"
          :maxlength="field.maxLength || undefined"
          :show-word-limit="!!field.maxLength"
          :placeholder="placeholder(field)"
        />
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="model[field.key]"
          type="textarea"
          :rows="3"
          :maxlength="field.maxLength || undefined"
          :show-word-limit="!!field.maxLength"
          :placeholder="placeholder(field)"
        />
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="model[field.key]"
          :min="field.min != null ? Number(field.min) : undefined"
          :max="field.max != null ? Number(field.max) : undefined"
          :precision="field.precision != null ? field.precision : undefined"
          :controls-position="'right'"
          style="width: 100%"
        />
        <el-select v-else-if="field.type === 'select'" v-model="model[field.key]" clearable style="width: 100%" :placeholder="placeholder(field)">
          <el-option v-for="option in field.options || []" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-radio-group v-else-if="field.type === 'radio'" v-model="model[field.key]">
          <el-radio v-for="option in field.options || []" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
        </el-radio-group>
        <el-date-picker
          v-else-if="field.type === 'date'"
          v-model="model[field.key]"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
        <el-date-picker
          v-else-if="field.type === 'datetime'"
          v-model="model[field.key]"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
        <el-switch v-else-if="field.type === 'switch'" v-model="model[field.key]" />
        <el-select
          v-else-if="field.type === 'user'"
          v-model="model[field.key]"
          :multiple="!!field.multiple"
          filterable
          clearable
          style="width: 100%"
          :placeholder="placeholder(field)"
        >
          <el-option v-for="user in users" :key="user.userId" :label="userLabel(user)" :value="String(user.userId)" />
        </el-select>
        <span v-else class="text-secondary">{{ t('page.workflow.form.unknownType') }}：{{ field.type }}</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * 动态表单渲染器：发起页可编辑，详情抽屉只读。
 *
 * <p>直接改 {@code model[field.key]}，父组件传进来的是 reactive 对象；
 * 校验以后端为准，这里只做基本的必填与长度提示。</p>
 */
const props = defineProps({
  schema: { type: Object, default: () => ({ fields: [] }) },
  modelValue: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  users: { type: Array, default: () => [] },
  userNames: { type: Object, default: () => ({}) }
})

const { t } = useI18n()
const fields = computed(() => props.schema?.fields || [])
const model = computed(() => props.modelValue)

const userLabel = (user) => (user.nickname ? `${user.nickname}（${user.username}）` : user.username)

const placeholder = (field) => t('page.workflow.form.placeholder', { label: field.label })

const display = (field) => {
  const value = model.value?.[field.key]
  if (value == null || value === '' || (Array.isArray(value) && !value.length)) {
    return '-'
  }
  if (field.type === 'switch') {
    return value ? t('common.yes') : t('common.no')
  }
  if (field.type === 'user') {
    const toName = (id) => props.userNames[id] || id
    return Array.isArray(value) ? value.map(toName).join('、') : toName(value)
  }
  if ((field.type === 'select' || field.type === 'radio') && field.options) {
    const option = field.options.find((item) => item.value === value)
    return option ? option.label : value
  }
  return String(value)
}
</script>

<style scoped>
.wf-form {
  max-width: 640px;
}
</style>