<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        class="channel-hint"
        type="info"
        :closable="false"
        show-icon
        :title="t('page.memberChannel.hint')"
        :description="t('page.memberChannel.hintDetail')"
      />

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.memberChannel.listHint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="configId">
        <el-table-column prop="channelType" :label="t('page.memberChannel.channel')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.channelType === 'sms' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ channelLabel(row.channelType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mode" :label="t('page.memberChannel.mode')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.mode === 'dev' ? 'info' : 'success'" size="small" effect="plain">
              {{ modeLabel(row.mode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provider" :label="t('page.memberChannel.provider')" width="120">
          <template #default="{ row }">{{ row.provider || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('page.memberChannel.sender')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ senderText(row) }}</template>
        </el-table-column>
        <el-table-column prop="endpoint" :label="t('page.memberChannel.endpoint')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.endpoint || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('page.memberChannel.updateTime')" width="165">
          <template #default="{ row }">{{ row.updateTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="170" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:member:channel:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:member:channel:test'" link type="success" @click="openTest(row)">
                {{ t('page.memberChannel.test') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="t('page.memberChannel.editTitle', { channel: channelLabel(form.channelType) })"
      width="660px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item :label="t('page.memberChannel.mode')" prop="mode">
          <el-radio-group v-model="form.mode">
            <el-radio value="dev">{{ t('page.memberChannel.modeDev') }}</el-radio>
            <el-radio value="prod">{{ t('page.memberChannel.modeProd') }}</el-radio>
          </el-radio-group>
          <div class="form-tip">{{ t('page.memberChannel.modeTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">{{ t('common.enabled') }}</el-radio>
            <el-radio value="1">{{ t('common.disabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('page.memberChannel.provider')">
          <el-select
            v-model="form.provider"
            filterable
            allow-create
            default-first-option
            clearable
            :placeholder="t('page.memberChannel.providerPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="item in PROVIDERS" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.memberChannel.endpoint')">
          <el-input v-model="form.endpoint" maxlength="255" :placeholder="endpointPlaceholder" />
        </el-form-item>
        <el-form-item :label="t('page.memberChannel.accessKey')">
          <el-input v-model="form.accessKey" maxlength="128" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="t('page.memberChannel.accessSecret')">
          <el-input
            v-model="form.accessSecret"
            type="password"
            show-password
            maxlength="255"
            autocomplete="new-password"
            :placeholder="t('page.memberChannel.secretPlaceholder')"
          />
          <div class="form-tip">{{ t('page.memberChannel.secretTip') }}</div>
        </el-form-item>
        <template v-if="form.channelType === 'sms'">
          <el-form-item :label="t('page.memberChannel.signName')">
            <el-input v-model="form.signName" maxlength="64" />
          </el-form-item>
          <el-form-item :label="t('page.memberChannel.templateCode')">
            <el-input v-model="form.templateCode" maxlength="64" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="t('page.memberChannel.sender')">
            <el-input v-model="form.sender" maxlength="128" :placeholder="t('page.memberChannel.senderPlaceholder')" />
          </el-form-item>
        </template>
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

    <el-dialog
      v-model="testVisible"
      :title="t('page.memberChannel.testTitle', { channel: channelLabel(testChannel?.channelType) })"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="testFormRef" :model="testForm" :rules="testRules" label-width="90px">
        <el-form-item :label="t('page.memberChannel.target')" prop="target">
          <el-input
            v-model="testForm.target"
            maxlength="128"
            :placeholder="testChannel?.channelType === 'sms'
              ? t('page.memberChannel.targetMobile')
              : t('page.memberChannel.targetEmail')"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="testResult"
        class="test-result"
        :type="testResult.code ? 'success' : 'info'"
        :closable="false"
        show-icon
        :title="testResult.message"
        :description="testResult.code
          ? t('page.memberChannel.testCode', { code: testResult.code })
          : t('page.memberChannel.testSubmitted')"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="testVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="testing" @click="submitTest">
            {{ t('page.memberChannel.sendTest') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listMemberChannels, testMemberChannel, updateMemberChannel } from '@/api/member'

/**
 * 会员消息通道配置。
 *
 * <p>短信 / 邮件各一行，独立于通用参数设置：这里集中展示启停、dev / prod、
 * 服务商与密钥，并提供测试发送。{@code accessSecret} 后端不回显，留空表示不修改。</p>
 */
const { t } = useI18n()

const PROVIDERS = ['dev', 'aliyun', 'tencent', 'smtp', 'custom']

const loading = ref(false)
const rows = ref([])

function channelLabel(type) {
  if (type === 'sms') return t('page.memberChannel.sms')
  if (type === 'email') return t('page.memberChannel.email')
  return type || '-'
}

function modeLabel(mode) {
  return mode === 'prod' ? t('page.memberChannel.modeProd') : t('page.memberChannel.modeDev')
}

function senderText(row) {
  if (row.channelType === 'sms') {
    return row.signName || row.sender || '-'
  }
  return row.sender || '-'
}

async function loadList() {
  loading.value = true
  try {
    rows.value = (await listMemberChannels()) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

/* ==================== 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  configId: null,
  channelType: '',
  mode: 'dev',
  provider: 'dev',
  endpoint: '',
  accessKey: '',
  accessSecret: '',
  signName: '',
  templateCode: '',
  sender: '',
  status: '0',
  remark: ''
})
const form = reactive(emptyForm())

const endpointPlaceholder = computed(() =>
  form.channelType === 'email' ? 'smtp.example.com:465' : t('page.memberChannel.endpointPlaceholder')
)

const rules = {
  mode: [{ required: true, message: t('page.memberChannel.modeRequired'), trigger: 'change' }]
}

function openEdit(row) {
  Object.assign(form, emptyForm(), {
    configId: row.configId,
    channelType: row.channelType,
    mode: row.mode || 'dev',
    provider: row.provider || 'dev',
    endpoint: row.endpoint || '',
    accessKey: row.accessKey || '',
    accessSecret: '',
    signName: row.signName || '',
    templateCode: row.templateCode || '',
    sender: row.sender || '',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value && formRef.value.clearValidate()
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await updateMemberChannel(form.configId, {
        mode: form.mode,
        provider: form.provider || null,
        endpoint: form.endpoint || null,
        accessKey: form.accessKey || null,
        accessSecret: form.accessSecret || null,
        signName: form.signName || null,
        templateCode: form.templateCode || null,
        sender: form.sender || null,
        status: form.status,
        remark: form.remark || null
      })
      ElMessage.success(t('common.saveSuccess'))
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

/* ==================== 测试发送 ==================== */
const testVisible = ref(false)
const testing = ref(false)
const testChannel = ref(null)
const testResult = ref(null)
const testFormRef = ref(null)
const testForm = reactive({ target: '' })

const testRules = {
  target: [
    { required: true, message: t('page.memberChannel.targetRequired'), trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const text = String(value || '').trim()
        if (!text) {
          callback()
          return
        }
        if (testChannel.value?.channelType === 'sms' && !/^1\d{10}$/.test(text)) {
          callback(new Error(t('page.memberChannel.targetMobileInvalid')))
          return
        }
        if (testChannel.value?.channelType === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(text)) {
          callback(new Error(t('page.memberChannel.targetEmailInvalid')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openTest(row) {
  testChannel.value = row
  testResult.value = null
  testForm.target = ''
  testVisible.value = true
}

async function submitTest() {
  await testFormRef.value.validate(async (valid) => {
    if (!valid) return
    testing.value = true
    try {
      testResult.value = await testMemberChannel(testChannel.value.configId, testForm.target.trim())
    } finally {
      testing.value = false
    }
  })
}
</script>

<style scoped>
.channel-hint {
  margin-bottom: 16px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.test-result {
  margin-top: 4px;
}
</style>
