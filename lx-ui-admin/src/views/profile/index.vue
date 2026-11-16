<template>
  <div class="page-container">
    <div class="page-card">
      <div class="card-header">
        <h3 class="section-title">个人中心</h3>
        <div class="card-actions">
          <el-button type="primary" :icon="Edit" @click="openProfileDialog">编辑资料</el-button>
          <el-button :icon="Lock" @click="openPasswordDialog">修改密码</el-button>
        </div>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户 ID">
          <span class="text-mono">{{ auth.userId || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="登录账号">{{ auth.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ auth.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ auth.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ auth.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="role in auth.roles" :key="role" size="small" class="role-tag">
            {{ role }}
          </el-tag>
          <span v-if="!auth.roles.length" class="text-secondary">未分配角色</span>
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title" style="margin-top: 24px">我的权限</h3>
      <el-alert
        v-if="auth.isSuperAdmin"
        type="success"
        :closable="false"
        show-icon
        title="超级管理员拥有全部权限（*:*:*）"
        description="无需逐个授权；新增模块的按钮权限会自动生效。"
      />
      <div v-else class="permission-list">
        <el-tag v-for="perm in auth.permissions" :key="perm" size="small" type="info" class="perm-tag">
          {{ perm }}
        </el-tag>
        <el-empty v-if="!auth.permissions.length" description="当前账号没有按钮权限" :image-size="70" />
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 20px"
        title="关于自助改密"
        description="修改密码需要先校验原密码；保存成功后，该账号在所有终端的登录状态会立即失效，需要用新密码重新登录。登录账号、状态与角色仍由管理员在「用户管理」维护。"
      />
    </div>

    <!-- 编辑资料 -->
    <el-dialog
      v-model="profileVisible"
      title="编辑资料"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetProfileForm"
    >
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px">
        <el-form-item label="登录账号">
          <el-input :model-value="auth.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" maxlength="64" show-word-limit placeholder="显示在顶部与操作日志里" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" maxlength="128" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" maxlength="32" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog
      v-model="passwordVisible"
      title="修改密码"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetPasswordForm"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="保存后需要重新登录"
        description="该账号在所有终端的会话都会被注销。"
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password maxlength="64" autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password maxlength="64" autocomplete="new-password" placeholder="至少 8 位，含字母和数字" />
          <div class="form-tip">实际规则以「参数设置 → 密码复杂度策略」为准</div>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="64" autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Lock } from '@element-plus/icons-vue'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

/**
 * 个人中心。
 *
 * 自助能力只有两件：改昵称 / 邮箱 / 手机号、改密码。
 * 用户名、状态、角色、部门仍由管理员维护，避免用户自助提权。
 * 改密成功后后端已把该账号所有 Token 注销，这里同步清本地状态并回登录页，
 * 不能只弹一个“成功”让用户继续用着已经失效的会话。
 */
const auth = useAuthStore()
const router = useRouter()

const profileVisible = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref()
const profileForm = reactive({ nickname: '', email: '', phone: '' })

const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    // 前端只做“体验层”校验：默认策略要求 8 位字母+数字；
    // 真正的复杂度由后端 passwordPolicy 判定，参数改成 strong 时由后端返回提示。
    { min: 8, max: 64, message: '新密码长度必须在 8~64 之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openProfileDialog() {
  profileForm.nickname = auth.nickname || ''
  profileForm.email = auth.email || ''
  profileForm.phone = auth.phone || ''
  profileVisible.value = true
}

function resetProfileForm() {
  profileFormRef.value?.resetFields()
}

async function submitProfile() {
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return
  profileSaving.value = true
  try {
    await auth.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone
    })
    ElMessage.success('资料已更新')
    profileVisible.value = false
  } finally {
    profileSaving.value = false
  }
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

function resetPasswordForm() {
  passwordFormRef.value?.resetFields()
}

async function submitPassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordVisible.value = false
    ElMessage.success('密码已修改，请使用新密码重新登录')
    auth.reset()
    router.replace('/login')
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.card-header .section-title {
  margin: 0;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.role-tag,
.perm-tag {
  margin: 2px 6px 2px 0;
}

.permission-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
