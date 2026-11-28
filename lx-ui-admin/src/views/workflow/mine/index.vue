<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.workflow.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.workflow.mine.keywordPlaceholder')"
            clearable
            style="width: 240px"
            @keyup.enter="loadList"
            @clear="loadList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.workflow.mine.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:workflow:instance:start'" type="primary" :icon="Plus" @click="openStart">
            {{ t('page.workflow.mine.start') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="processInstanceId">
        <el-table-column prop="title" :label="t('page.workflow.title')" min-width="200" />
        <el-table-column prop="processName" :label="t('page.workflow.processName')" min-width="140" />
        <el-table-column :label="t('common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentTaskName" :label="t('page.workflow.currentTask')" min-width="120">
          <template #default="{ row }">{{ row.currentTaskName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="startTime" :label="t('page.workflow.startTime')" width="170" />
        <el-table-column prop="endTime" :label="t('page.workflow.endTime')" width="170">
          <template #default="{ row }">{{ row.endTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="160" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="info" @click="openDetail(row)">{{ t('page.workflow.detail') }}</el-button>
              <el-button
                v-if="row.status === 'running'"
                v-hasPerm="'platform:workflow:instance:withdraw'"
                link
                type="warning"
                @click="handleWithdraw(row)"
              >
                {{ t('page.workflow.withdraw') }}
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <el-dialog v-model="startVisible" :title="t('page.workflow.mine.start')" width="640px" append-to-body destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="t('page.workflow.processName')" required>
          <el-select
            v-model="form.processKey"
            style="width: 100%"
            :placeholder="t('page.workflow.mine.processPlaceholder')"
            @change="handleProcessChange"
          >
            <el-option v-for="item in processes" :key="item.processKey" :label="item.name" :value="item.processKey" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.workflow.title')" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit :placeholder="t('page.workflow.mine.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.workflow.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" :placeholder="t('page.workflow.mine.remarkPlaceholder')" />
        </el-form-item>
      </el-form>

      <el-divider v-if="schema" content-position="left">{{ schemaName }}</el-divider>
      <div v-loading="schemaLoading">
        <WorkflowFormRenderer
          v-if="schema"
          :schema="schema"
          :model-value="formModel"
          :users="users"
        />
        <el-empty v-else-if="form.processKey" :description="t('page.workflow.form.notBound')" :image-size="60" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="startVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="starting" @click="submitStart">{{ t('common.confirm') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <WorkflowInstanceDrawer v-model="detailVisible" :process-instance-id="detailId" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { getProcessForm, listMyInstances, listProcessDefs, startInstance, withdrawInstance } from '@/api/workflow'
import { listUsers } from '@/api/user'
import WorkflowFormRenderer from '@/components/WorkflowFormRenderer.vue'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'

/**
 * 我发起的：进度、详情、撤回。
 *
 * <p>发起表单由流程绑定的动态表单决定（字段 key 即流程变量名）；
 * 审批人不再写死，而是表单里的 user 字段。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listMyInstances({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined
    })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

onMounted(loadList)

const statusText = (status) => {
  const map = {
    running: t('page.workflow.statusRunning'),
    completed: t('page.workflow.statusCompleted'),
    rejected: t('page.workflow.statusRejected'),
    canceled: t('page.workflow.statusCanceled')
  }
  return map[status] || status || '-'
}

const statusType = (status) => {
  const map = { running: 'primary', completed: 'success', rejected: 'danger', canceled: 'info' }
  return map[status] || 'info'
}

/* ==================== 发起 ==================== */
const startVisible = ref(false)
const starting = ref(false)
const schemaLoading = ref(false)
const processes = ref([])
const users = ref([])
const schema = ref(null)
const schemaName = ref('')
const formModel = ref({})

const form = reactive({ processKey: '', title: '', remark: '' })

const userLabel = (user) => (user.nickname ? `${user.nickname}（${user.username}）` : user.username)

async function openStart() {
  Object.assign(form, { processKey: '', title: '', remark: '' })
  schema.value = null
  schemaName.value = ''
  formModel.value = {}
  startVisible.value = true
  if (!processes.value.length) {
    const data = await listProcessDefs({ pageNum: 1, pageSize: 100 })
    processes.value = (data?.records || []).filter((item) => item.status === '0')
  }
  if (!users.value.length) {
    const data = await listUsers({ pageNum: 1, pageSize: 200 })
    users.value = data?.records || []
  }
}

/** 切换流程：拉表单 schema，按字段类型初始化模型 */
async function handleProcessChange(processKey) {
  schema.value = null
  formModel.value = {}
  schemaName.value = ''
  if (!processKey) return
  schemaLoading.value = true
  try {
    const data = await getProcessForm(processKey)
    if (!data) return
    schema.value = data.schema
    schemaName.value = data.formName
    const model = {}
    for (const field of data.schema?.fields || []) {
      if (field.type === 'switch') model[field.key] = false
      else if (field.type === 'user') model[field.key] = field.multiple ? [] : ''
      else model[field.key] = ''
    }
    formModel.value = model
  } finally {
    schemaLoading.value = false
  }
}

const currentUserFields = () => (schema.value?.fields || []).filter((field) => field.type === 'user')

/** 表单 user 字段的 ID → 姓名，写进 assigneeNames，详情页不用回查用户表 */
function buildAssigneeNames() {
  const names = {}
  for (const field of currentUserFields()) {
    const value = formModel.value[field.key]
    const ids = Array.isArray(value) ? value : value ? [value] : []
    for (const id of ids) {
      const user = users.value.find((item) => String(item.userId) === String(id))
      if (user) names[String(id)] = user.nickname || user.username
    }
  }
  return names
}

function validateRequired() {
  for (const field of schema.value?.fields || []) {
    if (!field.required) continue
    const value = formModel.value[field.key]
    const empty = value == null || value === '' || (Array.isArray(value) && value.length === 0)
    if (empty) {
      ElMessage.warning(t('page.workflow.form.fieldRequired', { label: field.label }))
      return false
    }
  }
  return true
}

async function submitStart() {
  if (!form.processKey) {
    ElMessage.warning(t('page.workflow.mine.processRequired'))
    return
  }
  if (!form.title) {
    ElMessage.warning(t('page.workflow.mine.titleRequired'))
    return
  }
  if (schema.value && !validateRequired()) {
    return
  }
  starting.value = true
  try {
    await startInstance({
      processKey: form.processKey,
      title: form.title,
      remark: form.remark,
      formData: schema.value ? { ...formModel.value } : {},
      assigneeNames: buildAssigneeNames()
    })
    ElMessage.success(t('page.workflow.mine.startSuccess'))
    startVisible.value = false
    loadList()
  } finally {
    starting.value = false
  }
}

/* ==================== 撤回 / 详情 ==================== */
async function handleWithdraw(row) {
  await ElMessageBox.confirm(t('page.workflow.mine.withdrawConfirm', { title: row.title }), t('common.confirm'), {
    type: 'warning'
  })
  await withdrawInstance(row.processInstanceId)
  ElMessage.success(t('common.success'))
  loadList()
}

const detailVisible = ref(false)
const detailId = ref('')

function openDetail(row) {
  detailId.value = row.processInstanceId
  detailVisible.value = true
}
</script>