<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select
            v-model="siteId"
            :placeholder="t('page.cms.common.allSites')"
            clearable
            style="width: 180px"
            @change="handleSearch"
          >
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.article.articleTitle')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.cms.recycle.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon :title="t('page.cms.recycle.hint')" style="margin-bottom: 12px" />

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.cms.recycle.total', { total }) }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe row-key="articleId">
        <el-table-column prop="title" :label="t('page.cms.article.articleTitle')" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="recycle-title">{{ row.title }}</span>
            <span v-if="row.slug" class="recycle-slug">/{{ row.slug }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="siteName" :label="t('page.cms.common.site')" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.siteName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="t('page.cms.article.category')" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('page.cms.recycle.statusBeforeDelete')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deleteByName" :label="t('page.cms.recycle.deletedBy')" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deleteByName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deleteTime" :label="t('page.cms.recycle.deletedAt')" width="170">
          <template #default="{ row }">{{ row.deleteTime || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-hasPerm="'platform:cms:article:restore'" link type="primary" @click="handleRestore(row)">
                {{ t('page.cms.recycle.restore') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:article:purge'" link type="danger" @click="handlePurge(row)">
                {{ t('page.cms.recycle.purge') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="t('page.cms.recycle.empty')" />
        </template>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listRecycleArticles, listSiteOptions, purgeArticle, restoreArticle } from '@/api/cms'

/**
 * 文章回收站。
 *
 * <p>删除文章只做逻辑删除，标签关联保留；恢复后统一回到草稿，避免
 * “恢复即重新对外发布”。彻底删除才清标签关联并物理删正文。</p>
 *
 * <p>本页权限与按钮权限分开：进入页面需要 platform:cms:article:recycle，
 * 恢复 / 彻底删除另有独立权限码，便于只给主编开放恢复、不开物理删除。</p>
 */
const { t } = useI18n()

const sites = ref([])
const siteId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })

function statusLabel(status) {
  if (status === '1') return t('page.cms.article.published')
  if (status === '2') return t('page.cms.article.offline')
  return t('page.cms.article.draft')
}

function statusTagType(status) {
  if (status === '1') return 'success'
  if (status === '2') return 'warning'
  return 'info'
}

async function loadSites() {
  sites.value = await listSiteOptions()
}

async function loadList() {
  loading.value = true
  try {
    const data = await listRecycleArticles({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      siteId: siteId.value || undefined,
      keyword: query.keyword || undefined
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
  siteId.value = null
  query.keyword = ''
  query.pageNum = 1
  loadList()
}

async function handleRestore(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.recycle.restoreConfirm', { title: row.title }),
      t('page.cms.recycle.restore'),
      { type: 'warning', confirmButtonText: t('page.cms.recycle.restore'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await restoreArticle(row.articleId)
  ElMessage.success(t('page.cms.recycle.restoreSuccess'))
  await loadList()
}

async function handlePurge(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.recycle.purgeConfirm', { title: row.title }),
      t('page.cms.recycle.purge'),
      { type: 'error', confirmButtonText: t('page.cms.recycle.purge'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await purgeArticle(row.articleId)
  ElMessage.success(t('page.cms.recycle.purgeSuccess'))
  await loadList()
}

onMounted(async () => {
  await loadSites()
  await loadList()
})
</script>

<style scoped>
.recycle-title {
  font-weight: 600;
}

.recycle-slug {
  margin-left: 8px;
  color: var(--lx-text-secondary);
  font-size: 12px;
}
</style>
