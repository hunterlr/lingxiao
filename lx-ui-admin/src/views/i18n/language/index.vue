<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.i18n.language.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.i18n.language.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="loadList"
            @clear="loadList"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('common.enabled')" value="0" />
            <el-option :label="t('common.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.i18n.language.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:i18n:language:add'" type="primary" :icon="Plus" @click="openAdd">
            {{ t('page.i18n.language.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="languageId">
        <el-table-column prop="languageCode" :label="t('page.i18n.language.code')" width="130">
          <template #default="{ row }"><span class="text-mono">{{ row.languageCode }}</span></template>
        </el-table-column>
        <el-table-column prop="languageName" :label="t('page.i18n.language.name')" min-width="150" />
        <el-table-column prop="nativeName" :label="t('page.i18n.language.nativeName')" min-width="130" />
        <el-table-column prop="locale" :label="t('page.i18n.language.locale')" width="110">
          <template #default="{ row }"><span class="text-mono">{{ row.locale }}</span></template>
        </el-table-column>
        <el-table-column prop="direction" :label="t('page.i18n.language.direction')" width="90" align="center" />
        <el-table-column :label="t('page.i18n.language.default')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small" effect="plain">
              {{ t('page.i18n.language.defaultTag') }}
            </el-tag>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="row.isDefault === 1 || !can('platform:i18n:language:status')"
              @change="(value) => handleStatusChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:i18n:language:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:i18n:language:remove'"
                link
                type="danger"
                :disabled="row.isDefault === 1"
                @click="handleDelete(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.i18n.language.edit') : t('page.i18n.language.add')"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.code')" prop="languageCode">
              <el-input v-model="form.languageCode" maxlength="16" :placeholder="t('page.i18n.language.codePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.locale')">
              <el-input v-model="form.locale" maxlength="32" :placeholder="t('page.i18n.language.localePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.name')" prop="languageName">
              <el-input v-model="form.languageName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.nativeName')">
              <el-input v-model="form.nativeName" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.direction')">
              <el-select v-model="form.direction" style="width: 100%">
                <el-option label="LTR" value="ltr" />
                <el-option label="RTL" value="rtl" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.i18n.language.default')">
              <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addLanguage, changeLanguageStatus, deleteLanguage, listLanguages, updateLanguage } from '@/api/i18n'
import { useAuthStore } from '@/store/auth'

/**
 * 语言管理。
 *
 * <p>默认语言不能停用 / 删除；已有译文或语言包的语言不允许删除，
 * 这些规则后端也会再校验一次，前端提前禁用按钮只是体验优化。</p>
 */
const { t } = useI18n()
const auth = useAuthStore()
const can = (perm) => auth.hasPermission(perm)

const loading = ref(false)
const rows = ref([])
const query = reactive({ keyword: '', status: '' })

async function loadList() {
  loading.value = true
  try {
    rows.value = await listLanguages({
      keyword: query.keyword || undefined,
      status: query.status || undefined
    })
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  loadList()
}

onMounted(loadList)

async function handleStatusChange(row, status) {
  const previous = status === '0' ? '1' : '0'
  try {
    await changeLanguageStatus(row.languageId, status)
    ElMessage.success(t('common.updateSuccess'))
  } catch (e) {
    row.status = previous
  }
}

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  languageCode: '',
  languageName: '',
  nativeName: '',
  locale: '',
  direction: 'ltr',
  isDefault: 0,
  status: '0',
  sort: 0
})

const form = reactive(emptyForm())

const rules = {
  languageCode: [{ required: true, message: t('page.i18n.language.codeRequired'), trigger: 'blur' }],
  languageName: [{ required: true, message: t('page.i18n.language.nameRequired'), trigger: 'blur' }]
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

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.languageId
  Object.assign(form, {
    languageCode: row.languageCode,
    languageName: row.languageName,
    nativeName: row.nativeName || '',
    locale: row.locale || '',
    direction: row.direction || 'ltr',
    isDefault: row.isDefault === 1 ? 1 : 0,
    status: row.status || '0',
    sort: row.sort || 0
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateLanguage(editingId.value, { ...form })
    } else {
      await addLanguage({ ...form })
    }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.addSuccess'))
    dialogVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    t('page.i18n.language.deleteConfirm', { code: row.languageCode }),
    t('common.deleteTitle'),
    { type: 'warning' }
  )
  await deleteLanguage(row.languageId)
  ElMessage.success(t('common.deleteSuccess'))
  loadList()
}
</script>
