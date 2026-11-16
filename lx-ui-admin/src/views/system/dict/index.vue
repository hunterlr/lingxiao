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
                placeholder="字典名称"
                clearable
                style="width: 140px"
                @keyup.enter="handleTypeSearch"
                @clear="handleTypeSearch"
              />
              <el-input
                v-model="typeQuery.dictType"
                placeholder="类型编码"
                clearable
                style="width: 150px"
                @keyup.enter="handleTypeSearch"
                @clear="handleTypeSearch"
              />
              <el-button type="primary" :icon="Search" @click="handleTypeSearch">搜索</el-button>
            </div>
            <div class="toolbar-right">
              <el-button
                v-hasPerm="'platform:system:dict:add'"
                type="primary"
                :icon="Plus"
                @click="openTypeAdd"
              >
                新增类型
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
            <el-table-column prop="dictName" label="字典名称" min-width="130" show-overflow-tooltip />
            <el-table-column prop="dictType" label="类型编码" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="text-mono">{{ row.dictType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <DictTag :value="row.status" dict-type="sys_common_status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    v-hasPerm="'platform:system:dict:edit'"
                    link
                    type="primary"
                    @click.stop="openTypeEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-hasPerm="'platform:system:dict:remove'"
                    link
                    type="danger"
                    @click.stop="handleTypeDelete(row)"
                  >
                    删除
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
              <span v-else class="text-secondary">先在左侧选择一个字典类型</span>
            </div>
            <div class="toolbar-right">
              <el-button
                v-hasPerm="'platform:system:dict:add'"
                type="primary"
                :icon="Plus"
                :disabled="!currentType"
                @click="openDataAdd"
              >
                新增数据
              </el-button>
            </div>
          </div>

          <el-form v-if="currentType" :inline="true" :model="dataQuery" class="search-form" @submit.prevent>
            <el-form-item label="标签">
              <el-input
                v-model="dataQuery.dictLabel"
                placeholder="字典标签"
                clearable
                style="width: 150px"
                @keyup.enter="handleDataSearch"
                @clear="handleDataSearch"
              />
            </el-form-item>
            <el-form-item label="状态">
              <DictSelect v-model="dataQuery.status" dict-type="sys_common_status" width="120px" placeholder="全部" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleDataSearch">搜索</el-button>
            </el-form-item>
          </el-form>

          <el-empty v-if="!currentType" description="选择左侧字典类型后查看数据" />
          <template v-else>
            <el-table v-loading="dataLoading" :data="dataRows" border stripe row-key="dictCode">
              <el-table-column prop="dictLabel" label="字典标签" min-width="130" />
              <el-table-column prop="dictValue" label="字典键值" min-width="110">
                <template #default="{ row }">
                  <span class="text-mono">{{ row.dictValue }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="dictSort" label="排序" width="70" align="center" />
              <el-table-column prop="listClass" label="标签样式" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.listClass" :type="row.listClass" size="small">
                    {{ row.listClass }}
                  </el-tag>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="isDefault" label="默认" width="70" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 'Y'" type="success" size="small" effect="plain">是</el-tag>
                  <span v-else class="text-secondary">否</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="80" align="center">
                <template #default="{ row }">
                  <DictTag :value="row.status" dict-type="sys_common_status" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button
                      v-hasPerm="'platform:system:dict:edit'"
                      link
                      type="primary"
                      @click="openDataEdit(row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-hasPerm="'platform:system:dict:remove'"
                      link
                      type="danger"
                      @click="handleDataDelete(row)"
                    >
                      删除
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
      :title="typeIsEdit ? '编辑字典类型' : '新增字典类型'"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" maxlength="100" placeholder="如 用户性别" />
        </el-form-item>
        <el-form-item label="类型编码" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            maxlength="100"
            :disabled="typeIsEdit"
            placeholder="如 sys_user_sex"
          />
          <div class="form-tip">类型编码被业务代码引用，创建后不建议修改。</div>
        </el-form-item>
        <el-form-item label="状态">
          <DictSelect v-model="typeForm.status" dict-type="sys_common_status" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="typeDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="typeSaving" @click="submitTypeForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 字典数据弹窗 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="dataIsEdit ? '编辑字典数据' : '新增字典数据'"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="所属类型">
          <el-input :model-value="dataForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" maxlength="100" placeholder="展示文本" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" maxlength="100" placeholder="存储值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="dataForm.listClass" clearable placeholder="不设置" style="width: 100%">
            <el-option v-for="item in listClassOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否默认">
          <el-radio-group v-model="dataForm.isDefault">
            <el-radio value="Y">是</el-radio>
            <el-radio value="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <DictSelect v-model="dataForm.status" dict-type="sys_common_status" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dataDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="dataSaving" @click="submitDataForm">保存</el-button>
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
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: '建议小写字母开头，只含小写字母、数字、下划线',
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
        ElMessage.success('新增成功')
      } else {
        await updateDictType(typeEditingId.value, {
          dictName: typeForm.dictName.trim(),
          dictType: typeForm.dictType.trim(),
          status: typeForm.status,
          remark: typeForm.remark || null
        })
        ElMessage.success('保存成功')
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
      `确认删除字典类型「${row.dictName}」吗？类型下仍有数据时服务端会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteDictType(row.dictId)
  ElMessage.success('删除成功')
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
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
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
        ElMessage.success('新增成功')
      } else {
        await updateDictData(dataEditingId.value, payload)
        ElMessage.success('保存成功')
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
    await ElMessageBox.confirm(`确认删除字典数据「${row.dictLabel}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await deleteDictData(row.dictCode)
  ElMessage.success('删除成功')
  await loadData()
}
</script>
