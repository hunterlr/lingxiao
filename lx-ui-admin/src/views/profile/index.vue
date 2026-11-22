<template>
  <div class="page-container">
    <div class="page-card">
      <div class="card-header">
        <h3 class="section-title">{{ t('page.profile.title') }}</h3>
        <div class="card-actions">
          <el-button type="primary" :icon="Edit" @click="openProfileDialog">{{ t('page.profile.edit') }}</el-button>
          <el-button :icon="Lock" @click="openPasswordDialog">{{ t('page.profile.changePassword') }}</el-button>
        </div>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('page.profile.avatar')">
          <div class="avatar-line">
            <el-avatar :size="48" :src="displayAvatar" class="profile-avatar">
              {{ (auth.nickname || auth.username || '?').slice(0, 1) }}
            </el-avatar>
            <span v-if="!auth.avatar" class="text-secondary">{{ t('page.profile.avatarEmpty') }}</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.userId')">
          <span class="text-mono">{{ auth.userId || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.loginAccount')">{{ auth.username || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.nickname')">{{ auth.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.email')">{{ auth.email || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.phone')">{{ auth.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.profile.role')">
          <el-tag v-for="role in auth.roles" :key="role" size="small" class="role-tag">
            {{ role }}
          </el-tag>
          <span v-if="!auth.roles.length" class="text-secondary">{{ t('page.profile.noRole') }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title" style="margin-top: 24px">{{ t('page.profile.permissions') }}</h3>
      <el-alert
        v-if="auth.isSuperAdmin"
        type="success"
        :closable="false"
        show-icon
        :title="t('page.profile.superAdminTitle')"
        :description="t('page.profile.superAdminDesc')"
      />
      <div v-else class="permission-list">
        <el-tag v-for="perm in auth.permissions" :key="perm" size="small" type="info" class="perm-tag">
          {{ perm }}
        </el-tag>
        <el-empty v-if="!auth.permissions.length" :description="t('page.profile.noPerm')" :image-size="70" />
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 20px"
        :title="t('page.profile.pwdTipTitle')"
        :description="t('page.profile.pwdTipDesc')"
      />
    </div>

    <!-- 编辑资料 -->
    <el-dialog
      v-model="profileVisible"
      :title="t('page.profile.edit')"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetProfileForm"
    >
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px">
        <el-form-item :label="t('page.profile.avatar')">
          <div class="avatar-editor">
            <el-avatar :size="64" :src="formAvatarUrl" class="profile-avatar">
              {{ (profileForm.nickname || auth.username || '?').slice(0, 1) }}
            </el-avatar>
            <div class="avatar-actions">
              <el-upload
                :show-file-list="false"
                :http-request="handleAvatarUpload"
                accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
              >
                <el-button size="small" :loading="avatarUploading">{{ t('page.profile.avatarUpload') }}</el-button>
              </el-upload>
              <el-button v-if="profileForm.avatar" size="small" link type="danger" @click="clearAvatar">
                {{ t('page.profile.avatarRemove') }}
              </el-button>
            </div>
            <div class="form-tip">{{ t('page.profile.avatarTip') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('page.profile.loginAccount')">
          <el-input :model-value="auth.username" disabled />
        </el-form-item>
        <el-form-item :label="t('page.profile.nickname')" prop="nickname">
          <el-input v-model="profileForm.nickname" maxlength="64" show-word-limit :placeholder="t('page.profile.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.profile.email')" prop="email">
          <el-input v-model="profileForm.email" maxlength="128" :placeholder="t('page.profile.optional')" />
        </el-form-item>
        <el-form-item :label="t('page.profile.phone')" prop="phone">
          <el-input v-model="profileForm.phone" maxlength="32" :placeholder="t('page.profile.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog
      v-model="passwordVisible"
      :title="t('page.profile.changePassword')"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetPasswordForm"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        :title="t('page.profile.pwdAlertTitle')"
        :description="t('page.profile.pwdAlertDesc')"
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item :label="t('page.profile.oldPassword')" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password maxlength="64" autocomplete="current-password" />
        </el-form-item>
        <el-form-item :label="t('page.profile.newPassword')" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password maxlength="64" autocomplete="new-password" :placeholder="t('page.profile.passwordPlaceholder')" />
          <div class="form-tip">{{ t('page.profile.passwordTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('page.profile.confirmPassword')" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="64" autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPassword">{{ t('page.profile.confirmChange') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Lock } from '@element-plus/icons-vue'
import { changePassword } from '@/api/auth'
import { uploadAvatar } from '@/api/file'
import { useAuthStore } from '@/store/auth'
import { resolveAssetUrl } from '@/utils/asset'

/**
 * 个人中心。
 *
 * 自助能力只有两件：改昵称 / 邮箱 / 手机号、改密码。
 * 用户名、状态、角色、部门仍由管理员维护，避免用户自助提权。
 * 改密成功后后端已把该账号所有 Token 注销，这里同步清本地状态并回登录页，
 * 不能只弹一个“成功”让用户继续用着已经失效的会话。
 */
const { t } = useI18n()

const auth = useAuthStore()
const router = useRouter()

const profileVisible = ref(false)
const profileSaving = ref(false)
const avatarUploading = ref(false)
const profileFormRef = ref()
const profileForm = reactive({ nickname: '', avatar: '', email: '', phone: '' })

const displayAvatar = computed(() => resolveAssetUrl(auth.avatar))
const formAvatarUrl = computed(() => resolveAssetUrl(profileForm.avatar))

async function handleAvatarUpload({ file }) {
  avatarUploading.value = true
  try {
    const result = await uploadAvatar(file)
    profileForm.avatar = String(result.fileId || '')
    ElMessage.success(t('page.profile.avatarUploaded'))
  } catch (e) {
    // 统一错误提示由 axios 拦截器弹出，这里只结束 loading
  } finally {
    avatarUploading.value = false
  }
}

function clearAvatar() {
  profileForm.avatar = ''
}

const profileRules = {
  nickname: [
    { required: true, message: t('page.profile.nicknameRequired'), trigger: 'blur' },
    { max: 64, message: t('page.profile.nicknameMax'), trigger: 'blur' }
  ],
  email: [{ type: 'email', message: t('page.dept.emailInvalid'), trigger: 'blur' }]
}

const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: t('page.profile.oldPwdRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('page.profile.newPwdRequired'), trigger: 'blur' },
    // 前端只做“体验层”校验：默认策略要求 8 位字母+数字；
    // 真正的复杂度由后端 passwordPolicy 判定，参数改成 strong 时由后端返回提示。
    { min: 8, max: 64, message: t('page.profile.newPwdLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('page.profile.confirmRequired'), trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error(t('page.profile.confirmMismatch')))
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
  profileForm.avatar = auth.avatar || ''
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
      avatar: profileForm.avatar,
      email: profileForm.email,
      phone: profileForm.phone
    })
    ElMessage.success(t('page.profile.profileUpdated'))
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
    ElMessage.success(t('page.profile.pwdChanged'))
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

.avatar-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.avatar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-avatar {
  flex: none;
  background: var(--lx-primary);
  color: #fff;
  font-size: 18px;
}

.permission-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
