<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.workflow.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.workflow.keywordPlaceholder')"
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

      <el-table v-loading="loading" :data="rows" border stripe row-key="taskId">
        <el-table-column prop="title" :label="t('page.workflow.title')" min-width="200" />
        <el-table-column prop="processName" :label="t('page.workflow.processName')" min-width="140" />
        <el-table-column prop="taskName" :label="t('page.workflow.node')" min-width="120" />
        <el-table-column prop="initiatorName" :label="t('page.workflow.initiator')" width="120" />
        <el-table-column prop="comment" :label="t('page.workflow.comment')" min-width="180">
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="endTime" :label="t('page.workflow.handledAt')" width="170" />
        <el-table-column :label="t('common.operation')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="openDetail(row)">{{ t('page.workflow.detail') }}</el-button>
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

    <WorkflowInstanceDrawer v-model="detailVisible" :process-instance-id="detailId" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listDone } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'

/**
 * 我的已办：只读列表 + 详情，不提供再次办理。
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listDone({
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

const detailVisible = ref(false)
const detailId = ref('')

function openDetail(row) {
  detailId.value = row.processInstanceId
  detailVisible.value = true
}
</script>