<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 180px" @change="handleSearch">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.banner.bannerGroup')">
          <el-input v-model="query.group" clearable style="width: 140px" placeholder="home" @keyup.enter="handleSearch" @clear="handleSearch" />
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
        :title="t('page.cms.banner.hint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:banner:add'" type="primary" :icon="Plus" @click="openAdd">
          {{ t('page.cms.banner.add') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column :label="t('page.cms.banner.image')" width="140">
          <template #default="{ row }">
            <el-image
              v-if="row.image"
              :src="imageUrl(row.image)"
              fit="cover"
              style="width: 100px; height: 56px; border-radius: 4px"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('page.cms.banner.titleLabel')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="bannerGroup" :label="t('page.cms.banner.bannerGroup')" width="120" />
        <el-table-column :label="t('page.cms.banner.timeRange')" min-width="260">
          <template #default="{ row }">
            {{ row.startTime || '-' }} ~ {{ row.endTime || '-' }}
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
        <el-table-column :label="t('common.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:banner:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:banner:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.banner.edit') : t('page.cms.banner.add')"
      width="720px"
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
            <el-form-item :label="t('page.cms.banner.bannerGroup')" prop="bannerGroup">
              <el-input v-model="form.bannerGroup" maxlength="32" placeholder="home" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.banner.titleLabel')">
              <el-input v-model="form.title" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.banner.image')">
              <MediaPicker v-model="form.image" :fallback="form.title" :placeholder="t('page.cms.banner.imagePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.banner.linkUrl')">
              <el-input v-model="form.linkUrl" maxlength="500" :placeholder="t('page.cms.banner.linkPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.banner.startTime')">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.banner.endTime')">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
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
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
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
import MediaPicker from '@/components/MediaPicker.vue'
import { resolveAssetUrl } from '@/utils/asset'
import {
  addBanner,
  deleteBanner,
  getBanner,
  listBanners,
  listSiteOptions,
  updateBanner
} from '@/api/cms'

/**
 * CMS 轮播管理：按 banner_group 分组，门户只取启用且处于起止时间窗内的记录。
 * 时间留空表示不限制。
 */
const { t } = useI18n()

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, group: '', status: '' })

function imageUrl(value) {
  return resolveAssetUrl(value, { size: 'thumb' })
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
    const data = await listBanners({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      siteId: siteId.value || undefined,
      group: query.group || undefined,
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
  query.group = ''
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
  bannerGroup: 'home',
  title: '',
  image: '',
  linkUrl: '',
  startTime: null,
  endTime: null,
  sort: 0,
  status: '0',
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  siteId: [{ required: true, message: t('page.cms.common.siteRequired'), trigger: 'change' }],
  bannerGroup: [{ required: true, message: t('page.cms.banner.groupRequired'), trigger: 'blur' }]
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
  editingId.value = row.bannerId
  form.siteId = row.siteId
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getBanner(row.bannerId)
    Object.assign(form, {
      siteId: detail.siteId,
      bannerGroup: detail.bannerGroup || 'home',
      title: detail.title || '',
      image: detail.image || '',
      linkUrl: detail.linkUrl || '',
      startTime: detail.startTime || null,
      endTime: detail.endTime || null,
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
        bannerGroup: form.bannerGroup.trim(),
        title: form.title || null,
        image: form.image || null,
        linkUrl: form.linkUrl || null,
        startTime: form.startTime || null,
        endTime: form.endTime || null,
        sort: form.sort || 0,
        status: form.status,
        remark: form.remark || null
      }
      if (isEdit.value) {
        await updateBanner(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addBanner(payload)
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
      t('page.cms.banner.deleteConfirm', { name: row.title || row.bannerId }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteBanner(row.bannerId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}
</script>
