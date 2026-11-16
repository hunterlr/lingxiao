<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- 左：字典类型 -->
      <el-col :xs="24" :lg="9">
        <div class="page-card">
          <div class="table-toolbar">
            <div class="toolbar-left">
              <el-input
                v-model="typeQuery.dictName"
                :placeholder="t('page.dict.namePlaceholder')"
                clearable
                style="width: 140px"
                @keyup.enter="handleTypeSearch"
                @clear="handleTypeSearch"
              />
              <el-input
                v-model="typeQuery.dictType"
                :placeholder="t('page.dict.typePlaceholder')"
                clearable
                style="width: 150px"
                @keyup.enter="handleTypeSearch"
                @clear="handleTypeSearch"
              />
              <el-button type="primary" :icon="Search" @click="handleTypeSearch">{{ t('common.search') }}</el-button>
            </div>
            <div class="toolbar-right">
              <el-button
                v-hasPerm="'platform:system:dict:add'"
                type="primary"
                :icon="Plus"
                @click="openTypeAdd"
              >
                {{ t('page.dict.addType') }}
              </el-button>
            </div>
          </div>

          <el-table
            ref="typeTableRef"
            v-loading="typeLoading"
            :data="typeRows"
            border
            stripe
            highlight-current-row
            row-key="dictId"
            @current-change="handleTypeSelect"
          >
            <el-table-column prop="dictName" :label="t('page.dict.dictName')" min-width="130" show-overflow-tooltip />
            <el-table-column prop="dictType" :label="t('page.dict.dictType')" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="text-mono">{{ row.dictType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" :label="t('common.status')" width="80" align="center">
              <template #default="{ row }">
                <DictTag :value="row.status" dict-type="sys_common_status" />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="110">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    v-hasPerm="'platform:system:dict:edit'"
                    link
                    type="primary"
                    @click.stop="openTypeEdit(row)"
                  >
                    {{ t('common.edit') }}
                  </el-button>
                  <el-button
                    v-hasPerm="'platform:system:dict:remove'"
                    link
                    type="danger"
                    @click.stop="handleTypeDelete(row)"
                  >
                    {{ t('common.delete') }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="typeQuery.pageNum"
              v-model:page-size="typeQuery.pageSize"
              :total="typeTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, prev, pager, next"
              small
              background
              @current-change="loadTypes"
            />
          </div>
        </div>
      </el-col>

      <!-- 右：字典数据 -->
      <el-col :xs="24" :lg="15">
        <div class="page-card">
          <div class="table-toolbar">
            <div class="toolbar-left">
              <template v-if="currentType">
                <el-tag type="primary" effect="plain">{{ currentType.dictName }}</el-tag>
                <span class="text-mono">{{ currentType.dictType }}</span>
              </template>
              <span v-else class="text-secondary">{{ t('page.dict.selectHint') }}</span>
            </div>
            <div class="toolbar-right">
              <el-button
                v-hasPerm="'platform:system:dict:add'"
                type="primary"
                :icon="Plus"
                :disabled="!currentType"
                @click="openDataAdd"
              >
                {{ t('page.dict.addData') }}
              </el-button>
            </div>
          </div>

          <el-form v-if="currentType" :inline="true" :model="dataQuery" class="search-form" @submit.prevent>
            <el-form-item :label="t('page.dict.label')">
              <el-input
                v-model="dataQuery.dictLabel"
                :placeholder="t('page.dict.labelPlaceholder')"
                clearable
                style="width: 150px"
                @keyup.enter="handleDataSearch"
                @clear="handleDataSearch"
              />
            </el-form-item>
            <el-form-item :label="t('common.status')">
              <DictSelect v-model="dataQuery.status" dict-type="sys_common_status" width="120px" :placeholder="t('common.all')" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleDataSearch">{{ t('common.search') }}</el-button>
            </el-form-item>
          </el-form>

          <el-empty v-if="!currentType" :description="t('page.dict.emptyData')" />
          <template v-else>
            <el-table v-loading="dataLoading" :data="dataRows" border stripe row-key="dictCode">
              <el-table-column prop="dictLabel" :label="t('page.dict.dictLabel')" min-width="130" />
              <el-table-column prop="dictValue" :label="t('page.dict.dictValue')" min-width="110">
                <template #default="{ row }">
                  <span class="text-mono">{{ row.dictValue }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="dictSort" :label="t('common.sort')" width="70" align="center" />
              <el-table-column prop="listClass" :label="t('page.dict.listClass')" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.listClass" :type="row.listClass" size="small">
                    {{ row.listClass }}
                  </el-tag>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="isDefault" :label="t('page.dict.isDefault')" width="70" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 'Y'" type="success" size="small" effect="plain">{{ t('common.yes') }}</el-tag>
                  <span v-else class="text-secondary">{{ t('common.no') }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" :label="t('common.status')" width="80" align="center">
                <template #default="{ row }">
                  <DictTag :value="row.status" dict-type="sys_common_status" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.operation')" width="120" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button
                      v-hasPerm="'platform:system:dict:edit'"
                      link
                      type="primary"
                      @click="openDataEdit(row)"
                    >
                      {{ t('common.edit') }}
                    </el-button>
                    <el-button
                      v-hasPerm="'platform:system:dict:remove'"
                      link
                      type="danger"
                      @click="handleDataDelete(row)"
                    >
                      {{ t('common.delete') }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-bar">
              <el-pagination
                v-model:current-page="dataQuery.pageNum"
                v-model:page-size="dataQuery.pageSize"
                :total="dataTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                @size-change="loadData"
                @current-change="loadData"
              />
            </div>
          </template>
        </div>
      </el-col>
    </el-row>

    <!-- 字典类型弹窗 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="typeIsEdit ? t('page.dict.editTypeTitle') : t('page.dict.addTypeTitle')"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item :label="t('page.dict.dictName')" prop="dictName">
          <el-input v-model="typeForm.dictName" maxlength="100" :placeholder="t('page.dict.namePlaceholderEdit')" />
        </el-form-item>
        <el-form-item :label="t('page.dict.dictType')" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            maxlength="100"
            :disabled="typeIsEdit"
            :placeholder="t('page.dict.typePlaceholderEdit')"
          />
          <div class="form-tip">{{ t('page.dict.typeTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="typeForm.status" dict-type="sys_common_status" />
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="typeSaving" @click="submitTypeForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 字典数据弹窗 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="dataIsEdit ? t('page.dict.editDataTitle') : t('page.dict.addDataTitle')"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item :label="t('page.dict.belongType')">
          <el-input :model-value="dataForm.dictType" disabled />
        </el-form-item>
        <el-form-item :label="t('page.dict.dictLabel')" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" maxlength="100" :placeholder="t('page.dict.displayText')" />
        </el-form-item>
        <el-form-item :label="t('page.dict.dictValue')" prop="dictValue">
          <el-input v-model="dataForm.dictValue" maxlength="100" :placeholder="t('page.dict.storeValue')" />
        </el-form-item>
        <el-form-item :label="t('common.sort')">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('page.dict.listClass')">
          <el-select v-model="dataForm.listClass" clearable :placeholder="t('page.dict.notSet')" style="width: 100%">
            <el-option v-for="item in listClassOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.dict.isDefaultLabel')">
          <el-radio-group v-model="dataForm.isDefault">
            <el-radio value="Y">{{ t('common.yes') }}</el-radio>
            <el-radio value="N">{{ t('common.no') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <DictSelect v-model="dataForm.status" dict-type="sys_common_status" />
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dataDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="dataSaving" @click="submitDataForm">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import DictSelect from '@/components/DictSelect.vue'
import DictTag from '@/components/DictTag.vue'
import {
  addDictData,
  addDictType,
  deleteDictData,
  deleteDictType,
  getDictData,
  listDictData,
  listDictTypes,
  updateDictData,
  updateDictType
} from '@/api/dict'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * 字典管理：左类型、右数据的主从布局。
 *
 * <p>数据表强制带 dictType 查询，所以右侧必须先选中类型；
 * 未选类型时只显示提示，不请求“全量字典”。</p>
 */
const listClassOptions = ['primary', 'success', 'info', 'warning', 'danger']

/* ==================== 类型 ==================== */
const typeLoading = ref(false)
const typeRows = ref([])
const typeTotal = ref(0)
const typeTableRef = ref(null)
const currentType = ref(null)
const typeQuery = reactive({ pageNum: 1, pageSize: 10, dictName: '', dictType: '' })

async function loadTypes() {
  typeLoading.value = true
  try {
    const data = await listDictTypes({
      pageNum: typeQuery.pageNum,
      pageSize: typeQuery.pageSize,
      dictName: typeQuery.dictName || undefined,
      dictType: typeQuery.dictType || undefined
    })
    typeRows.value = data.records || []
    typeTotal.value = Number(data.total || 0)

    // 保持选中态：分页 / 刷新后如果类型还在列表里，重新高亮
    if (currentType.value) {
      const hit = typeRows.value.find((item) => String(item.dictId) === String(currentType.value.dictId))
      if (hit) {
        currentType.value = hit
        typeTableRef.value && typeTableRef.value.setCurrentRow(hit)
      } else {
        currentType.value = null
      }
    }
  } finally {
    typeLoading.value = false
  }
}

function handleTypeSearch() {
  typeQuery.pageNum = 1
  loadTypes()
}

function handleTypeSelect(row) {
  if (!row) return
  if (currentType.value && String(currentType.value.dictId) === String(row.dictId)) return
  currentType.value = row
  dataQuery.pageNum = 1
  dataQuery.dictLabel = ''
  dataQuery.status = ''
  loadData()
}

onMounted(loadTypes)

const typeDialogVisible = ref(false)
const typeSaving = ref(false)
const typeIsEdit = ref(false)
const typeEditingId = ref('')
const typeFormRef = ref(null)
const typeForm = reactive({
  dictName: '',
  dictType: '',
  status: '0',
  remark: ''
})

const typeRules = {
  dictName: [{ required: true, message: t('page.dict.nameRequired'), trigger: 'blur' }],
  dictType: [
    { required: true, message: t('page.dict.typeRequired'), trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: t('page.dict.typePattern'),
      trigger: 'blur'
    }
  ]
}

function resetTypeForm() {
  Object.assign(typeForm, { dictName: '', dictType: '', status: '0', remark: '' })
  typeEditingId.value = ''
  typeIsEdit.value = false
}

function openTypeAdd() {
  resetTypeForm()
  typeDialogVisible.value = true
}

function openTypeEdit(row) {
  resetTypeForm()
  typeIsEdit.value = true
  typeEditingId.value = row.dictId
  Object.assign(typeForm, {
    dictName: row.dictName,
    dictType: row.dictType,
    status: row.status || '0',
    remark: row.remark || ''
  })
  typeDialogVisible.value = true
}

async function submitTypeForm() {
  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    typeSaving.value = true
    try {
      if (!typeIsEdit.value) {
        await addDictType({
          dictName: typeForm.dictName.trim(),
          dictType: typeForm.dictType.trim(),
          status: typeForm.status,
          remark: typeForm.remark || null
        })
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateDictType(typeEditingId.value, {
          dictName: typeForm.dictName.trim(),
          dictType: typeForm.dictType.trim(),
          status: typeForm.status,
          remark: typeForm.remark || null
        })
        ElMessage.success(t('common.saveSuccess'))
      }
      typeDialogVisible.value = false
      await loadTypes()
    } finally {
      typeSaving.value = false
    }
  })
}

async function handleTypeDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.dict.deleteTypeConfirm', { name: row.dictName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteDictType(row.dictId)
  ElMessage.success(t('common.deleteSuccess'))
  if (currentType.value && String(currentType.value.dictId) === String(row.dictId)) {
    currentType.value = null
  }
  await loadTypes()
}

/* ==================== 数据 ==================== */
const dataLoading = ref(false)
const dataRows = ref([])
const dataTotal = ref(0)
const dataQuery = reactive({ pageNum: 1, pageSize: 10, dictLabel: '', status: '' })

async function loadData() {
  if (!currentType.value) return
  dataLoading.value = true
  try {
    const data = await listDictData({
      pageNum: dataQuery.pageNum,
      pageSize: dataQuery.pageSize,
      dictType: currentType.value.dictType,
      dictLabel: dataQuery.dictLabel || undefined,
      status: dataQuery.status || undefined
    })
    dataRows.value = data.records || []
    dataTotal.value = Number(data.total || 0)
  } finally {
    dataLoading.value = false
  }
}

function handleDataSearch() {
  dataQuery.pageNum = 1
  loadData()
}

const dataDialogVisible = ref(false)
const dataSaving = ref(false)
const dataIsEdit = ref(false)
const dataEditingId = ref('')
const dataFormRef = ref(null)
const dataForm = reactive({
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  listClass: '',
  isDefault: 'N',
  status: '0',
  remark: ''
})

const dataRules = {
  dictLabel: [{ required: true, message: t('page.dict.labelRequired'), trigger: 'blur' }],
  dictValue: [{ required: true, message: t('page.dict.valueRequired'), trigger: 'blur' }]
}

function resetDataForm() {
  Object.assign(dataForm, {
    dictType: currentType.value ? currentType.value.dictType : '',
    dictLabel: '',
    dictValue: '',
    dictSort: 0,
    listClass: '',
    isDefault: 'N',
    status: '0',
    remark: ''
  })
  dataEditingId.value = ''
  dataIsEdit.value = false
}

function openDataAdd() {
  resetDataForm()
  dataDialogVisible.value = true
}

async function openDataEdit(row) {
  resetDataForm()
  dataIsEdit.value = true
  dataEditingId.value = row.dictCode
  Object.assign(dataForm, {
    dictType: row.dictType,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    dictSort: row.dictSort ?? 0,
    listClass: row.listClass || '',
    isDefault: row.isDefault || 'N',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dataDialogVisible.value = true
  const detail = await getDictData(row.dictCode).catch(() => null)
  if (detail) {
    Object.assign(dataForm, {
      dictType: detail.dictType,
      dictLabel: detail.dictLabel,
      dictValue: detail.dictValue,
      dictSort: detail.dictSort ?? 0,
      listClass: detail.listClass || '',
      isDefault: detail.isDefault || 'N',
      status: detail.status || '0',
      remark: detail.remark || ''
    })
  }
}

async function submitDataForm() {
  await dataFormRef.value.validate(async (valid) => {
    if (!valid) return
    dataSaving.value = true
    try {
      const payload = {
        dictType: dataForm.dictType,
        dictLabel: dataForm.dictLabel.trim(),
        dictValue: dataForm.dictValue.trim(),
        dictSort: dataForm.dictSort,
        listClass: dataForm.listClass || null,
        isDefault: dataForm.isDefault,
        status: dataForm.status,
        remark: dataForm.remark || null
      }
      if (!dataIsEdit.value) {
        await addDictData(payload)
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateDictData(dataEditingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      }
      dataDialogVisible.value = false
      await loadData()
    } finally {
      dataSaving.value = false
    }
  })
}

async function handleDataDelete(row) {
  try {
    await ElMessageBox.confirm(t('page.dict.deleteDataConfirm', { name: row.dictLabel }), t('common.deleteTitle'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) {
    return
  }
  await deleteDictData(row.dictCode)
  ElMessage.success(t('common.deleteSuccess'))
  await loadData()
}
</script>
