<template>
  <div class="error-page">
    <div class="error-code">403</div>
    <h2>没有权限访问这个页面</h2>
    <p class="error-desc">
      当前账号缺少该页面所需的权限码。请联系管理员在「角色管理」里授权，
      或换一个有权限的账号登录。
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
