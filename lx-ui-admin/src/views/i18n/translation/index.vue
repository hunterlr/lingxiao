<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.i18n.translation.namespace')">
          <el-select
            v-model="query.namespace"
            clearable
            filterable
            allow-create
            default-first-option
            :placeholder="t('common.all')"
            style="width: 160px"
          >
            <el-option v-for="item in namespaces" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.i18n.translation.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.i18n.translation.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('common.enabled')" value="0" />
            <el-option :label="t('common.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.i18n.translation.hint') }}</span>
          <el-tag v-if="dirtyCount" type="warning" size="small" effect="plain" style="margin-left: 8px">
            {{ t('page.i18n.translation.dirtyCount', { count: dirtyCount }) }}
          </el-tag>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:i18n:key:add'" type="primary" :icon="Plus" @click="openAddKey">
            {{ t('page.i18n.translation.addKey') }}
          </el-button>
          <el-upload
            v-hasPerm="'platform:i18n:import'"
            :show-file-list="false"
            accept=".csv,text/csv"
            :http-request="handleImport"
          >
            <el-button :icon="Upload" :loading="importing">{{ t('page.i18n.translation.import') }}</el-button>
          </el-upload>
          <el-button v-hasPerm="'platform:i18n:export'" :icon="Download" :loading="exporting" @click="handleExport">
            {{ t('page.i18n.translation.export') }}
          </el-button>
          <el-button v-hasPerm="'platform:i18n:coverage'" :icon="DataAnalysis" @click="openCoverage">
            {{ t('page.i18n.translation.coverage') }}
          </el-button>
          <el-button v-hasPerm="'platform:i18n:missing'" :icon="Warning" @click="openMissing">
            {{ t('page.i18n.translation.missing') }}
          </el-button>
          <el-button
            v-hasPerm="'platform:i18n:value:batch'"
            type="success"
            :icon="Check"
            :loading="saving"
            :disabled="!dirtyCount"
            @click="saveValues"
          >
            {{ t('page.i18n.translation.saveDraft') }}
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="keyId">
        <el-table-column prop="keyCode" :label="t('page.i18n.translation.keyCode')" min-width="240">
          <template #default="{ row }"><span class="text-mono">{{ row.keyCode }}</span></template>
        </el-table-column>
        <el-table-column prop="defaultValue" :label="t('page.i18n.translation.defaultValue')" min-width="200" show-overflow-tooltip />
        <el-table-column
          v-for="lang in languages"
          :key="lang.languageCode"
          :label="lang.languageName"
          min-width="220"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.values[lang.languageCode]"
              size="small"
              clearable
              :placeholder="t('page.i18n.translation.valuePlaceholder')"
              @input="markDirty(row, lang.languageCode)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="moduleCode" :label="t('page.i18n.translation.module')" width="110" />
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="130" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:i18n:key:edit'" link type="primary" @click="openEditKey(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:i18n:key:remove'" link type="danger" @click="handleDeleteKey(row)">
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
          :page-sizes="[10, 20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 新增 / 编辑翻译键 -->
    <el-dialog
      v-model="keyDialogVisible"
      :title="isEditKey ? t('page.i18n.translation.editKey') : t('page.i18n.translation.addKey')"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="keyFormRef" :model="keyForm" :rules="keyRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.translation.namespace')" prop="namespace">
              <el-input v-model="keyForm.namespace" maxlength="64" :placeholder="t('page.i18n.translation.namespacePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.translation.module')">
              <el-input v-model="keyForm.moduleCode" maxlength="64" :placeholder="t('page.i18n.translation.modulePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.i18n.translation.keyCode')" prop="keyCode">
              <el-input v-model="keyForm.keyCode" maxlength="255" :placeholder="t('page.i18n.translation.keyCodePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.i18n.translation.defaultValue')">
              <el-input
                v-model="keyForm.defaultValue"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                :placeholder="t('page.i18n.translation.defaultPlaceholder')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.status')">
              <el-radio-group v-model="keyForm.status">
                <el-radio value="0">{{ t('common.enabled') }}</el-radio>
                <el-radio value="1">{{ t('common.disabled') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('common.remark')">
              <el-input v-model="keyForm.remark" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="keyDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="keySaving" @click="submitKey">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 覆盖率 -->
    <el-dialog v-model="coverageVisible" :title="t('page.i18n.translation.coverageTitle')" width="720px" append-to-body>
      <el-form :inline="true">
        <el-form-item :label="t('page.i18n.translation.language')">
          <el-select v-model="coverageLanguage" style="width: 160px" @change="loadCoverage">
            <el-option
              v-for="item in languages"
              :key="item.languageCode"
              :label="item.languageName"
              :value="item.languageCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadCoverage">{{ t('common.refresh') }}</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="coverageLoading" :data="coverageRows" border stripe size="small">
        <el-table-column prop="namespace" :label="t('page.i18n.translation.namespace')" min-width="150" />
        <el-table-column prop="translated" :label="t('page.i18n.translation.translated')" width="100" align="center" />
        <el-table-column prop="totalKeys" :label="t('page.i18n.translation.totalKeys')" width="100" align="center" />
        <el-table-column :label="t('page.i18n.translation.coverageRate')" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.coverage || 0)" :stroke-width="12" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 缺失记录 -->
    <el-drawer v-model="missingVisible" :title="t('page.i18n.translation.missingTitle')" size="720px" append-to-body>
      <el-form :inline="true">
        <el-form-item :label="t('page.i18n.translation.language')">
          <el-select v-model="missingQuery.languageCode" clearable :placeholder="t('common.all')" style="width: 150px" @change="loadMissing">
            <el-option
              v-for="item in languages"
              :key="item.languageCode"
              :label="item.languageName"
              :value="item.languageCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.i18n.translation.namespace')">
          <el-input v-model="missingQuery.namespace" clearable style="width: 140px" @keyup.enter="loadMissing" />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadMissing">{{ t('common.search') }}</el-button>
          <el-button v-hasPerm="'platform:i18n:missing'" type="danger" plain @click="handleClearMissing">
            {{ t('page.i18n.translation.clearMissing') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="missingLoading" :data="missingRows" border stripe size="small">
        <el-table-column prop="keyCode" :label="t('page.i18n.translation.keyCode')" min-width="220">
          <template #default="{ row }"><span class="text-mono">{{ row.keyCode }}</span></template>
        </el-table-column>
        <el-table-column prop="languageCode" :label="t('page.i18n.translation.language')" width="100" />
        <el-table-column prop="namespace" :label="t('page.i18n.translation.namespace')" width="110" />
        <el-table-column prop="hitCount" :label="t('page.i18n.translation.hitCount')" width="90" align="center" />
        <el-table-column prop="lastSeen" :label="t('page.i18n.translation.lastSeen')" width="160" />
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="missingQuery.pageNum"
          v-model:page-size="missingQuery.pageSize"
          :total="missingTotal"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadMissing"
          @current-change="loadMissing"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, DataAnalysis, Download, Plus, Refresh, Search, Upload, Warning } from '@element-plus/icons-vue'
import {
  addKey,
  batchSaveValues,
  clearMissing,
  deleteKey,
  exportValues,
  getCoverage,
  importValues,
  languageOptions,
  listKeys,
  listMissing,
  updateKey
} from '@/api/i18n'

/**
 * 翻译维护。
 *
 * <p>编辑只改本地草稿，点“保存草稿”后写库；发布在“语言包”页面完成。
 * 保存时只提交本页改动过的单元格，避免把整页译文都重新标成草稿。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const languages = ref([])
const namespaces = ref([])
const query = reactive({ pageNum: 1, pageSize: 20, namespace: '', keyword: '', status: '' })
const dirty = ref({})

const dirtyCount = computed(() => Object.keys(dirty.value).length)

async function loadLanguages() {
  languages.value = await languageOptions()
}

async function loadList() {
  loading.value = true
  try {
    const data = await listKeys({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      namespace: query.namespace || undefined,
      keyword: query.keyword || undefined,
      status: query.status || undefined
    })
    rows.value = (data.records || []).map((item) => ({ ...item, values: item.values || {} }))
    total.value = Number(data.total || 0)
    dirty.value = {}
  } finally {
    loading.value = false
  }
}

async function loadNamespaces() {
  if (!languages.value.length) return
  try {
    const data = await getCoverage({ languageCode: languages.value[0].languageCode })
    namespaces.value = (data || []).map((item) => item.namespace)
  } catch (e) {
    namespaces.value = []
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.namespace = ''
  query.keyword = ''
  query.status = ''
  handleSearch()
}

function markDirty(row, languageCode) {
  dirty.value[`${row.keyId}::${languageCode}`] = true
}

onMounted(async () => {
  await loadLanguages().catch(() => {
    languages.value = []
  })
  await Promise.all([loadList(), loadNamespaces()])
})

/* ==================== 保存草稿 ==================== */

async function saveValues() {
  const items = []
  for (const key of Object.keys(dirty.value)) {
    const [keyId, languageCode] = key.split('::')
    const row = rows.value.find((item) => String(item.keyId) === keyId)
    if (!row) continue
    items.push({ keyId, languageCode, valueText: row.values[languageCode] ?? '' })
  }
  if (!items.length) return
  saving.value = true
  try {
    await batchSaveValues(items)
    ElMessage.success(t('page.i18n.translation.saveDraftSuccess'))
    await loadList()
  } finally {
    saving.value = false
  }
}

/* ==================== 翻译键 ==================== */

const keyDialogVisible = ref(false)
const keySaving = ref(false)
const isEditKey = ref(false)
const editingKeyId = ref('')
const keyFormRef = ref(null)
const emptyKeyForm = () => ({
  namespace: '',
  keyCode: '',
  defaultValue: '',
  moduleCode: '',
  status: '0',
  remark: ''
})
const keyForm = reactive(emptyKeyForm())
const keyRules = {
  namespace: [{ required: true, message: t('page.i18n.translation.namespaceRequired'), trigger: 'blur' }],
  keyCode: [{ required: true, message: t('page.i18n.translation.keyRequired'), trigger: 'blur' }]
}

function openAddKey() {
  Object.assign(keyForm, emptyKeyForm())
  keyForm.namespace = query.namespace || ''
  editingKeyId.value = ''
  isEditKey.value = false
  keyDialogVisible.value = true
}

function openEditKey(row) {
  Object.assign(keyForm, {
    namespace: row.namespace,
    keyCode: row.keyCode,
    defaultValue: row.defaultValue || '',
    moduleCode: row.moduleCode || '',
    status: row.status || '0',
    remark: row.remark || ''
  })
  editingKeyId.value = row.keyId
  isEditKey.value = true
  keyDialogVisible.value = true
}

async function submitKey() {
  if (!keyFormRef.value) return
  await keyFormRef.value.validate()
  keySaving.value = true
  try {
    if (isEditKey.value) {
      await updateKey(editingKeyId.value, { ...keyForm })
    } else {
      await addKey({ ...keyForm })
    }
    ElMessage.success(isEditKey.value ? t('common.updateSuccess') : t('common.addSuccess'))
    keyDialogVisible.value = false
    await Promise.all([loadList(), loadNamespaces()])
  } finally {
    keySaving.value = false
  }
}

async function handleDeleteKey(row) {
  await ElMessageBox.confirm(
    t('page.i18n.translation.deleteKeyConfirm', { key: row.keyCode }),
    t('common.deleteTitle'),
    { type: 'warning' }
  )
  await deleteKey(row.keyId)
  ElMessage.success(t('common.deleteSuccess'))
  loadList()
}

/* ==================== CSV ==================== */

async function handleImport(options) {
  importing.value = true
  try {
    const result = await importValues(options.file)
    const text = t('page.i18n.translation.importResult', {
      total: result.total,
      success: result.success,
      skipped: result.skipped,
      failed: result.failed
    })
    const detail = (result.errors || []).slice(0, 10).join('\n')
    await ElMessageBox.alert(detail ? `${text}\n\n${detail}` : text, t('page.i18n.translation.import'), {
      type: result.failed ? 'warning' : 'success'
    })
    await Promise.all([loadList(), loadNamespaces()])
  } finally {
    importing.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportValues({
      namespace: query.namespace || undefined,
      languages: languages.value.map((item) => item.languageCode)
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `i18n-${query.namespace || 'all'}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

/* ==================== 覆盖率 ==================== */

const coverageVisible = ref(false)
const coverageLoading = ref(false)
const coverageRows = ref([])
const coverageLanguage = ref('')

function openCoverage() {
  coverageLanguage.value = languages.value[0]?.languageCode || ''
  coverageVisible.value = true
  loadCoverage()
}

async function loadCoverage() {
  if (!coverageLanguage.value) return
  coverageLoading.value = true
  try {
    coverageRows.value = await getCoverage({ languageCode: coverageLanguage.value })
  } finally {
    coverageLoading.value = false
  }
}

/* ==================== 缺失记录 ==================== */

const missingVisible = ref(false)
const missingLoading = ref(false)
const missingRows = ref([])
const missingTotal = ref(0)
const missingQuery = reactive({ pageNum: 1, pageSize: 20, languageCode: '', namespace: '' })

function openMissing() {
  missingVisible.value = true
  loadMissing()
}

async function loadMissing() {
  missingLoading.value = true
  try {
    const data = await listMissing({
      pageNum: missingQuery.pageNum,
      pageSize: missingQuery.pageSize,
      languageCode: missingQuery.languageCode || undefined,
      namespace: missingQuery.namespace || undefined
    })
    missingRows.value = data.records || []
    missingTotal.value = Number(data.total || 0)
  } finally {
    missingLoading.value = false
  }
}

async function handleClearMissing() {
  await ElMessageBox.confirm(t('page.i18n.translation.clearMissingConfirm'), t('common.deleteTitle'), {
    type: 'warning'
  })
  await clearMissing({
    languageCode: missingQuery.languageCode || undefined,
    namespace: missingQuery.namespace || undefined
  })
  ElMessage.success(t('common.deleteSuccess'))
  loadMissing()
}
</script>
