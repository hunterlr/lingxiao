<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        class="oauth-hint"
        type="info"
        :closable="false"
        show-icon
        :title="t('page.memberOAuth.hint')"
        :description="t('page.memberOAuth.hintDetail')"
      />
      <el-alert
        class="oauth-hint"
        type="warning"
        :closable="false"
        show-icon
        :title="t('page.memberOAuth.globalHint')"
      />

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.memberOAuth.listHint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-empty v-if="!loading && rows.length === 0" :description="t('page.memberOAuth.loadEmpty')" />

      <section v-for="group in groups" :key="group.key" class="oauth-group">
        <div class="oauth-group-title">{{ group.title }}</div>
        <el-table v-loading="loading" :data="group.rows" border stripe row-key="provider">
          <el-table-column :label="t('page.memberOAuth.provider')" min-width="180">
            <template #default="{ row }">
              <div class="provider-name">
                {{ row.displayName || row.provider }}
                <el-tag v-if="row.browser === false" class="provider-tag" size="small" type="info" effect="plain">
                  {{ t('page.memberOAuth.miniProgram') }}
                </el-tag>
              </div>
              <div class="provider-code text-mono">{{ row.provider }}</div>
            </template>
          </el-table-column>

          <el-table-column :label="t('common.status')" width="120" align="center">
            <template #default="{ row }">
              <el-switch
                v-hasPerm="'platform:member:oauth:edit'"
                v-model="row.status"
                active-value="0"
                inactive-value="1"
                :loading="row.saving"
                @change="(value) => toggleStatus(row, value)"
              />
            </template>
          </el-table-column>

          <el-table-column :label="t('page.memberOAuth.configState')" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="row.configured ? 'success' : 'info'" size="small" effect="plain">
                {{ row.configured ? t('page.memberOAuth.configured') : t('page.memberOAuth.notConfigured') }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column :label="t('page.memberOAuth.clientId')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.clientId || '-' }}</template>
          </el-table-column>

          <el-table-column :label="t('page.memberOAuth.redirectUri')" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.browser === false ? t('page.memberOAuth.miniRedirect') : (row.redirectUri || row.suggestedRedirectUri) }}
            </template>
          </el-table-column>

          <el-table-column :label="t('common.operation')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-hasPerm="'platform:member:oauth:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('page.memberOAuth.configure') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="t('page.memberOAuth.editTitle', { name: form.displayName || form.provider })"
      width="760px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" label-width="170px">
        <el-form-item :label="t('page.memberOAuth.displayName')">
          <el-input v-model="form.displayName" maxlength="64" />
        </el-form-item>

        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">{{ t('common.enabled') }}</el-radio>
            <el-radio value="1">{{ t('common.disabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('page.memberOAuth.clientId')">
          <el-input v-model="form.clientId" maxlength="255" autocomplete="off" />
        </el-form-item>

        <el-form-item :label="t('page.memberOAuth.clientSecret')">
          <el-input
            v-model="form.clientSecret"
            type="password"
            show-password
            maxlength="4000"
            autocomplete="new-password"
            :placeholder="row.hasSecret ? t('page.memberOAuth.secretPlaceholder') : ''"
          />
          <div class="form-tip">{{ t('page.memberOAuth.secretTip') }}</div>
          <el-checkbox v-if="row.hasSecret" v-model="form.clearSecret" class="clear-secret">
            {{ t('page.memberOAuth.clearSecret') }}
          </el-checkbox>
        </el-form-item>

        <el-form-item :label="t('page.memberOAuth.redirectUri')">
          <template v-if="row.browser === false">
            <div class="form-tip">{{ t('page.memberOAuth.miniRedirectTip') }}</div>
          </template>
          <template v-else>
            <el-input v-model="form.redirectUri" maxlength="500" :placeholder="row.suggestedRedirectUri" />
            <div class="form-tip">
              {{ t('page.memberOAuth.suggestedRedirect') }}：
              <span class="text-mono">{{ row.suggestedRedirectUri }}</span>
              <el-button link type="primary" @click="copyRedirect(row)">
                {{ t('page.memberOAuth.copy') }}
              </el-button>
            </div>
          </template>
        </el-form-item>

        <el-form-item :label="t('page.memberOAuth.scope')">
          <el-input v-model="form.scope" maxlength="500" :placeholder="t('page.memberOAuth.scopePlaceholder')" />
        </el-form-item>

        <el-divider content-position="left">{{ t('page.memberOAuth.advanced') }}</el-divider>

        <el-form-item :label="t('page.memberOAuth.authorizeUrl')">
          <el-input v-model="form.authorizeUrl" maxlength="500" :placeholder="t('page.memberOAuth.endpointPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.memberOAuth.tokenUrl')">
          <el-input v-model="form.tokenUrl" maxlength="500" :placeholder="t('page.memberOAuth.endpointPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.memberOAuth.userInfoUrl')">
          <el-input v-model="form.userInfoUrl" maxlength="500" :placeholder="t('page.memberOAuth.endpointPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.memberOAuth.extraJson')">
          <el-input
            v-model="form.extraJson"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            :placeholder="t('page.memberOAuth.extraJsonPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('page.memberOAuth.sort')">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listMemberOAuthProviders, updateMemberOAuthProvider } from '@/api/member'

/**
 * 会员第三方登录配置。
 *
 * <p>代码里已内置全部适配器，这里只负责“启用哪些平台 + 填平台凭据”。
 * 密钥只写不读：列表只显示是否已配置，编辑时留空表示沿用原值。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])

const groups = computed(() => [
  {
    key: 'cn',
    title: t('page.memberOAuth.domestic'),
    rows: rows.value.filter((row) => row.group === 'cn')
  },
  {
    key: 'global',
    title: t('page.memberOAuth.global'),
    rows: rows.value.filter((row) => row.group !== 'cn')
  }
])

async function loadList() {
  loading.value = true
  try {
    rows.value = (await listMemberOAuthProviders()) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

/** 列表开关直接保存；失败时回滚到原状态，避免页面显示与后端不一致。 */
async function toggleStatus(row, value) {
  const previous = value === '0' ? '1' : '0'
  try {
    const name = row.displayName || row.provider
    await ElMessageBox.confirm(
      value === '0'
        ? t('page.memberOAuth.enableConfirm', { name })
        : t('page.memberOAuth.disableConfirm', { name }),
      t('common.tip'),
      { type: 'warning' }
    )
  } catch {
    row.status = previous
    return
  }
  row.saving = true
  try {
    const updated = await updateMemberOAuthProvider(row.provider, { status: value })
    Object.assign(row, updated, { saving: false })
    ElMessage.success(t('page.memberOAuth.updateSuccess'))
  } catch (error) {
    row.status = previous
    row.saving = false
    throw error
  }
}

/* ==================== 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const row = ref({})

const emptyForm = () => ({
  provider: '',
  displayName: '',
  status: '1',
  clientId: '',
  clientSecret: '',
  clearSecret: false,
  redirectUri: '',
  scope: '',
  authorizeUrl: '',
  tokenUrl: '',
  userInfoUrl: '',
  extraJson: '',
  sort: 100,
  remark: ''
})
const form = reactive(emptyForm())

function openEdit(target) {
  row.value = target
  Object.assign(form, emptyForm(), {
    provider: target.provider,
    displayName: target.displayName || '',
    status: target.status || '1',
    clientId: target.clientId || '',
    clientSecret: '',
    clearSecret: false,
    redirectUri: target.redirectUri || '',
    scope: target.scope || '',
    authorizeUrl: target.authorizeUrl || '',
    tokenUrl: target.tokenUrl || '',
    userInfoUrl: target.userInfoUrl || '',
    extraJson: target.extraJson || '',
    sort: target.sort == null ? 100 : target.sort,
    remark: target.remark || ''
  })
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
}

async function copyRedirect(target) {
  try {
    await navigator.clipboard.writeText(target.suggestedRedirectUri)
    ElMessage.success(t('page.memberOAuth.copySuccess'))
  } catch {
    // 非安全上下文（http 内网）没有剪贴板权限，退回手动选择
    ElMessage.info(target.suggestedRedirectUri)
  }
}

async function submitForm() {
  if (form.status === '0' && (!form.clientId.trim() || (!row.value.hasSecret && !form.clientSecret.trim()))) {
    ElMessage.warning(t('page.memberOAuth.notConfiguredTip'))
    return
  }
  saving.value = true
  try {
    const updated = await updateMemberOAuthProvider(form.provider, {
      displayName: form.displayName || null,
      status: form.status,
      clientId: form.clientId || null,
      clientSecret: form.clientSecret || null,
      clearSecret: form.clearSecret,
      redirectUri: form.redirectUri || null,
      scope: form.scope || null,
      authorizeUrl: form.authorizeUrl || null,
      tokenUrl: form.tokenUrl || null,
      userInfoUrl: form.userInfoUrl || null,
      extraJson: form.extraJson || null,
      sort: form.sort,
      remark: form.remark || null
    })
    const index = rows.value.findIndex((item) => item.provider === updated.provider)
    if (index >= 0) rows.value[index] = updated
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.oauth-hint {
  margin-bottom: 12px;
}

.oauth-group + .oauth-group {
  margin-top: 20px;
}

.oauth-group-title {
  margin: 4px 0 10px;
  font-size: 15px;
  font-weight: 600;
}

.provider-name {
  font-weight: 500;
}

.provider-code {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.provider-tag {
  margin-left: 6px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.clear-secret {
  margin-top: 4px;
}
</style>
