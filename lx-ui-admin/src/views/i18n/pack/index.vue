<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.i18n.pack.language')">
          <el-select v-model="query.languageCode" clearable :placeholder="t('common.all')" style="width: 160px" @change="loadAll">
            <el-option
              v-for="item in languages"
              :key="item.languageCode"
              :label="`${item.languageName} (${item.languageCode})`"
              :value="item.languageCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.i18n.pack.namespace')">
          <el-select
            v-model="query.namespace"
            clearable
            filterable
            allow-create
            default-first-option
            :placeholder="t('common.all')"
            style="width: 160px"
            @change="loadAll"
          >
            <el-option v-for="item in namespaces" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadAll">{{ t('common.refresh') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.i18n.pack.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:i18n:pack:publish'" type="primary" :icon="Upload" @click="openPublish">
            {{ t('page.i18n.pack.publish') }}
          </el-button>
        </div>
      </div>

      <h4 class="section-title">{{ t('page.i18n.pack.currentTitle') }}</h4>
      <el-table v-loading="loadingCurrent" :data="currentRows" border stripe size="small">
        <el-table-column prop="languageCode" :label="t('page.i18n.pack.language')" width="120">
          <template #default="{ row }"><span class="text-mono">{{ row.languageCode }}</span></template>
        </el-table-column>
        <el-table-column prop="namespace" :label="t('page.i18n.pack.namespace')" min-width="140" />
        <el-table-column prop="version" :label="t('page.i18n.pack.version')" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" type="success" effect="plain">v{{ row.version }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('page.i18n.pack.checksum')" min-width="180">
          <template #default="{ row }"><span class="text-mono">{{ shortChecksum(row.checksum) }}</span></template>
        </el-table-column>
        <el-table-column prop="publishedAt" :label="t('page.i18n.pack.publishedAt')" width="160" />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="plain">{{ t('page.i18n.pack.statusCurrent') }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <h4 class="section-title">{{ t('page.i18n.pack.historyTitle') }}</h4>
      <el-table v-loading="loadingHistory" :data="historyRows" border stripe size="small">
        <el-table-column prop="languageCode" :label="t('page.i18n.pack.language')" width="120">
          <template #default="{ row }"><span class="text-mono">{{ row.languageCode }}</span></template>
        </el-table-column>
        <el-table-column prop="namespace" :label="t('page.i18n.pack.namespace')" min-width="140" />
        <el-table-column prop="version" :label="t('page.i18n.pack.version')" width="90" align="center">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column :label="t('page.i18n.pack.checksum')" min-width="180">
          <template #default="{ row }"><span class="text-mono">{{ shortChecksum(row.checksum) }}</span></template>
        </el-table-column>
        <el-table-column prop="publishedAt" :label="t('page.i18n.pack.publishedAt')" width="160" />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '1' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-hasPerm="'platform:i18n:pack:rollback'"
              link
              type="warning"
              :disabled="row.status === '1'"
              @click="handleRollback(row)"
            >
              {{ t('page.i18n.pack.rollback') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="publishVisible" :title="t('page.i18n.pack.publish')" width="520px" append-to-body>
      <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="110px">
        <el-form-item :label="t('page.i18n.pack.language')" prop="languageCode">
          <el-select v-model="publishForm.languageCode" style="width: 100%">
            <el-option
              v-for="item in languages"
              :key="item.languageCode"
              :label="`${item.languageName} (${item.languageCode})`"
              :value="item.languageCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.i18n.pack.namespace')">
          <el-select
            v-model="publishForm.namespace"
            clearable
            filterable
            allow-create
            default-first-option
            :placeholder="t('page.i18n.pack.namespacePlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="item in namespaces" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-alert :title="t('page.i18n.pack.publishTip')" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="publishVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="publishing" @click="submitPublish">{{ t('common.submit') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Upload } from '@element-plus/icons-vue'
import {
  currentPacks,
  getCoverage,
  languageOptions,
  listPacks,
  publishPack,
  rollbackPack
} from '@/api/i18n'

/**
 * 语言包发布 / 回滚。
 *
 * <p>发布后本实例立即生效；多实例最迟 5 秒。回滚会按快照恢复译文，
 * 不只是切换版本号。</p>
 */
const { t } = useI18n()

const languages = ref([])
const namespaces = ref([])
const loadingCurrent = ref(false)
const loadingHistory = ref(false)
const currentRows = ref([])
const historyRows = ref([])
const query = reactive({ languageCode: '', namespace: '' })

async function loadAll() {
  await Promise.all([loadCurrent(), loadHistory()])
}

async function loadCurrent() {
  loadingCurrent.value = true
  try {
    currentRows.value = await currentPacks({
      languageCode: query.languageCode || undefined,
      namespace: query.namespace || undefined
    })
  } finally {
    loadingCurrent.value = false
  }
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    historyRows.value = await listPacks({
      languageCode: query.languageCode || undefined,
      namespace: query.namespace || undefined
    })
  } finally {
    loadingHistory.value = false
  }
}

async function loadLanguages() {
  languages.value = await languageOptions()
}

async function loadNamespaces() {
  if (!languages.value.length) return
  try {
    const data = await getCoverage({ languageCode: languages.value[0].languageCode })
    namespaces.value = (data || []).map((item) => item.namespace)
  } catch (e) {
    namespaces.value = []
  }
}

onMounted(async () => {
  await loadLanguages().catch(() => {
    languages.value = []
  })
  await Promise.all([loadAll(), loadNamespaces()])
})

function shortChecksum(checksum) {
  if (!checksum) return '-'
  return checksum.length > 16 ? `${checksum.slice(0, 16)}...` : checksum
}

function statusLabel(status) {
  if (status === '1') return t('page.i18n.pack.statusCurrent')
  if (status === '2') return t('page.i18n.pack.statusDeprecated')
  return t('page.i18n.pack.statusHistory')
}

/* ==================== 发布 ==================== */
const publishVisible = ref(false)
const publishing = ref(false)
const publishFormRef = ref(null)
const publishForm = reactive({ languageCode: '', namespace: '' })
const publishRules = {
  languageCode: [{ required: true, message: t('page.i18n.pack.languageRequired'), trigger: 'change' }]
}

function openPublish() {
  publishForm.languageCode = query.languageCode || languages.value[0]?.languageCode || ''
  publishForm.namespace = query.namespace || ''
  publishVisible.value = true
}

async function submitPublish() {
  if (!publishFormRef.value) return
  await publishFormRef.value.validate()
  publishing.value = true
  try {
    const packs = await publishPack({
      languageCode: publishForm.languageCode,
      namespace: publishForm.namespace || undefined
    })
    ElMessage.success(t('page.i18n.pack.publishSuccess', { count: (packs || []).length }))
    publishVisible.value = false
    await loadAll()
  } finally {
    publishing.value = false
  }
}

/* ==================== 回滚 ==================== */
async function handleRollback(row) {
  await ElMessageBox.confirm(
    t('page.i18n.pack.rollbackConfirm', {
      language: row.languageCode,
      namespace: row.namespace,
      version: row.version
    }),
    t('page.i18n.pack.rollback'),
    { type: 'warning' }
  )
  await rollbackPack(row.packId)
  ElMessage.success(t('page.i18n.pack.rollbackSuccess'))
  await loadAll()
}
</script>

<style scoped>
.section-title {
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
}
</style>
