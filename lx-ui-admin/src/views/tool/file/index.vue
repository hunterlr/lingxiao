<template>
  <div class="page-container">
    <div class="page-card">
      <div class="file-head">
        <div>
          <h3 class="section-title" style="margin-bottom: 4px">{{ t('page.file.title') }}</h3>
          <p class="text-secondary" style="margin: 0; font-size: 13px">
            {{ t('page.file.hint') }}
          </p>
        </div>
        <el-tag type="info" effect="plain">{{ t('page.file.tag') }}</el-tag>
      </div>
      <FileLibrary show-upload />
    </div>
  </div>
</template>

<script setup>
import FileLibrary from '@/components/FileLibrary.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * 文件中心：上传入口 + 文件浏览。
 * 具体能力都在 FileLibrary，页面只负责标题与说明。
 */
</script>

<style scoped>
.file-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
