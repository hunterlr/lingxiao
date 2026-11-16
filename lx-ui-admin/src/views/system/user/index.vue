<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="账号">
          <el-input
            v-model="query.username"
            placeholder="登录账号"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input
            v-model="query.nickname"
            placeholder="显示名称"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="query.deptId"
            :data="deptTree"
            node-key="id"
            :props="treeProps"
            check-strictly
            clearable
            placeholder="全部部门"
            style="width: 180px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <DictSelect v-model="query.status" dict-type="sys_common_status" width="130px" placeholder="全部状态" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.includeSubDept" :disabled="!query.deptId">含下级部门</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">共 {{ total }} 个账号</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:user:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            新增用户
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="userId">
        <el-table-column prop="username" label="账号" min-width="130">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="26" :src="row.avatar ? `/api/file/${row.avatar}` : ''">
                {{ (row.nickname || row.username || '?').slice(0, 1) }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="110" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="120">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sex" label="性别" width="80" align="center">
          <template #default="{ row }">
            <DictTag :value="row.sex" dict-type="sys_user_sex" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="isBuiltin(row)"
              @change="(value) => handleStatusChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="isLocked(row)" type="danger" effect="plain" size="small">已锁定</el-tag>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:user:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPerm="'platform:system:user:resetPwd'"
                link
                type="primary"
                @click="openResetPwd(row)"
              >
                重置密码
              </el-button>
              <el-button
                v-if="isLocked(row)"
                v-hasPerm="'platform:system:user:unlock'"
                link
                type="warning"
                @click="handleUnlock(row)"
              >
                解锁
              </el-button>
              <el-button
                v-hasPerm="'platform:system:user:remove'"
                link
                type="danger"
                :disabled="isBuiltin(row)"
                @click="handleDelete(row)"
              >
                删除
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
      :title="isEdit ? '编辑用户' : '新增用户'"
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
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" maxlength="64" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" maxlength="64" placeholder="显示名称" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isEdit" :span="12">
            <el-form-item label="初始密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                maxlength="64"
                placeholder="至少 8 位，含字母和数字"
              />
              <div class="form-tip">实际规则以「参数设置 → 密码复杂度策略」为准</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                node-key="id"
                :props="treeProps"
                check-strictly
                clearable
                placeholder="请选择部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <DictSelect v-model="form.sex" dict-type="sys_user_sex" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <DictSelect v-model="form.status" dict-type="sys_common_status" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple clearable placeholder="请选择角色" style="width: 100%">
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
            <el-form-item label="岗位">
              <el-select v-model="form.postIds" multiple clearable placeholder="请选择岗位" style="width: 100%">
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
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="440px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`即将重置账号「${pwdTarget.username}」的密码`"
        description="重置后该账号的旧密码立即失效，请通过安全渠道告知本人。"
        style="margin-bottom: 16px"
      />
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            maxlength="64"
            placeholder="至少 8 位，含字母和数字"
          />
          <div class="form-tip">重置后该账号的失败次数与锁定状态会一并清除</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="pwdVisible = false">取消</el-button>
          <el-button type="primary" :loading="pwdSaving" @click="submitResetPwd">确定重置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
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

/**
 * 用户管理。
 *
 * <p>分页、搜索、停用启用、重置密码、多角色 / 多岗位授权都在这一页完成。
 * 内置管理员（user_id=1）的停用与删除按钮直接置灰，但真正的保护在后端：
 * 前端禁用只是让用户少走一步。</p>
 */
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
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 64, message: '不能超过 64 个字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 64, message: '不能超过 64 个字符', trigger: 'blur' }
  ],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!isEdit.value && !value) {
          callback(new Error('请输入初始密码'))
          return
        }
        if (value && (value.length < 8 || value.length > 64)) {
          callback(new Error('密码长度必须在 8~64 之间'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
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
        ElMessage.success('新增成功')
      } else {
        await updateUser(editingId.value, payload)
        ElMessage.success('保存成功')
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
    ElMessage.success(value === '0' ? '已启用' : '已停用')
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
      `确认解锁用户「${row.nickname || row.username}」吗？解锁后失败次数会清零。`,
      '解锁确认',
      { type: 'warning', confirmButtonText: '解锁', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await unlockUser(row.userId)
  ElMessage.success('已解锁')
  await loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.nickname || row.username}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteUser(row.userId)
  ElMessage.success('删除成功')
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
    { required: true, message: '请输入新密码', trigger: 'blur' },
    // 与新增用户一致：前端按默认策略提示，strong 策略由后端返回具体原因
    { min: 8, max: 64, message: '密码长度必须在 8~64 之间', trigger: 'blur' }
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
      ElMessage.success('密码已重置')
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
