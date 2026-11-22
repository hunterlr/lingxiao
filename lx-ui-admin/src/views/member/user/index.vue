<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.member.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.member.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.member.level')">
          <el-select v-model="query.levelId" clearable :placeholder="t('common.all')" style="width: 150px">
            <el-option v-for="level in levels" :key="level.levelId" :label="level.levelName" :value="level.levelId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option :label="t('common.enabled')" value="0" />
            <el-option :label="t('common.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.member.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:member:user:add'" type="primary" :icon="Plus" @click="openAdd">
            {{ t('page.member.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="memberId">
        <el-table-column prop="avatar" :label="t('page.member.avatar')" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="36" :src="resolveAssetUrl(row.avatar, { size: 'thumb' })">
              {{ (row.nickname || row.username || '?').slice(0, 1) }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="t('page.member.username')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-mono">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column prop="nickname" :label="t('page.member.nickname')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="mobile" :label="t('page.member.mobile')" width="140">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" :label="t('page.member.email')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="levelName" :label="t('page.member.level')" width="110" align="center">
          <template #default="{ row }">{{ row.levelName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="points" :label="t('page.member.points')" width="90" align="center" />
        <el-table-column prop="growth" :label="t('page.member.growth')" width="90" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('page.member.registerTime')" width="165" />
        <el-table-column :label="t('common.operation')" width="240" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:member:user:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:member:user:status'"
                link
                :type="row.status === '0' ? 'warning' : 'success'"
                @click="handleToggleStatus(row)"
              >
                {{ row.status === '0' ? t('page.member.disable') : t('page.member.enable') }}
              </el-button>
              <el-button v-hasPerm="'platform:member:user:resetPwd'" link type="warning" @click="handleResetPassword(row)">
                {{ t('page.member.resetPwd') }}
              </el-button>
              <el-button v-hasPerm="'platform:member:user:merge'" link type="primary" @click="openMerge(row)">
                {{ t('page.member.merge') }}
              </el-button>
              <el-button v-hasPerm="'platform:member:user:remove'" link type="danger" @click="handleDelete(row)">
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.member.edit') : t('page.member.add')"
      width="640px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('page.member.username')" prop="username">
          <el-input
            v-model="form.username"
            maxlength="64"
            :disabled="isEdit"
            :placeholder="t('page.member.usernamePlaceholder')"
          />
          <div v-if="isEdit" class="form-tip">{{ t('page.member.usernameLocked') }}</div>
        </el-form-item>
        <el-form-item v-if="!isEdit" :label="t('page.member.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="64"
            :placeholder="t('page.member.passwordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('page.member.nickname')" prop="nickname">
          <el-input v-model="form.nickname" maxlength="64" :placeholder="t('page.member.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.member.avatar')">
          <MediaPicker v-model="form.avatar" shape="circle" :size="56" upload-biz-type="member" />
        </el-form-item>
        <el-form-item :label="t('page.member.mobile')" prop="mobile">
          <el-input v-model="form.mobile" maxlength="32" :placeholder="t('page.member.mobilePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.member.email')" prop="email">
          <el-input v-model="form.email" maxlength="128" :placeholder="t('page.member.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.member.sex')">
          <el-radio-group v-model="form.sex">
            <el-radio value="0">{{ t('page.member.male') }}</el-radio>
            <el-radio value="1">{{ t('page.member.female') }}</el-radio>
            <el-radio value="2">{{ t('page.member.unknown') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('page.member.level')" prop="levelId">
          <el-select v-model="form.levelId" :placeholder="t('page.member.levelPlaceholder')" style="width: 100%">
            <el-option v-for="level in levels" :key="level.levelId" :label="level.levelName" :value="level.levelId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">{{ t('common.enabled') }}</el-radio>
            <el-radio value="1">{{ t('common.disabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="mergeVisible"
      :title="t('page.member.mergeTitle')"
      width="720px"
      append-to-body
      destroy-on-close
      @closed="resetMerge"
    >
      <el-alert type="warning" :closable="false" show-icon :title="t('page.member.mergeHint')" />

      <el-form label-width="120px" style="margin-top: 16px">
        <el-form-item :label="t('page.member.mergeSource')">
          <el-input :model-value="mergeSourceLabel" disabled />
        </el-form-item>
        <el-form-item :label="t('page.member.mergeTarget')">
          <el-select
            v-model="mergeForm.targetMemberId"
            filterable
            remote
            clearable
            :remote-method="searchMergeTargets"
            :loading="mergeOptionsLoading"
            :placeholder="t('page.member.mergeTargetPlaceholder')"
            style="width: 100%"
            @change="mergePreview = null"
          >
            <el-option
              v-for="item in mergeOptions"
              :key="item.memberId"
              :label="`${item.username}（${item.nickname || '-'}）`"
              :value="String(item.memberId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="mergeForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <el-table v-if="mergePreview" :data="mergePreviewRows" border size="small">
        <el-table-column prop="label" :label="t('page.member.mergeItem')" min-width="160" />
        <el-table-column prop="source" :label="t('page.member.mergeSourceCount')" width="110" align="center" />
        <el-table-column prop="target" :label="t('page.member.mergeTargetCount')" width="110" align="center" />
        <el-table-column prop="skipped" :label="t('page.member.mergeSkipped')" width="140" align="center" />
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="mergeVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button :loading="mergePreviewLoading" @click="runMergePreview">{{ t('page.member.mergePreview') }}</el-button>
          <el-button type="primary" :loading="merging" :disabled="!mergePreview" @click="submitMerge">
            {{ t('page.member.mergeConfirmButton') }}
          </el-button>
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
import MediaPicker from '@/components/MediaPicker.vue'
import { resolveAssetUrl } from '@/utils/asset'
import {
  addMember,
  changeMemberStatus,
  deleteMember,
  listLevelOptions,
  listMembers,
  mergeMember,
  previewMemberMerge,
  resetMemberPassword,
  updateMember
} from '@/api/member'

/**
 * 后台会员列表。
 *
 * <p>只管理会员自己的 mem_user，不展示也不修改平台后台账号；
 * 删除会员会保留用户名占用，避免同名账号被他人抢注。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const levels = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', levelId: null })

async function loadLevels() {
  levels.value = await listLevelOptions()
}

async function loadList() {
  loading.value = true
  try {
    const data = await listMembers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      levelId: query.levelId || undefined
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
  query.keyword = ''
  query.status = ''
  query.levelId = null
  handleSearch()
}

onMounted(async () => {
  await loadLevels()
  await loadList()
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  username: '',
  password: '',
  nickname: '',
  avatar: '',
  mobile: '',
  email: '',
  sex: '2',
  levelId: null,
  status: '0',
  remark: ''
})
const form = reactive(emptyForm())

const rules = {
  username: [{ required: true, message: t('page.member.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('page.member.passwordRequired'), trigger: 'blur' }],
  nickname: [{ required: true, message: t('page.member.nicknameRequired'), trigger: 'blur' }],
  levelId: [{ required: true, message: t('page.member.levelRequired'), trigger: 'change' }],
  email: [{ type: 'email', message: t('page.member.emailFormat'), trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function defaultLevelId() {
  return levels.value.length ? levels.value[0].levelId : null
}

function openAdd() {
  resetForm()
  form.levelId = defaultLevelId()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.memberId
  Object.assign(form, {
    username: row.username || '',
    nickname: row.nickname || '',
    avatar: row.avatar || '',
    mobile: row.mobile || '',
    email: row.email || '',
    sex: row.sex || '2',
    levelId: row.levelId || defaultLevelId(),
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateMember(editingId.value, {
          nickname: form.nickname.trim(),
          avatar: form.avatar || null,
          mobile: form.mobile || null,
          email: form.email || null,
          sex: form.sex,
          levelId: form.levelId,
          status: form.status,
          remark: form.remark || null
        })
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addMember({
          username: form.username.trim(),
          password: form.password,
          nickname: form.nickname.trim(),
          avatar: form.avatar || null,
          mobile: form.mobile || null,
          email: form.email || null,
          sex: form.sex,
          levelId: form.levelId,
          status: form.status,
          remark: form.remark || null
        })
        ElMessage.success(t('common.addSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

/* ==================== 启停 / 重置密码 / 删除 ==================== */
async function handleToggleStatus(row) {
  const disabling = row.status === '0'
  const message = disabling ? t('page.member.disableConfirm') : t('page.member.enableConfirm')
  try {
    await ElMessageBox.confirm(message, t('common.tip'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) {
    return
  }
  await changeMemberStatus(row.memberId, disabling ? '1' : '0')
  ElMessage.success(t('common.success'))
  await loadList()
}

async function handleResetPassword(row) {
  let value
  try {
    const result = await ElMessageBox.prompt(
      t('page.member.resetPwdPrompt', { name: row.username }),
      t('page.member.resetPwd'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputType: 'password',
        inputPattern: /^.{6,64}$/,
        inputErrorMessage: t('page.member.passwordRule')
      }
    )
    value = result.value
  } catch (e) {
    return
  }
  await resetMemberPassword(row.memberId, value)
  ElMessage.success(t('page.member.resetPwdSuccess'))
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.member.deleteConfirm', { name: row.username }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteMember(row.memberId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 合并账号 ==================== */
const mergeVisible = ref(false)
const mergeOptionsLoading = ref(false)
const mergePreviewLoading = ref(false)
const merging = ref(false)
const mergeOptions = ref([])
const mergePreview = ref(null)
const mergeSourceRow = ref(null)
const mergeForm = reactive({ sourceMemberId: '', targetMemberId: '', remark: '' })

const mergeSourceLabel = computed(() => {
  const row = mergeSourceRow.value
  return row ? `${row.username}（${row.nickname || '-'}）` : ''
})

/** 预览结果按行展示；合并时重叠条目会跳过。 */
const mergePreviewRows = computed(() => {
  const data = mergePreview.value
  if (!data) return []
  return [
    {
      label: t('page.member.mergeFavorites'),
      source: data.favoriteSource,
      target: data.favoriteTarget,
      skipped: data.favoriteOverlap
    },
    {
      label: t('page.member.mergeLikes'),
      source: data.likeSource,
      target: data.likeTarget,
      skipped: data.likeOverlap
    },
    {
      label: t('page.member.mergeComments'),
      source: data.commentSource,
      target: '-',
      skipped: 0
    },
    {
      label: t('page.member.mergeSocial'),
      source: data.socialSource,
      target: data.socialTarget,
      skipped: data.socialOverlap
    }
  ]
})

function openMerge(row) {
  mergePreview.value = null
  mergeSourceRow.value = row
  mergeForm.sourceMemberId = String(row.memberId)
  mergeForm.targetMemberId = ''
  mergeForm.remark = ''
  mergeOptions.value = []
  mergeVisible.value = true
  searchMergeTargets('')
}

function resetMerge() {
  mergePreview.value = null
  mergeSourceRow.value = null
  mergeForm.sourceMemberId = ''
  mergeForm.targetMemberId = ''
  mergeForm.remark = ''
  mergeOptions.value = []
}

/** 目标账号只允许选启用的会员，且不能与旧账号相同。 */
async function searchMergeTargets(keyword) {
  mergeOptionsLoading.value = true
  try {
    const data = await listMembers({
      pageNum: 1,
      pageSize: 20,
      keyword: keyword || undefined,
      status: '0'
    })
    mergeOptions.value = (data.records || []).filter(
      (item) => String(item.memberId) !== String(mergeForm.sourceMemberId)
    )
  } finally {
    mergeOptionsLoading.value = false
  }
}

async function runMergePreview() {
  if (!mergeForm.targetMemberId) {
    ElMessage.warning(t('page.member.mergeTargetRequired'))
    return
  }
  mergePreviewLoading.value = true
  try {
    mergePreview.value = await previewMemberMerge(
      mergeForm.sourceMemberId,
      mergeForm.targetMemberId
    )
  } finally {
    mergePreviewLoading.value = false
  }
}

async function submitMerge() {
  const preview = mergePreview.value
  if (!preview) return
  try {
    await ElMessageBox.confirm(
      t('page.member.mergeConfirm', {
        source: preview.sourceUsername,
        target: preview.targetUsername
      }),
      t('page.member.mergeTitle'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  merging.value = true
  try {
    await mergeMember({
      sourceMemberId: mergeForm.sourceMemberId,
      targetMemberId: mergeForm.targetMemberId,
      remark: mergeForm.remark || null
    })
    ElMessage.success(t('page.member.mergeSuccess'))
    mergeVisible.value = false
    await loadList()
  } finally {
    merging.value = false
  }
}
</script>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
