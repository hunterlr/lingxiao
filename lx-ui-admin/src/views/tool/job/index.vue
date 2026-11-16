<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="任务名称">
          <el-input
            v-model="query.jobName"
            placeholder="任务名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px">
            <el-option label="正常" value="0" />
            <el-option label="暂停" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            Spring Task + 数据库锁：集群中同一任务同一时刻只执行一次；停机期间错过的执行按“放弃”处理。
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:job:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            新增任务
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="jobId">
        <el-table-column prop="jobName" label="任务名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.jobName }}</div>
            <div v-if="row.remark" class="text-secondary job-remark">{{ row.remark }}</div>
          </template>
        </el-table-column>
        <el-table-column label="执行器" min-width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.invokeTarget }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Cron 表达式" width="150">
          <template #default="{ row }">
            <span class="text-mono">{{ row.cronExpression }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              :loading="statusLoadingId === row.jobId"
              :disabled="!canChangeStatus"
              inline-prompt
              active-text="正常"
              inactive-text="暂停"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="上次执行" width="165">
          <template #default="{ row }">{{ row.lastRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="下次执行" width="165">
          <template #default="{ row }">{{ row.nextRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:monitor:job:run'"
                link
                type="primary"
                @click="handleRun(row)"
              >
                执行一次
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:log'"
                link
                type="primary"
                @click="openLog(row)"
              >
                日志
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPerm="'platform:monitor:job:remove'"
                link
                type="danger"
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
      :title="isEdit ? '编辑任务' : '新增任务'"
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
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="form.jobName" maxlength="64" placeholder="例如：每日清理任务日志" />
        </el-form-item>
        <el-form-item label="执行器" prop="invokeTarget">
          <el-select v-model="form.invokeTarget" placeholder="选择执行器" style="width: 100%">
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
            placeholder="秒 分 时 日 月 周，例如 0 0 2 * * ?"
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
              {{ preset.label }}
            </el-button>
          </div>
          <div class="text-secondary form-tip">
            6 段格式：秒 分 时 日 月 周；不支持 5 段 UNIX cron。
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常（参与调度）</el-radio>
            <el-radio value="1">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="并发策略">
          <el-radio-group v-model="form.concurrent">
            <el-radio value="1">禁止（上次没跑完就跳过）</el-radio>
            <el-radio value="0">允许</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="错失策略">
          <el-select v-model="form.misfirePolicy" style="width: 100%">
            <el-option label="放弃（v1.0 实际行为）" value="3" />
            <el-option label="执行一次（保留，当前按放弃处理）" value="2" />
            <el-option label="立即补偿（保留，当前按放弃处理）" value="1" />
          </el-select>
          <div class="text-secondary form-tip">
            Spring Task 不持久化触发器，停机期间错过的执行不会补跑；字段保留用于兼容旧数据。
          </div>
        </el-form-item>
        <el-form-item label="备注">
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
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 执行日志 -->
    <el-drawer v-model="logVisible" size="62%" append-to-body :destroy-on-close="true">
      <template #header>
        <div class="log-header">
          <span>执行日志</span>
          <span class="text-secondary log-header-sub">{{ logJobName }}</span>
        </div>
      </template>

      <div class="log-toolbar">
        <el-select v-model="logQuery.status" clearable placeholder="全部结果" style="width: 120px">
          <el-option label="成功" value="0" />
          <el-option label="失败" value="1" />
        </el-select>
        <el-date-picker
          v-model="logTimeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 330px"
        />
        <el-button type="primary" :icon="Search" @click="handleLogSearch">查询</el-button>
        <div class="log-toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:job:log:clean'"
            type="danger"
            plain
            :icon="Delete"
            @click="handleCleanLogs"
          >
            清空日志
          </el-button>
          <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="logLoading" :data="logRows" border stripe row-key="jobLogId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="log-expand">
              <div class="log-expand-title">失败堆栈 / 执行上下文</div>
              <pre class="log-exception">{{ row.exceptionInfo || '（无异常信息）' }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="执行时间" width="165" />
        <el-table-column label="触发" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.triggerType === 'MANUAL' ? 'warning' : 'info'">
              {{ row.triggerType === 'MANUAL' ? '手动' : '调度' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobMessage" label="结果摘要" min-width="240" show-overflow-tooltip />
        <el-table-column label="耗时" width="100" align="right">
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

/**
 * 定时任务管理。
 *
 * 页面只做三件事：配置任务、启停、看日志。
 * 真正的调度、集群锁、失败捕获都在后端，前端不假装“执行成功”。
 */

const auth = useAuthStore()
const canChangeStatus = computed(() => auth.hasPermission('platform:monitor:job:changeStatus'))

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, jobName: '', status: '' })
const statusLoadingId = ref('')

const cronPresets = [
  { label: '每分钟', value: '0 * * * * ?' },
  { label: '每 5 分钟', value: '0 */5 * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天 02:00', value: '0 0 2 * * ?' },
  { label: '每周一 03:00', value: '0 0 3 ? * MON' }
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
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  invokeTarget: [{ required: true, message: '请选择执行器', trigger: 'change' }],
  cronExpression: [{ required: true, message: '请输入 Cron 表达式', trigger: 'blur' }]
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
        ElMessage.success('新增成功')
      } else {
        await updateJob(editingId.value, payload)
        ElMessage.success('保存成功')
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
    ElMessage.success(next === '0' ? '任务已启动' : '任务已暂停')
    await loadList()
  } finally {
    statusLoadingId.value = ''
  }
}

async function handleRun(row) {
  try {
    await ElMessageBox.confirm(
      `立即执行任务「${row.jobName}」一次？任务会提交到后台线程池，结果请到「日志」里查看。`,
      '执行确认',
      { type: 'info', confirmButtonText: '执行', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await runJob(row.jobId)
  ElMessage.success('已提交执行，稍后刷新日志查看结果')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除任务「${row.jobName}」吗？历史执行日志会保留，锁行会被清理。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteJob(row.jobId)
  ElMessage.success('删除成功')
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
      '确认清空全部定时任务日志吗？该操作不可恢复，任务定义不受影响。',
      '清空确认',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const count = await cleanJobLogs()
  ElMessage.success(`已清空 ${count || 0} 条任务日志`)
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
