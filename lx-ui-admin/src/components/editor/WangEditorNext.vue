<template>
  <div class="wang-editor-next">
    <Toolbar
      class="editor-toolbar"
      :editor="editorRef"
      :default-config="toolbarConfig"
      mode="default"
    />
    <Editor
      v-model="valueHtml"
      :style="{ height, overflowY: 'hidden' }"
      :default-config="editorConfig"
      mode="default"
      @on-created="handleCreated"
      @on-change="handleChange"
    />
  </div>
</template>

<script setup>
import '@wangeditor-next/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor-next/editor-for-vue'
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/file'

/**
 * wangEditor Next（原 wangEditor 5 的社区续作）适配组件。
 *
 * <p>只负责编辑与图片上传；引擎切换、平台默认值在 `RichEditor.vue` 里统一处理。</p>
 *
 * <p>图片上传走文件中心 {@code /api/admin/file/upload}，插入的
 * {@code src} 是 {@code /api/file/{fileId}}，不是 base64；
 * 粘贴 / 拖拽的图片也走同一通道，避免正文体积失控。</p>
 *
 * <p>视频菜单默认排除：v1.0 没有视频转码与封面，先不开这个口子。</p>
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  height: { type: String, default: '360px' },
  placeholder: { type: String, default: '请输入内容…' }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = shallowRef(null)
const valueHtml = ref(props.modelValue || '')

watch(
  () => props.modelValue,
  (value) => {
    if ((value || '') !== valueHtml.value) {
      valueHtml.value = value || ''
    }
  }
)

const toolbarConfig = {
  excludeKeys: ['group-video', 'fullScreen']
}

const editorConfig = {
  placeholder: props.placeholder,
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        try {
          const data = await uploadFile(file, { bizType: 'notice', remark: '富文本配图' })
          insertFn(data.url, data.displayName || data.originalName, data.url)
        } catch (e) {
          ElMessage.error('图片上传失败')
        }
      }
    }
  }
}

function handleCreated(editor) {
  editorRef.value = editor
}

function handleChange(editor) {
  emit('update:modelValue', editor.getHtml())
}

onBeforeUnmount(() => {
  if (editorRef.value) {
    editorRef.value.destroy()
    editorRef.value = null
  }
})
</script>

<style scoped>
.wang-editor-next {
  width: 100%;
}

.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}
</style>
