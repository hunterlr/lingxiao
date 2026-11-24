<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 200px" @change="handleSiteChange">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.category.categoryName')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.cms.category.namePlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="loadTree"
            @clear="loadTree"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option :label="t('common.enabled')" value="0" />
            <el-option :label="t('common.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadTree">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.cms.category.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-hasPerm="'platform:cms:category:add'"
            type="primary"
            :icon="Plus"
            :disabled="!siteId"
            @click="openAdd(null)"
          >
            {{ t('page.cms.category.add') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadTree">{{ t('common.refresh') }}</el-button>
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

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        row-key="categoryId"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll"
      >
        <el-table-column prop="categoryName" :label="t('page.cms.category.categoryName')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="categoryKey" :label="t('page.cms.category.categoryKey')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-mono">{{ row.categoryKey }}</span></template>
        </el-table-column>
        <el-table-column prop="slug" :label="t('page.cms.category.slug')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.slug || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" align="center" />
        <el-table-column prop="visible" :label="t('page.cms.category.visible')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.visible === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.visible === '0' ? t('page.cms.category.visible') : t('page.cms.category.hidden') }}
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
        <el-table-column :label="t('common.operation')" width="230" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:category:add'" link type="primary" @click="openAdd(row)">
                {{ t('page.cms.category.addChild') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:category:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:category:remove'" link type="danger" @click="handleDelete(row)">
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('page.cms.category.edit') : t('page.cms.category.add')"
      width="780px"
      top="6vh"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.parent')">
              <el-tree-select
                v-model="form.parentId"
                :data="parentOptions"
                :props="{ label: 'categoryName', children: 'children' }"
                node-key="categoryId"
                check-strictly
                :render-after-expand="false"
                style="width: 100%"
              />
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
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.categoryName')" prop="categoryName">
              <el-input v-model="form.categoryName" maxlength="100" :placeholder="t('page.cms.category.namePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.categoryKey')" prop="categoryKey">
              <el-input v-model="form.categoryKey" maxlength="64" :placeholder="t('page.cms.category.keyPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.model.model')">
              <el-select v-model="form.modelId" clearable style="width: 100%" :placeholder="t('page.cms.model.defaultModel')">
                <el-option v-for="item in modelOptions" :key="item.modelId" :label="item.modelName" :value="item.modelId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.slug')">
              <el-input v-model="form.slug" maxlength="100" :placeholder="t('page.cms.category.slugPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.path')">
              <el-input v-model="form.path" maxlength="255" :placeholder="t('page.cms.category.pathPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.template')">
              <el-input v-model="form.template" maxlength="100" :placeholder="t('page.cms.category.templatePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.icon')">
              <el-input v-model="form.icon" maxlength="64" :placeholder="t('page.cms.category.iconPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.category.cover')">
              <MediaPicker
                v-model="form.cover"
                :fallback="form.categoryName"
                :placeholder="t('page.cms.category.coverPlaceholder')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.category.visible')">
              <el-radio-group v-model="form.visible">
                <el-radio value="0">{{ t('page.cms.category.visible') }}</el-radio>
                <el-radio value="1">{{ t('page.cms.category.hidden') }}</el-radio>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  addCategory,
  deleteCategory,
  getCategory,
  getCategoryTree,
  listModelOptions,
  listSiteOptions,
  updateCategory
} from '@/api/cms'
import MediaPicker from '@/components/MediaPicker.vue'

/**
 * CMS 栏目管理。
 *
 * <p>树表直接用后端返回的 children；父栏目下拉复用同一棵树，
 * 只额外加一个“根栏目(0)”节点。成环与跨站点由后端校验。</p>
 */
const { t } = useI18n()

const sites = ref([])
const sitesLoading = ref(false)
const siteId = ref(null)
const modelOptions = ref([])
const loading = ref(false)
const rows = ref([])
const expandAll = ref(true)
const query = reactive({ keyword: '', status: '' })

const parentOptions = computed(() => [
  { categoryId: 0, categoryName: t('page.cms.category.root'), children: rows.value }
])

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

async function loadModelOptions() {
  modelOptions.value = []
  if (!siteId.value) return
  modelOptions.value = (await listModelOptions(siteId.value)) || []
}

async function loadTree() {
  if (!siteId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await getCategoryTree({
      siteId: siteId.value,
      keyword: query.keyword || undefined,
      status: query.status || undefined
    })
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  loadTree()
}

function handleSiteChange() {
  loadModelOptions()
  loadTree()
}

onMounted(async () => {
  await loadSites()
  await loadModelOptions()
  await loadTree()
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const emptyForm = () => ({
  parentId: 0,
  modelId: null,
  categoryKey: '',
  categoryName: '',
  slug: '',
  path: '',
  template: '',
  icon: '',
  cover: '',
  sort: 0,
  visible: '0',
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  categoryName: [{ required: true, message: t('page.cms.category.nameRequired'), trigger: 'blur' }],
  categoryKey: [{ required: true, message: t('page.cms.category.keyRequired'), trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

function openAdd(parent) {
  resetForm()
  form.parentId = parent ? parent.categoryId : 0
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.categoryId
  Object.assign(form, {
    parentId: row.parentId || 0,
    modelId: row.modelId || null,
    categoryKey: row.categoryKey || '',
    categoryName: row.categoryName || '',
    slug: row.slug || '',
    path: row.path || '',
    template: row.template || '',
    icon: row.icon || '',
    cover: row.cover || '',
    sort: row.sort || 0,
    visible: row.visible || '0',
    status: row.status || '0',
    remark: row.remark || ''
  })
  dialogVisible.value = true
  const detail = await getCategory(row.categoryId).catch(() => null)
  if (detail) {
    Object.assign(form, {
      parentId: detail.parentId || 0,
      modelId: detail.modelId || null,
      categoryKey: detail.categoryKey || '',
      categoryName: detail.categoryName || '',
      slug: detail.slug || '',
      path: detail.path || '',
      template: detail.template || '',
      icon: detail.icon || '',
      cover: detail.cover || '',
      sort: detail.sort || 0,
      visible: detail.visible || '0',
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
        siteId: siteId.value,
        parentId: form.parentId || 0,
        modelId: form.modelId || 0,
        categoryKey: form.categoryKey.trim(),
        categoryName: form.categoryName.trim(),
        slug: form.slug || null,
        path: form.path || null,
        template: form.template || null,
        icon: form.icon || null,
        cover: form.cover || null,
        sort: form.sort || 0,
        visible: form.visible,
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateCategory(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addCategory(payload)
        ElMessage.success(t('common.addSuccess'))
      }
      dialogVisible.value = false
      await loadTree()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.category.deleteConfirm', { name: row.categoryName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteCategory(row.categoryId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadTree()
}
</script>

<style scoped>
/* 封面字段的布局由 MediaPicker 自己负责，本页不再重复样式。 */
</style>
