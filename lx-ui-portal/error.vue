<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

const statusCode = computed(() => Number(props.error.statusCode) || 500)
const title = computed(() => (statusCode.value === 404 ? '页面不存在' : '页面暂时不可用'))
const message = computed(() => {
  if (statusCode.value === 404) {
    return '这个地址可能已经下线、被移动，或从未发布过。'
  }
  return props.error.statusMessage || '内容服务暂时不可用，请稍后再试。'
})

useSeoMeta({ title, robots: 'noindex' })
</script>

<template>
  <div class="error-page">
    <div class="error-panel">
      <p class="error-code">{{ statusCode }}</p>
      <h1 class="error-title">{{ title }}</h1>
      <p class="error-message">{{ message }}</p>
      <button type="button" class="button" @click="clearError({ redirect: '/' })">返回首页</button>
    </div>
  </div>
</template>
