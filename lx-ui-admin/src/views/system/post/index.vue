<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.post.postCode')">
          <el-input
            v-model="query.postCode"
            :placeholder="t('page.post.codePlaceholder')"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.post.postName')">
          <el-input
            v-model="query.postName"
            :placeholder="t('page.post.postName')"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="query.status" dict-type="sys_common_status" width="130px" :placeholder="t('common.allStatus')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.post.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:post:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.post.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="postId">
        <el-table-column prop="postCode" :label="t('page.post.postCode')" min-width="140">
          <template #default="{ row }">
            <span class="text-mono">{{ row.postCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="postName" :label="t('page.post.postName')" min-width="150" />
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <DictTag :value="row.status" dict-type="sys_common_status" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:post:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:post:remove'"
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
      :title="isEdit ? t('page.post.edit') : t('page.post.add')"
      width="520px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        v-loading="dialogLoading"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item :label="t('page.post.postCode')" prop="postCode">
          <el-input v-model="form.postCode" maxlength="64" :placeholder="t('page.post.codePlaceholderEdit')" />
        </el-form-item>
        <el-form-item :label="t('page.post.postName')" prop="postName">
          <el-input v-model="form.postName" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('common.sort')" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="form.status" dict-type="sys_common_status" />
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import { addPost, deletePost, getPost, listPosts, updatePost } from '@/api/post'

/**
 * 岗位管理。字段少，单列表 + 弹窗即可，不套树。
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, postCode: '', postName: '', status: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listPosts({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      postCode: query.postCode || undefined,
      postName: query.postName || undefined,
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
  query.postCode = ''
  query.postName = ''
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
  postCode: '',
  postName: '',
  sort: 0,
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  postCode: [{ required: true, message: t('page.post.codeRequired'), trigger: 'blur' }],
  postName: [{ required: true, message: t('page.post.nameRequired'), trigger: 'blur' }]
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
  editingId.value = row.postId
  Object.assign(form, {
    postCode: row.postCode,
    postName: row.postName,
    sort: row.sort ?? 0,
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getPost(row.postId)
    Object.assign(form, {
      postCode: detail.postCode,
      postName: detail.postName,
      sort: detail.sort ?? 0,
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
        postCode: form.postCode.trim(),
        postName: form.postName.trim(),
        sort: form.sort,
        status: form.status,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addPost(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updatePost(editingId.value, payload)
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
    await ElMessageBox.confirm(
      t('page.post.deleteConfirm', { name: row.postName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deletePost(row.postId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>
