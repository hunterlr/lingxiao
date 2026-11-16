<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="模块">
          <el-input
            v-model="query.title"
            placeholder="如 用户管理"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input
            v-model="query.operName"
            placeholder="登录账号"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.businessType" clearable placeholder="全部" style="width: 120px">
            <el-option
              v-for="item in businessTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="defaultTime"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            只记录写操作；请求参数里的密码 / Token 已脱敏，单字段超过 2000 字符会被截断。
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
            清空日志
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="operId">
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column prop="title" label="模块" width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.businessType)" size="small" effect="plain">
              {{ typeLabel(row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestMethod" label="请求" width="80" align="center" />
        <el-table-column prop="operName" label="操作人" width="120" show-overflow-tooltip />
        <el-table-column label="请求地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-mono">{{ row.operUrl || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operIp" label="IP" width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.costTime }} ms</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="操作日志详情" width="760px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border label-width="90px">
        <el-descriptions-item label="模块">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          {{ typeLabel(detail.businessType) }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.operIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.requestMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTime }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.operTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detail.status === '0' ? '成功' : '失败' }}
        </el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">
          <span class="text-mono">{{ detail.operUrl || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">
          <span class="text-mono">{{ detail.method || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="log-pre">{{ pretty(detail.operParam) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回结果" :span="2">
          <pre class="log-pre">{{ pretty(detail.jsonResult) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="异常信息" :span="2">
          <pre class="log-pre log-pre--error">{{ detail.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
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

/**
 * 操作日志（/monitor/oper-log）。
 *
 * <p>数据由后端 {@code LogAspect} 在 @Log 标注的写接口上自动落库，
 * 页面只做查询与清空。业务类型存的是枚举序号，这里负责翻译成中文，
 * 与后端 {@code BusinessType} 保持一致，新增类型两端一起改。</p>
 */
const businessTypes = [
  { value: 0, label: '其他' },
  { value: 1, label: '新增' },
  { value: 2, label: '修改' },
  { value: 3, label: '删除' },
  { value: 4, label: '授权' },
  { value: 5, label: '导出' },
  { value: 6, label: '导入' },
  { value: 7, label: '强退' },
  { value: 8, label: '清空' }
]

const typeMap = new Map(businessTypes.map((item) => [item.value, item.label]))
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
  return typeMap.get(value) ?? '其他'
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
      '清空会删除全部操作日志，且不可恢复。审计数据仅在确认无需追溯时清空。',
      '清空操作日志',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const count = await cleanOperLogs()
  ElMessage.success(`已清空 ${count ?? 0} 条操作日志`)
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
