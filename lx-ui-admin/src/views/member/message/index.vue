<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.memberMessage.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.memberMessage.keywordPlaceholder')"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.memberMessage.readFlag')">
          <el-select v-model="query.readFlag" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('page.memberMessage.unread')" value="0" />
            <el-option :label="t('page.memberMessage.read')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.memberMessage.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:member:message:send'" type="primary" :icon="Plus" @click="openSend">
            {{ t('page.memberMessage.send') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="msgId">
        <el-table-column :label="t('page.memberMessage.member')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="member-cell">
              <strong>{{ row.nickname || '-' }}</strong>
              <span class="text-secondary text-mono">{{ row.username || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('page.memberMessage.title')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" :label="t('page.memberMessage.content')" min-width="280" show-overflow-tooltip />
        <el-table-column prop="msgType" :label="t('page.memberMessage.type')" width="140">
          <template #default="{ row }">{{ typeLabel(row.msgType) }}</template>
        </el-table-column>
        <el-table-column prop="readFlag" :label="t('page.memberMessage.readFlag')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readFlag === '1' ? 'info' : 'warning'" size="small" effect="plain">
              {{ row.readFlag === '1' ? t('page.memberMessage.read') : t('page.memberMessage.unread') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('page.memberMessage.createTime')" width="165" />
        <el-table-column :label="t('common.operation')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-hasPerm="'platform:member:message:remove'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
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

    <el-dialog v-model="sendVisible" :title="t('page.memberMessage.send')" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item :label="t('page.memberMessage.member')" prop="memberIds">
          <el-select
            v-model="form.memberIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchMembers"
            :loading="memberLoading"
            :placeholder="t('page.memberMessage.memberPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="item in memberOptions"
              :key="item.memberId"
              :label="`${item.nickname || item.username}（${item.username}）`"
              :value="item.memberId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.memberMessage.title')" prop="title">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('page.memberMessage.content')" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            :placeholder="t('page.memberMessage.contentPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="sending" @click="handleSend">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  deleteMemberMessage,
  listMemberMessages,
  listMembers,
  sendMemberMessage
} from '@/api/member'

/**
 * 后台消息管理。
 *
 * <p>评论审核产生的站内信由后端自动写，这里只做查看、手动发送与清理。
 * 发送时按会员多选（远程搜索），一次最多 200 人；正文是纯文本。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  readFlag: ''
})

const sendVisible = ref(false)
const sending = ref(false)
const formRef = ref()
const memberLoading = ref(false)
const memberOptions = ref([])
const form = reactive({
  memberIds: [],
  title: '',
  content: ''
})
const rules = {
  memberIds: [{ required: true, message: t('page.memberMessage.memberRequired'), trigger: 'change' }],
  title: [{ required: true, message: t('page.memberMessage.titleRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('page.memberMessage.contentRequired'), trigger: 'blur' }]
}

function typeLabel(type) {
  if (type === 'comment_approved') return t('page.memberMessage.typeApproved')
  if (type === 'comment_rejected') return t('page.memberMessage.typeRejected')
  return t('page.memberMessage.typeSystem')
}

async function loadList() {
  loading.value = true
  try {
    const data = await listMemberMessages({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      readFlag: query.readFlag || undefined
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
  query.readFlag = ''
  handleSearch()
}

async function searchMembers(keyword) {
  memberLoading.value = true
  try {
    const data = await listMembers({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    memberOptions.value = data.records || []
  } finally {
    memberLoading.value = false
  }
}

function openSend() {
  sendVisible.value = true
  if (!memberOptions.value.length) searchMembers('')
}

function resetForm() {
  formRef.value?.resetFields()
  form.memberIds = []
  form.title = ''
  form.content = ''
}

async function handleSend() {
  await formRef.value.validate()
  sending.value = true
  try {
    const count = await sendMemberMessage({
      memberIds: form.memberIds,
      title: form.title,
      content: form.content
    })
    ElMessage.success(t('page.memberMessage.sendSuccess', { count }))
    sendVisible.value = false
    query.pageNum = 1
    await loadList()
  } finally {
    sending.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.memberMessage.deleteConfirm'),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteMemberMessage(row.msgId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

onMounted(loadList)
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