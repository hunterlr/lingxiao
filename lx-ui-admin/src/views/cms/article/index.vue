<template>
  <div class="page-container">
    <div class="page-card">
      <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
        <el-form-item :label="t('page.cms.common.site')">
          <el-select v-model="siteId" :placeholder="t('page.cms.common.allSites')" clearable style="width: 180px" @change="handleSiteChange">
            <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.article.category')">
          <el-tree-select
            v-model="query.categoryId"
            :data="categoryTree"
            :props="{ label: 'categoryName', children: 'children' }"
            node-key="categoryId"
            check-strictly
            clearable
            :render-after-expand="false"
            :placeholder="t('common.all')"
            style="width: 180px"
            :disabled="!siteId"
          />
        </el-form-item>
        <el-form-item :label="t('page.cms.article.tag')">
          <el-select v-model="query.tagId" clearable :placeholder="t('common.all')" style="width: 150px" :disabled="!siteId">
            <el-option v-for="tag in tags" :key="tag.tagId" :label="tag.tagName" :value="tag.tagId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('page.cms.article.articleTitle')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('page.cms.article.keywordPlaceholder')"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option :label="t('page.cms.article.draft')" value="0" />
            <el-option :label="t('page.cms.article.published')" value="1" />
            <el-option :label="t('page.cms.article.offline')" value="2" />
            <el-option :label="t('page.cms.article.scheduled')" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="text-secondary">{{ t('page.cms.article.hint') }}</span>
        </div>
        <div class="toolbar-right">
          <el-button v-hasPerm="'platform:cms:article:add'" type="primary" :icon="Plus" :disabled="!sites.length" @click="openAdd">
            {{ t('page.cms.article.add') }}
          </el-button>
          <el-button v-hasPerm="'platform:cms:article:recycle'" :icon="Delete" @click="goRecycle">
            {{ t('page.cms.recycle.title') }}
          </el-button>
          <el-button :icon="Refresh" @click="loadList">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-alert
        v-if="!sites.length && !sitesLoading"
        type="warning"
        :closable="false"
        show-icon
        :title="t('page.cms.article.selectSiteFirst')"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="rows" border stripe row-key="articleId">
        <el-table-column prop="title" :label="t('page.cms.article.articleTitle')" min-width="260" show-overflow-tooltip />
        <el-table-column :label="t('page.cms.article.category')" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryNameMap[row.categoryId] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="authorName" :label="t('page.cms.article.author')" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.authorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" :label="t('page.cms.article.publishedAt')" width="160">
          <template #default="{ row }">{{ row.publishTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="scheduleTime" :label="t('page.cms.article.scheduleTime')" width="160">
          <template #default="{ row }">{{ row.scheduleTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="viewCount" :label="t('page.cms.article.views')" width="90" align="center" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
        <el-table-column :label="t('common.operation')" width="480" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openPreview(row)">{{ t('page.cms.article.preview') }}</el-button>
              <el-button v-hasPerm="'platform:cms:article:edit'" link type="primary" @click="openEdit(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:article:edit'" link type="primary" @click="openI18n(row)">
                {{ t('page.cms.i18n.entry') }}
              </el-button>
              <el-button
                v-hasPerm="'platform:cms:article:version'"
                link
                type="info"
                @click="openVersions(row)"
              >
                {{ t('page.cms.article.versionHistory') }}
              </el-button>
              <el-button
                v-if="row.status !== '1'"
                v-hasPerm="'platform:cms:article:publish'"
                link
                type="success"
                @click="handlePublish(row)"
              >
                {{ t('page.cms.article.publish') }}
              </el-button>
              <el-button
                v-else
                v-hasPerm="'platform:cms:article:offline'"
                link
                type="warning"
                @click="handleOffline(row)"
              >
                {{ t('page.cms.article.offlineAction') }}
              </el-button>
              <el-button
                v-if="row.status !== '1'"
                v-hasPerm="'platform:cms:article:schedule'"
                link
                type="primary"
                @click="openSchedule(row)"
              >
                {{ t('page.cms.article.schedule') }}
              </el-button>
              <el-button
                v-if="row.status === '3'"
                v-hasPerm="'platform:cms:article:schedule'"
                link
                type="warning"
                @click="handleCancelSchedule(row)"
              >
                {{ t('page.cms.article.cancelSchedule') }}
              </el-button>
              <el-button v-hasPerm="'platform:cms:article:remove'" link type="danger" @click="handleDelete(row)">
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
      :title="isEdit ? t('page.cms.article.edit') : t('page.cms.article.add')"
      width="1000px"
      top="5vh"
      append-to-body
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" v-loading="dialogLoading" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('page.cms.common.site')" prop="siteId">
              <el-select v-model="form.siteId" :disabled="isEdit" style="width: 100%" @change="handleFormSiteChange">
                <el-option v-for="site in sites" :key="site.siteId" :label="site.siteName" :value="site.siteId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.article.category')" prop="categoryId">
              <el-tree-select
                v-model="form.categoryId"
                :data="formCategoryTree"
                :props="{ label: 'categoryName', children: 'children' }"
                node-key="categoryId"
                check-strictly
                :render-after-expand="false"
                style="width: 100%"
                @change="handleFormCategoryChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.article.tag')">
              <el-select v-model="form.tagIds" multiple collapse-tags :placeholder="t('page.cms.article.tagPlaceholder')" style="width: 100%">
                <el-option v-for="tag in formTags" :key="tag.tagId" :label="tag.tagName" :value="tag.tagId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.article.articleTitle')" prop="title">
              <el-input v-model="form.title" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('page.cms.article.slug')">
              <el-input v-model="form.slug" maxlength="200" :placeholder="t('page.cms.category.slugPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('common.sort')">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.article.cover')">
              <MediaPicker
                v-model="form.cover"
                :fallback="form.title"
                :placeholder="t('page.cms.article.coverPlaceholder')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.article.summary')">
              <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="t('page.cms.article.summaryPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('page.cms.article.source')">
              <el-input v-model="form.source" maxlength="100" :placeholder="t('page.cms.article.sourcePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="t('page.cms.article.sourceUrl')">
              <el-input v-model="form.sourceUrl" maxlength="255" :placeholder="t('page.cms.article.sourceUrlPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="t('page.cms.article.content')" prop="content">
              <RichEditor v-model="form.content" height="340px" :placeholder="t('page.cms.article.contentPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col v-if="formModelFields.length" :span="24">
            <el-divider content-position="left">{{ t('page.cms.model.extFields') }}</el-divider>
            <CmsExtFields v-model="form.ext" :fields="formModelFields" />
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

    <CmsI18nDialog v-model="i18nVisible" owner-type="article" :owner-id="i18nOwnerId" :source="i18nSource" />


    <el-dialog
      v-model="scheduleVisible"
      :title="t('page.cms.article.scheduleTitle')"
      width="440px"
      append-to-body
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('page.cms.article.scheduleHint')"
        style="margin-bottom: 16px"
      />
      <el-form label-width="110px">
        <el-form-item :label="t('page.cms.article.scheduleTime')" required>
          <el-date-picker
            v-model="scheduleTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="t('page.cms.article.scheduleRequired')"
            :disabled-date="disablePastDate"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="scheduleVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="scheduleSaving" @click="submitSchedule">
            {{ t('page.cms.article.scheduleSubmit') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="versionVisible"
      :title="t('page.cms.article.versionTitle', { title: versionArticle ? versionArticle.title : '' })"
      size="860px"
      append-to-body
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('page.cms.article.versionHint')"
        style="margin-bottom: 16px"
      />
      <el-table v-loading="versionLoading" :data="versionRows" border>
        <el-table-column prop="versionNo" :label="t('page.cms.article.versionNo')" width="80" align="center" />
        <el-table-column :label="t('page.cms.article.changeType')" width="110">
          <template #default="{ row }">{{ changeTypeLabel(row.changeType) }}</template>
        </el-table-column>
        <el-table-column prop="title" :label="t('page.cms.article.articleTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status" :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" :label="t('page.cms.article.versionOperator')" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="165" />
        <el-table-column :label="t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersionDetail(row)">
              {{ t('page.cms.article.preview') }}
            </el-button>
            <el-button
              v-hasPerm="'platform:cms:article:rollback'"
              link
              type="warning"
              :disabled="row.versionNo === latestVersionNo"
              @click="handleRollback(row)"
            >
              {{ t('page.cms.article.versionRollback') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="versionQuery.pageNum"
          v-model:page-size="versionQuery.pageSize"
          :total="versionTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadVersions"
          @current-change="loadVersions"
        />
      </div>
    </el-drawer>


    <el-dialog
      v-model="versionDetailVisible"
      :title="t('page.cms.article.versionDetailTitle', { no: versionDetail ? versionDetail.versionNo : '' })"
      width="820px"
      top="5vh"
      append-to-body
      destroy-on-close
    >
      <el-descriptions v-if="versionDetail" :column="2" border>
        <el-descriptions-item :label="t('page.cms.article.changeType')">
          {{ changeTypeLabel(versionDetail.changeType) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('common.createTime')">
          {{ versionDetail.createTime || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.cms.article.articleTitle')" :span="2">
          {{ versionDetail.title }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.cms.article.slug')">
          {{ versionDetail.slug || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.cms.article.versionOperator')">
          {{ versionDetail.operatorName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.cms.article.summary')" :span="2">
          {{ versionDetail.summary || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="version-content">
        <div class="article-preview" v-html="versionDetail ? versionDetail.content : ''"></div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="versionDetailVisible = false">{{ t('common.close') }}</el-button>
          <el-button
            v-if="versionDetail && versionDetail.versionNo !== latestVersionNo"
            v-hasPerm="'platform:cms:article:rollback'"
            type="warning"
            @click="handleRollback(versionDetail)"
          >
            {{ t('page.cms.article.versionRollback') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" :title="previewTitle" size="640px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('page.cms.article.previewTitle')"
        :description="t('page.cms.article.previewDesc')"
        style="margin-bottom: 16px"
      />
      <div class="article-preview" v-html="previewContent"></div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="previewVisible = false">{{ t('common.close') }}</el-button>
          <el-button type="primary" :loading="previewTicketLoading" @click="openPortalPreview">
            {{ t('page.cms.article.openPortalPreview') }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import RichEditor from '@/components/RichEditor.vue'
import MediaPicker from '@/components/MediaPicker.vue'
import CmsExtFields from '@/components/CmsExtFields.vue'
import CmsI18nDialog from '@/components/CmsI18nDialog.vue'
import {
  addArticle,
  cancelScheduleArticle,
  createArticlePreviewTicket,
  deleteArticle,
  getArticle,
  getArticleVersion,
  getCategoryTree,
  listArticles,
  listArticleVersions,
  listModelFields,
  listSiteOptions,
  listTagOptions,
  offlineArticle,
  publishArticle,
  rollbackArticleVersion,
  scheduleArticle,
  updateArticle
} from '@/api/cms'

/**
 * CMS 文章管理。
 *
 * <p>正文走 RichEditor 提交 HTML，服务端会再做一次白名单清洗；
 * 预览用 v-html 展示的是后端清洗后的结果，不直接信任编辑器内容。</p>
 *
 * <p>发布 / 下线是独立动作：新建文章一律草稿，编辑保存不会改变状态，
 * 避免“改个错别字把下线文章又发出去”。</p>
 */
const { t } = useI18n()
const router = useRouter()

const sites = ref([])
const sitesLoading = ref(false)
const siteId = ref(null)
const categoryTree = ref([])
const flatCategories = ref([])
const tags = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, categoryId: null, tagId: null, keyword: '', status: '' })

const categoryNameMap = computed(() => {
  const map = {}
  for (const category of flatCategories.value) {
    map[category.categoryId] = category.categoryName
  }
  return map
})

function statusLabel(status) {
  if (status === '1') return t('page.cms.article.published')
  if (status === '2') return t('page.cms.article.offline')
  if (status === '3') return t('page.cms.article.scheduled')
  return t('page.cms.article.draft')
}

function statusTagType(status) {
  if (status === '1') return 'success'
  if (status === '2') return 'warning'
  if (status === '3') return 'primary'
  return 'info'
}

function flattenCategories(nodes, result = []) {
  for (const node of nodes || []) {
    result.push(node)
    flattenCategories(node.children, result)
  }
  return result
}

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

async function loadCategoryOptions() {
  query.categoryId = null
  query.tagId = null
  categoryTree.value = []
  flatCategories.value = []
  tags.value = []
  if (!siteId.value) return
  const [tree, tagList] = await Promise.all([
    getCategoryTree({ siteId: siteId.value }),
    listTagOptions(siteId.value)
  ])
  categoryTree.value = tree || []
  flatCategories.value = flattenCategories(tree || [])
  tags.value = tagList || []
}

async function loadList() {
  loading.value = true
  try {
    const data = await listArticles({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      siteId: siteId.value || undefined,
      categoryId: query.categoryId || undefined,
      tagId: query.tagId || undefined,
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
  query.categoryId = null
  query.tagId = null
  query.keyword = ''
  query.status = ''
  handleSearch()
}

function handleSiteChange() {
  loadCategoryOptions()
  handleSearch()
}

function goRecycle() {
  router.push('/cms/recycle')
}

onMounted(async () => {
  await loadSites()
  await loadCategoryOptions()
  await loadList()
})

/* ==================== 新增 / 编辑 ==================== */
const dialogVisible = ref(false)

/* ==================== 翻译（人工填写） ==================== */

const i18nVisible = ref(false)
const i18nOwnerId = ref('')
const i18nSource = ref({ title: '', summary: '', content: '' })

/** 打开翻译弹窗：先取详情，把默认语言内容作为“复制原文”的来源。 */
async function openI18n(row) {
  try {
    const detail = await getArticle(row.articleId)
    i18nOwnerId.value = String(row.articleId)
    i18nSource.value = {
      title: detail?.title || row.title || '',
      summary: detail?.summary || row.summary || '',
      content: detail?.content || ''
    }
    i18nVisible.value = true
  } catch (e) {
    ElMessage.error(t('page.cms.i18n.loadFailed'))
  }
}
const dialogLoading = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)
const formCategoryTree = ref([])
const formTags = ref([])
const formModelFields = ref([])

const emptyForm = () => ({
  siteId: siteId.value,
  categoryId: null,
  tagIds: [],
  title: '',
  slug: '',
  summary: '',
  cover: '',
  content: '',
  source: '',
  sourceUrl: '',
  sort: 0,
  remark: '',
  ext: {}
})

const form = reactive(emptyForm())

const rules = {
  siteId: [{ required: true, message: t('page.cms.common.siteRequired'), trigger: 'change' }],
  categoryId: [{ required: true, message: t('page.cms.article.categoryRequired'), trigger: 'change' }],
  title: [{ required: true, message: t('page.cms.article.titleRequired'), trigger: 'blur' }],
  content: [
    {
      validator: (rule, value, callback) => {
        const text = (value || '').replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
        if (!text) return callback(new Error(t('page.cms.article.contentRequired')))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

async function loadFormOptions(targetSiteId) {
  formCategoryTree.value = []
  formTags.value = []
  formModelFields.value = []
  if (!targetSiteId) return
  const [tree, tagList] = await Promise.all([
    getCategoryTree({ siteId: targetSiteId }),
    listTagOptions(targetSiteId)
  ])
  formCategoryTree.value = tree || []
  formTags.value = tagList || []
}

/** 在栏目树里找当前节点，拿它绑定的模型。 */
function findCategoryNode(nodes, categoryId) {
  for (const node of nodes || []) {
    if (String(node.categoryId) === String(categoryId)) return node
    const found = findCategoryNode(node.children, categoryId)
    if (found) return found
  }
  return null
}

/** 栏目切换后加载对应模型的扩展字段；旧值保留，后端会按新模型过滤。 */
async function loadFormModelFields(categoryId) {
  formModelFields.value = []
  const node = findCategoryNode(formCategoryTree.value, categoryId)
  if (!node || !node.modelId) return
  const fields = await listModelFields(node.modelId).catch(() => [])
  formModelFields.value = fields || []
}

function handleFormCategoryChange(value) {
  loadFormModelFields(value)
}

function handleFormSiteChange(value) {
  form.categoryId = null
  form.tagIds = []
  loadFormOptions(value)
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingId.value = ''
  isEdit.value = false
}

async function openAdd() {
  resetForm()
  form.siteId = siteId.value || (sites.value[0] && sites.value[0].siteId)
  await loadFormOptions(form.siteId)
  dialogVisible.value = true
}

async function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.articleId
  form.siteId = row.siteId
  await loadFormOptions(row.siteId)
  Object.assign(form, {
    siteId: row.siteId,
    categoryId: row.categoryId,
    title: row.title || '',
    slug: row.slug || '',
    summary: row.summary || '',
    cover: row.cover || '',
    source: row.source || '',
    sourceUrl: row.sourceUrl || '',
    sort: row.sort || 0,
    remark: row.remark || ''
  })
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const detail = await getArticle(row.articleId)
    Object.assign(form, {
      siteId: detail.siteId,
      categoryId: detail.categoryId,
      tagIds: detail.tagIds || [],
      title: detail.title || '',
      slug: detail.slug || '',
      summary: detail.summary || '',
      cover: detail.cover || '',
      content: detail.content || '',
      source: detail.source || '',
      sourceUrl: detail.sourceUrl || '',
      sort: detail.sort || 0,
      remark: detail.remark || '',
      ext: detail.ext || {}
    })
    await loadFormModelFields(detail.categoryId)
  } finally {
    dialogLoading.value = false
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
        siteId: form.siteId,
        categoryId: form.categoryId,
        tagIds: form.tagIds,
        title: form.title.trim(),
        slug: form.slug || null,
        summary: form.summary || null,
        cover: form.cover || null,
        content: form.content,
        source: form.source || null,
        sourceUrl: form.sourceUrl || null,
        sort: form.sort || 0,
        remark: form.remark || null,
        ext: form.ext || {}
      }
      if (isEdit.value) {
        await updateArticle(editingId.value, payload)
        ElMessage.success(t('common.saveSuccess'))
      } else {
        await addArticle(payload)
        ElMessage.success(t('common.addSuccess'))
      }
      dialogVisible.value = false
      await loadList()
    } finally {
      saving.value = false
    }
  })
}

/* ==================== 发布 / 下线 / 删除 ==================== */
async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.article.publishConfirm', { title: row.title }),
      t('page.cms.article.publish'),
      { type: 'info', confirmButtonText: t('page.cms.article.publish'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await publishArticle(row.articleId)
  ElMessage.success(t('common.success'))
  await loadList()
}

async function handleOffline(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.article.offlineConfirm', { title: row.title }),
      t('page.cms.article.offlineAction'),
      { type: 'warning', confirmButtonText: t('page.cms.article.offlineAction'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await offlineArticle(row.articleId)
  ElMessage.success(t('common.success'))
  await loadList()
}

/* ==================== 定时发布 ==================== */
const scheduleVisible = ref(false)
const scheduleSaving = ref(false)
const scheduleTime = ref('')
const scheduleRow = ref(null)

function formatLocalDateTime(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function disablePastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function openSchedule(row) {
  scheduleRow.value = row
  // 已有排期时默认沿用，改时间比重新选更方便
  scheduleTime.value = row.scheduleTime || formatLocalDateTime(new Date(Date.now() + 60 * 60 * 1000))
  scheduleVisible.value = true
}

async function submitSchedule() {
  if (!scheduleTime.value) {
    ElMessage.warning(t('page.cms.article.scheduleRequired'))
    return
  }
  const target = new Date(scheduleTime.value.replace(/-/g, '/')).getTime()
  if (!Number.isFinite(target) || target <= Date.now()) {
    ElMessage.warning(t('page.cms.article.scheduleFuture'))
    return
  }
  scheduleSaving.value = true
  try {
    await scheduleArticle(scheduleRow.value.articleId, { scheduleTime: scheduleTime.value })
    ElMessage.success(t('page.cms.article.scheduleSuccess'))
    scheduleVisible.value = false
    await loadList()
  } finally {
    scheduleSaving.value = false
  }
}

async function handleCancelSchedule(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.article.cancelScheduleConfirm', { title: row.title }),
      t('page.cms.article.cancelSchedule'),
      { type: 'warning', confirmButtonText: t('page.cms.article.cancelSchedule'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await cancelScheduleArticle(row.articleId)
  ElMessage.success(t('page.cms.article.cancelScheduleSuccess'))
  await loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.article.deleteConfirm', { title: row.title }),
      t('common.deleteTitle'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await deleteArticle(row.articleId)
  ElMessage.success(t('common.deleteSuccess'))
  await loadList()
}

/* ==================== 内容版本 ==================== */
const versionVisible = ref(false)
const versionLoading = ref(false)
const versionRows = ref([])
const versionTotal = ref(0)
const versionArticle = ref(null)
const versionQuery = reactive({ pageNum: 1, pageSize: 10 })
const latestVersionNo = ref(0)
const versionDetailVisible = ref(false)
const versionDetail = ref(null)

const changeTypeKeys = {
  create: 'page.cms.article.changeCreate',
  edit: 'page.cms.article.changeEdit',
  publish: 'page.cms.article.changePublish',
  rollback: 'page.cms.article.changeRollback',
  restore: 'page.cms.article.changeRestore'
}

function changeTypeLabel(type) {
  const key = changeTypeKeys[type]
  return key ? t(key) : (type || '-')
}

async function openVersions(row) {
  versionArticle.value = row
  versionQuery.pageNum = 1
  latestVersionNo.value = 0
  versionVisible.value = true
  await loadVersions()
}

async function loadVersions() {
  if (!versionArticle.value) return
  versionLoading.value = true
  try {
    const data = await listArticleVersions(versionArticle.value.articleId, {
      pageNum: versionQuery.pageNum,
      pageSize: versionQuery.pageSize
    })
    versionRows.value = data.records || []
    versionTotal.value = data.total || 0
    if (versionQuery.pageNum === 1 && versionRows.value.length) {
      latestVersionNo.value = versionRows.value[0].versionNo
    }
  } finally {
    versionLoading.value = false
  }
}

async function openVersionDetail(row) {
  versionDetail.value = await getArticleVersion(versionArticle.value.articleId, row.versionId)
  versionDetailVisible.value = true
}

async function handleRollback(row) {
  try {
    await ElMessageBox.confirm(
      t('page.cms.article.versionRollbackConfirm', { no: row.versionNo }),
      t('page.cms.article.versionRollback'),
      { type: 'warning', confirmButtonText: t('page.cms.article.versionRollback'), cancelButtonText: t('common.cancel') }
    )
  } catch (e) {
    return
  }
  await rollbackArticleVersion(versionArticle.value.articleId, row.versionId)
  ElMessage.success(t('page.cms.article.versionRollbackSuccess'))
  versionDetailVisible.value = false
  await loadVersions()
  await loadList()
}

/* ==================== 预览 ==================== */
const previewVisible = ref(false)
const previewTitle = ref('')
const previewContent = ref('')
const previewArticleId = ref('')
const previewTicketLoading = ref(false)

async function openPreview(row) {
  previewArticleId.value = row.articleId
  previewTitle.value = row.title
  previewContent.value = ''
  previewVisible.value = true
  const detail = await getArticle(row.articleId).catch(() => null)
  previewContent.value = (detail && detail.content) || `<p>${t('page.cms.article.noContent')}</p>`
}

/** 生成一次性票据后在新窗口打开门户预览；票据 10 分钟内有效且只能用一次。 */
async function openPortalPreview() {
  if (!previewArticleId.value) return
  previewTicketLoading.value = true
  try {
    const ticket = await createArticlePreviewTicket(previewArticleId.value)
    const base = String(import.meta.env.VITE_PORTAL_BASE_URL || 'http://localhost:3000').replace(/\/$/, '')
    window.open(`${base}/preview/${encodeURIComponent(ticket.ticket)}`, '_blank')
  } finally {
    previewTicketLoading.value = false
  }
}
</script>

<style scoped>
.article-preview {
  line-height: 1.8;
  font-size: 14px;
  color: var(--lx-text);
  word-break: break-word;
}

.version-content {
  margin-top: 16px;
  max-height: 48vh;
  overflow-y: auto;
}

.article-preview :deep(img) {
  max-width: 100%;
  height: auto;
}

.article-preview :deep(p) {
  margin: 0 0 10px;
}
</style>
