<template>
  <div class="page-container">
    <div v-if="memberSwitch" class="page-card member-switch-card">
      <div class="member-switch-main">
        <div class="member-switch-text">
          <div class="member-switch-title">
            <span>{{ t('page.config.memberSwitch') }}</span>
            <el-tag :type="memberSwitchOn ? 'success' : 'info'" size="small" effect="plain">
              {{ memberSwitchOn ? t('page.config.switchOn') : t('page.config.switchOff') }}
            </el-tag>
          </div>
          <div class="member-switch-desc">{{ t('page.config.memberSwitchHint') }}</div>
        </div>
        <el-switch
          v-hasPerm="'platform:system:config:edit'"
          v-model="memberSwitchValue"
          :active-value="memberSwitchValues.on"
          :inactive-value="memberSwitchValues.off"
          :loading="memberSwitchSaving"
          @change="handleMemberSwitchChange"
        />
      </div>
    </div>

    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.config.configName')">
          <el-input
            v-model="query.configName"
            :placeholder="t('page.config.namePlaceholder')"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.config.configKey')">
          <el-input
            v-model="query.configKey"
            :placeholder="t('page.config.keyPlaceholder')"
            clearable
            style="width: 230px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('page.config.control')">
          <el-select v-model="query.controlType" clearable :placeholder="t('common.all')" style="width: 110px">
            <el-option
              v-for="item in CONTROL_TYPES"
              :key="item.value"
              :label="controlTypeLabel(item.value)"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.config.configType')">
          <el-select v-model="query.configType" clearable :placeholder="t('common.all')" style="width: 110px">
            <el-option :label="t('page.config.builtin')" value="Y" />
            <el-option :label="t('page.config.normal')" value="N" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">
            {{ t('page.config.hint') }}
          </span>
          <span v-if="memberSwitch" class="member-setting-tip">
            {{ t('page.config.memberMovedHint') }}
          </span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:system:config:add'"
            type="primary"
            :icon="Plus"
            @click="openAdd"
          >
            {{ t('page.config.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="configId">
        <el-table-column prop="configName" :label="t('page.config.configName')" min-width="160">
          <template #default="{ row }">{{ configLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="configKey" :label="t('page.config.configKey')" min-width="220">
          <template #default="{ row }">
            <span class="text-mono">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" :label="t('page.config.configValue')" min-width="170">
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
        <el-table-column prop="controlType" :label="t('page.config.control')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="controlTagType(row.controlType)" size="small" effect="plain">
              {{ controlTypeLabel(row.controlType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configType" :label="t('page.config.configType')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'Y' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.configType === 'Y' ? t('page.config.builtin') : t('page.config.normal') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-hasPerm="'platform:system:config:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:system:config:remove'"
                link
                type="danger"
                :disabled="row.configType === 'Y'"
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.config.edit') : t('page.config.add')"
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
        <el-form-item :label="t('page.config.configName')" prop="configName">
          <el-input v-model="form.configName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('page.config.configKey')" prop="configKey">
          <el-input
            v-model="form.configKey"
            maxlength="100"
            :disabled="isBuiltinConfig"
            :placeholder="t('page.config.keyPlaceholder')"
          />
          <div v-if="isBuiltinConfig" class="form-tip">{{ t('page.config.builtinKeyTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('page.config.controlType')">
          <el-select
            v-model="form.controlType"
            :disabled="isBuiltinConfig"
            style="width: 100%"
            @change="handleControlTypeChange"
          >
            <el-option
              v-for="item in CONTROL_TYPES"
              :key="item.value"
              :label="controlTypeLabel(item.value)"
              :value="item.value"
            />
          </el-select>
          <div v-if="isBuiltinConfig" class="form-tip">
            {{ t('page.config.builtinControlTip') }}
          </div>
        </el-form-item>
        <el-form-item v-if="form.controlType !== 'text'" :label="t('page.config.controlOptions')" prop="configOptions">
          <el-input
            v-if="form.controlType === 'select'"
            v-model="form.configOptions"
            type="textarea"
            :rows="3"
            maxlength="1000"
            :placeholder="t('page.config.optionsPlaceholder')"
          />
          <el-input
            v-else-if="form.controlType === 'number'"
            v-model="form.configOptions"
            maxlength="1000"
            :placeholder="t('page.config.numberPlaceholder')"
          />
          <el-input
            v-else
            v-model="form.configOptions"
            maxlength="1000"
            :placeholder="t('page.config.switchPlaceholder')"
          />
          <div class="form-tip">
            <template v-if="form.controlType === 'select'">
              {{ t('page.config.selectOptionTip') }}
            </template>
            <template v-else-if="form.controlType === 'number'">
              {{ t('page.config.numberOptionTip') }}
            </template>
            <template v-else>
              {{ t('page.config.switchOptionTip') }}
            </template>
          </div>
        </el-form-item>
        <el-form-item :label="t('page.config.configValue')" prop="configValue">
          <el-input
            v-if="form.controlType === 'text'"
            v-model="form.configValue"
            maxlength="500"
            :placeholder="t('page.config.valuePlaceholder')"
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
            :active-text="t('page.config.switchOn')"
            :inactive-text="t('page.config.switchOff')"
          />
          <el-select
            v-else
            v-model="form.configValue"
            :placeholder="t('page.config.selectValuePlaceholder')"
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
        <el-form-item :label="t('page.config.configTypeLabel')">
          <el-radio-group v-model="form.configType" :disabled="isBuiltinConfig">
            <el-radio value="Y">{{ t('page.config.builtin') }}</el-radio>
            <el-radio value="N">{{ t('page.config.normal') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
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
const { t, te } = useI18n()

/** 参数名走语言包 config.<config_key>；没有译文时退回数据库里的 config_name。 */
function configLabel(row) {
  const key = row.configKey ? `config.${row.configKey}` : ''
  return key && te(key) ? t(key) : row.configName
}

/* ==================== 会员功能总开关 ==================== */
const memberSwitch = ref(null)
const memberSwitchValue = ref('')
const memberSwitchSaving = ref(false)
const memberSwitchValues = computed(() => parseSwitchValues(memberSwitch.value?.configOptions))
const memberSwitchOn = computed(() => memberSwitchValue.value === memberSwitchValues.value.on)

async function loadMemberSwitch() {
  try {
    const data = await listConfigs({ pageNum: 1, pageSize: 10, configKey: 'mem.enabled' })
    const row = (data.records || []).find((item) => item.configKey === 'mem.enabled')
    memberSwitch.value = row || null
    memberSwitchValue.value = row ? String(row.configValue) : ''
  } catch (e) {
    // 会员模块未安装时没有这个参数；开关卡片直接不显示，不影响通用参数页
    memberSwitch.value = null
  }
}

async function handleMemberSwitchChange(value) {
  if (!memberSwitch.value) return
  const { on, off } = memberSwitchValues.value
  const previous = value === on ? off : on
  memberSwitchSaving.value = true
  try {
    await updateConfig(memberSwitch.value.configId, { ...memberSwitch.value, configValue: value })
    memberSwitch.value.configValue = value
    ElMessage.success(t('page.config.memberSwitchSaved'))
  } catch (e) {
    memberSwitchValue.value = previous
  } finally {
    memberSwitchSaving.value = false
  }
}

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
      controlType: query.controlType || undefined,
      // 会员参数集中在「会员管理 → 会员设置」，通用参数页不再混排
      excludeConfigKeyPrefix: 'mem.'
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

onMounted(() => {
  loadList()
  loadMemberSwitch()
})

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
    callback(new Error(t('page.config.optionsRequired')))
    return
  }
  if (form.controlType === 'number' && raw && !/^(-?\d+)?:(-?\d+)?$/.test(raw)) {
    callback(new Error(t('page.config.rangeFormat')))
    return
  }
  if (form.controlType === 'switch' && raw) {
    const parts = raw.split(':')
    if (parts.length !== 2 || !parts[0].trim() || !parts[1].trim() || parts[0].trim() === parts[1].trim()) {
      callback(new Error(t('page.config.switchFormat')))
      return
    }
  }
  callback()
}

function validateConfigValue(rule, value, callback) {
  const raw = value === null || value === undefined ? '' : String(value).trim()
  if (!raw) {
    callback(new Error(t('page.config.valueRequired')))
    return
  }
  if (form.controlType === 'number') {
    if (!/^-?\d+$/.test(raw)) {
      callback(new Error(t('page.config.integer')))
      return
    }
    const number = Number(raw)
    const { min, max } = numberRange.value
    if (min !== undefined && number < min) {
      callback(new Error(t('page.config.minOnly', { min })))
      return
    }
    if (max !== undefined && number > max) {
      callback(new Error(t('page.config.maxOnly', { max })))
      return
    }
  } else if (form.controlType === 'switch') {
    const { on, off } = switchValues.value
    if (raw !== on && raw !== off) {
      callback(new Error(t('page.config.switchValue', { on, off })))
      return
    }
  } else if (form.controlType === 'select') {
    if (!selectOptions.value.some((option) => option.value === raw)) {
      callback(new Error(t('page.config.notInOptions')))
      return
    }
  }
  callback()
}

const rules = {
  configName: [{ required: true, message: t('page.config.nameRequired'), trigger: 'blur' }],
  configKey: [
    { required: true, message: t('page.config.keyRequired'), trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_.-]*$/,
      message: t('page.config.keyPattern'),
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
        ElMessage.success(t('common.addSuccess'))
      } else {
        await updateConfig(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
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
    await ElMessageBox.confirm(t('page.config.deleteConfirm', { name: configLabel(row) }), t('common.deleteTitle'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) {
    return
  }
  await deleteConfig(row.configId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>

<style scoped>
.member-switch-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.member-switch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.member-switch-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.member-setting-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

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
