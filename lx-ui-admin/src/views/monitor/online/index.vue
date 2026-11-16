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
        <el-form-item :label="t('common.loginIp')">
          <el-input
            v-model="query.ip"
            :placeholder="t('page.online.ipPlaceholder')"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
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
            {{ t('page.online.hint') }}
          </span>
          <el-checkbox v-model="autoRefresh" style="margin-left: 8px">{{ t('common.autoRefresh') }}</el-checkbox>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:monitor:online:kickout'"
            type="danger"
            plain
            :icon="SwitchButton"
            :disabled="!selectedRows.length"
            @click="handleBatchKickout"
          >
            {{ t('page.online.batchKickout') }}{{ selectedRows.length ? t('page.online.batchCount', { count: selectedRows.length }) : '' }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        border
        stripe
        row-key="tokenId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="(row) => row.kickable" />
        <el-table-column prop="username" :label="t('page.user.username')" width="130" show-overflow-tooltip />
        <el-table-column prop="nickname" :label="t('page.user.nickname')" width="130" show-overflow-tooltip />
        <el-table-column :label="t('page.user.dept')" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="loginIp" :label="t('common.loginIp')" width="130" show-overflow-tooltip />
        <el-table-column :label="t('page.online.client')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.browser || '-' }}</template>
        </el-table-column>
        <el-table-column prop="loginTime" :label="t('common.loginTime')" width="170" />
        <el-table-column prop="lastActiveTime" :label="t('page.online.lastActive')" width="170" />
        <el-table-column :label="t('page.online.remaining')" width="120" align="center">
          <template #default="{ row }">{{ formatRemaining(row.timeoutSeconds) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.current" type="success" size="small">{{ t('page.online.currentSession') }}</el-tag>
            <el-tag v-else-if="!row.kickable" type="warning" size="small">
              {{ row.kickableReason || t('page.online.cannotKick') }}
            </el-tag>
            <el-tag v-else type="info" size="small" effect="plain">{{ t('common.online') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.kickable"
              v-hasPerm="'platform:monitor:online:kickout'"
              link
              type="danger"
              @click="handleKickout(row)"
            >
              {{ t('page.online.kickout') }}
            </el-button>
            <span v-else class="text-secondary">-</span>
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
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, SwitchButton } from '@element-plus/icons-vue'
import { kickoutOnlineUsers, listOnlineUsers } from '@/api/monitor'
import { useI18n } from 'vue-i18n'

/**
 * 在线用户（/monitor/online）。
 *
 * <p>数据直接来自 Sa-Token 会话，不是数据库表：用户被踢、会话过期后
 * 下一次刷新立刻消失，不存在“表里还在线”的脏数据。</p>
 *
 * <p>三处前端只做展示、不做策略：</p>
 * <ul>
 *   <li>{@code row.current} 标记当前会话；</li>
 *   <li>{@code row.kickable / kickableReason} 由后端判定，前端只据此置灰；</li>
 *   <li>真实 Token 不下发，列表里的 {@code tokenId} 是脱敏值，只能用于强退。</li>
 * </ul>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const tableRef = ref(null)
const selectedRows = ref([])
const autoRefresh = ref(false)
const autoTimer = ref(null)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  ip: ''
})

/** 剩余秒数转成“x 小时 y 分”，避免表格里出现 43199 这种难读数字。 */
function formatRemaining(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) {
    return t('page.online.expired')
  }
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  if (hours > 0) {
    return t('page.online.hoursMinutes', { hours, minutes })
  }
  return t('page.online.minutesOnly', { minutes: Math.max(minutes, 1) })
}

async function loadList() {
  loading.value = true
  try {
    const data = await listOnlineUsers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      ip: query.ip || undefined
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
    // 刷新后旧勾选已不在当前数据里，主动清空避免“看不见的批量目标”
    selectedRows.value = []
    tableRef.value?.clearSelection()
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
  handleSearch()
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
}

onMounted(loadList)

// 自动刷新：默认关闭，避免运维正在看某一行时列表突然跳动
watch(autoRefresh, (enabled) => {
  if (autoTimer.value) {
    clearInterval(autoTimer.value)
    autoTimer.value = null
  }
  if (enabled) {
    autoTimer.value = setInterval(loadList, 15000)
  }
})

onBeforeUnmount(() => {
  if (autoTimer.value) {
    clearInterval(autoTimer.value)
  }
})

/* ==================== 强退 ==================== */
async function confirmKickout(message) {
  try {
    await ElMessageBox.confirm(message, t('page.online.kickoutTitle'), {
      type: 'warning',
      confirmButtonText: t('page.online.confirmKickout'),
      cancelButtonText: t('common.cancel')
    })
    return true
  } catch (e) {
    return false
  }
}

function reportResult(result, count) {
  const success = Number(result?.successCount || 0)
  const failures = result?.failures || []
  if (success > 0) {
    ElMessage.success(t('page.online.kickoutSuccess', { count: success }))
  }
  if (failures.length) {
    ElMessage.warning({
      message: t('page.online.kickoutPartial', { failures: failures.join('; ') }),
      duration: 6000
    })
  }
  if (success === 0 && failures.length === 0) {
    ElMessage.info(t('page.online.kickoutNone', { count }))
  }
  loadList()
}

async function handleKickout(row) {
  const ok = await confirmKickout(
    t('page.online.kickoutMsg', { name: row.nickname || row.username, ip: row.loginIp || t('page.online.unknownIp') })
  )
  if (!ok) return
  const result = await kickoutOnlineUsers([row.tokenId])
  reportResult(result, 1)
}

async function handleBatchKickout() {
  const ids = selectedRows.value.map((row) => row.tokenId)
  if (!ids.length) return
  const ok = await confirmKickout(t('page.online.kickoutBatchMsg', { count: ids.length }))
  if (!ok) return
  const result = await kickoutOnlineUsers(ids)
  reportResult(result, ids.length)
}
</script>
