<template>
  <div class="page-container">
    <div class="page-card">
      <div class="file-head">
        <div>
          <h3 class="section-title" style="margin-bottom: 4px">文件中心</h3>
          <p class="text-secondary" style="margin: 0; font-size: 13px">
            上传后按「原名 / 现名 / 备注」都能搜到；改名只改展示名，原始文件名永久保留。
          </p>
        </div>
        <el-tag type="info" effect="plain">单文件 ≤ 10MB · 图片自动生成缩略图/中图</el-tag>
      </div>
      <FileLibrary show-upload />
    </div>
  </div>
</template>

<script setup>
import FileLibrary from '@/components/FileLibrary.vue'

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
