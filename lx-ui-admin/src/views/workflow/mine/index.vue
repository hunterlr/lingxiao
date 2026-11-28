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

    <el-dialog v-model="startVisible" :title="t('page.workflow.mine.start')" width="560px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('page.workflow.processName')" prop="processKey">
          <el-select v-model="form.processKey" style="width: 100%" :placeholder="t('page.workflow.mine.processPlaceholder')">
            <el-option v-for="item in processes" :key="item.processKey" :label="item.name" :value="item.processKey" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.workflow.title')" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit :placeholder="t('page.workflow.mine.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.workflow.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" :placeholder="t('page.workflow.mine.remarkPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.workflow.approver')" prop="approver">
          <el-select v-model="form.approver" filterable style="width: 100%" :placeholder="t('page.workflow.mine.approverPlaceholder')">
            <el-option v-for="user in users" :key="user.userId" :label="userLabel(user)" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.workflow.approver2')">
          <el-select v-model="form.approver2" filterable clearable style="width: 100%" :placeholder="t('page.workflow.mine.approver2Placeholder')">
            <el-option v-for="user in users" :key="user.userId" :label="userLabel(user)" :value="user.userId" />
          </el-select>
        </el-form-item>
      </el-form>
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
import { listMyInstances, listProcessDefs, startInstance, withdrawInstance } from '@/api/workflow'
import { listUsers } from '@/api/user'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'

/**
 * 我发起的：进度、详情、撤回。
 *
 * <p>第一刀没有表单引擎，发起时只需要标题、说明和两级审批人；
 * 审批人姓名随请求一起提交，详情页不用回查用户表。</p>
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
const formRef = ref(null)
const processes = ref([])
const users = ref([])

const emptyForm = () => ({ processKey: '', title: '', remark: '', approver: null, approver2: null })
const form = reactive(emptyForm())

const rules = {
  processKey: [{ required: true, message: t('page.workflow.mine.processRequired'), trigger: 'change' }],
  title: [{ required: true, message: t('page.workflow.mine.titleRequired'), trigger: 'blur' }],
  approver: [{ required: true, message: t('page.workflow.mine.approverRequired'), trigger: 'change' }]
}

const userLabel = (user) => (user.nickname ? `${user.nickname}（${user.username}）` : user.username)

async function openStart() {
  Object.assign(form, emptyForm())
  startVisible.value = true
  if (!processes.value.length) {
    const data = await listProcessDefs({ pageNum: 1, pageSize: 100, categoryKey: undefined })
    processes.value = (data?.records || []).filter((item) => item.status === '0')
  }
  if (!users.value.length) {
    const data = await listUsers({ pageNum: 1, pageSize: 200 })
    users.value = data?.records || []
  }
}

function userOf(userId) {
  return users.value.find((user) => user.userId === userId)
}

async function submitStart() {
  if (!formRef.value) return
  await formRef.value.validate()
  starting.value = true
  try {
    const approver1 = userOf(form.approver)
    const approver2 = form.approver2 ? userOf(form.approver2) : null
    await startInstance({
      processKey: form.processKey,
      title: form.title,
      remark: form.remark,
      approver: form.approver,
      approverName: approver1?.nickname || approver1?.username,
      approver2: form.approver2 || null,
      approver2Name: approver2 ? approver2.nickname || approver2.username : null
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