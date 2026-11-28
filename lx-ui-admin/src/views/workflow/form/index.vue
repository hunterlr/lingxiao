<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.workflow.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.workflow.form.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="loadList"
            @clear="loadList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.workflow.form.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:workflow:form:add'" type="primary" :icon="Plus" @click="openAdd">
            {{ t('page.workflow.form.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="formId">
        <el-table-column prop="name" :label="t('page.workflow.form.name')" min-width="180" />
        <el-table-column prop="formKey" :label="t('page.workflow.form.key')" min-width="160">
          <template #default="{ row }"><span class="text-mono">{{ row.formKey }}</span></template>
        </el-table-column>
        <el-table-column :label="t('page.workflow.version')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.version > 0" type="success" size="small" effect="plain">v{{ row.version }}</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">{{ t('page.workflow.form.neverPublished') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.workflow.form.draftState')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.changed" type="warning" size="small" effect="plain">
              {{ t('page.workflow.form.unpublished') }}
            </el-tag>
            <span v-else class="text-secondary">{{ t('page.workflow.form.published') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('common.updateTime')" width="170">
          <template #default="{ row }">{{ row.updateTime || row.createTime }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:workflow:form:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:workflow:form:publish'" link type="success" @click="handlePublish(row)">
                {{ t('page.workflow.form.publish') }}
              </el-button>
              <el-button v-hasPerm="'platform:workflow:form:remove'" link type="danger" @click="handleDelete(row)">
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <el-drawer
      v-model="editVisible"
      :title="isEdit ? t('page.workflow.form.edit') : t('page.workflow.form.add')"
      size="720px"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="90px" class="form-basic">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="t('page.workflow.form.key')" required>
              <el-input v-model="form.formKey" maxlength="64" :disabled="isEdit && version > 0" placeholder="leave_form" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.workflow.form.name')" required>
              <el-input v-model="form.name" maxlength="64" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('page.workflow.remark')">
          <el-input v-model="form.remark" maxlength="255" />
        </el-form-item>
      </el-form>

      <div class="field-header">
        <span class="field-title">{{ t('page.workflow.form.fields') }}</span>
        <span class="text-secondary">{{ t('page.workflow.form.fieldHint') }}</span>
        <el-button size="small" :icon="Plus" @click="addField">{{ t('page.workflow.form.addField') }}</el-button>
      </div>

      <div v-for="(field, index) in form.fields" :key="index" class="field-card">
        <el-row :gutter="8">
          <el-col :span="7">
            <el-input v-model="field.key" :placeholder="t('page.workflow.form.keyPlaceholder')">
              <template #prepend>{{ t('page.workflow.form.key') }}</template>
            </el-input>
          </el-col>
          <el-col :span="7">
            <el-input v-model="field.label" :placeholder="t('page.workflow.form.labelPlaceholder')">
              <template #prepend>{{ t('page.workflow.form.label') }}</template>
            </el-input>
          </el-col>
          <el-col :span="6">
            <el-select v-model="field.type" style="width: 100%">
              <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-col>
          <el-col :span="4" class="field-actions">
            <el-switch v-model="field.required" :active-text="t('page.workflow.form.required')" />
          </el-col>
        </el-row>

        <el-row :gutter="8" class="field-props">
          <el-col v-if="field.type === 'input' || field.type === 'textarea'" :span="6">
            <el-input-number v-model="field.maxLength" :min="1" :max="20000" :controls="false" style="width: 100%">
              <template #prefix>{{ t('page.workflow.form.maxLength') }}</template>
            </el-input-number>
          </el-col>
          <el-col v-if="field.type === 'number'" :span="6">
            <el-input-number v-model="field.min" :controls="false" style="width: 100%" placeholder="min" />
          </el-col>
          <el-col v-if="field.type === 'number'" :span="6">
            <el-input-number v-model="field.max" :controls="false" style="width: 100%" placeholder="max" />
          </el-col>
          <el-col v-if="field.type === 'number'" :span="6">
            <el-input-number v-model="field.precision" :min="0" :max="6" :controls="false" style="width: 100%">
              <template #prefix>{{ t('page.workflow.form.precision') }}</template>
            </el-input-number>
          </el-col>
          <el-col v-if="field.type === 'user'" :span="8">
            <el-switch v-model="field.multiple" :active-text="t('page.workflow.form.multiple')" />
          </el-col>
          <el-col :span="24" class="field-ops">
            <el-button link :icon="Top" :disabled="index === 0" @click="move(index, -1)" />
            <el-button link :icon="Bottom" :disabled="index === form.fields.length - 1" @click="move(index, 1)" />
            <el-button link type="danger" :icon="Delete" @click="removeField(index)" />
          </el-col>
        </el-row>

        <div v-if="field.type === 'select' || field.type === 'radio'" class="field-options">
          <el-input
            v-model="field.optionsText"
            type="textarea"
            :rows="2"
            :placeholder="t('page.workflow.form.optionsPlaceholder')"
          />
        </div>
      </div>

      <div class="preview-block">
        <div class="field-header">
          <span class="field-title">{{ t('page.workflow.form.preview') }}</span>
          <el-button size="small" @click="resetPreview">{{ t('page.workflow.form.resetPreview') }}</el-button>
        </div>
        <WorkflowFormRenderer :schema="previewSchema" :model-value="previewModel" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bottom, Delete, Plus, Refresh, Search, Top } from '@element-plus/icons-vue'
import { addForm, getForm, listForms, publishForm, removeForm, updateForm } from '@/api/workflow'
import WorkflowFormRenderer from '@/components/WorkflowFormRenderer.vue'

/**
 * 表单管理：字段列表编辑器 + 实时预览，不做拖拽画布。
 *
 * <p>编辑改的是草稿，发布才生成快照；发起流程只认已发布快照。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const types = computed(() => [
  { value: 'input', label: t('page.workflow.form.typeInput') },
  { value: 'textarea', label: t('page.workflow.form.typeTextarea') },
  { value: 'number', label: t('page.workflow.form.typeNumber') },
  { value: 'select', label: t('page.workflow.form.typeSelect') },
  { value: 'radio', label: t('page.workflow.form.typeRadio') },
  { value: 'date', label: t('page.workflow.form.typeDate') },
  { value: 'datetime', label: t('page.workflow.form.typeDatetime') },
  { value: 'switch', label: t('page.workflow.form.typeSwitch') },
  { value: 'user', label: t('page.workflow.form.typeUser') }
])

async function loadList() {
  loading.value = true
  try {
    const data = await listForms({
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

/* ==================== 编辑 ==================== */
const editVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const version = ref(0)
const form = reactive({ formKey: '', name: '', remark: '', fields: [] })
const previewModel = ref({})

const emptyField = () => ({ key: '', label: '', type: 'input', required: false, multiple: false, optionsText: '' })

const previewSchema = computed(() => ({
  fields: form.fields
    .filter((field) => field.key && field.label)
    .map((field) => ({
      key: field.key,
      label: field.label,
      type: field.type,
      required: field.required,
      multiple: field.multiple,
      maxLength: field.maxLength,
      min: field.min,
      max: field.max,
      precision: field.precision,
      options: parseOptions(field.optionsText)
    }))
}))

function parseOptions(text) {
  return String(text || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const index = line.indexOf(':')
      if (index < 0) return { label: line, value: line }
      return { label: line.slice(0, index).trim(), value: line.slice(index + 1).trim() }
    })
}

function stringifyOptions(options) {
  return (options || []).map((option) => `${option.label}:${option.value}`).join('\n')
}

function openAdd() {
  isEdit.value = false
  editingId.value = null
  version.value = 0
  Object.assign(form, { formKey: '', name: '', remark: '', fields: [emptyField()] })
  resetPreview()
  editVisible.value = true
}

async function openEdit(row) {
  const detail = await getForm(row.formId)
  isEdit.value = true
  editingId.value = row.formId
  version.value = detail?.version || 0
  Object.assign(form, {
    formKey: detail?.formKey || '',
    name: detail?.name || '',
    remark: detail?.remark || '',
    fields: (detail?.schema?.fields || []).map((field) => ({
      key: field.key,
      label: field.label,
      type: field.type,
      required: !!field.required,
      multiple: !!field.multiple,
      maxLength: field.maxLength,
      min: field.min != null ? Number(field.min) : undefined,
      max: field.max != null ? Number(field.max) : undefined,
      precision: field.precision,
      optionsText: stringifyOptions(field.options)
    }))
  })
  if (!form.fields.length) form.fields.push(emptyField())
  resetPreview()
  editVisible.value = true
}

function addField() {
  form.fields.push(emptyField())
}

function removeField(index) {
  form.fields.splice(index, 1)
}

function move(index, delta) {
  const target = index + delta
  if (target < 0 || target >= form.fields.length) return
  const [item] = form.fields.splice(index, 1)
  form.fields.splice(target, 0, item)
}

function resetPreview() {
  previewModel.value = {}
}

function buildSchema() {
  const keys = new Set()
  for (const field of form.fields) {
    if (!field.key || !field.label) {
      throw new Error(t('page.workflow.form.fieldsRequired'))
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(field.key)) {
      throw new Error(t('page.workflow.form.keyInvalid', { key: field.key }))
    }
    if (keys.has(field.key)) {
      throw new Error(t('page.workflow.form.keyDuplicate', { key: field.key }))
    }
    keys.add(field.key)
    if ((field.type === 'select' || field.type === 'radio') && !parseOptions(field.optionsText).length) {
      throw new Error(t('page.workflow.form.optionsRequired', { label: field.label }))
    }
  }
  return {
    fields: form.fields.map((field) => ({
      key: field.key,
      label: field.label,
      type: field.type,
      required: !!field.required,
      multiple: field.type === 'user' ? !!field.multiple : undefined,
      maxLength: field.type === 'input' || field.type === 'textarea' ? field.maxLength || undefined : undefined,
      min: field.type === 'number' ? field.min : undefined,
      max: field.type === 'number' ? field.max : undefined,
      precision: field.type === 'number' ? field.precision : undefined,
      options: field.type === 'select' || field.type === 'radio' ? parseOptions(field.optionsText) : undefined
    }))
  }
}

async function submitForm() {
  let schema
  try {
    schema = buildSchema()
  } catch (e) {
    ElMessage.warning(e.message)
    return
  }
  saving.value = true
  try {
    const payload = { formKey: form.formKey, name: form.name, remark: form.remark, schema }
    if (isEdit.value) {
      await updateForm(editingId.value, payload)
    } else {
      await addForm(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    editVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

/* ==================== 发布 / 删除 ==================== */
async function handlePublish(row) {
  await ElMessageBox.confirm(
    t('page.workflow.form.publishConfirm', { name: row.name }),
    t('common.confirm'),
    { type: 'warning' }
  )
  const next = await publishForm(row.formId)
  ElMessage.success(t('page.workflow.form.publishSuccess', { version: next }))
  loadList()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    t('page.workflow.form.deleteConfirm', { name: row.name }),
    t('common.deleteTitle'),
    { type: 'warning' }
  )
  await removeForm(row.formId)
  ElMessage.success(t('common.deleteSuccess'))
  loadList()
}
</script>

<style scoped>
.form-basic {
  margin-bottom: 8px;
}

.field-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}

.field-title {
  font-weight: 600;
}

.field-header .el-button {
  margin-left: auto;
}

.field-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.field-props {
  margin-top: 8px;
}

.field-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.field-ops {
  display: flex;
  justify-content: flex-end;
}

.field-options {
  margin-top: 8px;
}

.preview-block {
  margin-top: 16px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>