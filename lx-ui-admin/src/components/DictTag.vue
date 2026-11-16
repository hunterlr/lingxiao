<template>
  <el-tag v-if="current" :type="tagType" :effect="effect" size="small" disable-transitions>
    {{ current.dictLabel }}
  </el-tag>
  <span v-else class="text-secondary">-</span>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listDictDataOptions } from '@/api/dict'

/**
 * 字典标签。用于表格里把 0/1、Y/N 渲染成“正常 / 停用”这类可读文本。
 *
 * <p>数据优先取父组件传入的 options（同一页多处使用时避免重复请求），
 * 不传则按 dictType 自己拉一次并缓存到页面内。</p>
 *
 * <p>不认识的字典值原样显示，便于发现“字典缺项”，而不是显示空白。</p>
 */
const props = defineProps({
  value: { type: [String, Number], default: '' },
  dictType: { type: String, default: '' },
  options: { type: Array, default: null },
  effect: { type: String, default: 'light' }
})

const list = ref(props.options || [])

const current = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return null
  const hit = list.value.find((item) => String(item.dictValue) === String(props.value))
  if (hit) return hit
  return { dictLabel: String(props.value), listClass: '' }
})

const tagType = computed(() => {
  const cls = current.value && current.value.listClass
  const allowed = ['primary', 'success', 'info', 'warning', 'danger']
  return allowed.includes(cls) ? cls : 'info'
})

watch(
  () => props.options,
  (value) => {
    if (value) list.value = value
  }
)

onMounted(async () => {
  if (!props.options && props.dictType && !list.value.length) {
    list.value = await listDictDataOptions(props.dictType).catch(() => [])
  }
})
</script>
