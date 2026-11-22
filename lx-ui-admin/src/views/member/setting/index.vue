<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        class="setting-hint"
        type="info"
        :closable="false"
        show-icon
        :title="t('page.memberSetting.hint')"
        :description="t('page.memberSetting.switchHint')"
      />

      <div v-loading="loading">
        <el-empty
          v-if="!loading && groups.length === 0"
          :description="t('page.memberSetting.loadEmpty')"
        />

        <section v-for="group in groups" :key="group.key" class="setting-group">
          <div class="setting-group-header">
            <div class="setting-group-title">{{ t(group.titleKey) }}</div>
            <div class="setting-group-desc">{{ t(group.descriptionKey) }}</div>
          </div>

          <el-form label-width="210px" label-position="right" @submit.prevent>
            <el-form-item v-for="item in group.items" :key="item.configKey" :label="item.configName">
              <div class="setting-control">
                <el-switch
                  v-if="item.controlType === 'switch'"
                  v-model="values[item.configKey]"
                  :active-value="switchValues(item).on"
                  :inactive-value="switchValues(item).off"
                />
                <el-input-number
                  v-else-if="item.controlType === 'number'"
                  :model-value="numberValue(item)"
                  :min="numberRange(item).min"
                  :max="numberRange(item).max"
                  :step="1"
                  controls-position="right"
                  style="width: 200px"
                  @update:model-value="(value) => setNumberValue(item, value)"
                />
                <el-select
                  v-else-if="item.controlType === 'select'"
                  v-model="values[item.configKey]"
                  style="width: 280px"
                >
                  <el-option
                    v-for="option in selectOptions(item)"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
                <el-input
                  v-else
                  v-model="values[item.configKey]"
                  maxlength="500"
                  style="width: 340px"
                />
                <span class="setting-key text-mono">{{ item.configKey }}</span>
              </div>
              <div v-if="item.remark" class="setting-remark">{{ item.remark }}</div>
            </el-form-item>
          </el-form>
        </section>
      </div>

      <div class="setting-footer">
        <el-button
          v-hasPerm="'platform:system:config:edit'"
          type="primary"
          :loading="saving"
          :disabled="!changedItems.length"
          @click="save"
        >
          {{ t('page.memberSetting.save') }}
        </el-button>
        <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        <span v-if="!loading && !changedItems.length" class="text-secondary">
          {{ t('page.memberSetting.noChanges') }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { batchUpdateConfigs, listConfigs } from '@/api/config'
import {
  parseNumberRange,
  parseSelectOptions,
  parseSwitchValues
} from '@/utils/config-control'

/**
 * 会员设置。
 *
 * <p>会员参数原来混在「系统管理 → 参数设置」里，和系统参数、CMS 参数排在一张表，
 * 运营很难找到。这里按安全 / 账号 / 注册 / 找回分组，用控件直接编辑值，
 * 一次保存只提交发生变化的项。</p>
 *
 * <p>会员功能总开关 {@code mem.enabled} 仍留在通用参数页：关闭后本页菜单会随
 * 会员模块菜单一起隐藏，因此总开关不能只放在这里。</p>
 */
const { t } = useI18n()

const GROUP_DEFS = [
  {
    key: 'security',
    titleKey: 'page.memberSetting.groupSecurity',
    descriptionKey: 'page.memberSetting.groupSecurityHint',
    keys: ['mem.security.captchaEnabled', 'mem.security.captchaMaxAttempts', 'mem.security.passwordPolicy']
  },
  {
    key: 'account',
    titleKey: 'page.memberSetting.groupAccount',
    descriptionKey: 'page.memberSetting.groupAccountHint',
    keys: [
      'mem.account.maxRetryCount',
      'mem.account.lockMinutes',
      'mem.account.multiDeviceLogin',
      'mem.account.loginRateLimitCount',
      'mem.account.loginRateLimitMinutes'
    ]
  },
  {
    key: 'register',
    titleKey: 'page.memberSetting.groupRegister',
    descriptionKey: 'page.memberSetting.groupRegisterHint',
    keys: [
      'mem.register.mobileRequired',
      'mem.register.emailRequired',
      'mem.register.rateLimitCount',
      'mem.register.rateLimitMinutes'
    ]
  },
  {
    key: 'reset',
    titleKey: 'page.memberSetting.groupReset',
    descriptionKey: 'page.memberSetting.groupResetHint',
    keys: [
      'mem.register.resetCodeExpireMinutes',
      'mem.register.resetRateLimitCount',
      'mem.register.resetRateLimitMinutes'
    ]
  }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const groups = ref([])
const values = reactive({})
const original = ref({})

function switchValues(item) {
  return parseSwitchValues(item.configOptions)
}

function numberRange(item) {
  return parseNumberRange(item.configOptions)
}

function selectOptions(item) {
  return parseSelectOptions(item.configOptions)
}

function numberValue(item) {
  const value = Number(values[item.configKey])
  return Number.isFinite(value) ? value : undefined
}

function setNumberValue(item, value) {
  values[item.configKey] = value === undefined || value === null ? '' : String(value)
}

/** 已知参数按分组展示；将来新增的 mem.* 参数落到「其他」，不会被静默藏起来。 */
function buildGroups(list) {
  const byKey = new Map(list.map((row) => [row.configKey, row]))
  const used = new Set()
  const result = []
  for (const def of GROUP_DEFS) {
    const items = def.keys.map((key) => byKey.get(key)).filter(Boolean)
    items.forEach((item) => used.add(item.configKey))
    if (items.length) {
      result.push({ ...def, items })
    }
  }
  const rest = list.filter((row) => !used.has(row.configKey))
  if (rest.length) {
    result.push({
      key: 'other',
      titleKey: 'page.memberSetting.groupOther',
      descriptionKey: 'page.memberSetting.groupOtherHint',
      items: rest
    })
  }
  return result
}

async function loadList() {
  loading.value = true
  try {
    const data = await listConfigs({ pageNum: 1, pageSize: 100, configKeyPrefix: 'mem.' })
    // 总开关在通用参数页编辑，这里不再重复展示
    const list = (data.records || []).filter((row) => row.configKey !== 'mem.enabled')
    rows.value = list
    const next = {}
    for (const row of list) {
      next[row.configKey] = row.configValue == null ? '' : String(row.configValue)
    }
    for (const key of Object.keys(values)) {
      delete values[key]
    }
    Object.assign(values, next)
    original.value = { ...next }
    groups.value = buildGroups(list)
  } finally {
    loading.value = false
  }
}

const changedItems = computed(() => {
  return rows.value
    .filter((row) => String(values[row.configKey] ?? '') !== String(original.value[row.configKey] ?? ''))
    .map((row) => ({ configId: row.configId, configValue: String(values[row.configKey] ?? '') }))
})

async function save() {
  if (!changedItems.value.length) {
    ElMessage.info(t('page.memberSetting.noChanges'))
    return
  }
  saving.value = true
  try {
    await batchUpdateConfigs(changedItems.value)
    ElMessage.success(t('page.memberSetting.saveSuccess'))
    await loadList()
  } finally {
    saving.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.setting-hint {
  margin-bottom: 16px;
}

.setting-group {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.setting-group + .setting-group {
  margin-top: 14px;
}

.setting-group-header {
  margin-bottom: 14px;
}

.setting-group-title {
  font-size: 15px;
  font-weight: 600;
}

.setting-group-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.setting-control {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.setting-key {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.setting-remark {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.setting-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}
</style>
