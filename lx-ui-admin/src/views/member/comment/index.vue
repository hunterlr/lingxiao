<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.memberComment.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.memberComment.keywordPlaceholder')"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.memberComment.articleId')">
          <el-input-number
            v-model="query.articleId"
            :min="1"
            :controls="false"
            :placeholder="t('page.memberComment.articleIdPlaceholder')"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('page.memberComment.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('page.memberComment.statusPending')" value="0" />
            <el-option :label="t('page.memberComment.statusApproved')" value="1" />
            <el-option :label="t('page.memberComment.statusRejected')" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.memberComment.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="commentId">
        <el-table-column prop="content" :label="t('page.memberComment.content')" min-width="300" show-overflow-tooltip />
        <el-table-column prop="articleId" :label="t('page.memberComment.articleId')" width="190">
          <template #default="{ row }"><span class="text-mono">{{ row.articleId }}</span></template>
        </el-table-column>
        <el-table-column :label="t('page.memberComment.member')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="member-cell">
              <strong>{{ row.nickname || '-' }}</strong>
              <span class="text-secondary text-mono">{{ row.username || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('page.memberComment.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('page.memberComment.createTime')" width="165" />
        <el-table-column prop="auditTime" :label="t('page.memberComment.auditTime')" width="165">
          <template #default="{ row }">{{ row.auditTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="auditRemark" :label="t('page.memberComment.auditRemark')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="190" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-if="row.status !== '1'"
                v-hasPerm="'platform:member:comment:audit'"
                link
                type="success"
                @click="handleApprove(row)"
              >
                {{ t('page.memberComment.approve') }}
              </el-button>
              <el-button
                v-if="row.status !== '2'"
                v-hasPerm="'platform:member:comment:audit'"
                link
                type="warning"
                @click="handleReject(row)"
              >
                {{ t('page.memberComment.reject') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:member:comment:remove'"
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { auditMemberComment, deleteMemberComment, listMemberComments } from '@/api/member'

/**
 * 后台评论审核。
 *
 * <p>评论先审核后展示：待审核（0）可通过对 / 拒绝，已通过（1）可改为拒绝，
 * 已拒绝（2）可改为通过。删除只做逻辑删除，审核记录仍保留在库中。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  status: '',
  articleId: null
})

function statusLabel(status) {
  if (status === '1') return t('page.memberComment.statusApproved')
  if (status === '2') return t('page.memberComment.statusRejected')
  return t('page.memberComment.statusPending')
}

function statusTag(status) {
  if (status === '1') return 'success'
  if (status === '2') return 'danger'
  return 'warning'
}

async function loadList() {
  loading.value = true
  try {
    const data = await listMemberComments({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      articleId: query.articleId || undefined
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
  query.keyword = ''
  query.status = ''
  query.articleId = null
  handleSearch()
}

onMounted(loadList)

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(
      t('page.memberComment.approveConfirm'),
      t('common.tip'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await auditMemberComment(row.commentId, { status: '1' })
  ElMessage.success(t('page.memberComment.approveSuccess'))
  await loadList()
}

async function handleReject(row) {
  let remark = ''
  try {
    const result = await ElMessageBox.prompt(
      t('page.memberComment.rejectPrompt'),
      t('page.memberComment.rejectTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputPlaceholder: t('page.memberComment.rejectRemarkPlaceholder'),
        inputValidator: (value) => !value || value.length <= 255 || t('page.memberComment.rejectRemarkPlaceholder')
      }
    )
    remark = result.value || ''
  } catch (e) {
    return
  }
  await auditMemberComment(row.commentId, { status: '2', remark: remark || undefined })
  ElMessage.success(t('page.memberComment.rejectSuccess'))
  await loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.memberComment.deleteConfirm'),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteMemberComment(row.commentId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>

<style scoped>
.member-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.member-cell strong,
.member-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
