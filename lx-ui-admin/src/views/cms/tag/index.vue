<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 200px" @change="handleSearch">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.tag.tagName')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.cms.tag.namePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
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
          <span class="text-secondary">{{ t('page.cms.tag.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:cms:tag:add'"
            type="primary"
            :icon="Plus"
            :disabled="!siteId"
            @click="openAdd"
          >
            {{ t('page.cms.tag.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-alert
        v-if="!sites.length && !sitesLoading"
        type="warning"
        :closable="false"
        show-icon
        :title="t('page.cms.common.selectSiteFirst')"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="rows" border stripe row-key="tagId">
        <el-table-column prop="tagName" :label="t('page.cms.tag.tagName')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="tagKey" :label="t('page.cms.tag.tagKey')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-mono">{{ row.tagKey }}</span></template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('common.remark')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:tag:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:tag:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.tag.edit') : t('page.cms.tag.add')"
      width="560px"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('page.cms.tag.tagName')" prop="tagName">
          <el-input v-model="form.tagName" maxlength="64" :placeholder="t('page.cms.tag.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.tag.tagKey')" prop="tagKey">
          <el-input v-model="form.tagKey" maxlength="64" :placeholder="t('page.cms.tag.keyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">{{ t('common.enabled') }}</el-radio>
            <el-radio value="1">{{ t('common.disabled') }}</el-radio>
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
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addTag, deleteTag, listSiteOptions, listTags, updateTag } from '@/api/cms'

/**
 * CMS 标签管理。
 *
 * <p>标签不能跨站点复用：同一句“Java”在中文站和英文站是两条数据，
 * 各自有自己的 key 与 URL。删除前由后端检查是否仍被文章引用。</p>
 */
const { t } = useI18n()

const sites = ref([])
const sitesLoading = ref(false)
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function loadSites() {
  sitesLoading.value = true
  try {
    sites.value = await listSiteOptions()
    if (!siteId.value && sites.value.length) {
      siteId.value = sites.value[0].siteId
    }
  } finally {
    sitesLoading.value = false
  }
}

async function loadList() {
  if (!siteId.value) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const data = await listTags({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      siteId: siteId.value,
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
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({ tagName: '', tagKey: '', status: '0', remark: '' })
const form = reactive(emptyForm())

const rules = {
  tagName: [{ required: true, message: t('page.cms.tag.nameRequired'), trigger: 'blur' }],
  tagKey: [{ required: true, message: t('page.cms.tag.keyRequired'), trigger: 'blur' }]
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

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.tagId
  Object.assign(form, {
    tagName: row.tagName || '',
    tagKey: row.tagKey || '',
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
      const payload = {
        siteId: siteId.value,
        tagName: form.tagName.trim(),
        tagKey: form.tagKey.trim(),
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateTag(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addTag(payload)
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
      t('page.cms.tag.deleteConfirm', { name: row.tagName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteTag(row.tagId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>
