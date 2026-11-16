<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.gen.tableName')">
          <el-input
            v-model="query.tableName"
            :placeholder="t('page.gen.namePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.gen.tableComment')">
          <el-input
            v-model="query.tableComment"
            :placeholder="t('page.gen.commentPlaceholder')"
            clearable
            style="width: 180px"
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
            {{ t('page.gen.hint') }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:tool:gen:import'"
            type="primary"
            :icon="Upload"
            @click="openImport"
          >
            {{ t('page.gen.import') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="tableId">
        <el-table-column prop="tableName" :label="t('page.gen.tableName')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-mono">{{ row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" :label="t('page.gen.tableComment')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="className" :label="t('page.gen.entity')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="moduleName" :label="t('page.gen.module')" width="100" />
        <el-table-column prop="businessName" :label="t('page.gen.businessName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="functionName" :label="t('page.gen.functionName')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="functionAuthor" :label="t('page.gen.author')" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" :label="t('page.gen.importTime')" width="165" />
        <el-table-column :label="t('common.operation')" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:tool:gen:preview'"
                link
                type="primary"
                @click="openPreview(row)"
              >
                {{ t('page.gen.preview') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:edit'"
                link
                type="primary"
                @click="openConfig(row)"
              >
                {{ t('page.gen.config') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:download'"
                link
                type="primary"
                @click="handleDownload(row)"
              >
                {{ t('page.gen.download') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:remove'"
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

    <!-- 导入表 -->
    <el-dialog
      v-model="importVisible"
      :title="t('page.gen.importTitle')"
      width="780px"
      append-to-body
      destroy-on-close
      @closed="handleImportClosed"
    >
      <div class="import-toolbar">
        <el-input
          v-model="dbKeyword"
          :placeholder="t('page.gen.searchTable')"
          clearable
          style="width: 240px"
          @keyup.enter="loadDbTables"
          @clear="loadDbTables"
        />
        <el-button :icon="Search" @click="loadDbTables">{{ t('common.search') }}</el-button>
        <span class="text-secondary">
          {{ t('page.gen.importHint') }}
        </span>
      </div>

      <el-alert
        v-if="importResult"
        :type="importResult.skipped.length ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="import-result"
      >
        <template #title>
          {{ t('page.gen.importedCount', { count: importResult.imported }) }}
          <span v-if="importResult.skipped.length">
            {{ t('page.gen.skippedCount', { count: importResult.skipped.length }) }}
          </span>
        </template>
        <ul v-if="importResult.skipped.length" class="skip-list">
          <li v-for="(item, index) in importResult.skipped" :key="index">{{ item }}</li>
        </ul>
      </el-alert>

      <el-table
        v-loading="dbLoading"
        :data="dbRows"
        border
        stripe
        max-height="420"
        row-key="tableName"
        @selection-change="handleDbSelectionChange"
      >
        <el-table-column type="selection" width="50" :selectable="(row) => !row.imported" />
        <el-table-column prop="tableName" :label="t('page.gen.tableName')" min-width="220">
          <template #default="{ row }">
            <span class="text-mono">{{ row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" :label="t('page.gen.tableCommentCol')" min-width="220" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.imported" size="small" effect="plain" type="info">{{ t('page.gen.imported') }}</el-tag>
            <el-tag v-else size="small" effect="plain" type="success">{{ t('page.gen.importable') }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <span class="text-secondary selected-tip">{{ t('page.gen.selectedCount', { count: selectedTables.length }) }}</span>
          <el-button @click="importVisible = false">{{ t('common.close') }}</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="!selectedTables.length"
            @click="submitImport"
          >
            {{ t('page.gen.importBtn') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 表配置 / 字段配置 -->
    <el-drawer
      v-model="configVisible"
      size="78%"
      append-to-body
      destroy-on-close
      @closed="handleConfigClosed"
    >
      <template #header>
        <div class="config-header">
          <span>{{ t('page.gen.configTitle') }}</span>
          <span class="text-secondary text-mono">{{ current.tableName }}</span>
        </div>
      </template>

      <el-tabs v-model="configTab">
        <el-tab-pane :label="t('page.gen.tabBase')" name="base">
          <el-form
            ref="configFormRef"
            v-loading="configLoading"
            :model="current"
            :rules="configRules"
            label-width="120px"
            class="config-form"
          >
            <el-form-item :label="t('page.gen.tableName')">
              <el-input :model-value="current.tableName" disabled />
            </el-form-item>
            <el-form-item :label="t('page.gen.tableComment')" prop="tableComment">
              <el-input v-model="current.tableComment" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('page.gen.className')" prop="className">
              <el-input v-model="current.className" :placeholder="t('page.gen.classNamePlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('page.gen.moduleName')" prop="moduleName">
              <el-input v-model="current.moduleName" :placeholder="t('page.gen.modulePlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('page.gen.businessName')" prop="businessName">
              <el-input v-model="current.businessName" :placeholder="t('page.gen.businessPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('page.gen.functionName')" prop="functionName">
              <el-input v-model="current.functionName" maxlength="64" :placeholder="t('page.gen.functionPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('page.gen.author')" prop="functionAuthor">
              <el-input v-model="current.functionAuthor" maxlength="64" />
            </el-form-item>
            <el-form-item :label="t('page.gen.genPath')" prop="genPath">
              <el-input v-model="current.genPath" :placeholder="t('page.gen.genPathPlaceholder')" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="configSaving" @click="saveBase">{{ t('page.gen.saveBase') }}</el-button>
              <el-button @click="configVisible = false">{{ t('common.close') }}</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="t('page.gen.tabColumns')" name="columns">
          <div class="column-toolbar">
            <span class="text-secondary">
              {{ t('page.gen.columnHint') }}
            </span>
            <el-button type="primary" :loading="configSaving" @click="saveColumns">{{ t('page.gen.saveColumns') }}</el-button>
          </div>

          <el-table
            v-loading="configLoading"
            :data="columns"
            border
            size="small"
            row-key="columnId"
            max-height="560"
          >
            <el-table-column prop="columnName" :label="t('page.gen.columnName')" width="160" fixed>
              <template #default="{ row }">
                <span class="text-mono">{{ row.columnName }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.columnComment')" width="170">
              <template #default="{ row }">
                <el-input v-model="row.columnComment" size="small" maxlength="500" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.javaField')" width="150">
              <template #default="{ row }">
                <el-input v-model="row.javaField" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.javaType')" width="140">
              <template #default="{ row }">
                <el-select v-model="row.javaType" size="small" style="width: 100%">
                  <el-option v-for="type in javaTypes" :key="type" :label="type" :value="type" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.required')" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isRequired" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.insert')" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isInsert" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.edit')" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isEdit" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.list')" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isList" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.query')" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isQuery" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.queryType')" width="120">
              <template #default="{ row }">
                <el-select
                  v-model="row.queryType"
                  size="small"
                  :disabled="row.isQuery !== '1'"
                  style="width: 100%"
                >
                  <el-option v-for="item in queryTypes" :key="item.value" :label="t('page.gen.q_' + item.key)" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.htmlType')" width="140">
              <template #default="{ row }">
                <el-select v-model="row.htmlType" size="small" style="width: 100%">
                  <el-option v-for="item in htmlTypes" :key="item.value" :label="t('page.gen.h_' + item.key)" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.dictType')" width="150">
              <template #default="{ row }">
                <el-input v-model="row.dictType" size="small" :placeholder="t('page.gen.dictPlaceholder')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.sort')" width="100">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.sort"
                  size="small"
                  :min="0"
                  :max="9999"
                  controls-position="right"
                  style="width: 80px"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('page.gen.marks')" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isPk === '1'" size="small" effect="plain" type="warning">{{ t('page.gen.pk') }}</el-tag>
                <el-tag v-if="row.isIncrement === '1'" size="small" effect="plain" type="info">{{ t('page.gen.increment') }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 预览代码 -->
    <el-dialog
      v-model="previewVisible"
      :title="t('page.gen.previewTitle')"
      width="86%"
      append-to-body
      destroy-on-close
      top="4vh"
    >
      <div v-loading="previewLoading" class="preview-wrap">
        <el-tabs v-if="previewFiles.length" v-model="previewTab">
          <el-tab-pane
            v-for="file in previewFiles"
            :key="file.path"
            :label="file.name"
            :name="file.path"
          >
            <div class="preview-meta">
              <span class="text-mono text-secondary">{{ file.path }}</span>
              <span class="text-secondary">{{ file.language }}</span>
            </div>
          </el-tab-pane>
        </el-tabs>
        <pre v-if="activePreview" class="code-preview">{{ activePreview.content }}</pre>
        <el-empty v-else-if="!previewLoading" :description="t('page.gen.emptyPreview')" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button :icon="CopyDocument" @click="copyPreview">{{ t('page.gen.copyCurrent') }}</el-button>
          <el-button @click="previewVisible = false">{{ t('common.close') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  deleteGenTable,
  downloadGenCode,
  getGenTable,
  importGenTables,
  listDbTables,
  listGenTables,
  previewGenCode,
  updateGenColumns,
  updateGenTable
} from '@/api/gen'
import { useI18n } from 'vue-i18n'

/**
 * 代码生成器。
 *
 * <p>页面只负责“选表 / 改配置 / 看结果”，不假装知道生成物长什么样：
 * 预览内容全部来自后端 Velocity 渲染，下载的 ZIP 与预览使用同一份变量，
 * 避免“预览对了、下载错了”的双份实现。</p>
 */

const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, tableName: '', tableComment: '' })

async function loadList() {
  loading.value = true
  try {
    const data = await listGenTables({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      tableName: query.tableName || undefined,
      tableComment: query.tableComment || undefined
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
  query.tableName = ''
  query.tableComment = ''
  handleSearch()
}

onMounted(loadList)

/* ==================== 导入表 ==================== */
const importVisible = ref(false)
const dbLoading = ref(false)
const importing = ref(false)
const dbKeyword = ref('')
const dbRows = ref([])
const selectedTables = ref([])
const importResult = ref(null)

async function openImport() {
  importResult.value = null
  selectedTables.value = []
  dbKeyword.value = ''
  importVisible.value = true
  await loadDbTables()
}

async function loadDbTables() {
  dbLoading.value = true
  try {
    dbRows.value = await listDbTables(dbKeyword.value || undefined)
  } finally {
    dbLoading.value = false
  }
}

function handleDbSelectionChange(selection) {
  selectedTables.value = selection || []
}

function handleImportClosed() {
  selectedTables.value = []
  importResult.value = null
}

async function submitImport() {
  if (!selectedTables.value.length) {
    ElMessage.warning(t('page.gen.selectFirst'))
    return
  }
  importing.value = true
  try {
    const result = await importGenTables(selectedTables.value.map((row) => row.tableName))
    importResult.value = result
    if (result.imported > 0) {
      ElMessage.success(t('page.gen.importSuccess', { count: result.imported }))
      await loadList()
    } else {
      ElMessage.warning(t('page.gen.importNone'))
    }
    await loadDbTables()
    selectedTables.value = []
  } finally {
    importing.value = false
  }
}

/* ==================== 表配置 / 字段配置 ==================== */
const configVisible = ref(false)
const configLoading = ref(false)
const configSaving = ref(false)
const configTab = ref('base')
const configFormRef = ref(null)
const columns = ref([])

const emptyCurrent = () => ({
  tableId: '',
  tableName: '',
  tableComment: '',
  className: '',
  moduleName: '',
  businessName: '',
  functionName: '',
  functionAuthor: '',
  genPath: ''
})

const current = reactive(emptyCurrent())

const configRules = {
  className: [{ required: true, message: t('page.gen.classNameRequired'), trigger: 'blur' }],
  moduleName: [{ required: true, message: t('page.gen.moduleRequired'), trigger: 'blur' }],
  businessName: [{ required: true, message: t('page.gen.businessRequired'), trigger: 'blur' }],
  functionName: [{ required: true, message: t('page.gen.functionRequired'), trigger: 'blur' }],
  genPath: [{ required: true, message: t('page.gen.genPathRequired'), trigger: 'blur' }]
}

const javaTypes = [
  'String', 'Long', 'Integer', 'BigDecimal', 'Double', 'Float',
  'Boolean', 'LocalDate', 'LocalDateTime', 'LocalTime', 'byte[]'
]

const queryTypes = [
  { value: 'EQ', key: 'eq' },
  { value: 'LIKE', key: 'like' },
  { value: 'GT', key: 'gt' },
  { value: 'GE', key: 'ge' },
  { value: 'LT', key: 'lt' },
  { value: 'LE', key: 'le' }
]

const htmlTypes = [
  { value: 'input', key: 'input' },
  { value: 'textarea', key: 'textarea' },
  { value: 'select', key: 'select' },
  { value: 'radio', key: 'radio' },
  { value: 'checkbox', key: 'checkbox' },
  { value: 'datetime', key: 'datetime' },
  { value: 'upload', key: 'upload' },
  { value: 'editor', key: 'editor' }
]

async function openConfig(row) {
  Object.assign(current, emptyCurrent())
  columns.value = []
  configTab.value = 'base'
  configVisible.value = true
  configLoading.value = true
  try {
    const detail = await getGenTable(row.tableId)
    Object.assign(current, {
      tableId: detail.tableId,
      tableName: detail.tableName,
      tableComment: detail.tableComment || '',
      className: detail.className,
      moduleName: detail.moduleName,
      businessName: detail.businessName,
      functionName: detail.functionName,
      functionAuthor: detail.functionAuthor || '',
      genPath: detail.genPath || '/'
    })
    columns.value = detail.columns || []
  } finally {
    configLoading.value = false
  }
}

function handleConfigClosed() {
  configFormRef.value && configFormRef.value.clearValidate()
}

async function saveBase() {
  await configFormRef.value.validate(async (valid) => {
    if (!valid) return
    configSaving.value = true
    try {
      await updateGenTable(current.tableId, {
        tableComment: current.tableComment,
        className: current.className,
        moduleName: current.moduleName,
        businessName: current.businessName,
        functionName: current.functionName,
        functionAuthor: current.functionAuthor,
        genPath: current.genPath
      })
      ElMessage.success(t('page.gen.baseSaved'))
      await loadList()
    } finally {
      configSaving.value = false
    }
  })
}

async function saveColumns() {
  if (!columns.value.length) {
    ElMessage.warning(t('page.gen.noColumns'))
    return
  }
  configSaving.value = true
  try {
    const payload = columns.value.map((column) => ({
      columnId: column.columnId,
      columnComment: column.columnComment,
      javaType: column.javaType,
      javaField: column.javaField,
      isRequired: column.isRequired,
      isInsert: column.isInsert,
      isEdit: column.isEdit,
      isList: column.isList,
      isQuery: column.isQuery,
      queryType: column.queryType,
      htmlType: column.htmlType,
      dictType: column.dictType,
      sort: column.sort
    }))
    await updateGenColumns(current.tableId, payload)
    ElMessage.success(t('page.gen.columnsSaved'))
    const detail = await getGenTable(current.tableId)
    columns.value = detail.columns || []
  } finally {
    configSaving.value = false
  }
}

/* ==================== 预览 / 下载 / 删除 ==================== */
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewFiles = ref([])
const previewTab = ref('')

const activePreview = computed(() => {
  return previewFiles.value.find((file) => file.path === previewTab.value) || null
})

async function openPreview(row) {
  previewFiles.value = []
  previewTab.value = ''
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewFiles.value = await previewGenCode(row.tableId)
    previewTab.value = previewFiles.value.length ? previewFiles.value[0].path : ''
  } finally {
    previewLoading.value = false
  }
}

async function copyPreview() {
  if (!activePreview.value) {
    ElMessage.warning(t('page.gen.noCopy'))
    return
  }
  try {
    await navigator.clipboard.writeText(activePreview.value.content)
    ElMessage.success(t('page.gen.copied'))
  } catch (e) {
    ElMessage.error(t('page.gen.copyFailed'))
  }
}

async function handleDownload(row) {
  const blob = await downloadGenCode(row.tableId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.businessName}-code.zip`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success(t('page.gen.zipStarted'))
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.gen.deleteConfirm', { name: row.tableName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteGenTable(row.tableId)
  ElMessage.success(t('page.gen.deleted'))
  await loadList()
}
</script>

<style scoped>
.import-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.import-result {
  margin-bottom: 12px;
}

.skip-list {
  margin: 6px 0 0;
  padding-left: 18px;
  max-height: 120px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
}

.selected-tip {
  margin-right: auto;
  font-size: 13px;
}

.config-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.config-form {
  max-width: 640px;
}

.column-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.column-toolbar .text-secondary {
  flex: 1;
  min-width: 260px;
  font-size: 13px;
  line-height: 1.6;
}

.preview-wrap {
  min-height: 320px;
}

.preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  margin-bottom: 8px;
}

.code-preview {
  margin: 0;
  max-height: 65vh;
  overflow: auto;
  padding: 12px 14px;
  background: var(--lx-code-bg);
  border: 1px solid var(--lx-border);
  border-radius: 6px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.65;
  white-space: pre;
  tab-size: 4;
}
</style>
