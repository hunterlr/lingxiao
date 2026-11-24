<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.selectSite')" style="width: 200px" @change="handleSiteChange">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        :title="t('page.cms.nav.hint')"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:nav:add'" type="primary" :icon="Plus" @click="openNavAdd">
          {{ t('page.cms.nav.add') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="navKey" :label="t('page.cms.nav.navKey')" width="150" />
        <el-table-column prop="navName" :label="t('page.cms.nav.navName')" min-width="160" />
        <el-table-column prop="position" :label="t('page.cms.nav.position')" width="120" />
        <el-table-column :label="t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">
              {{ row.status === '0' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openItems(row)">
                {{ t('page.cms.nav.itemsTitle') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:nav:edit'" link type="primary" @click="openNavEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:nav:remove'" link type="danger" @click="handleNavDelete(row)">
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="navDialogVisible"
      :title="navIsEdit ? t('page.cms.nav.edit') : t('page.cms.nav.add')"
      width="560px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="navFormRef" :model="navForm" :rules="navRules" label-width="100px">
        <el-form-item :label="t('page.cms.nav.navKey')" prop="navKey">
          <el-input v-model="navForm.navKey" maxlength="64" :placeholder="t('page.cms.nav.keyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.nav.navName')" prop="navName">
          <el-input v-model="navForm.navName" maxlength="100" :placeholder="t('page.cms.nav.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.nav.position')" prop="position">
          <el-select v-model="navForm.position" style="width: 100%">
            <el-option :label="t('page.cms.nav.positionTop')" value="top" />
            <el-option :label="t('page.cms.nav.positionFooter')" value="footer" />
            <el-option :label="t('page.cms.nav.positionMobile')" value="mobile" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="navForm.status">
            <el-radio value="0">{{ t('common.enabled') }}</el-radio>
            <el-radio value="1">{{ t('common.disabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="navForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="navDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="navSaving" @click="submitNav">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="itemDrawerVisible" :title="itemDrawerTitle" size="900px" append-to-body>
      <div class="toolbar">
        <el-button v-hasPerm="'platform:cms:nav:item:add'" type="primary" :icon="Plus" @click="openItemAdd(null)">
          {{ t('page.cms.nav.addItem') }}
        </el-button>
      </div>
      <el-table v-loading="itemLoading" :data="itemRows" border row-key="itemId" :tree-props="{ children: 'children' }" default-expand-all>
        <el-table-column prop="itemName" :label="t('page.cms.nav.itemName')" min-width="180" />
        <el-table-column :label="t('page.cms.nav.targetType')" width="110" align="center">
          <template #default="{ row }">{{ targetTypeLabel(row.targetType) }}</template>
        </el-table-column>
        <el-table-column :label="t('page.cms.nav.target')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ targetLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('page.cms.nav.visible')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.visible === '0' ? 'success' : 'info'" size="small">
              {{ row.visible === '0' ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="70" align="center" />
        <el-table-column :label="t('common.operation')" width="210" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:nav:item:add'" link type="primary" @click="openItemAdd(row)">
                {{ t('page.cms.nav.addChild') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:nav:item:edit'" link type="primary" @click="openItemEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:nav:item:remove'" link type="danger" @click="handleItemDelete(row)">
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog
      v-model="itemDialogVisible"
      :title="itemIsEdit ? t('page.cms.nav.editItem') : t('page.cms.nav.addItem')"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="110px">
        <el-form-item :label="t('page.cms.nav.parent')">
          <el-tree-select
            v-model="itemForm.parentId"
            :data="parentOptions"
            :props="{ label: 'itemName', children: 'children' }"
            node-key="itemId"
            check-strictly
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('page.cms.nav.itemName')" prop="itemName">
          <el-input v-model="itemForm.itemName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('page.cms.nav.targetType')" prop="targetType">
          <el-select v-model="itemForm.targetType" style="width: 100%" @change="handleTargetTypeChange">
            <el-option :label="t('page.cms.nav.targetCategory')" value="category" />
            <el-option :label="t('page.cms.nav.targetPage')" value="page" />
            <el-option :label="t('page.cms.nav.targetLink')" value="link" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="itemForm.targetType === 'category'" :label="t('page.cms.nav.targetCategory')">
          <el-select v-model="itemForm.targetId" filterable style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.categoryName" :value="item.categoryId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="itemForm.targetType === 'page'" :label="t('page.cms.nav.targetPage')">
          <el-select v-model="itemForm.targetId" filterable style="width: 100%">
            <el-option v-for="item in pageOptions" :key="item.pageId" :label="`${item.pageName} (${item.pageKey})`" :value="item.pageId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="itemForm.targetType === 'link'" :label="t('page.cms.nav.url')" prop="url">
          <el-input v-model="itemForm.url" maxlength="500" :placeholder="t('page.cms.nav.urlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('page.cms.nav.icon')">
          <el-input v-model="itemForm.icon" maxlength="64" :placeholder="t('page.cms.nav.iconPlaceholder')" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('page.cms.nav.visible')">
              <el-radio-group v-model="itemForm.visible">
                <el-radio value="0">{{ t('common.yes') }}</el-radio>
                <el-radio value="1">{{ t('common.no') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('common.status')">
              <el-radio-group v-model="itemForm.status">
                <el-radio value="0">{{ t('common.enabled') }}</el-radio>
                <el-radio value="1">{{ t('common.disabled') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="itemForm.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('common.remark')">
          <el-input v-model="itemForm.remark" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="itemDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="itemSaving" @click="submitItem">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  addNav,
  addNavItem,
  deleteNav,
  deleteNavItem,
  listCategories,
  listNavItems,
  listNavs,
  listPages,
  listSiteOptions,
  updateNav,
  updateNavItem
} from '@/api/cms'

/**
 * CMS 导航管理：导航下挂树形导航项，目标支持栏目 / 单页 / 外链。
 * 目标是否存在、是否同站点由后端校验；门户只下发有效目标。
 */
const { t } = useI18n()

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const categoryOptions = ref([])
const pageOptions = ref([])

async function loadSites() {
  sites.value = (await listSiteOptions()) || []
  if (!siteId.value && sites.value.length) {
    siteId.value = sites.value[0].siteId
  }
}

async function loadReferenceOptions() {
  categoryOptions.value = []
  pageOptions.value = []
  if (!siteId.value) return
  const [categories, pages] = await Promise.all([
    listCategories(siteId.value).catch(() => []),
    listPages({ siteId: siteId.value, pageNum: 1, pageSize: 100 }).catch(() => ({ records: [] }))
  ])
  categoryOptions.value = categories || []
  pageOptions.value = pages.records || []
}

async function loadList() {
  if (!siteId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = (await listNavs(siteId.value)) || []
  } finally {
    loading.value = false
  }
}

async function handleSiteChange() {
  await loadReferenceOptions()
  await loadList()
}

onMounted(async () => {
  await loadSites()
  await loadReferenceOptions()
  await loadList()
})

/* ==================== 导航表单 ==================== */
const navDialogVisible = ref(false)
const navSaving = ref(false)
const navIsEdit = ref(false)
const navId = ref('')
const navFormRef = ref(null)

const emptyNavForm = () => ({
  navKey: '',
  navName: '',
  position: 'top',
  status: '0',
  remark: ''
})

const navForm = reactive(emptyNavForm())

const navRules = {
  navKey: [{ required: true, message: t('page.cms.nav.keyRequired'), trigger: 'blur' }],
  navName: [{ required: true, message: t('page.cms.nav.nameRequired'), trigger: 'blur' }],
  position: [{ required: true, message: t('page.cms.nav.positionRequired'), trigger: 'change' }]
}

function openNavAdd() {
  Object.assign(navForm, emptyNavForm())
  navIsEdit.value = false
  navId.value = ''
  navDialogVisible.value = true
}

function openNavEdit(row) {
  Object.assign(navForm, {
    navKey: row.navKey || '',
    navName: row.navName || '',
    position: row.position || 'top',
    status: row.status || '0',
    remark: row.remark || ''
  })
  navIsEdit.value = true
  navId.value = row.navId
  navDialogVisible.value = true
}

async function submitNav() {
  await navFormRef.value.validate(async (valid) => {
    if (!valid) return
    navSaving.value = true
    try {
      const payload = {
        siteId: siteId.value,
        navKey: navForm.navKey.trim(),
        navName: navForm.navName.trim(),
        position: navForm.position,
        status: navForm.status,
        remark: navForm.remark || null
      }
      if (navIsEdit.value) {
        await updateNav(navId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addNav(payload)
        ElMessage.success(t('common.addSuccess'))
      }
      navDialogVisible.value = false
      await loadList()
    } finally {
      navSaving.value = false
    }
  })
}

async function handleNavDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.nav.deleteConfirm', { name: row.navName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteNav(row.navId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 导航项 ==================== */
const itemDrawerVisible = ref(false)
const itemDrawerTitle = ref('')
const itemLoading = ref(false)
const itemRows = ref([])
const currentNav = ref(null)

async function openItems(row) {
  currentNav.value = row
  itemDrawerTitle.value = `${row.navName} · ${t('page.cms.nav.itemsTitle')}`
  itemDrawerVisible.value = true
  await loadItems()
}

async function loadItems() {
  if (!currentNav.value) return
  itemLoading.value = true
  try {
    itemRows.value = (await listNavItems(currentNav.value.navId)) || []
  } finally {
    itemLoading.value = false
  }
}

function flatten(items, result = []) {
  for (const item of items || []) {
    result.push(item)
    flatten(item.children, result)
  }
  return result
}

const parentOptions = computed(() => {
  const roots = [{ itemId: 0, itemName: t('page.cms.nav.root') }]
  const flat = flatten(itemRows.value)
  if (!flat.length) return roots
  const map = {}
  const nodes = flat.map((item) => {
    const node = { itemId: item.itemId, itemName: item.itemName, children: [] }
    map[item.itemId] = node
    return node
  })
  const tree = []
  for (const item of flat) {
    const node = map[item.itemId]
    const parent = map[item.parentId]
    if (parent) parent.children.push(node)
    else tree.push(node)
  }
  return roots.concat(tree)
})

const itemDialogVisible = ref(false)
const itemSaving = ref(false)
const itemIsEdit = ref(false)
const itemId = ref('')
const itemFormRef = ref(null)

const emptyItemForm = () => ({
  parentId: 0,
  itemName: '',
  targetType: 'category',
  targetId: null,
  url: '',
  icon: '',
  visible: '0',
  status: '0',
  sort: 0,
  remark: ''
})

const itemForm = reactive(emptyItemForm())

const itemRules = {
  itemName: [{ required: true, message: t('page.cms.nav.itemNameRequired'), trigger: 'blur' }],
  targetType: [{ required: true, message: t('page.cms.nav.targetTypeRequired'), trigger: 'change' }],
  url: [
    {
      validator: (rule, value, callback) => {
        if (itemForm.targetType === 'link' && !String(value || '').trim()) {
          return callback(new Error(t('page.cms.nav.urlRequired')))
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openItemAdd(parent) {
  Object.assign(itemForm, emptyItemForm())
  itemForm.parentId = parent ? parent.itemId : 0
  itemIsEdit.value = false
  itemId.value = ''
  itemDialogVisible.value = true
}

function openItemEdit(row) {
  Object.assign(itemForm, {
    parentId: row.parentId || 0,
    itemName: row.itemName || '',
    targetType: row.targetType || 'category',
    targetId: row.targetId || null,
    url: row.url || '',
    icon: row.icon || '',
    visible: row.visible || '0',
    status: row.status || '0',
    sort: row.sort || 0,
    remark: row.remark || ''
  })
  itemIsEdit.value = true
  itemId.value = row.itemId
  itemDialogVisible.value = true
}

function handleTargetTypeChange() {
  itemForm.targetId = null
  itemForm.url = ''
}

async function submitItem() {
  await itemFormRef.value.validate(async (valid) => {
    if (!valid) return
    itemSaving.value = true
    try {
      const payload = {
        parentId: itemForm.parentId || 0,
        itemName: itemForm.itemName.trim(),
        targetType: itemForm.targetType,
        targetId: itemForm.targetType === 'link' ? null : itemForm.targetId,
        url: itemForm.targetType === 'link' ? itemForm.url : null,
        icon: itemForm.icon || null,
        visible: itemForm.visible,
        status: itemForm.status,
        sort: itemForm.sort || 0,
        remark: itemForm.remark || null
      }
      if (itemIsEdit.value) {
        await updateNavItem(currentNav.value.navId, itemId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addNavItem(currentNav.value.navId, payload)
        ElMessage.success(t('common.addSuccess'))
      }
      itemDialogVisible.value = false
      await loadItems()
    } finally {
      itemSaving.value = false
    }
  })
}

async function handleItemDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.nav.itemDeleteConfirm', { name: row.itemName }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteNavItem(currentNav.value.navId, row.itemId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadItems()
}

function targetTypeLabel(type) {
  if (type === 'category') return t('page.cms.nav.targetCategory')
  if (type === 'page') return t('page.cms.nav.targetPage')
  return t('page.cms.nav.targetLink')
}

function targetLabel(row) {
  if (row.targetType === 'link') return row.url || '-'
  if (row.targetType === 'category') {
    const category = categoryOptions.value.find((item) => String(item.categoryId) === String(row.targetId))
    return category ? category.categoryName : '-'
  }
  const page = pageOptions.value.find((item) => String(item.pageId) === String(row.targetId))
  return page ? page.pageName : '-'
}
</script>
