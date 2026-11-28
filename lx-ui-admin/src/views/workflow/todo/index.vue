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
        <el-table-column prop="createTime" :label="t('page.workflow.arrivedAt')" width="170" />
        <el-table-column :label="t('common.operation')" width="160" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:workflow:task:complete'" link type="primary" @click="openHandle(row)">
                {{ t('page.workflow.handle') }}
              </el-button>
              <el-button link type="info" @click="openDetail(row)">{{ t('page.workflow.detail') }}</el-button>
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

    <el-dialog v-model="handleVisible" :title="t('page.workflow.handleTitle')" width="520px" append-to-body destroy-on-close>
      <el-descriptions :column="1" border size="small" class="handle-summary">
        <el-descriptions-item :label="t('page.workflow.title')">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.processName')">{{ current.processName }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.initiator')">{{ current.initiatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.node')">{{ current.taskName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" class="handle-form">
        <el-form-item :label="t('page.workflow.comment')">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="t('page.workflow.commentPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <div class="text-secondary">{{ t('page.workflow.rejectTip') }}</div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button v-hasPerm="'platform:workflow:task:reject'" type="danger" plain :loading="submitting" @click="submit('reject')">
            {{ t('page.workflow.reject') }}
          </el-button>
          <el-button v-hasPerm="'platform:workflow:task:complete'" type="primary" :loading="submitting" @click="submit('complete')">
            {{ t('page.workflow.approve') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <WorkflowInstanceDrawer v-model="detailVisible" :process-instance-id="detailId" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { completeTask, listTodo, rejectTask } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'

/**
 * 我的待办：只查当前登录人的任务（后端按 Token 过滤，前端不传 userId）。
 *
 * <p>驳回即终止流程，不是退回上一节点——所以按钮旁边写了提示，
 * 避免审批人以为是“退回重填”。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listTodo({
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

/* ==================== 办理 ==================== */
const handleVisible = ref(false)
const submitting = ref(false)
const current = ref({})
const comment = ref('')

function openHandle(row) {
  current.value = row
  comment.value = ''
  handleVisible.value = true
}

async function submit(action) {
  submitting.value = true
  try {
    if (action === 'reject') {
      await rejectTask(current.value.taskId, { comment: comment.value })
    } else {
      await completeTask(current.value.taskId, { comment: comment.value })
    }
    ElMessage.success(t('common.success'))
    handleVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

/* ==================== 详情 ==================== */
const detailVisible = ref(false)
const detailId = ref('')

function openDetail(row) {
  detailId.value = row.processInstanceId
  detailVisible.value = true
}
</script>

<style scoped>
.handle-summary {
  margin-bottom: 16px;
}
</style>