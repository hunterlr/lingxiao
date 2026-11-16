<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('common.module')">
          <el-input
            v-model="query.title"
            :placeholder="t('page.operlog.titlePlaceholder')"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.operlog.operator')">
          <el-input
            v-model="query.operName"
            :placeholder="t('page.user.usernamePlaceholder')"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.type')">
          <el-select v-model="query.businessType" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option
              v-for="item in businessTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 110px">
            <el-option :label="t('common.successStatus')" value="0" />
            <el-option :label="t('common.failStatus')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.time')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            :range-separator="t('common.to')"
            :start-placeholder="t('common.startTime')"
            :end-placeholder="t('common.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="defaultTime"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            {{ t('page.operlog.hint') }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:operlog:clean'"
            type="danger"
            plain
            :icon="Delete"
            @click="handleClean"
          >
            {{ t('common.clearLog') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="operId">
        <el-table-column prop="operTime" :label="t('page.operlog.operTime')" width="170" />
        <el-table-column prop="title" :label="t('common.module')" width="130" show-overflow-tooltip />
        <el-table-column :label="t('common.type')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.businessType)" size="small" effect="plain">
              {{ typeLabel(row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestMethod" :label="t('page.operlog.request')" width="80" align="center" />
        <el-table-column prop="operName" :label="t('page.operlog.operator')" width="120" show-overflow-tooltip />
        <el-table-column :label="t('page.operlog.requestUrl')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-mono">{{ row.operUrl || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operIp" :label="t('common.ip')" width="130" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? t('common.successStatus') : t('common.failStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.operlog.cost')" width="90" align="right">
          <template #default="{ row }">{{ row.costTime }} ms</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('common.detail') }}</el-button>
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

    <el-dialog v-model="detailVisible" :title="t('page.operlog.detailTitle')" width="760px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border label-width="90px">
        <el-descriptions-item :label="t('common.module')">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.type')">
          {{ typeLabel(detail.businessType) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.operator')">{{ detail.operName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.ip')">{{ detail.operIp || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.requestMethod')">{{ detail.requestMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.cost')">{{ detail.costTime }} ms</el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.operTime')">{{ detail.operTime }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          {{ detail.status === '0' ? t('common.successStatus') : t('common.failStatus') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.requestUrl')" :span="2">
          <span class="text-mono">{{ detail.operUrl || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.method')" :span="2">
          <span class="text-mono">{{ detail.method || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.requestParam')" :span="2">
          <pre class="log-pre">{{ pretty(detail.operParam) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.operlog.responseResult')" :span="2">
          <pre class="log-pre">{{ pretty(detail.jsonResult) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" :label="t('page.operlog.errorMsg')" :span="2">
          <pre class="log-pre log-pre--error">{{ detail.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">{{ t('common.close') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { cleanOperLogs, getOperLog, listOperLogs } from '@/api/log'
import { useI18n } from 'vue-i18n'

/**
 * 操作日志（/monitor/oper-log）。
 *
 * <p>数据由后端 {@code LogAspect} 在 @Log 标注的写接口上自动落库，
 * 页面只做查询与清空。业务类型存的是枚举序号，这里负责翻译成中文，
 * 与后端 {@code BusinessType} 保持一致，新增类型两端一起改。</p>
 */
const { t } = useI18n()

/** 存的是枚举序号，译文按 page.operlog.<key> 取；新增类型时与后端 BusinessType 一起改。 */
const businessTypes = [
  { value: 0, key: 'other' },
  { value: 1, key: 'insert' },
  { value: 2, key: 'update' },
  { value: 3, key: 'delete' },
  { value: 4, key: 'grant' },
  { value: 5, key: 'export' },
  { value: 6, key: 'import' },
  { value: 7, key: 'kickout' },
  { value: 8, key: 'clean' }
]
const tagMap = { 1: 'success', 2: 'primary', 3: 'danger', 4: 'warning', 8: 'danger' }

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const timeRange = ref([])
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  title: '',
  operName: '',
  businessType: '',
  status: ''
})

function typeLabel(value) {
  const item = businessTypes.find((entry) => entry.value === value)
  return t(`page.operlog.${item ? item.key : 'other'}`)
}

function typeTag(value) {
  return tagMap[value] || 'info'
}

function pretty(text) {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch (e) {
    return text
  }
}

async function loadList() {
  loading.value = true
  try {
    const [beginTime, endTime] = timeRange.value || []
    const data = await listOperLogs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      title: query.title || undefined,
      operName: query.operName || undefined,
      businessType: query.businessType === '' ? undefined : query.businessType,
      status: query.status || undefined,
      beginTime: beginTime || undefined,
      endTime: endTime || undefined
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
  query.title = ''
  query.operName = ''
  query.businessType = ''
  query.status = ''
  timeRange.value = []
  handleSearch()
}

onMounted(loadList)

/* ==================== 详情 ==================== */
const detailVisible = ref(false)
const detail = ref(null)

async function openDetail(row) {
  detail.value = row
  detailVisible.value = true
  try {
    detail.value = await getOperLog(row.operId)
  } catch (e) {
    // 列表行本身已经够看，详情失败不额外打断；拦截器已提示
  }
}

/* ==================== 清空 ==================== */
async function handleClean() {
  try {
    await ElMessageBox.confirm(
      t('page.operlog.cleanConfirm'),
      t('page.operlog.cleanTitle'),
      { type: 'warning', confirmButtonText: t('page.operlog.cleanConfirmBtn'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  const count = await cleanOperLogs()
  ElMessage.success(t('page.operlog.cleanSuccess', { count: count ?? 0 }))
  query.pageNum = 1
  loadList()
}
</script>

<style scoped>
.log-pre {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 8px 10px;
  background: var(--lx-code-bg);
  border: 1px solid var(--lx-border);
  border-radius: 6px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-pre--error {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-7);
  color: var(--el-color-danger);
}
</style>
