<template>
  <div class="media-picker">
    <el-avatar
      :size="size"
      :shape="shape"
      :src="previewUrl"
      class="media-picker__preview"
    >
      {{ fallbackChar }}
    </el-avatar>

    <el-input
      v-model="inner"
      class="media-picker__input"
      maxlength="255"
      clearable
      :placeholder="placeholder"
      @blur="handleBlur"
    />

    <el-button
      v-hasPerm="'platform:file:list'"
      :icon="Picture"
      @click="openDialog"
    >
      {{ t('mediaPicker.select') }}
    </el-button>
    <el-button v-if="inner" :icon="Delete" @click="clear">
      {{ t('mediaPicker.clear') }}
    </el-button>
  </div>

  <el-dialog
    v-model="dialogVisible"
    :title="t('mediaPicker.dialogTitle')"
    width="880px"
    append-to-body
    destroy-on-close
  >
    <div class="media-toolbar">
      <el-input
        v-model="query.keyword"
        :placeholder="t('mediaPicker.searchPlaceholder')"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="handleSearch">
        {{ t('common.search') }}
      </el-button>
      <el-button :icon="Refresh" @click="handleReset">
        {{ t('common.reset') }}
      </el-button>

      <div class="media-toolbar__right">
        <el-upload
          v-hasPerm="'platform:file:upload'"
          :show-file-list="false"
          accept="image/*"
          :before-upload="beforeUpload"
          :http-request="doUpload"
          :disabled="uploading"
        >
          <el-button type="primary" :icon="UploadFilled" :loading="uploading">
            {{ t('mediaPicker.upload') }}
          </el-button>
        </el-upload>
        <el-button :icon="Refresh" circle :title="t('common.refresh')" @click="loadList" />
      </div>
    </div>

    <div v-loading="loading" class="media-grid-wrapper">
      <el-empty v-if="!loading && !images.length" :description="t('mediaPicker.empty')" />
      <div v-else class="media-grid">
        <div
          v-for="file in images"
          :key="file.fileId"
          class="media-card"
          role="button"
          tabindex="0"
          @click="choose(file)"
          @keyup.enter="choose(file)"
        >
          <div class="media-card__thumb">
            <el-image
              :src="file.thumbUrl || file.url"
              fit="cover"
              lazy
              class="media-card__image"
            >
              <template #error>
                <div class="media-card__fallback">{{ (file.ext || 'IMG').toUpperCase() }}</div>
              </template>
            </el-image>
          </div>
          <div class="media-card__meta">
            <span class="media-card__name" :title="file.displayName || file.originalName">
              {{ file.displayName || file.originalName }}
            </span>
            <span class="media-card__sub">
              {{ file.width && file.height ? `${file.width}×${file.height}` : formatFileSize(file.size) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="handleSizeChange"
        @current-change="loadList"
      />
    </div>

    <template #footer>
      <div class="dialog-footer media-dialog-footer">
        <span class="form-tip">{{ t('mediaPicker.hint') }}</span>
        <el-button @click="dialogVisible = false">{{ t('common.close') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Delete, Picture, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import { listFiles, uploadFile } from '@/api/file'
import { formatFileSize } from '@/utils'
import { resolveAssetUrl } from '@/utils/asset'

/**
 * 图片素材选择器。
 *
 * <p>用于 CMS 站点 Logo、栏目封面、文章封面这类“一个图片地址”的字段。
 * 字段值仍兼容两种存量格式：文件中心 fileId 与 http(s) 外链；通过选择器
 * 选中的一律写 fileId，避免把当前环境域名写进业务数据。</p>
 *
 * <p>浏览素材需要 {@code platform:file:list}，上传需要
 * {@code platform:file:upload}；没有权限时对应按钮不渲染，输入框仍可手工
 * 粘贴外链，保证编辑流程不被文件权限卡死。</p>
 */

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  /** 上传时写入的 bizType，默认 cms */
  uploadBizType: { type: String, default: 'cms' },
  /** circle / square，与 el-avatar 的 shape 一致 */
  shape: { type: String, default: 'square' },
  size: { type: Number, default: 48 },
  /** 预览为空时的占位文字，通常传名称 */
  fallback: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const inner = computed({
  get: () => props.modelValue || '',
  set: (value) => emit('update:modelValue', value)
})

const previewUrl = computed(() => resolveAssetUrl(props.modelValue, { size: 'thumb' }))
const fallbackChar = computed(() => (props.fallback || '?').slice(0, 1))

function clear() {
  emit('update:modelValue', '')
}

/** 把页面内 /api/file/{id} 地址归一成 fileId，避免存成绑定域名的绝对地址。 */
function handleBlur() {
  const text = (props.modelValue || '').trim()
  if (!text) return
  const match = text.match(/\/api\/file\/(\d+)(?:[?#].*)?$/)
  const next = match ? match[1] : text
  if (next !== props.modelValue) {
    emit('update:modelValue', next)
  }
}

/* ==================== 选择弹窗 ==================== */

const dialogVisible = ref(false)
const loading = ref(false)
const uploading = ref(false)
const files = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 12, keyword: '' })

const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
const images = computed(() =>
  files.value.filter((file) => {
    const ext = (file.ext || '').toLowerCase()
    return imageExts.includes(ext) || (file.mime || '').startsWith('image/')
  })
)

function openDialog() {
  dialogVisible.value = true
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const data = await listFiles({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      imageOnly: true
    })
    files.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    files.value = []
    total.value = 0
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
  query.pageNum = 1
  loadList()
}

function handleSizeChange() {
  query.pageNum = 1
  loadList()
}

function choose(file) {
  emit('update:modelValue', String(file.fileId))
  dialogVisible.value = false
}

/* ==================== 上传 ==================== */

function beforeUpload(file) {
  if (!file.type || !file.type.startsWith('image/')) {
    ElMessage.warning(t('mediaPicker.imageOnly'))
    return false
  }
  return true
}

async function doUpload(options) {
  uploading.value = true
  try {
    const data = await uploadFile(options.file, { bizType: props.uploadBizType || 'cms' })
    ElMessage.success(t('mediaPicker.uploadSuccess', { name: data.displayName || data.originalName }))
    emit('update:modelValue', String(data.fileId))
    dialogVisible.value = false
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.media-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.media-picker__preview {
  flex: 0 0 auto;
  background: var(--lx-fill-bg);
  color: var(--lx-text-secondary);
  font-size: 16px;
  font-weight: 600;
}

.media-picker__input {
  flex: 1;
  min-width: 0;
}

.media-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.media-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.media-grid-wrapper {
  min-height: 260px;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.media-card {
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  overflow: hidden;
  background: var(--lx-card-bg);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}

.media-card:hover,
.media-card:focus-visible {
  border-color: var(--el-color-primary);
  box-shadow: var(--lx-shadow);
  transform: translateY(-2px);
  outline: none;
}

.media-card__thumb {
  height: 110px;
  background: var(--lx-fill-bg);
  overflow: hidden;
}

.media-card__image {
  width: 100%;
  height: 100%;
}

.media-card__fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9aa7bd;
  font-size: 18px;
  font-weight: 700;
}

.media-card__meta {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.media-card__name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.media-card__sub {
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.media-dialog-footer {
  justify-content: space-between;
}
</style>
