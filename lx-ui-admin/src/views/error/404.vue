<template>
  <div class="error-page">
    <div class="error-code">404</div>
    <h2>页面不存在</h2>
    <p class="error-desc">
      地址可能已经变更，或者对应模块尚未接入前端。
      如果是刚新增的菜单，请检查菜单的「组件路径」是否与
      <code>src/views</code> 下的文件一致。
    </p>
    <div class="error-actions">
      <el-button type="primary" @click="router.replace('/')">返回首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
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
  max-width: 480px;
  color: var(--lx-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.error-desc code {
  background: var(--lx-fill-strong);
  padding: 1px 5px;
  border-radius: 3px;
}

.error-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
}
</style>
