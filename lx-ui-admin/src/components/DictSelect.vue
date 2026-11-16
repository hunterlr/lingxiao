<template>
  <el-select
    :model-value="modelValue"
    :placeholder="resolvedPlaceholder"
    :clearable="clearable"
    :disabled="disabled"
    :style="{ width }"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <el-option
      v-for="item in options"
      :key="item.dictValue"
      :label="optionLabel(item)"
      :value="item.dictValue"
    />
  </el-select>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadDict } from '@/composables/useDict'

/**
 * 字典下拉框。用法：
 * <pre>
 *   &lt;DictSelect v-model="form.status" dict-type="sys_common_status" /&gt;
 * </pre>
 *
 * <p>内部走 loadDict 的共享缓存：同一页面多个状态筛选也只会请求一次。</p>
 */
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  dictType: { type: String, required: true },
  placeholder: { type: String, default: '' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  width: { type: String, default: '100%' }
})

const emit = defineEmits(['update:modelValue'])
const { t, te } = useI18n()

const options = ref([])

const resolvedPlaceholder = computed(() => props.placeholder || t('common.pleaseSelect'))

/** 与 DictTag 同一规则：内置字典走 dict.<type>.<value>，缺项回退后端标签。 */
function optionLabel(item) {
  const key = `dict.${props.dictType}.${item.dictValue}`
  return te(key) ? t(key) : item.dictLabel
}

async function refresh() {
  options.value = await loadDict(props.dictType)
}

onMounted(refresh)
watch(() => props.dictType, refresh)
</script>
