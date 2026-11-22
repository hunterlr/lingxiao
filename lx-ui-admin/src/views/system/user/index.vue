<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.user.username')">
          <el-input
            v-model="query.username"
            :placeholder="t('page.user.usernamePlaceholder')"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.user.nickname')">
          <el-input
            v-model="query.nickname"
            :placeholder="t('page.user.nicknamePlaceholder')"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.user.dept')">
          <el-tree-select
            v-model="query.deptId"
            :data="deptTree"
            node-key="id"
            :props="treeProps"
            check-strictly
            clearable
            :placeholder="t('page.user.allDepts')"
            style="width: 180px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="query.status" dict-type="sys_common_status" width="130px" :placeholder="t('common.allStatus')" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.includeSubDept" :disabled="!query.deptId">{{ t('page.user.includeSubDept') }}</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.user.total', { total }) }}</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:user:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.user.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="userId">
        <el-table-column prop="username" :label="t('page.user.username')" min-width="130">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="26" :src="resolveAssetUrl(row.avatar, { size: 'thumb' })">
                {{ (row.nickname || row.username || '?').slice(0, 1) }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" :label="t('page.user.nickname')" min-width="110" show-overflow-tooltip />
        <el-table-column prop="deptName" :label="t('page.user.dept')" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" :label="t('page.user.phone')" min-width="120">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" :label="t('page.dept.email')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sex" :label="t('page.user.sex')" width="80" align="center">
          <template #default="{ row }">
            <DictTag :value="row.sex" dict-type="sys_user_sex" />
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="isBuiltin(row) || !canEditUser"
              @change="(value) => handleStatusChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('page.user.locked')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="isLocked(row)" type="danger" effect="plain" size="small">{{ t('page.user.isLocked') }}</el-tag>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:user:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:user:resetPwd'"
                link
                type="primary"
                @click="openResetPwd(row)"
              >
                {{ t('page.user.resetPwd') }}
              </el-button>
              <el-button
                v-if="isLocked(row)"
                v-hasPerm="'platform:system:user:unlock'"
                link
                type="warning"
                @click="handleUnlock(row)"
              >
                {{ t('page.user.unlock') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:user:remove'"
                link
                type="danger"
                :disabled="isBuiltin(row)"
                @click="handleDelete(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.user.edit') : t('page.user.add')"
      width="720px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.user.username')" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" maxlength="64" :placeholder="t('page.user.usernamePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.nickname')" prop="nickname">
              <el-input v-model="form.nickname" maxlength="64" :placeholder="t('page.user.nicknamePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isEdit" :span="12">
            <el-form-item :label="t('page.user.initialPassword')" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                maxlength="64"
                :placeholder="t('page.user.passwordPlaceholder')"
              />
              <div class="form-tip">{{ t('page.user.passwordTip') }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.dept')" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                node-key="id"
                :props="treeProps"
                check-strictly
                clearable
                :placeholder="t('page.user.selectDept')"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.phone')" prop="phone">
              <el-input v-model="form.phone" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.dept.email')" prop="email">
              <el-input v-model="form.email" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.sex')">
              <DictSelect v-model="form.sex" dict-type="sys_user_sex" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.status')">
              <DictSelect v-model="form.status" dict-type="sys_common_status" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.role')">
              <el-select v-model="form.roleIds" multiple clearable :placeholder="t('page.user.selectRole')" style="width: 100%">
                <el-option
                  v-for="role in roleOptions"
                  :key="role.roleId"
                  :label="role.roleName"
                  :value="role.roleId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.user.post')">
              <el-select v-model="form.postIds" multiple clearable :placeholder="t('page.user.selectPost')" style="width: 100%">
                <el-option
                  v-for="post in postOptions"
                  :key="post.postId"
                  :label="post.postName"
                  :value="post.postId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('common.remark')">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" :title="t('page.user.resetPwdTitle')" width="440px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="t('page.user.resetPwdTarget', { name: pwdTarget.username })"
        :description="t('page.user.resetPwdDesc')"
        style="margin-bottom: 16px"
      />
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item :label="t('page.user.newPassword')" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            maxlength="64"
            :placeholder="t('page.user.passwordPlaceholder')"
          />
          <div class="form-tip">{{ t('page.user.resetPwdTip') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="pwdVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="pwdSaving" @click="submitResetPwd">{{ t('page.user.confirmReset') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { useAuthStore } from '@/store/auth'
import {
  addUser,
  changeUserStatus,
  deleteUser,
  getUser,
  listUsers,
  resetUserPassword,
  unlockUser,
  updateUser
} from '@/api/user'
import { getDeptTree } from '@/api/dept'
import { listRoleOptions } from '@/api/role'
import { listPostOptions } from '@/api/post'
import { resolveAssetUrl } from '@/utils/asset'

/**
 * 用户管理。
 *
 * <p>分页、搜索、停用启用、重置密码、多角色 / 多岗位授权都在这一页完成。
 * 内置管理员（user_id=1）的停用与删除按钮直接置灰，但真正的保护在后端：
 * 前端禁用只是让用户少走一步。</p>
 */
const { t } = useI18n()
const auth = useAuthStore()

/** 状态开关是“修改用户”，没有该权限时只读（与定时任务页的 canChangeStatus 一致）。 */
const canEditUser = computed(() => auth.hasPermission('platform:system:user:edit'))

const treeProps = { label: 'label', children: 'children' }

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  nickname: '',
  status: '',
  deptId: '',
  includeSubDept: false
})

const deptTree = ref([])
const roleOptions = ref([])
const postOptions = ref([])

async function loadList() {
  loading.value = true
  try {
    const data = await listUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      nickname: query.nickname || undefined,
      status: query.status || undefined,
      deptId: query.deptId || undefined,
      includeSubDept: query.deptId ? query.includeSubDept : undefined
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.username = ''
  query.nickname = ''
  query.status = ''
  query.deptId = ''
  query.includeSubDept = false
  handleSearch()
}

onMounted(async () => {
  loadList()
  const [tree, roles, posts] = await Promise.all([
    getDeptTree().catch(() => []),
    listRoleOptions().catch(() => []),
    listPostOptions().catch(() => [])
  ])
  deptTree.value = tree || []
  roleOptions.value = roles || []
  postOptions.value = posts || []
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  username: '',
  nickname: '',
  password: '',
  deptId: '',
  phone: '',
  email: '',
  sex: '2',
  status: '0',
  roleIds: [],
  postIds: [],
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  username: [
    { required: true, message: t('page.user.usernameRequired'), trigger: 'blur' },
    { max: 64, message: t('page.user.maxLength64'), trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: t('page.user.nicknameRequired'), trigger: 'blur' },
    { max: 64, message: t('page.user.maxLength64'), trigger: 'blur' }
  ],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!isEdit.value && !value) {
          callback(new Error(t('page.user.passwordRequired')))
          return
        }
        if (value && (value.length < 8 || value.length > 64)) {
          callback(new Error(t('page.user.passwordLength')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: t('page.dept.emailInvalid'), trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function openAdd() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.userId
  Object.assign(form, {
    username: row.username,
    nickname: row.nickname,
    deptId: row.deptId || '',
    phone: row.phone || '',
    email: row.email || '',
    sex: row.sex || '2',
    status: row.status || '0',
    remark: row.remark || '',
    roleIds: [],
    postIds: []
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getUser(row.userId)
    Object.assign(form, {
      username: detail.username,
      nickname: detail.nickname,
      deptId: detail.deptId || '',
      phone: detail.phone || '',
      email: detail.email || '',
      sex: detail.sex || '2',
      status: detail.status || '0',
      remark: detail.remark || '',
      roleIds: detail.roleIds || [],
      postIds: detail.postIds || []
    })
  } finally {
    dialogLoading.value = false
  }
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        username: form.username.trim(),
        nickname: form.nickname.trim(),
        deptId: form.deptId || null,
        phone: form.phone || null,
        email: form.email || null,
        sex: form.sex,
        status: form.status,
        roleIds: form.roleIds,
        postIds: form.postIds,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        payload.password = form.password
        await addUser(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateUser(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

/* ==================== 状态 / 删除 ==================== */
function isBuiltin(row) {
  return String(row.userId) === '1'
}

async function handleStatusChange(row, value) {
  try {
    await changeUserStatus(row.userId, value)
    ElMessage.success(value === '0' ? t('page.user.enabledToast') : t('page.user.disabledToast'))
    await loadList()
  } catch (e) {
    // 失败时重新拉列表，把开关状态恢复成服务端的真实值
    await loadList()
  }
}

/**
 * 是否处于锁定期。
 *
 * 后端 `lock_time` 是“锁定截止时间”，过去即视为未锁定；
 * 用时间比较而不是看 login_fail_count，是因为次数本身不清零也能解锁。
 * 时间串兼容 `2026-11-15T21:40:00` 与 `2026-11-15 21:40:00` 两种格式。
 */
function isLocked(row) {
  if (!row || !row.lockTime) return false
  const expire = new Date(String(row.lockTime).replace(' ', 'T')).getTime()
  return Number.isFinite(expire) && expire > Date.now()
}

async function handleUnlock(row) {
  try {
    await ElMessageBox.confirm(
      t('page.user.unlockConfirm', { name: row.nickname || row.username }),
      t('page.user.unlockTitle'),
      { type: 'warning', confirmButtonText: t('page.user.unlock'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await unlockUser(row.userId)
  ElMessage.success(t('page.user.unlocked'))
  await loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.user.deleteConfirm', { name: row.nickname || row.username }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteUser(row.userId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 重置密码 ==================== */
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref(null)
const pwdTarget = reactive({ userId: '', username: '' })
const pwdForm = reactive({ newPassword: '' })
const pwdRules = {
  newPassword: [
    { required: true, message: t('page.user.newPasswordRequired'), trigger: 'blur' },
    // 与新增用户一致：前端按默认策略提示，strong 策略由后端返回具体原因
    { min: 8, max: 64, message: t('page.user.passwordLength'), trigger: 'blur' }
  ]
}

function openResetPwd(row) {
  pwdTarget.userId = row.userId
  pwdTarget.username = row.username
  pwdForm.newPassword = ''
  pwdVisible.value = true
}

async function submitResetPwd() {
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    pwdSaving.value = true
    try {
      await resetUserPassword(pwdTarget.userId, pwdForm.newPassword)
      ElMessage.success(t('page.user.pwdReset'))
      pwdVisible.value = false
    } finally {
      pwdSaving.value = false
    }
  })
}
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
