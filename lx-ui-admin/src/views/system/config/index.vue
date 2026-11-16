<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item label="参数名称">
          <el-input
            v-model="query.configName"
            placeholder="参数名称"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="参数键名">
          <el-input
            v-model="query.configKey"
            placeholder="如 sys.account.captchaEnabled"
            clearable
            style="width: 230px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="控件">
          <el-select v-model="query.controlType" clearable placeholder="全部" style="width: 110px">
            <el-option
              v-for="item in CONTROL_TYPES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.configType" clearable placeholder="全部" style="width: 110px">
            <el-option label="内置" value="Y" />
            <el-option label="普通" value="N" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            参数按“控件”渲染：文本 / 数字 / 开关 / 下拉。内置参数只允许改值，
            键名、控件类型、选项与删除入口都会被禁用。
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:config:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            新增参数
          </el-button>
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="configId">
        <el-table-column prop="configName" label="参数名称" min-width="160" />
        <el-table-column prop="configKey" label="参数键名" min-width="220">
          <template #default="{ row }">
            <span class="text-mono">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="参数键值" min-width="170">
          <template #default="{ row }">
            <el-tag
              v-if="row.controlType === 'switch'"
              :type="isSwitchOn(row) ? 'success' : 'info'"
              size="small"
              effect="plain"
            >
              {{ displayConfigValue(row) }}
            </el-tag>
            <span v-else class="config-value">{{ displayConfigValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="controlType" label="控件" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="controlTagType(row.controlType)" size="small" effect="plain">
              {{ controlTypeLabel(row.controlType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'Y' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.configType === 'Y' ? '内置' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:config:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPerm="'platform:system:config:remove'"
                link
                type="danger"
                :disabled="row.configType === 'Y'"
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑参数' : '新增参数'"
      width="600px"
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
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" maxlength="100" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input
            v-model="form.configKey"
            maxlength="100"
            :disabled="isBuiltinConfig"
            placeholder="如 sys.account.captchaEnabled"
          />
          <div v-if="isBuiltinConfig" class="form-tip">内置参数的键名被代码引用，不允许修改。</div>
        </el-form-item>
        <el-form-item label="控件类型">
          <el-select
            v-model="form.controlType"
            :disabled="isBuiltinConfig"
            style="width: 100%"
            @change="handleControlTypeChange"
          >
            <el-option
              v-for="item in CONTROL_TYPES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div v-if="isBuiltinConfig" class="form-tip">
            内置参数的控件类型与选项被代码依赖，不允许修改。
          </div>
        </el-form-item>
        <el-form-item v-if="form.controlType !== 'text'" label="控件选项" prop="configOptions">
          <el-input
            v-if="form.controlType === 'select'"
            v-model="form.configOptions"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder="每行一个，格式 值:显示名&#10;wangeditor-next:wangEditor Next&#10;ueditor-plus:UEditor Plus"
          />
          <el-input
            v-else-if="form.controlType === 'number'"
            v-model="form.configOptions"
            maxlength="1000"
            placeholder="最小值:最大值，例如 1:3650；留空表示不限制"
          />
          <el-input
            v-else
            v-model="form.configOptions"
            maxlength="1000"
            placeholder="开值:关值，例如 true:false；留空默认 true:false"
          />
          <div class="form-tip">
            <template v-if="form.controlType === 'select'">
              下拉显示“显示名”，入库保存“值”。选项值不能为空、不能重复。
            </template>
            <template v-else-if="form.controlType === 'number'">
              只接受整数；可只写一侧，例如 1: 或 :100。
            </template>
            <template v-else>
              开关保存的是原始值（true/false 或 1/0），显示为“开启 / 关闭”。
            </template>
          </div>
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input
            v-if="form.controlType === 'text'"
            v-model="form.configValue"
            maxlength="500"
            placeholder="请输入参数值"
          />
          <el-input-number
            v-else-if="form.controlType === 'number'"
            v-model="numberValue"
            :min="numberRange.min"
            :max="numberRange.max"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
          <el-switch
            v-else-if="form.controlType === 'switch'"
            v-model="switchValue"
            :active-value="switchValues.on"
            :inactive-value="switchValues.off"
            active-text="开启"
            inactive-text="关闭"
          />
          <el-select
            v-else
            v-model="form.configValue"
            placeholder="请选择参数值"
            style="width: 100%"
          >
            <el-option
              v-for="option in selectOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参数类型">
          <el-radio-group v-model="form.configType" :disabled="isBuiltinConfig">
            <el-radio value="Y">内置</el-radio>
            <el-radio value="N">普通</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addConfig, deleteConfig, getConfig, listConfigs, updateConfig } from '@/api/config'
import {
  CONTROL_TYPES,
  controlTypeLabel,
  displayConfigValue,
  parseNumberRange,
  parseSelectOptions,
  parseSwitchValues
} from '@/utils/config-control'

/**
 * 系统参数。
 *
 * <p>参数不只是一种“字符串”，列表按 controlType 渲染开关 / 下拉可读值，
 * 编辑弹窗按同一字段切换输入控件。内置参数（configType=Y）只允许改值：
 * 键名、控件类型、选项、删除入口全部禁用。</p>
 *
 * <p>前端校验只是为了少一次往返，最终约束在 `SysConfigService`：
 * 下拉值必须在选项内、数字必须是合法整数、开关值必须匹配开/关值。</p>
 */
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  configName: '',
  configKey: '',
  configType: '',
  controlType: ''
})

async function loadList() {
  loading.value = true
  try {
    const data = await listConfigs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      configName: query.configName || undefined,
      configKey: query.configKey || undefined,
      configType: query.configType || undefined,
      controlType: query.controlType || undefined
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
  query.configName = ''
  query.configKey = ''
  query.configType = ''
  query.controlType = ''
  handleSearch()
}

onMounted(loadList)

/* ==================== 列表展示 ==================== */
function isSwitchOn(row) {
  const { on } = parseSwitchValues(row.configOptions)
  return String(row.configValue) === on
}

function controlTagType(type) {
  if (type === 'switch') return 'success'
  if (type === 'number') return 'warning'
  if (type === 'select') return 'primary'
  return 'info'
}

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)
const isBuiltinConfig = computed(() => isEdit.value && form.configType === 'Y')

const emptyForm = () => ({
  configName: '',
  configKey: '',
  configValue: '',
  controlType: 'text',
  configOptions: '',
  configType: 'N',
  remark: ''
})

const form = reactive(emptyForm())

/* 选项解析与后端保持一致；解析失败给安全默认值，保存时再由后端拒绝。 */
const selectOptions = computed(() => parseSelectOptions(form.configOptions))
const numberRange = computed(() => parseNumberRange(form.configOptions))
const switchValues = computed(() => parseSwitchValues(form.configOptions))

const numberValue = computed({
  get() {
    const value = Number(form.configValue)
    return Number.isFinite(value) ? value : undefined
  },
  set(value) {
    form.configValue = value === undefined || value === null ? '' : String(value)
  }
})

/**
 * el-switch 的 v-model 只做字符串透传：
 * 组件用 active-value / inactive-value 与 modelValue 做严格比较，
 * 这里若返回布尔值会导致 `true !== 'true'`，开关永远显示为关。
 */
const switchValue = computed({
  get() {
    return form.configValue
  },
  set(value) {
    form.configValue = value
  }
})

function validateConfigOptions(rule, value, callback) {
  const raw = String(value || '').trim()
  if (form.controlType === 'select' && parseSelectOptions(raw).length === 0) {
    callback(new Error('下拉控件至少配置一个选项'))
    return
  }
  if (form.controlType === 'number' && raw && !/^(-?\d+)?:(-?\d+)?$/.test(raw)) {
    callback(new Error('数字范围格式应为 最小值:最大值，例如 1:3650'))
    return
  }
  if (form.controlType === 'switch' && raw) {
    const parts = raw.split(':')
    if (parts.length !== 2 || !parts[0].trim() || !parts[1].trim() || parts[0].trim() === parts[1].trim()) {
      callback(new Error('开关选项格式应为 开值:关值，且两值不同'))
      return
    }
  }
  callback()
}

function validateConfigValue(rule, value, callback) {
  const raw = value === null || value === undefined ? '' : String(value).trim()
  if (!raw) {
    callback(new Error('请输入参数键值'))
    return
  }
  if (form.controlType === 'number') {
    if (!/^-?\d+$/.test(raw)) {
      callback(new Error('数字参数必须是整数'))
      return
    }
    const number = Number(raw)
    const { min, max } = numberRange.value
    if (min !== undefined && number < min) {
      callback(new Error(`不能小于 ${min}`))
      return
    }
    if (max !== undefined && number > max) {
      callback(new Error(`不能大于 ${max}`))
      return
    }
  } else if (form.controlType === 'switch') {
    const { on, off } = switchValues.value
    if (raw !== on && raw !== off) {
      callback(new Error(`只能是 ${on} 或 ${off}`))
      return
    }
  } else if (form.controlType === 'select') {
    if (!selectOptions.value.some((option) => option.value === raw)) {
      callback(new Error('参数值不在可选项内'))
      return
    }
  }
  callback()
}

const rules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [
    { required: true, message: '请输入参数键名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_.-]*$/,
      message: '以字母开头，只能包含字母、数字、点、下划线、短横线',
      trigger: 'blur'
    }
  ],
  configOptions: [{ validator: validateConfigOptions, trigger: ['blur', 'change'] }],
  configValue: [{ validator: validateConfigValue, trigger: ['blur', 'change'] }]
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
  editingId.value = row.configId
  Object.assign(form, {
    configName: row.configName,
    configKey: row.configKey,
    configValue: row.configValue == null ? '' : String(row.configValue),
    controlType: row.controlType || 'text',
    configOptions: row.configOptions || '',
    configType: row.configType || 'N',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getConfig(row.configId)
    Object.assign(form, {
      configName: detail.configName,
      configKey: detail.configKey,
      configValue: detail.configValue == null ? '' : String(detail.configValue),
      controlType: detail.controlType || 'text',
      configOptions: detail.configOptions || '',
      configType: detail.configType || 'N',
      remark: detail.remark || ''
    })
  } finally {
    dialogLoading.value = false
  }
}

/**
 * 用户切换控件类型时，把当前值收敛到新控件能接受的范围。
 *
 * <p>只挂在 select 的 @change 上，不在 watch 里做：打开编辑弹窗时
 * 历史值可能不符合新控件，但不应该被悄悄改掉——保存时再让校验拦。</p>
 */
function handleControlTypeChange(type) {
  if (type === 'switch') {
    const { on, off } = switchValues.value
    if (form.configValue !== on && form.configValue !== off) {
      form.configValue = on
    }
  } else if (type === 'select') {
    const options = selectOptions.value
    if (!options.some((option) => option.value === form.configValue)) {
      form.configValue = options.length ? options[0].value : ''
    }
  } else if (type === 'number') {
    if (!/^-?\d+$/.test(form.configValue)) {
      form.configValue = String(numberRange.value.min ?? 0)
    }
  }
  formRef.value && formRef.value.clearValidate(['configValue', 'configOptions'])
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
        configName: form.configName.trim(),
        configKey: form.configKey.trim(),
        configValue: String(form.configValue).trim(),
        controlType: form.controlType,
        configOptions: form.controlType === 'text' ? null : form.configOptions.trim() || null,
        configType: form.configType,
        remark: form.remark || null
      }
      if (!isEdit.value) {
        await addConfig(payload)
        ElMessage.success('新增成功')
      } else {
        await updateConfig(editingId.value, payload)
        ElMessage.success('保存成功')
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
    await ElMessageBox.confirm(`确认删除参数「${row.configName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await deleteConfig(row.configId)
  ElMessage.success('删除成功')
  await loadList()
}
</script>

<style scoped>
.config-value {
  word-break: break-all;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
