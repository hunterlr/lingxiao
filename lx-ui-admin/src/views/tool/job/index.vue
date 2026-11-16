<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.job.jobName')">
          <el-input
            v-model="query.jobName"
            :placeholder="t('page.job.namePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.allStatus')" style="width: 130px">
            <el-option :label="t('page.job.normal')" value="0" />
            <el-option :label="t('page.job.paused')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            {{ t('page.job.hint') }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:job:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.job.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="jobId">
        <el-table-column prop="jobName" :label="t('page.job.jobName')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.jobName }}</div>
            <div v-if="row.remark" class="text-secondary job-remark">{{ row.remark }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.job.executor')" min-width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.invokeTarget }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.job.cronLabel')" width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.cronExpression }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              :loading="statusLoadingId === row.jobId"
              :disabled="!canChangeStatus"
              inline-prompt
              :active-text="t('page.job.normal')"
              :inactive-text="t('page.job.paused')"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('page.job.lastRun')" width="165">
          <template #default="{ row }">{{ row.lastRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('page.job.nextRun')" width="165">
          <template #default="{ row }">{{ row.nextRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:monitor:job:run'"
                link
                type="primary"
                @click="handleRun(row)"
              >
                {{ t('page.job.runOnce') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:log'"
                link
                type="primary"
                @click="openLog(row)"
              >
                {{ t('page.job.log') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:remove'"
                link
                type="danger"
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
      :title="isEdit ? t('page.job.edit') : t('page.job.add')"
      width="620px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item :label="t('page.job.jobName')" prop="jobName">
          <el-input v-model="form.jobName" maxlength="64" :placeholder="t('page.job.namePlaceholderEdit')" />
        </el-form-item>
        <el-form-item :label="t('page.job.executor')" prop="invokeTarget">
          <el-select v-model="form.invokeTarget" :placeholder="t('page.job.selectExecutor')" style="width: 100%">
            <el-option
              v-for="item in handlers"
              :key="item.name"
              :label="`${item.description}（${item.name}）`"
              :value="item.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron" prop="cronExpression">
          <el-input
            v-model="form.cronExpression"
            class="text-mono"
            :placeholder="t('page.job.cronPlaceholder')"
          />
          <div class="cron-presets">
            <el-button
              v-for="preset in cronPresets"
              :key="preset.value"
              size="small"
              text
              type="primary"
              @click="form.cronExpression = preset.value"
            >
              {{ t('page.job.' + preset.key) }}
            </el-button>
          </div>
          <div class="text-secondary form-tip">
            {{ t('page.job.cronTip') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">{{ t('page.job.statusActive') }}</el-radio>
            <el-radio value="1">{{ t('page.job.paused') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('page.job.concurrent')">
          <el-radio-group v-model="form.concurrent">
            <el-radio value="1">{{ t('page.job.concurrentForbid') }}</el-radio>
            <el-radio value="0">{{ t('page.job.concurrentAllow') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('page.job.misfire')">
          <el-select v-model="form.misfirePolicy" style="width: 100%">
            <el-option :label="t('page.job.misfireGiveUp')" value="3" />
            <el-option :label="t('page.job.misfireOnce')" value="2" />
            <el-option :label="t('page.job.misfireCatch')" value="1" />
          </el-select>
          <div class="text-secondary form-tip">
            {{ t('page.job.misfireTip') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 执行日志 -->
    <el-drawer v-model="logVisible" size="62%" append-to-body :destroy-on-close="true">
      <template #header>
        <div class="log-header">
          <span>{{ t('page.job.logTitle') }}</span>
          <span class="text-secondary log-header-sub">{{ logJobName }}</span>
        </div>
      </template>

      <div class="log-toolbar">
        <el-select v-model="logQuery.status" clearable :placeholder="t('page.job.logResultAll')" style="width: 120px">
          <el-option :label="t('common.successStatus')" value="0" />
          <el-option :label="t('common.failStatus')" value="1" />
        </el-select>
        <el-date-picker
          v-model="logTimeRange"
          type="datetimerange"
          :range-separator="t('common.to')"
          :start-placeholder="t('common.startTime')"
          :end-placeholder="t('common.endTime')"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 330px"
        />
        <el-button type="primary" :icon="Search" @click="handleLogSearch">{{ t('common.search') }}</el-button>
        <div class="log-toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:job:log:clean'"
            type="danger"
            plain
            :icon="Delete"
            @click="handleCleanLogs"
          >
            {{ t('common.clearLog') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadLogs">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="logLoading" :data="logRows" border stripe row-key="jobLogId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="log-expand">
              <div class="log-expand-title">{{ t('page.job.stackTitle') }}</div>
              <pre class="log-exception">{{ row.exceptionInfo || t('page.job.noException') }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('page.job.execTime')" width="165" />
        <el-table-column :label="t('page.job.trigger')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.triggerType === 'MANUAL' ? 'warning' : 'info'">
              {{ row.triggerType === 'MANUAL' ? t('page.job.manual') : t('page.job.schedule') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.job.result')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? t('common.successStatus') : t('common.failStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobMessage" :label="t('page.job.resultSummary')" min-width="240" show-overflow-tooltip />
        <el-table-column :label="t('page.job.duration')" width="100" align="right">
          <template #default="{ row }">
            {{ row.durationMs === null || row.durationMs === undefined ? '-' : `${row.durationMs} ms` }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="logQuery.pageNum"
          v-model:page-size="logQuery.pageSize"
          :total="logTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import {
  addJob,
  changeJobStatus,
  cleanJobLogs,
  deleteJob,
  getJob,
  listJobHandlers,
  listJobLogs,
  listJobs,
  runJob,
  updateJob
} from '@/api/job'
import { useI18n } from 'vue-i18n'

/**
 * 定时任务管理。
 *
 * 页面只做三件事：配置任务、启停、看日志。
 * 真正的调度、集群锁、失败捕获都在后端，前端不假装“执行成功”。
 */

const { t } = useI18n()

const auth = useAuthStore()
const canChangeStatus = computed(() => auth.hasPermission('platform:monitor:job:changeStatus'))

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, jobName: '', status: '' })
const statusLoadingId = ref('')

const cronPresets = [
  { key: 'everyMinute', value: '0 * * * * ?' },
  { key: 'every5', value: '0 */5 * * * ?' },
  { key: 'hourly', value: '0 0 * * * ?' },
  { key: 'daily2', value: '0 0 2 * * ?' },
  { key: 'weeklyMon', value: '0 0 3 ? * MON' }
]

async function loadList() {
  loading.value = true
  try {
    const data = await listJobs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      jobName: query.jobName || undefined,
      status: query.status || undefined
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
  query.jobName = ''
  query.status = ''
  handleSearch()
}

onMounted(async () => {
  await Promise.all([loadList(), loadHandlers()])
})

/* ==================== 执行器 ==================== */
const handlers = ref([])

async function loadHandlers() {
  handlers.value = (await listJobHandlers()) || []
}

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  jobName: '',
  invokeTarget: '',
  cronExpression: '0 0 2 * * ?',
  status: '1',
  concurrent: '1',
  misfirePolicy: '3',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  jobName: [{ required: true, message: t('page.job.nameRequired'), trigger: 'blur' }],
  invokeTarget: [{ required: true, message: t('page.job.executorRequired'), trigger: 'change' }],
  cronExpression: [{ required: true, message: t('page.job.cronRequired'), trigger: 'blur' }]
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
  editingId.value = row.jobId
  Object.assign(form, {
    jobName: row.jobName,
    invokeTarget: row.invokeTarget,
    cronExpression: row.cronExpression,
    status: row.status || '1',
    concurrent: row.concurrent || '1',
    misfirePolicy: row.misfirePolicy || '3',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getJob(row.jobId)
    Object.assign(form, {
      jobName: detail.jobName,
      invokeTarget: detail.invokeTarget,
      cronExpression: detail.cronExpression,
      status: detail.status || '1',
      concurrent: detail.concurrent || '1',
      misfirePolicy: detail.misfirePolicy || '3',
      remark: detail.remark || ''
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
        jobName: form.jobName.trim(),
        invokeTarget: form.invokeTarget,
        cronExpression: form.cronExpression.trim(),
        status: form.status,
        concurrent: form.concurrent,
        misfirePolicy: form.misfirePolicy,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addJob(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateJob(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

/* ==================== 启停 / 执行 / 删除 ==================== */
async function handleStatusChange(row, checked) {
  const next = checked ? '0' : '1'
  statusLoadingId.value = row.jobId
  try {
    await changeJobStatus(row.jobId, next)
    ElMessage.success(next === '0' ? t('page.job.started') : t('page.job.pausedToast'))
    await loadList()
  } finally {
    statusLoadingId.value = ''
  }
}

async function handleRun(row) {
  try {
    await ElMessageBox.confirm(
      t('page.job.runConfirm', { name: row.jobName }),
      t('page.job.runTitle'),
      { type: 'info', confirmButtonText: t('page.job.runBtn'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await runJob(row.jobId)
  ElMessage.success(t('page.job.runSubmitted'))
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.job.deleteConfirm', { name: row.jobName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteJob(row.jobId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 执行日志 ==================== */
const logVisible = ref(false)
const logLoading = ref(false)
const logRows = ref([])
const logTotal = ref(0)
const logJobId = ref('')
const logJobName = ref('')
const logTimeRange = ref([])
const logQuery = reactive({ pageNum: 1, pageSize: 20, status: '' })

async function openLog(row) {
  logJobId.value = row.jobId
  logJobName.value = row.jobName
  logQuery.pageNum = 1
  logQuery.status = ''
  logTimeRange.value = []
  logVisible.value = true
  await loadLogs()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await listJobLogs({
      pageNum: logQuery.pageNum,
      pageSize: logQuery.pageSize,
      jobId: logJobId.value || undefined,
      status: logQuery.status || undefined,
      beginTime: logTimeRange.value && logTimeRange.value[0] ? logTimeRange.value[0] : undefined,
      endTime: logTimeRange.value && logTimeRange.value[1] ? logTimeRange.value[1] : undefined
    })
    logRows.value = data.records || []
    logTotal.value = Number(data.total || 0)
  } finally {
    logLoading.value = false
  }
}

function handleLogSearch() {
  logQuery.pageNum = 1
  loadLogs()
}

async function handleCleanLogs() {
  try {
    await ElMessageBox.confirm(
      t('page.job.cleanConfirm'),
      t('page.job.cleanTitle'),
      { type: 'warning', confirmButtonText: t('page.job.cleanBtn'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  const count = await cleanJobLogs()
  ElMessage.success(t('page.job.cleanSuccess', { count: count || 0 }))
  await loadLogs()
}
</script>

<style scoped>
.job-remark {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
}

.cron-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 4px;
}

.form-tip {
  font-size: 12px;
  line-height: 1.5;
  margin-top: 2px;
}

.log-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.log-header-sub {
  font-size: 13px;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.log-toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.log-expand {
  padding: 8px 16px;
}

.log-expand-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.log-exception {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  padding: 10px;
  background: var(--lx-code-bg);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
