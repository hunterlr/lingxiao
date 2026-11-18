<template>
  <div class="file-library">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="query.keyword"
          :placeholder="t('fileLib.searchPlaceholder')"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-input
          v-if="!fixedBizType"
          v-model="query.bizType"
          :placeholder="t('fileLib.bizTypePlaceholder')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
      </div>
      <div class="toolbar-right">
        <el-button v-if="showUpload" v-hasPerm="'platform:file:upload'" type="primary" :icon="UploadFilled" @click="openUpload">
          {{ t('fileLib.upload') }}
        </el-button>
        <el-button :icon="Refresh" circle :title="t('common.refresh')" @click="loadList" />
      </div>
    </div>

    <div v-loading="loading" class="file-grid-wrapper">
      <el-empty v-if="!loading && !files.length" :description="t('fileLib.empty')" />

      <div v-else class="file-grid">
        <div v-for="file in files" :key="file.fileId" class="file-card">
          <div class="file-thumb" @click="openDetail(file)">
            <el-image
              v-if="isImage(file)"
              :src="file.thumbUrl || file.url"
              fit="cover"
              class="file-image"
            >
              <template #error>
                <div class="file-ext">{{ (file.ext || 'file').toUpperCase() }}</div>
              </template>
            </el-image>
            <div v-else class="file-ext">{{ (file.ext || 'file').toUpperCase() }}</div>
          </div>

          <div class="file-meta">
            <el-tooltip :content="file.displayName || file.originalName" placement="top">
              <div class="file-name">{{ file.displayName || file.originalName }}</div>
            </el-tooltip>
            <div v-if="isRenamed(file)" class="file-origin">
              {{ t('fileLib.originalName', { name: file.originalName }) }}
            </div>
            <div class="file-sub">
              <span>{{ formatFileSize(file.size) }}</span>
              <span v-if="file.width && file.height">{{ file.width }}×{{ file.height }}</span>
              <span>{{ file.bizType || 'common' }}</span>
            </div>
            <div class="file-remark" v-if="file.remark">{{ file.remark }}</div>
          </div>

          <div class="file-actions">
            <el-button link type="primary" @click="openDetail(file)">{{ t('common.detail') }}</el-button>
            <el-button v-hasPerm="'platform:file:rename'" link type="primary" @click="openRename(file)">{{ t('fileLib.rename') }}</el-button>
            <el-button link type="primary" @click="copyUrl(file)">{{ t('fileLib.copyLink') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[12, 24, 48, 96]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 上传 -->
    <el-dialog v-model="uploadVisible" :title="t('fileLib.uploadTitle')" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item :label="t('fileLib.bizType')">
          <el-input v-model="uploadForm.bizType" placeholder="common / notice / cms ..." />
        </el-form-item>
        <el-form-item :label="t('common.remark')">
          <el-input
            v-model="uploadForm.remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            :placeholder="t('fileLib.remarkPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('fileLib.selectFile')">
          <el-upload
            drag
            :show-file-list="false"
            :http-request="doUpload"
            :disabled="uploading"
            class="upload-area"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              {{ t('fileLib.dropHint') }}<em>{{ t('fileLib.clickSelect') }}</em>
            </div>
            <template #tip>
              <div class="form-tip">{{ t('fileLib.uploadTip') }}</div>
            </template>
          </el-upload>
          <el-progress
            v-if="uploading"
            :percentage="uploadPercent"
            :stroke-width="12"
            style="width: 100%; margin-top: 8px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="uploadVisible = false">{{ t('common.close') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 改名 -->
    <el-dialog v-model="renameVisible" :title="t('fileLib.renameTitle')" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item :label="t('fileLib.originalFileName')">
          <span class="text-secondary">{{ renameTarget.originalName }}</span>
        </el-form-item>
        <el-form-item :label="t('fileLib.currentName')">
          <el-input v-model="renameTarget.displayName" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item>
          <div class="form-tip">
            {{ t('fileLib.renameTip') }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="renameVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="renaming" @click="submitRename">{{ t('common.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="t('fileLib.detailTitle')" size="480px" append-to-body>
      <div v-if="detail" class="file-detail">
        <el-image
          v-if="isImage(detail) && detail.url"
          :src="detail.mediumUrl || detail.url"
          fit="contain"
          class="detail-image"
          :preview-src-list="[detail.url]"
          preview-teleported
        />
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('fileLib.fileId')">
            <span class="text-mono">{{ detail.fileId }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.displayName')">{{ detail.displayName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.originalNameLabel')">{{ detail.originalName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.mimeType')">{{ detail.mime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.size')">{{ formatFileSize(detail.size) }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.dimension')">
            {{ detail.width && detail.height ? `${detail.width} × ${detail.height}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.variants')">
            <span v-if="detail.thumbUrl" class="text-mono">
              {{ t('fileLib.variantsReady') }}
            </span>
            <span v-else class="text-secondary">{{ t('fileLib.variantsMissing') }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.bizType')">{{ detail.bizType || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.remark')">{{ detail.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.uploadedAt')">{{ detail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('fileLib.url')">
            <span class="text-mono">{{ detail.url }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title" style="margin-top: 20px">{{ t('fileLib.renameHistory') }}</h4>
        <el-empty v-if="!history.length" :description="t('fileLib.noHistory')" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in history"
            :key="item.logId || item.id || item.createTime"
            :timestamp="item.createTime"
          >
            {{ item.oldName || t('fileLib.originalBadge') }} → {{ item.newName || item.displayName }}
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import {
  getFile,
  listFileNameHistory,
  listFiles,
  renameFile,
  uploadFile
} from '@/api/file'
import { formatFileSize } from '@/utils'

/**
 * 文件浏览器（文件中心 / 文件库共用）。
 *
 * <p>为什么做成组件而不是两个页面各写一份：文件中心与文件库的数据源、
 * 搜索条件、改名与详情行为完全一致，差别只是入口语义。公共部分沉到这里，
 * 后续加“网格 / 列表切换”也只改一处。</p>
 */
const props = defineProps({
  showUpload: { type: Boolean, default: true },
  fixedBizType: { type: String, default: '' }
})

const loading = ref(false)
const { t } = useI18n()
const files = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 12,
  keyword: '',
  bizType: props.fixedBizType
})

const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']

function isImage(file) {
  const ext = (file.ext || '').toLowerCase()
  return imageExts.includes(ext) || (file.mime || '').startsWith('image/')
}

function isRenamed(file) {
  return file.originalName && file.displayName && file.originalName !== file.displayName
}

async function loadList() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      bizType: query.bizType || undefined
    }
    const data = await listFiles(params)
    files.value = data.records || []
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
  query.bizType = props.fixedBizType
  query.pageNum = 1
  loadList()
}

onMounted(loadList)

/* ==================== 上传 ==================== */
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadForm = reactive({ bizType: 'common', remark: '' })

function openUpload() {
  uploadForm.bizType = props.fixedBizType || 'common'
  uploadForm.remark = ''
  uploadPercent.value = 0
  uploadVisible.value = true
}

async function doUpload(options) {
  uploading.value = true
  uploadPercent.value = 0
  try {
    const data = await uploadFile(options.file, {
      bizType: uploadForm.bizType || 'common',
      remark: uploadForm.remark,
      onProgress: (value) => {
        uploadPercent.value = value
      }
    })
    ElMessage.success(t('fileLib.uploadSuccess', { name: data.displayName || data.originalName }))
    uploadVisible.value = false
    query.pageNum = 1
    await loadList()
  } catch (e) {
    // 错误提示已由 axios 拦截器统一弹出
  } finally {
    uploading.value = false
  }
}

/* ==================== 改名 ==================== */
const renameVisible = ref(false)
const renaming = ref(false)
const renameTarget = reactive({ fileId: '', displayName: '', originalName: '' })
const renameHistory = ref([])

function openRename(file) {
  renameTarget.fileId = file.fileId
  renameTarget.displayName = file.displayName || file.originalName
  renameTarget.originalName = file.originalName
  renameHistory.value = []
  renameVisible.value = true
}

async function submitRename() {
  if (!renameTarget.displayName || !renameTarget.displayName.trim()) {
    ElMessage.warning(t('fileLib.nameRequired'))
    return
  }
  renaming.value = true
  try {
    await renameFile(renameTarget.fileId, renameTarget.displayName.trim())
    ElMessage.success(t('fileLib.renamed'))
    renameVisible.value = false
    await loadList()
    if (detailVisible.value && detail.value && String(detail.value.fileId) === String(renameTarget.fileId)) {
      await openDetail({ fileId: renameTarget.fileId })
    }
  } finally {
    renaming.value = false
  }
}

/* ==================== 详情 ==================== */
const detailVisible = ref(false)
const detail = ref(null)
const history = ref([])

async function openDetail(file) {
  detail.value = null
  history.value = []
  detailVisible.value = true
  const [info, logs] = await Promise.all([
    getFile(file.fileId).catch(() => null),
    listFileNameHistory(file.fileId).catch(() => [])
  ])
  detail.value = info || file
  history.value = logs || []
}

/* ==================== 其他 ==================== */
async function copyUrl(file) {
  const url = `${window.location.origin}${file.url || ''}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('fileLib.linkCopied'))
  } catch (e) {
    ElMessage.warning(t('fileLib.copyFailedManual', { url }))
  }
}
</script>

<style scoped>
.file-grid-wrapper {
  min-height: 220px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.file-card {
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  overflow: hidden;
  background: var(--lx-card-bg);
  transition: box-shadow 0.2s, transform 0.2s;
}

.file-card:hover {
  box-shadow: var(--lx-shadow);
  transform: translateY(-2px);
}

.file-thumb {
  height: 140px;
  background: var(--lx-fill-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
}

.file-image {
  width: 100%;
  height: 100%;
}

.file-ext {
  font-size: 26px;
  font-weight: 700;
  color: #9aa7bd;
  letter-spacing: 1px;
}

.file-meta {
  padding: 10px 12px 4px;
}

.file-name {
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-origin {
  margin-top: 2px;
  font-size: 12px;
  color: #98a4b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-sub {
  display: flex;
  gap: 10px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-secondary);
  flex-wrap: wrap;
}

.file-remark {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lx-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-actions {
  display: flex;
  gap: 2px;
  padding: 0 8px 8px;
}

.upload-area {
  width: 100%;
}

.upload-icon {
  font-size: 42px;
  color: #a8b3c7;
  margin-top: 12px;
}

.detail-image {
  width: 100%;
  max-height: 260px;
  margin-bottom: 16px;
  border-radius: var(--lx-radius);
  background: var(--lx-fill-bg);
}
</style>
