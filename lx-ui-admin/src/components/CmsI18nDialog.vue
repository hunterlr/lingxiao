<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('page.cms.i18n.title')"
    width="780px"
    top="6vh"
    @update:model-value="(value) => emit('update:modelValue', value)"
    @open="onOpen"
  >
    <el-form label-width="96px">
      <el-form-item :label="t('page.cms.i18n.language')">
        <el-select v-model="lang" style="width: 220px" @change="loadOne">
          <el-option
            v-for="item in langOptions"
            :key="item.code"
            :label="`${item.nativeName}（${item.code}）`"
            :value="item.code"
          />
        </el-select>
        <el-tag v-if="current" class="i18n-status" :type="current.status === '1' ? 'success' : 'info'" effect="plain">
          {{ current.status === '1' ? t('page.cms.i18n.published') : t('page.cms.i18n.draft') }}
        </el-tag>
        <span v-else class="text-secondary i18n-status">{{ t('page.cms.i18n.empty') }}</span>
      </el-form-item>

      <el-form-item :label="t('page.cms.i18n.source')">
        <el-button size="small" @click="copySource">{{ t('page.cms.i18n.copy') }}</el-button>
        <span class="text-secondary i18n-hint">{{ t('page.cms.i18n.copyHint') }}</span>
      </el-form-item>

      <el-form-item :label="t('page.cms.article.title')" required>
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>

      <el-form-item :label="t('page.cms.article.summary')">
        <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>

      <el-form-item :label="t('page.cms.article.content')">
        <RichEditor v-model="form.content" height="300px" />
      </el-form-item>

      <el-form-item :label="t('page.cms.i18n.seoTitle')">
        <el-input v-model="form.seoTitle" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('page.cms.i18n.seoKeywords')">
        <el-input v-model="form.seoKeywords" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('page.cms.i18n.seoDescription')">
        <el-input v-model="form.seoDescription" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button :disabled="!current" @click="onUnpublish">{{ t('page.cms.i18n.unpublish') }}</el-button>
      <el-button :disabled="!current" type="danger" plain @click="onDelete">{{ t('common.delete') }}</el-button>
      <el-button :loading="saving" @click="onSaveDraft">{{ t('page.cms.i18n.saveDraft') }}</el-button>
      <el-button type="primary" :loading="publishing" @click="onPublish">{{ t('page.cms.i18n.publish') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichEditor from '@/components/RichEditor.vue'
import {
  deleteContentI18n,
  listContentI18n,
  publishContentI18n,
  saveContentI18n,
  unpublishContentI18n
} from '@/api/cmsI18n'
import { getContentI18nLanguages } from '@/api/cmsI18nOptions'

/**
 * 内容翻译弹窗（文章 / 单页共用）。
 *
 * <p><b>只做人工填写：</b>“复制原文”只是把默认语言的内容填进来当起点，
 * 保存后仍是草稿，必须点“发布”才会上线；没有机翻按钮。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ownerType: { type: String, default: 'article' },
  ownerId: { type: [String, Number], default: '' },
  source: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const { t } = useI18n()

const langOptions = ref(getContentI18nLanguages())
const lang = ref(langOptions.value.find((item) => !item.isDefault)?.code || langOptions.value[0].code)
const rows = ref([])
const saving = ref(false)
const publishing = ref(false)
const form = reactive({
  title: '',
  summary: '',
  content: '',
  seoTitle: '',
  seoKeywords: '',
  seoDescription: ''
})

const current = computed(() => rows.value.find((item) => item.lang === lang.value) || null)

function fill(row) {
  form.title = row?.title || ''
  form.summary = row?.summary || ''
  form.content = row?.content || ''
  form.seoTitle = row?.seoTitle || ''
  form.seoKeywords = row?.seoKeywords || ''
  form.seoDescription = row?.seoDescription || ''
}

async function loadList() {
  if (!props.ownerId) return
  rows.value = (await listContentI18n(props.ownerType, props.ownerId)) || []
  fill(current.value)
}

function loadOne() {
  fill(current.value)
}

async function onOpen() {
  lang.value = langOptions.value.find((item) => !item.isDefault)?.code || langOptions.value[0].code
  await loadList()
}

function copySource() {
  form.title = props.source?.title || ''
  form.summary = props.source?.summary || ''
  form.content = props.source?.content || ''
  ElMessage.info(t('page.cms.i18n.copied'))
}

async function saveDraft() {
  if (!form.title.trim()) {
    ElMessage.warning(t('page.cms.i18n.titleRequired'))
    return false
  }
  saving.value = true
  try {
    await saveContentI18n(props.ownerType, props.ownerId, lang.value, { ...form })
    await loadList()
    emit('changed')
    return true
  } finally {
    saving.value = false
  }
}

async function onSaveDraft() {
  if (await saveDraft()) {
    ElMessage.success(t('page.cms.i18n.saved'))
  }
}

async function onPublish() {
  if (!(await saveDraft())) return
  publishing.value = true
  try {
    await publishContentI18n(props.ownerType, props.ownerId, lang.value)
    await loadList()
    emit('changed')
    ElMessage.success(t('page.cms.i18n.publishedSuccess'))
  } finally {
    publishing.value = false
  }
}

async function onUnpublish() {
  await unpublishContentI18n(props.ownerType, props.ownerId, lang.value)
  await loadList()
  emit('changed')
  ElMessage.success(t('page.cms.i18n.unpublishedSuccess'))
}

async function onDelete() {
  try {
    await ElMessageBox.confirm(t('page.cms.i18n.deleteConfirm'), t('common.deleteTitle'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) {
    return
  }
  await deleteContentI18n(props.ownerType, props.ownerId, lang.value)
  await loadList()
  emit('changed')
  ElMessage.success(t('common.deleteSuccess'))
}

onMounted(() => {
  if (props.modelValue) onOpen()
})
</script>

<style scoped>
.i18n-status {
  margin-left: 12px;
}

.i18n-hint {
  margin-left: 12px;
  font-size: 12px;
}
</style>
