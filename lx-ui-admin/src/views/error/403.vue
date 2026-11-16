<template>
  <div class="error-page">
    <div class="error-code">403</div>
    <h2>{{ t('errorPage.forbiddenTitle') }}</h2>
    <p class="error-desc">
      {{ t('errorPage.forbiddenTip') }}
    </p>
    <div class="error-actions">
      <el-button type="primary" @click="router.replace('/')">{{ t('errorPage.backHome') }}</el-button>
      <el-button @click="router.back()">{{ t('errorPage.backPrev') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  background: var(--lx-content-bg);
}

.error-code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, #2f6fed, #7a5af8);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.error-page h2 {
  margin: 18px 0 8px;
  font-size: 20px;
}

.error-desc {
  max-width: 460px;
  color: var(--lx-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.error-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
}
</style>
