<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="表名称">
          <el-input
            v-model="query.tableName"
            placeholder="输入表名"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="表描述">
          <el-input
            v-model="query.tableComment"
            placeholder="输入表注释"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            导入表 → 配置字段 → 预览 → 下载 ZIP；生成物需要放进目标模块后编译。
            生成器只读表结构，不会修改数据库。
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:tool:gen:import'"
            type="primary"
            :icon="Upload"
            @click="openImport"
          >
            导入表
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="tableId">
        <el-table-column prop="tableName" label="表名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-mono">{{ row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" label="表描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="className" label="实体类" min-width="140" show-overflow-tooltip />
        <el-table-column prop="moduleName" label="模块" width="100" />
        <el-table-column prop="businessName" label="业务名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="functionName" label="功能名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="functionAuthor" label="作者" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="导入时间" width="165" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:tool:gen:preview'"
                link
                type="primary"
                @click="openPreview(row)"
              >
                预览
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:edit'"
                link
                type="primary"
                @click="openConfig(row)"
              >
                配置
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:download'"
                link
                type="primary"
                @click="handleDownload(row)"
              >
                下载
              </el-button>
              <el-button
                v-hasPerm="'platform:tool:gen:remove'"
                link
                type="danger"
                @click="handleDelete(row)"
              >
                删除
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
      title="导入数据库表"
      width="780px"
      append-to-body
      destroy-on-close
      @closed="handleImportClosed"
    >
      <div class="import-toolbar">
        <el-input
          v-model="dbKeyword"
          placeholder="搜索表名 / 表注释"
          clearable
          style="width: 240px"
          @keyup.enter="loadDbTables"
          @clear="loadDbTables"
        />
        <el-button :icon="Search" @click="loadDbTables">搜索</el-button>
        <span class="text-secondary">
          已导入的表会置灰；一次最多导入 50 张，无主键 / 复合主键会被跳过。
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
          成功导入 {{ importResult.imported }} 张表
          <span v-if="importResult.skipped.length">
            ，{{ importResult.skipped.length }} 张被跳过
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
        <el-table-column prop="tableName" label="表名称" min-width="220">
          <template #default="{ row }">
            <span class="text-mono">{{ row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" label="表注释" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.imported" size="small" effect="plain" type="info">已导入</el-tag>
            <el-tag v-else size="small" effect="plain" type="success">可导入</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <span class="text-secondary selected-tip">已选 {{ selectedTables.length }} 张</span>
          <el-button @click="importVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="!selectedTables.length"
            @click="submitImport"
          >
            导入
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
          <span>生成配置</span>
          <span class="text-secondary text-mono">{{ current.tableName }}</span>
        </div>
      </template>

      <el-tabs v-model="configTab">
        <el-tab-pane label="基本信息" name="base">
          <el-form
            ref="configFormRef"
            v-loading="configLoading"
            :model="current"
            :rules="configRules"
            label-width="120px"
            class="config-form"
          >
            <el-form-item label="表名称">
              <el-input :model-value="current.tableName" disabled />
            </el-form-item>
            <el-form-item label="表描述" prop="tableComment">
              <el-input v-model="current.tableComment" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="实体类名" prop="className">
              <el-input v-model="current.className" placeholder="PascalCase，例如 BizOrder" />
            </el-form-item>
            <el-form-item label="模块名" prop="moduleName">
              <el-input v-model="current.moduleName" placeholder="小写，例如 biz；决定包名与接口前缀" />
            </el-form-item>
            <el-form-item label="业务名" prop="businessName">
              <el-input v-model="current.businessName" placeholder="camelCase，例如 bizOrder" />
            </el-form-item>
            <el-form-item label="功能名" prop="functionName">
              <el-input v-model="current.functionName" maxlength="64" placeholder="菜单标题，例如 订单管理" />
            </el-form-item>
            <el-form-item label="作者" prop="functionAuthor">
              <el-input v-model="current.functionAuthor" maxlength="64" />
            </el-form-item>
            <el-form-item label="前端路由" prop="genPath">
              <el-input v-model="current.genPath" placeholder="例如 /biz/order" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="configSaving" @click="saveBase">保存基本信息</el-button>
              <el-button @click="configVisible = false">关闭</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="字段配置" name="columns">
          <div class="column-toolbar">
            <span class="text-secondary">
              列名 / 主键 / 自增来自数据库，不可修改；其余字段决定生成代码里的字段、表单控件和查询条件。
            </span>
            <el-button type="primary" :loading="configSaving" @click="saveColumns">保存字段配置</el-button>
          </div>

          <el-table
            v-loading="configLoading"
            :data="columns"
            border
            size="small"
            row-key="columnId"
            max-height="560"
          >
            <el-table-column prop="columnName" label="列名" width="160" fixed>
              <template #default="{ row }">
                <span class="text-mono">{{ row.columnName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="注释" width="170">
              <template #default="{ row }">
                <el-input v-model="row.columnComment" size="small" maxlength="500" />
              </template>
            </el-table-column>
            <el-table-column label="Java 字段" width="150">
              <template #default="{ row }">
                <el-input v-model="row.javaField" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="Java 类型" width="140">
              <template #default="{ row }">
                <el-select v-model="row.javaType" size="small" style="width: 100%">
                  <el-option v-for="type in javaTypes" :key="type" :label="type" :value="type" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isRequired" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="新增" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isInsert" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="编辑" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isEdit" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="列表" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isList" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="查询" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isQuery" active-value="1" inactive-value="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="查询方式" width="120">
              <template #default="{ row }">
                <el-select
                  v-model="row.queryType"
                  size="small"
                  :disabled="row.isQuery !== '1'"
                  style="width: 100%"
                >
                  <el-option v-for="item in queryTypes" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="表单控件" width="140">
              <template #default="{ row }">
                <el-select v-model="row.htmlType" size="small" style="width: 100%">
                  <el-option v-for="item in htmlTypes" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="字典类型" width="150">
              <template #default="{ row }">
                <el-input v-model="row.dictType" size="small" placeholder="可空" />
              </template>
            </el-table-column>
            <el-table-column label="排序" width="100">
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
            <el-table-column label="标记" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isPk === '1'" size="small" effect="plain" type="warning">主键</el-tag>
                <el-tag v-if="row.isIncrement === '1'" size="small" effect="plain" type="info">自增</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 预览代码 -->
    <el-dialog
      v-model="previewVisible"
      title="代码预览"
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
        <el-empty v-else-if="!previewLoading" description="没有可预览的文件" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button :icon="CopyDocument" @click="copyPreview">复制当前文件</el-button>
          <el-button @click="previewVisible = false">关闭</el-button>
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

/**
 * 代码生成器。
 *
 * <p>页面只负责“选表 / 改配置 / 看结果”，不假装知道生成物长什么样：
 * 预览内容全部来自后端 Velocity 渲染，下载的 ZIP 与预览使用同一份变量，
 * 避免“预览对了、下载错了”的双份实现。</p>
 */

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
    ElMessage.warning('请先选择要导入的表')
    return
  }
  importing.value = true
  try {
    const result = await importGenTables(selectedTables.value.map((row) => row.tableName))
    importResult.value = result
    if (result.imported > 0) {
      ElMessage.success(`成功导入 ${result.imported} 张表`)
      await loadList()
    } else {
      ElMessage.warning('没有表被导入，请查看跳过原因')
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
  className: [{ required: true, message: '请输入实体类名', trigger: 'blur' }],
  moduleName: [{ required: true, message: '请输入模块名', trigger: 'blur' }],
  businessName: [{ required: true, message: '请输入业务名', trigger: 'blur' }],
  functionName: [{ required: true, message: '请输入功能名', trigger: 'blur' }],
  genPath: [{ required: true, message: '请输入前端路由', trigger: 'blur' }]
}

const javaTypes = [
  'String', 'Long', 'Integer', 'BigDecimal', 'Double', 'Float',
  'Boolean', 'LocalDate', 'LocalDateTime', 'LocalTime', 'byte[]'
]

const queryTypes = [
  { value: 'EQ', label: '等于 EQ' },
  { value: 'LIKE', label: '模糊 LIKE' },
  { value: 'GT', label: '大于 GT' },
  { value: 'GE', label: '大于等于 GE' },
  { value: 'LT', label: '小于 LT' },
  { value: 'LE', label: '小于等于 LE' }
]

const htmlTypes = [
  { value: 'input', label: '输入框' },
  { value: 'textarea', label: '多行文本' },
  { value: 'select', label: '下拉框' },
  { value: 'radio', label: '单选框' },
  { value: 'checkbox', label: '复选框' },
  { value: 'datetime', label: '日期时间' },
  { value: 'upload', label: '上传' },
  { value: 'editor', label: '富文本' }
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
      ElMessage.success('基本信息已保存')
      await loadList()
    } finally {
      configSaving.value = false
    }
  })
}

async function saveColumns() {
  if (!columns.value.length) {
    ElMessage.warning('没有字段配置可保存，请重新导入')
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
    ElMessage.success('字段配置已保存')
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
    ElMessage.warning('没有可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(activePreview.value.content)
    ElMessage.success('已复制当前文件内容')
  } catch (e) {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
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
  ElMessage.success('ZIP 已开始下载')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除表「${row.tableName}」的生成配置吗？只会删除 gen_table / gen_table_column 记录，不会动数据库原表。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteGenTable(row.tableId)
  ElMessage.success('已删除生成配置')
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
