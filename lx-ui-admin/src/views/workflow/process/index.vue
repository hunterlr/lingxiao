<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.workflow.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.workflow.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="loadList"
            @clear="loadList"
          />
        </el-form-item>
        <el-form-item :label="t('page.workflow.category')">
          <el-select v-model="query.categoryKey" clearable :placeholder="t('common.all')" style="width: 140px">
            <el-option v-for="item in categories" :key="item.categoryKey" :label="item.name" :value="item.categoryKey" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.workflow.process.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:workflow:process:import'" type="primary" :icon="Upload" @click="openImport">
            {{ t('page.workflow.process.import') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="defId">
        <el-table-column prop="name" :label="t('page.workflow.processName')" min-width="180" />
        <el-table-column prop="processKey" :label="t('page.workflow.processKey')" min-width="150">
          <template #default="{ row }"><span class="text-mono">{{ row.processKey }}</span></template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="t('page.workflow.category')" width="110" />
        <el-table-column prop="formName" :label="t('page.workflow.form.binding')" width="150">
          <template #default="{ row }">{{ row.formName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="processVersion" :label="t('page.workflow.version')" width="90" align="center">
          <template #default="{ row }">v{{ row.processVersion }}</template>
        </el-table-column>
        <el-table-column prop="runningCount" :label="t('page.workflow.process.running')" width="110" align="center" />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="!can('platform:workflow:process:status')"
              @change="(value) => handleStatusChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="170" />
        <el-table-column :label="t('common.operation')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-hasPerm="'platform:workflow:process:remove'"
              link
              type="danger"
              :disabled="row.runningCount > 0"
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <el-dialog v-model="importVisible" :title="t('page.workflow.process.import')" width="520px" append-to-body destroy-on-close>
      <el-form label-width="90px">
        <el-form-item :label="t('page.workflow.process.file')" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".bpmn20.xml,.bpmn"
            :on-change="onFileChange"
            :on-remove="() => (file = null)"
          >
            <el-button :icon="Upload">{{ t('page.workflow.process.chooseFile') }}</el-button>
            <template #tip>
              <div class="text-secondary">{{ t('page.workflow.process.fileTip') }}</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('page.workflow.form.binding')">
          <el-select v-model="importForm.formKey" clearable style="width: 100%" :placeholder="t('page.workflow.process.formNone')">
            <el-option
              v-for="item in publishedForms"
              :key="item.formKey"
              :label="`${item.name}（${item.formKey}）`"
              :value="item.formKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.workflow.category')">
          <el-select v-model="importForm.categoryKey" style="width: 100%">
            <el-option v-for="item in categories" :key="item.categoryKey" :label="item.name" :value="item.categoryKey" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.workflow.remark')">
          <el-input v-model="importForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="importVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="importing" @click="submitImport">{{ t('common.confirm') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  changeProcessStatus,
  importProcess,
  listProcessCategories,
  listProcessDefs,
  listForms,
  removeProcess
} from '@/api/workflow'
import { useAuthStore } from '@/store/auth'

/**
 * 流程定义：上传 BPMN 部署、启停、删除。
 *
 * <p>删除只允许在没有运行中实例时进行；停用后不允许新发起，
 * 已运行的实例不受影响——这两条后端也会再校验。</p>
 */
const { t } = useI18n()
const auth = useAuthStore()
const can = (perm) => auth.hasPermission(perm)

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', categoryKey: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listProcessDefs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      categoryKey: query.categoryKey || undefined
    })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadPublishedForms() {
  const data = await listForms({ pageNum: 1, pageSize: 100 })
  publishedForms.value = (data?.records || []).filter((item) => item.version > 0)
}

function handleReset() {
  query.keyword = ''
  query.categoryKey = ''
  query.pageNum = 1
  loadList()
}

onMounted(async () => {
  categories.value = (await listProcessCategories()) || []
  loadList()
})

async function handleStatusChange(row, status) {
  const previous = status === '0' ? '1' : '0'
  try {
    await changeProcessStatus(row.defId, status)
    ElMessage.success(t('common.updateSuccess'))
  } catch (e) {
    row.status = previous
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    t('page.workflow.process.deleteConfirm', { name: row.name }),
    t('common.deleteTitle'),
    { type: 'warning' }
  )
  await removeProcess(row.defId)
  ElMessage.success(t('common.deleteSuccess'))
  loadList()
}

/* ==================== 导入 ==================== */
const importVisible = ref(false)
const importing = ref(false)
const uploadRef = ref(null)
const file = ref(null)
const importForm = reactive({ categoryKey: 'common', remark: '', formKey: '' })
const publishedForms = ref([])

const onFileChange = (uploadFile) => {
  file.value = uploadFile.raw
}

function openImport() {
  file.value = null
  importForm.categoryKey = categories.value[0]?.categoryKey || 'common'
  importForm.remark = ''
  importForm.formKey = ''
  loadPublishedForms()
  importVisible.value = true
  uploadRef.value?.clearFiles()
}

async function submitImport() {
  if (!file.value) {
    ElMessage.warning(t('page.workflow.process.fileRequired'))
    return
  }
  const formData = new FormData()
  formData.append('file', file.value)
  if (importForm.categoryKey) formData.append('categoryKey', importForm.categoryKey)
  if (importForm.remark) formData.append('remark', importForm.remark)
  if (importForm.formKey) formData.append('formKey', importForm.formKey)
  importing.value = true
  try {
    await importProcess(formData)
    ElMessage.success(t('page.workflow.process.importSuccess'))
    importVisible.value = false
    loadList()
  } finally {
    importing.value = false
  }
}
</script>