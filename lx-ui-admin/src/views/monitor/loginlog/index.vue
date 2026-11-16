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
        <el-form-item label="IP">
          <el-input
            v-model="query.ip"
            placeholder="如 127.0.0.1"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
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
            登录成功与失败都会记录；失败原因见「提示」列，可用于排查暴力破解与停用账号。
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:loginlog:clean'"
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

      <el-table v-loading="loading" :data="rows" border stripe row-key="infoId">
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column prop="username" label="账号" width="140" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="browser" label="浏览器" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.browser || '-' }}</template>
        </el-table-column>
        <el-table-column prop="msg" label="提示" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.msg || '-' }}</template>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { cleanLoginLogs, listLoginLogs } from '@/api/log'

/**
 * 登录日志（/monitor/login-log）。
 *
 * <p>写入发生在登录流程里（成功、密码错、账号停用都会写一条），
 * 页面只做查询与清空。操作日志回答“登录后做了什么”，这里回答“谁在什么时候登录”。</p>
 */
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const timeRange = ref([])
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  ip: '',
  status: ''
})

async function loadList() {
  loading.value = true
  try {
    const [beginTime, endTime] = timeRange.value || []
    const data = await listLoginLogs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      ip: query.ip || undefined,
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
  query.username = ''
  query.ip = ''
  query.status = ''
  timeRange.value = []
  handleSearch()
}

onMounted(loadList)

async function handleClean() {
  try {
    await ElMessageBox.confirm(
      '清空会删除全部登录日志，且不可恢复。审计数据仅在确认无需追溯时清空。',
      '清空登录日志',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const count = await cleanLoginLogs()
  ElMessage.success(`已清空 ${count ?? 0} 条登录日志`)
  query.pageNum = 1
  loadList()
}
</script>
