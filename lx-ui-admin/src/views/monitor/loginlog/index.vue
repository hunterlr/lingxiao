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
        <el-form-item :label="t('common.ip')">
          <el-input
            v-model="query.ip"
            :placeholder="t('page.loginlog.ipPlaceholder')"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
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
            {{ t('page.loginlog.hint') }}
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
            {{ t('common.clearLog') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="infoId">
        <el-table-column prop="loginTime" :label="t('common.loginTime')" width="170" />
        <el-table-column prop="username" :label="t('page.user.username')" width="140" show-overflow-tooltip />
        <el-table-column prop="ip" :label="t('common.ip')" width="140" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? t('common.successStatus') : t('common.failStatus') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="browser" :label="t('common.browser')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.browser || '-' }}</template>
        </el-table-column>
        <el-table-column prop="msg" :label="t('page.loginlog.msg')" min-width="180" show-overflow-tooltip>
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
import { useI18n } from 'vue-i18n'

/**
 * 登录日志（/monitor/login-log）。
 *
 * <p>写入发生在登录流程里（成功、密码错、账号停用都会写一条），
 * 页面只做查询与清空。操作日志回答“登录后做了什么”，这里回答“谁在什么时候登录”。</p>
 */
const { t } = useI18n()

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
      t('page.loginlog.cleanConfirm'),
      t('page.loginlog.cleanTitle'),
      { type: 'warning', confirmButtonText: t('page.loginlog.cleanConfirmBtn'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  const count = await cleanLoginLogs()
  ElMessage.success(t('page.loginlog.cleanSuccess', { count: count ?? 0 }))
  query.pageNum = 1
  loadList()
}
</script>
