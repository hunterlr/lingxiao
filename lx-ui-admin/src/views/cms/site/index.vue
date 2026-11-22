<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.site.siteName')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.cms.site.namePlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option :label="t('common.enabled')" value="0" />
            <el-option :label="t('common.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.cms.site.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:cms:site:add'" type="primary" :icon="Plus" @click="openAdd">
            {{ t('page.cms.site.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="siteId">
        <el-table-column :label="t('page.cms.site.logo')" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="34" :src="assetUrl(row.logo)">{{ (row.siteName || '?').slice(0, 1) }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="siteName" :label="t('page.cms.site.siteName')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="siteKey" :label="t('page.cms.site.siteKey')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-mono">{{ row.siteKey }}</span></template>
        </el-table-column>
        <el-table-column prop="domain" :label="t('page.cms.site.domain')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.domain || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:site:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:site:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.site.edit') : t('page.cms.site.add')"
      width="720px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.cms.site.siteKey')" prop="siteKey">
              <el-input v-model="form.siteKey" maxlength="64" :placeholder="t('page.cms.site.keyPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.site.siteName')" prop="siteName">
              <el-input v-model="form.siteName" maxlength="100" :placeholder="t('page.cms.site.namePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.site.domain')">
              <el-input v-model="form.domain" maxlength="255" :placeholder="t('page.cms.site.domainPlaceholder')" />
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
          <el-col :span="24">
            <el-form-item :label="t('page.cms.site.logo')">
              <div class="asset-field">
                <el-avatar :size="48" :src="assetUrl(form.logo)">{{ (form.siteName || '?').slice(0, 1) }}</el-avatar>
                <el-input v-model="form.logo" maxlength="255" :placeholder="t('page.cms.site.logoPlaceholder')" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.site.seoTitle')">
              <el-input v-model="form.seoTitle" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.site.seoKeywords')">
              <el-input v-model="form.seoKeywords" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.site.seoDescription')">
              <el-input v-model="form.seoDescription" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('common.remark')">
              <el-input v-model="form.remark" maxlength="500" />
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
import { addSite, deleteSite, getSite, listSites, updateSite } from '@/api/cms'

/**
 * CMS 站点管理。
 *
 * <p>站点是内容根节点，删除前必须先清空栏目与文章；后端会拦截，
 * 前端只负责提示，不做“级联删除”这种危险操作。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

/** 头像 / 封面 / Logo 统一兼容 fileId 与 http(s) 外链。 */
function assetUrl(value) {
  if (!value) return ''
  if (value.startsWith('http') || value.startsWith('/')) return value
  return `/api/file/${value}`
}

async function loadList() {
  loading.value = true
  try {
    const data = await listSites({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined
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
  query.keyword = ''
  query.status = ''
  handleSearch()
}

onMounted(loadList)

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  siteKey: '',
  siteName: '',
  domain: '',
  logo: '',
  seoTitle: '',
  seoKeywords: '',
  seoDescription: '',
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  siteKey: [{ required: true, message: t('page.cms.site.keyRequired'), trigger: 'blur' }],
  siteName: [{ required: true, message: t('page.cms.site.nameRequired'), trigger: 'blur' }]
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
  editingId.value = row.siteId
  Object.assign(form, {
    siteKey: row.siteKey || '',
    siteName: row.siteName || '',
    domain: row.domain || '',
    logo: row.logo || '',
    seoTitle: row.seoTitle || '',
    seoKeywords: row.seoKeywords || '',
    seoDescription: row.seoDescription || '',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  const detail = await getSite(row.siteId).catch(() => null)
  if (detail) {
    Object.assign(form, {
      siteKey: detail.siteKey || '',
      siteName: detail.siteName || '',
      domain: detail.domain || '',
      logo: detail.logo || '',
      seoTitle: detail.seoTitle || '',
      seoKeywords: detail.seoKeywords || '',
      seoDescription: detail.seoDescription || '',
      status: detail.status || '0',
      remark: detail.remark || ''
    })
  }
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
        siteKey: form.siteKey.trim(),
        siteName: form.siteName.trim(),
        domain: form.domain || null,
        logo: form.logo || null,
        seoTitle: form.seoTitle || null,
        seoKeywords: form.seoKeywords || null,
        seoDescription: form.seoDescription || null,
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateSite(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addSite(payload)
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
      t('page.cms.site.deleteConfirm', { name: row.siteName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteSite(row.siteId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>

<style scoped>
.asset-field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.asset-field :deep(.el-input) {
  flex: 1;
}
</style>
