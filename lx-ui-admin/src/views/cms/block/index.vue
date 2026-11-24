<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 180px" @change="handleSearch">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.block.blockName')">
          <el-input v-model="query.keyword" clearable style="width: 180px" @keyup.enter="handleSearch" @clear="handleSearch" />
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

      <el-alert
        :title="t('page.cms.block.hint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:block:add'" type="primary" :icon="Plus" @click="openAdd">
          {{ t('page.cms.block.add') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="blockKey" :label="t('page.cms.block.blockKey')" width="160" />
        <el-table-column prop="blockName" :label="t('page.cms.block.blockName')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('page.cms.block.blockType')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ blockTypeLabel(row.blockType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:block:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:block:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.block.edit') : t('page.cms.block.add')"
      width="860px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" v-loading="dialogLoading" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.cms.common.site')" prop="siteId">
              <el-select v-model="form.siteId" :disabled="isEdit" style="width: 100%">
                <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.block.blockKey')" prop="blockKey">
              <el-input v-model="form.blockKey" maxlength="64" :placeholder="t('page.cms.block.keyPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.block.blockName')" prop="blockName">
              <el-input v-model="form.blockName" maxlength="100" :placeholder="t('page.cms.block.namePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.block.blockType')" prop="blockType">
              <el-select v-model="form.blockType" style="width: 100%">
                <el-option :label="t('page.cms.block.typeHtml')" value="html" />
                <el-option :label="t('page.cms.block.typeImage')" value="image" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item v-if="form.blockType === 'html'" :label="t('page.cms.block.content')">
              <RichEditor v-model="form.content" height="280px" />
            </el-form-item>
            <el-form-item v-else :label="t('page.cms.block.cover')">
              <MediaPicker v-model="form.cover" :fallback="form.blockName" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.block.linkUrl')">
              <el-input v-model="form.linkUrl" maxlength="500" :placeholder="t('page.cms.block.linkPlaceholder')" />
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
import {
  addBlock,
  deleteBlock,
  getBlock,
  listBlocks,
  listSiteOptions,
  updateBlock
} from '@/api/cms'

/**
 * CMS 区块管理：首页推荐位、可复用文案等碎片内容。
 * html 类型正文由后端白名单清洗；image 类型用封面图 + 跳转地址。
 */
const { t } = useI18n()

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

function blockTypeLabel(type) {
  return type === 'image' ? t('page.cms.block.typeImage') : t('page.cms.block.typeHtml')
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
    const data = await listBlocks({
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
})

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  siteId: siteId.value,
  blockKey: '',
  blockName: '',
  blockType: 'html',
  content: '',
  cover: '',
  linkUrl: '',
  sort: 0,
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  siteId: [{ required: true, message: t('page.cms.common.siteRequired'), trigger: 'change' }],
  blockKey: [{ required: true, message: t('page.cms.block.keyRequired'), trigger: 'blur' }],
  blockName: [{ required: true, message: t('page.cms.block.nameRequired'), trigger: 'blur' }],
  blockType: [{ required: true, message: t('page.cms.block.typeRequired'), trigger: 'change' }]
}

function openAdd() {
  Object.assign(form, emptyForm())
  form.siteId = siteId.value || (sites.value[0] && sites.value[0].siteId)
  isEdit.value = false
  editingId.value = ''
  dialogVisible.value = true
}

async function openEdit(row) {
  Object.assign(form, emptyForm())
  isEdit.value = true
  editingId.value = row.blockId
  form.siteId = row.siteId
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getBlock(row.blockId)
    Object.assign(form, {
      siteId: detail.siteId,
      blockKey: detail.blockKey || '',
      blockName: detail.blockName || '',
      blockType: detail.blockType || 'html',
      content: detail.content || '',
      cover: detail.cover || '',
      linkUrl: detail.linkUrl || '',
      sort: detail.sort || 0,
      status: detail.status || '0',
      remark: detail.remark || ''
    })
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
        blockKey: form.blockKey.trim(),
        blockName: form.blockName.trim(),
        blockType: form.blockType,
        content: form.blockType === 'html' ? form.content : null,
        cover: form.blockType === 'image' ? form.cover : null,
        linkUrl: form.linkUrl || null,
        sort: form.sort || 0,
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateBlock(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addBlock(payload)
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
      t('page.cms.block.deleteConfirm', { name: row.blockName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteBlock(row.blockId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>
