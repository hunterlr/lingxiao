<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.notice.title')">
          <el-input
            v-model="query.noticeTitle"
            :placeholder="t('page.notice.titlePlaceholder')"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.notice.type')">
          <el-select v-model="query.noticeType" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('page.notice.notification')" value="1" />
            <el-option :label="t('page.notice.announcement')" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('page.notice.normal')" value="0" />
            <el-option :label="t('page.notice.closed')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            {{ t('page.notice.hint') }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:notice:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.notice.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="noticeId">
        <el-table-column prop="noticeTitle" :label="t('page.notice.title')" min-width="260" show-overflow-tooltip />
        <el-table-column prop="noticeType" :label="t('page.notice.type')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.noticeType === '2' ? 'primary' : 'warning'" size="small" effect="plain">
              {{ row.noticeType === '2' ? t('page.notice.announcement') : t('page.notice.notification') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('page.notice.normal') : t('page.notice.closed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="190" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openPreview(row)">{{ t('page.notice.preview') }}</el-button>
              <el-button
                v-hasPerm="'platform:system:notice:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:notice:remove'"
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.notice.edit') : t('page.notice.add')"
      width="900px"
      top="6vh"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item :label="t('page.notice.title')" prop="noticeTitle">
              <el-input v-model="form.noticeTitle" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.notice.type')">
              <el-radio-group v-model="form.noticeType">
                <el-radio value="1">{{ t('page.notice.notification') }}</el-radio>
                <el-radio value="2">{{ t('page.notice.announcement') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.status')">
              <el-radio-group v-model="form.status">
                <el-radio value="0">{{ t('page.notice.normal') }}</el-radio>
                <el-radio value="1">{{ t('page.notice.closed') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('common.remark')">
              <el-input v-model="form.remark" maxlength="500" :placeholder="t('page.notice.remarkPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.notice.content')" prop="noticeContent">
              <RichEditor v-model="form.noticeContent" height="380px" :placeholder="t('page.notice.contentPlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" :title="previewTitle" size="600px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="t('page.notice.xssTitle')"
        :description="t('page.notice.xssDesc')"
        style="margin-bottom: 16px"
      />
      <div class="notice-preview" v-html="previewContent"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import RichEditor from '@/components/RichEditor.vue'
import { addNotice, deleteNotice, getNotice, listNotices, updateNotice } from '@/api/notice'

/**
 * 公告管理。
 *
 * <p>正文是富文本 HTML。预览用 {@code v-html} 是必要的，
 * 但这也是 XSS 面：服务端消毒补上之前，公告只对后台账号可见。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  noticeTitle: '',
  noticeType: '',
  status: ''
})

async function loadList() {
  loading.value = true
  try {
    const data = await listNotices({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      noticeTitle: query.noticeTitle || undefined,
      noticeType: query.noticeType || undefined,
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
  query.noticeTitle = ''
  query.noticeType = ''
  query.status = ''
  handleSearch()
}

onMounted(loadList)

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  noticeTitle: '',
  noticeType: '2',
  noticeContent: '',
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  noticeTitle: [{ required: true, message: t('page.notice.titleRequired'), trigger: 'blur' }],
  noticeContent: [
    {
      validator: (rule, value, callback) => {
        const text = (value || '').replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
        if (!text) return callback(new Error(t('page.notice.contentRequired')))
        callback()
      },
      trigger: 'blur'
    }
  ]
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
  editingId.value = row.noticeId
  Object.assign(form, {
    noticeTitle: row.noticeTitle,
    noticeType: row.noticeType || '2',
    noticeContent: row.noticeContent || '',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getNotice(row.noticeId)
    Object.assign(form, {
      noticeTitle: detail.noticeTitle,
      noticeType: detail.noticeType || '2',
      noticeContent: detail.noticeContent || '',
      status: detail.status || '0',
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
        noticeTitle: form.noticeTitle.trim(),
        noticeType: form.noticeType,
        noticeContent: form.noticeContent,
        status: form.status,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addNotice(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateNotice(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(t('page.notice.deleteConfirm', { name: row.noticeTitle }), t('common.deleteTitle'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) {
    return
  }
  await deleteNotice(row.noticeId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 预览 ==================== */
const previewVisible = ref(false)
const previewTitle = ref('')
const previewContent = ref('')

async function openPreview(row) {
  previewTitle.value = row.noticeTitle
  previewContent.value = ''
  previewVisible.value = true
  const detail = await getNotice(row.noticeId).catch(() => null)
  previewContent.value = (detail && detail.noticeContent) || row.noticeContent || `<p>${t('page.notice.noContent')}</p>`
}
</script>

<style scoped>
.notice-preview {
  line-height: 1.8;
  font-size: 14px;
  color: var(--lx-text);
  word-break: break-word;
}

.notice-preview :deep(img) {
  max-width: 100%;
  height: auto;
}

.notice-preview :deep(p) {
  margin: 0 0 10px;
}
</style>
