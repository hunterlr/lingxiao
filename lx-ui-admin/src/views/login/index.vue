<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-inner">
        <div class="brand-logo">LX</div>
        <h1>凌霄 LingXiao</h1>
        <p class="brand-sub">后台管理基座 · v1.0</p>
        <ul class="brand-points">
          <li>平台 / 租户 / 会员三域隔离，v1.0 先实装平台域</li>
          <li>RBAC 权限到按钮，权限码由后端菜单统一下发</li>
          <li>文件中心保留原名、现名与改名历史</li>
          <li>模块可插拔：关闭即不加载、接口 404</li>
        </ul>
      </div>
      <div class="brand-footer">Spring Boot 2.7 · Sa-Token · Vue 3 · Element Plus</div>
    </div>

    <div class="login-panel">
      <div class="login-card">
        <h2>登录后台</h2>
        <p class="login-tip">请使用管理员账号登录</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item v-if="captcha.enabled" prop="captchaCode">
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="验证码"
                maxlength="8"
                :prefix-icon="Key"
                clearable
              />
              <div class="captcha-image" title="点击刷新验证码" @click="refreshCaptcha">
                <img v-if="captcha.image" :src="captcha.image" alt="验证码" />
                <span v-else class="captcha-loading">加载中…</span>
              </div>
            </div>
          </el-form-item>
          <p v-else class="captcha-off-tip">验证码已由平台关闭</p>

          <el-button type="primary" class="login-button" :loading="loading" @click="submit">
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>
        </el-form>

        <div class="login-extra">
          <el-checkbox v-model="remember">记住用户名</el-checkbox>
          <span class="text-secondary">默认账号 admin / admin123</span>
        </div>
      </div>

      <div class="login-copyright">© 2026 凌霄 · 后台管理</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, User } from '@element-plus/icons-vue'
import { getCaptcha } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

/**
 * 登录页。
 *
 * <p>验证码是否显示由后端决定：`GET /auth/captcha` 返回 `enabled`，
 * 平台在参数设置里关掉 `sys.account.captchaEnabled` 时返回 false，
 * 这里就隐藏输入框、不传 captchaKey / captchaCode。</p>
 *
 * <p>“记住用户名”只存账号不存密码：浏览器密码管理已经足够，
 * 自己存密码是明摆着的风险。</p>
 */
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const remember = ref(localStorage.getItem('lx-admin-remember') !== '0')

const form = reactive({
  username: localStorage.getItem('lx-admin-username') || 'admin',
  password: '',
  captchaKey: '',
  captchaCode: ''
})

const captcha = reactive({ enabled: true, image: '', key: '' })

const rules = computed(() => {
  const base = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
  }
  if (captcha.enabled) {
    base.captchaCode = [{ required: true, message: '请输入验证码', trigger: 'blur' }]
  }
  return base
})

async function refreshCaptcha() {
  captcha.image = ''
  try {
    const data = await getCaptcha()
    captcha.enabled = data.enabled !== false
    if (!captcha.enabled) {
      // 平台关闭了验证码：清掉残留，避免把空 key 发给后端
      captcha.key = ''
      form.captchaKey = ''
      form.captchaCode = ''
      return
    }
    captcha.key = data.captchaKey
    captcha.image = data.image
    form.captchaKey = data.captchaKey
    form.captchaCode = ''
  } catch (e) {
    // 取不到就按“开启”处理：宁可多填一次，也不能把登录页变成免验证码
    captcha.enabled = true
    ElMessage.error('验证码加载失败，请检查后端服务')
  }
}

async function submit() {
  if (loading.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.login({
        username: form.username.trim(),
        password: form.password,
        captchaKey: captcha.enabled ? form.captchaKey : '',
        captchaCode: captcha.enabled ? form.captchaCode.trim() : ''
      })
      if (remember.value) {
        localStorage.setItem('lx-admin-username', form.username.trim())
        localStorage.setItem('lx-admin-remember', '1')
      } else {
        localStorage.removeItem('lx-admin-username')
        localStorage.setItem('lx-admin-remember', '0')
      }
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      await router.replace(redirect)
    } catch (e) {
      // 密码错误 / 验证码过期都要换一张，避免用户重复用同一个失效验证码
      await refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--lx-content-bg);
}

.login-brand {
  position: relative;
  flex: 1.1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px 8vw;
  color: #fff;
  background: radial-gradient(circle at 20% 20%, #3a6ff0 0%, #1b2c5c 45%, #0f1932 100%);
  overflow: hidden;
}

.login-brand::after {
  content: '';
  position: absolute;
  width: 520px;
  height: 520px;
  right: -200px;
  bottom: -200px;
  border-radius: 50%;
  background: rgba(90, 140, 255, 0.18);
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 460px;
}

.brand-logo {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 20px;
  letter-spacing: 1px;
  margin-bottom: 24px;
}

.brand-inner h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 2px;
}

.brand-sub {
  margin: 8px 0 32px;
  color: rgba(255, 255, 255, 0.66);
  font-size: 15px;
}

.brand-points {
  margin: 0;
  padding-left: 18px;
  line-height: 2;
  color: rgba(255, 255, 255, 0.82);
  font-size: 14px;
}

.brand-footer {
  position: absolute;
  left: 8vw;
  bottom: 32px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.login-panel {
  flex: 0.9;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

.login-card {
  width: 100%;
  max-width: 380px;
  background: var(--lx-card-bg);
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 18px 50px rgba(20, 35, 70, 0.12);
}

.login-card h2 {
  margin: 0;
  font-size: 24px;
  color: var(--lx-text);
}

.login-tip {
  margin: 6px 0 24px;
  color: var(--lx-text-secondary);
  font-size: 13px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-off-tip {
  margin: -6px 0 14px;
  font-size: 12px;
  color: var(--lx-text-secondary);
  text-align: left;
}

.captcha-image {
  width: 120px;
  height: 40px;
  flex: 0 0 120px;
  border: 1px solid var(--lx-border);
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lx-fill-bg);
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-loading {
  font-size: 12px;
  color: #9aa6ba;
}

.login-button {
  width: 100%;
  height: 42px;
  font-size: 15px;
  letter-spacing: 4px;
  margin-top: 4px;
}

.login-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  font-size: 12px;
}

.login-copyright {
  position: absolute;
  bottom: 24px;
  font-size: 12px;
  color: #9aa6ba;
}

@media (max-width: 960px) {
  .login-brand {
    display: none;
  }
}
</style>
