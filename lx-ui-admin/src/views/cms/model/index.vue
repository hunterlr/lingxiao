<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 200px" @change="loadList">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        :title="t('page.cms.model.hint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:model:add'" type="primary" :icon="Plus" @click="openAdd">
          {{ t('page.cms.model.add') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border row-key="modelId">
        <el-table-column prop="modelKey" :label="t('page.cms.model.modelKey')" width="140" />
        <el-table-column prop="modelName" :label="t('page.cms.model.modelName')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('page.cms.model.modelType')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.modelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.cms.model.builtIn')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.builtIn === '1'" type="warning" size="small">{{ t('common.yes') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openFields(row)">
                {{ t('page.cms.model.fieldsTitle') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:model:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:model:remove'" link type="danger" @click="handleDelete(row)">
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.cms.model.edit') : t('page.cms.model.add')"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('page.cms.model.modelKey')" prop="modelKey">
          <el-input v-model="form.modelKey" maxlength="64" :disabled="isBuiltIn" :placeholder="t('page.cms.model.keyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.model.modelName')" prop="modelName">
          <el-input v-model="form.modelName" maxlength="100" :placeholder="t('page.cms.model.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.model.modelType')" prop="modelType">
          <el-select v-model="form.modelType" :disabled="isBuiltIn" style="width: 100%">
            <el-option :label="t('page.cms.model.typeArticle')" value="article" />
            <el-option :label="t('page.cms.model.typeGallery')" value="gallery" />
            <el-option :label="t('page.cms.model.typePage')" value="page" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.status')">
              <el-radio-group v-model="form.status">
                <el-radio value="0">{{ t('common.enabled') }}</el-radio>
                <el-radio value="1">{{ t('common.disabled') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="fieldDrawerVisible" :title="fieldDrawerTitle" size="860px" append-to-body>
      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:field:add'" type="primary" :icon="Plus" @click="openFieldAdd">
          {{ t('page.cms.model.addField') }}
        </el-button>
      </div>
      <el-table v-loading="fieldLoading" :data="fieldRows" border>
        <el-table-column prop="fieldKey" :label="t('page.cms.model.fieldKey')" width="130" />
        <el-table-column prop="fieldName" :label="t('page.cms.model.fieldName')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('page.cms.model.fieldType')" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ fieldTypeLabel(row.fieldType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('page.cms.model.required')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.required === '1'" type="danger" size="small">{{ t('common.yes') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="70" align="center" />
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:field:edit'" link type="primary" @click="openFieldEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:field:remove'" link type="danger" @click="handleFieldDelete(row)">
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog
      v-model="fieldDialogVisible"
      :title="fieldIsEdit ? t('page.cms.model.editField') : t('page.cms.model.addField')"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="fieldFormRef" :model="fieldForm" :rules="fieldRules" label-width="110px">
        <el-form-item :label="t('page.cms.model.fieldKey')" prop="fieldKey">
          <el-input v-model="fieldForm.fieldKey" maxlength="64" :placeholder="t('page.cms.model.fieldKeyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.model.fieldName')" prop="fieldName">
          <el-input v-model="fieldForm.fieldName" maxlength="100" :placeholder="t('page.cms.model.fieldNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.model.fieldType')" prop="fieldType">
          <el-select v-model="fieldForm.fieldType" style="width: 100%">
            <el-option v-for="item in fieldTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="fieldForm.fieldType === 'select' || fieldForm.fieldType === 'checkbox'"
          :label="t('page.cms.model.options')"
        >
          <el-input
            v-model="fieldForm.optionsJson"
            type="textarea"
            :rows="4"
            maxlength="4000"
            :placeholder="t('page.cms.model.optionsPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('page.cms.model.defaultValue')">
          <el-input v-model="fieldForm.defaultValue" maxlength="500" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('page.cms.model.required')">
              <el-radio-group v-model="fieldForm.required">
                <el-radio value="1">{{ t('common.yes') }}</el-radio>
                <el-radio value="0">{{ t('common.no') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="fieldForm.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('common.status')">
              <el-radio-group v-model="fieldForm.status">
                <el-radio value="0">{{ t('common.enabled') }}</el-radio>
                <el-radio value="1">{{ t('common.disabled') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="fieldForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="fieldDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="fieldSaving" @click="submitField">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  addModel,
  addModelField,
  deleteModel,
  deleteModelField,
  listModelFields,
  listModels,
  listSiteOptions,
  updateModel,
  updateModelField
} from '@/api/cms'

/**
 * CMS 内容模型与字段管理。
 *
 * <p>模型是站点级的：文章 / 图集 / 单页共用一套内容表，差异由模型字段定义。
 * 内置模型可以改名称、状态，但不能改标识与类型。</p>
 */
const { t } = useI18n()

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])

const fieldTypes = computed(() => [
  { value: 'text', label: 'text' },
  { value: 'textarea', label: 'textarea' },
  { value: 'richtext', label: 'richtext' },
  { value: 'number', label: 'number' },
  { value: 'date', label: 'date' },
  { value: 'select', label: 'select' },
  { value: 'checkbox', label: 'checkbox' },
  { value: 'image', label: 'image' },
  { value: 'images', label: 'images' },
  { value: 'file', label: 'file' }
])

const typeLabels = {
  article: 'page.cms.model.typeArticle',
  gallery: 'page.cms.model.typeGallery',
  page: 'page.cms.model.typePage'
}

function typeLabel(type) {
  return typeLabels[type] ? t(typeLabels[type]) : type
}

function fieldTypeLabel(type) {
  return type
}

async function loadSites() {
  sites.value = (await listSiteOptions()) || []
  if (!siteId.value && sites.value.length) {
    siteId.value = sites.value[0].siteId
  }
}

async function loadList() {
  if (!siteId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = (await listModels({ siteId: siteId.value })) || []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadSites()
  await loadList()
})

/* ==================== 模型表单 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const isBuiltIn = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  modelKey: '',
  modelName: '',
  modelType: 'article',
  sort: 0,
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  modelKey: [{ required: true, message: t('page.cms.model.keyRequired'), trigger: 'blur' }],
  modelName: [{ required: true, message: t('page.cms.model.nameRequired'), trigger: 'blur' }],
  modelType: [{ required: true, message: t('page.cms.model.typeRequired'), trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
  isBuiltIn.value = false
}

function openAdd() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  isEdit.value = true
  isBuiltIn.value = row.builtIn === '1'
  editingId.value = row.modelId
  Object.assign(form, {
    modelKey: row.modelKey || '',
    modelName: row.modelName || '',
    modelType: row.modelType || 'article',
    sort: row.sort || 0,
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        siteId: siteId.value,
        modelKey: form.modelKey.trim(),
        modelName: form.modelName.trim(),
        modelType: form.modelType,
        sort: form.sort || 0,
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateModel(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addModel(payload)
        ElMessage.success(t('common.addSuccess'))
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
      t('page.cms.model.deleteConfirm', { name: row.modelName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteModel(row.modelId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 字段 ==================== */
const fieldDrawerVisible = ref(false)
const fieldDrawerTitle = ref('')
const fieldLoading = ref(false)
const fieldRows = ref([])
const currentModel = ref(null)

async function openFields(row) {
  currentModel.value = row
  fieldDrawerTitle.value = `${row.modelName} · ${t('page.cms.model.fieldsTitle')}`
  fieldDrawerVisible.value = true
  await loadFields()
}

async function loadFields() {
  if (!currentModel.value) return
  fieldLoading.value = true
  try {
    fieldRows.value = (await listModelFields(currentModel.value.modelId)) || []
  } finally {
    fieldLoading.value = false
  }
}

const fieldDialogVisible = ref(false)
const fieldSaving = ref(false)
const fieldIsEdit = ref(false)
const fieldId = ref('')
const fieldFormRef = ref(null)

const emptyFieldForm = () => ({
  fieldKey: '',
  fieldName: '',
  fieldType: 'text',
  required: '0',
  defaultValue: '',
  optionsJson: '',
  sort: 0,
  status: '0',
  remark: ''
})

const fieldForm = reactive(emptyFieldForm())

const fieldRules = {
  fieldKey: [{ required: true, message: t('page.cms.model.fieldKeyRequired'), trigger: 'blur' }],
  fieldName: [{ required: true, message: t('page.cms.model.fieldNameRequired'), trigger: 'blur' }],
  fieldType: [{ required: true, message: t('page.cms.model.fieldTypeRequired'), trigger: 'change' }]
}

function openFieldAdd() {
  Object.assign(fieldForm, emptyFieldForm())
  fieldIsEdit.value = false
  fieldId.value = ''
  fieldDialogVisible.value = true
}

function openFieldEdit(row) {
  Object.assign(fieldForm, {
    fieldKey: row.fieldKey || '',
    fieldName: row.fieldName || '',
    fieldType: row.fieldType || 'text',
    required: row.required || '0',
    defaultValue: row.defaultValue || '',
    optionsJson: row.optionsJson || '',
    sort: row.sort || 0,
    status: row.status || '0',
    remark: row.remark || ''
  })
  fieldIsEdit.value = true
  fieldId.value = row.fieldId
  fieldDialogVisible.value = true
}

async function submitField() {
  await fieldFormRef.value.validate(async (valid) => {
    if (!valid) return
    fieldSaving.value = true
    try {
      const payload = {
        fieldKey: fieldForm.fieldKey.trim(),
        fieldName: fieldForm.fieldName.trim(),
        fieldType: fieldForm.fieldType,
        required: fieldForm.required,
        defaultValue: fieldForm.defaultValue || null,
        optionsJson: fieldForm.optionsJson || null,
        sort: fieldForm.sort || 0,
        status: fieldForm.status,
        remark: fieldForm.remark || null
      }
      if (fieldIsEdit.value) {
        await updateModelField(currentModel.value.modelId, fieldId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addModelField(currentModel.value.modelId, payload)
        ElMessage.success(t('common.addSuccess'))
      }
      fieldDialogVisible.value = false
      await loadFields()
    } finally {
      fieldSaving.value = false
    }
  })
}

async function handleFieldDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.model.fieldDeleteConfirm', { name: row.fieldName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteModelField(currentModel.value.modelId, row.fieldId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadFields()
}
</script>
