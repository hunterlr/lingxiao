<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

const { t } = usePortalI18n()

const statusCode = computed(() => Number(props.error.statusCode) || 500)
const isNotFound = computed(() => statusCode.value === 404)
const title = computed(() =>
  isNotFound.value ? t('error.404.title') : t('error.500.title')
)
// 只给本站自己的文案：后端 statusMessage 可能是内部细节或另一种语言
const message = computed(() => (isNotFound.value ? t('error.404.desc') : t('error.500.desc')))

useSeoMeta({ title, robots: 'noindex' })
</script>

<template>
  <div class="error-page">
    <div class="error-panel">
      <p class="error-code">{{ statusCode }}</p>
      <h1 class="error-title">{{ title }}</h1>
      <p class="error-message">{{ message }}</p>
      <button type="button" class="button" @click="clearError({ redirect: '/' })">{{ t('error.backHome') }}</button>
    </div>
  </div>
</template>