<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 180px" @change="handleSearch">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.page.pageName')">
          <el-input v-model="query.keyword" clearable style="width: 180px" @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option :label="t('page.cms.page.draft')" value="0" />
            <el-option :label="t('page.cms.page.published')" value="1" />
            <el-option :label="t('page.cms.page.offline')" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        :title="t('page.cms.page.hint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:page:add'" type="primary" :icon="Plus" @click="openAdd">
          {{ t('page.cms.page.add') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="pageKey" :label="t('page.cms.page.pageKey')" width="140" />
        <el-table-column prop="pageName" :label="t('page.cms.page.pageName')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="title" :label="t('page.cms.page.titleLabel')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="publishTime" :label="t('page.cms.page.publishTime')" width="160" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="380" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="row.status !== '1'" v-hasPerm="'platform:cms:page:publish'" link type="success" @click="handlePublish(row)">
                {{ t('page.cms.page.publish') }}
              </el-button>
              <el-button v-if="row.status === '1'" v-hasPerm="'platform:cms:page:offline'" link type="warning" @click="handleOffline(row)">
                {{ t('page.cms.page.offlineAction') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:page:preview'" link type="primary" @click="openPreview(row)">
                {{ t('page.cms.page.preview') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:page:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:page:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.page.edit') : t('page.cms.page.add')"
      width="960px"
      top="5vh"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" v-loading="dialogLoading" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('page.cms.common.site')" prop="siteId">
              <el-select v-model="form.siteId" :disabled="isEdit" style="width: 100%">
                <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.page.pageKey')" prop="pageKey">
              <el-input v-model="form.pageKey" maxlength="100" :placeholder="t('page.cms.page.keyPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.page.pageName')" prop="pageName">
              <el-input v-model="form.pageName" maxlength="100" :placeholder="t('page.cms.page.namePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.page.titleLabel')">
              <el-input v-model="form.title" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.page.template')">
              <el-select v-model="form.template" clearable style="width: 100%" :placeholder="t('page.cms.page.templatePlaceholder')">
                <el-option
                  v-for="item in templateOptions"
                  :key="item.code"
                  :label="templateOptionLabel(item)"
                  :value="item.code"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.page.cover')">
              <MediaPicker v-model="form.cover" :fallback="form.pageName" :placeholder="t('page.cms.page.coverPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.page.summary')">
              <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.page.content')">
              <RichEditor v-model="form.content" height="320px" />
            </el-form-item>
          </el-col>
          <el-col v-if="modelFields.length" :span="24">
            <el-divider content-position="left">{{ t('page.cms.model.extFields') }}</el-divider>
            <CmsExtFields v-model="form.ext" :fields="modelFields" />
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.page.seoTitle')">
              <el-input v-model="form.seoTitle" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.page.seoKeywords')">
              <el-input v-model="form.seoKeywords" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.page.seoDescription')">
              <el-input v-model="form.seoDescription" type="textarea" :rows="2" maxlength="500" />
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
import RichEditor from '@/components/RichEditor.vue'
import MediaPicker from '@/components/MediaPicker.vue'
import CmsExtFields from '@/components/CmsExtFields.vue'
import {
  addPage,
  createPagePreviewTicket,
  deletePage,
  getPage,
  getTemplateOptions,
  listModelFields,
  listModelOptions,
  listPages,
  listSiteOptions,
  offlinePage,
  publishPage,
  updatePage
} from '@/api/cms'

/**
 * CMS 单页管理：page_key 是门户 URL 的稳定键，改名不影响引用。
 * 新建一律草稿，发布后才能被门户与导航目标读取。
 */
const { t, locale } = useI18n()

/** 模板接口同时返回中英文名称，后台按当前语言展示。 */
function templateOptionLabel(item) {
  return String(locale.value).startsWith('en') ? item.nameEn : item.name
}

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const templateOptions = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

function statusLabel(status) {
  if (status === '1') return t('page.cms.page.published')
  if (status === '2') return t('page.cms.page.offline')
  return t('page.cms.page.draft')
}

function statusTagType(status) {
  if (status === '1') return 'success'
  if (status === '2') return 'warning'
  return 'info'
}

async function loadSites() {
  sites.value = (await listSiteOptions()) || []
  if (!siteId.value && sites.value.length) {
    siteId.value = sites.value[0].siteId
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await listPages({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      siteId: siteId.value || undefined,
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

onMounted(async () => {
  await loadSites()
  await loadList()
  try {
    const options = await getTemplateOptions()
    templateOptions.value = options.pageTemplates || []
  } catch (e) {
    // 权限不足或 CMS 关闭时保持空列表，模板留空即回退默认版式
    templateOptions.value = []
  }
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)
const modelFields = ref([])

const emptyForm = () => ({
  siteId: siteId.value,
  pageKey: '',
  pageName: '',
  title: '',
  summary: '',
  cover: '',
  content: '',
  template: '',
  seoTitle: '',
  seoKeywords: '',
  seoDescription: '',
  sort: 0,
  remark: '',
  ext: {}
})

const form = reactive(emptyForm())

const rules = {
  siteId: [{ required: true, message: t('page.cms.common.siteRequired'), trigger: 'change' }],
  pageKey: [{ required: true, message: t('page.cms.page.keyRequired'), trigger: 'blur' }],
  pageName: [{ required: true, message: t('page.cms.page.nameRequired'), trigger: 'blur' }]
}

async function loadPageModelFields(targetSiteId) {
  modelFields.value = []
  if (!targetSiteId) return
  const models = (await listModelOptions(targetSiteId)) || []
  const pageModel = models.find((item) => item.modelType === 'page')
  if (!pageModel) return
  modelFields.value = (await listModelFields(pageModel.modelId).catch(() => [])) || []
}

async function openAdd() {
  Object.assign(form, emptyForm())
  form.siteId = siteId.value || (sites.value[0] && sites.value[0].siteId)
  isEdit.value = false
  editingId.value = ''
  dialogVisible.value = true
  await loadPageModelFields(form.siteId)
}

async function openEdit(row) {
  Object.assign(form, emptyForm())
  isEdit.value = true
  editingId.value = row.pageId
  form.siteId = row.siteId
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getPage(row.pageId)
    Object.assign(form, {
      siteId: detail.siteId,
      pageKey: detail.pageKey || '',
      pageName: detail.pageName || '',
      title: detail.title || '',
      summary: detail.summary || '',
      cover: detail.cover || '',
      content: detail.content || '',
      template: detail.template || '',
      seoTitle: detail.seoTitle || '',
      seoKeywords: detail.seoKeywords || '',
      seoDescription: detail.seoDescription || '',
      sort: detail.sort || 0,
      remark: detail.remark || '',
      ext: detail.ext || {}
    })
    await loadPageModelFields(detail.siteId)
  } finally {
    dialogLoading.value = false
  }
}

async function submitForm() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        siteId: form.siteId,
        pageKey: form.pageKey.trim(),
        pageName: form.pageName.trim(),
        title: form.title || null,
        summary: form.summary || null,
        cover: form.cover || null,
        content: form.content,
        template: form.template || null,
        seoTitle: form.seoTitle || null,
        seoKeywords: form.seoKeywords || null,
        seoDescription: form.seoDescription || null,
        sort: form.sort || 0,
        remark: form.remark || null,
        ext: form.ext || {}
      }
      if (isEdit.value) {
        await updatePage(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addPage(payload)
        ElMessage.success(t('common.addSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.page.publishConfirm', { name: row.pageName }),
      t('page.cms.page.publish'),
      { type: 'info', confirmButtonText: t('page.cms.page.publish'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await publishPage(row.pageId)
  ElMessage.success(t('common.success'))
  await loadList()
}

async function handleOffline(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.page.offlineConfirm', { name: row.pageName }),
      t('page.cms.page.offlineAction'),
      { type: 'warning', confirmButtonText: t('page.cms.page.offlineAction'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await offlinePage(row.pageId)
  ElMessage.success(t('common.success'))
  await loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.page.deleteConfirm', { name: row.pageName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deletePage(row.pageId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

async function openPreview(row) {
  const ticket = await createPagePreviewTicket(row.pageId)
  const base = String(import.meta.env.VITE_PORTAL_BASE_URL || 'http://localhost:3000').replace(/\/$/, '')
  window.open(`${base}/preview/${encodeURIComponent(ticket.ticket)}`, '_blank')
}
</script>
