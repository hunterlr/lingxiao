<template>
  <div class="ueditor-plus" :style="{ minHeight: height }">
    <vue-ueditor-wrap
      v-model="inner"
      :editor-id="editorId"
      :config="config"
      :editor-dependencies="dependencies"
      :editor-dependencies-checker="dependenciesReady"
      :force-init="true"
      :destroy="true"
      @ready="onReady"
    />
  </div>
</template>

<script setup>
// 注意：这个包的默认导出是「插件对象」（{ install, version }），不是组件；
// 组件要用具名导出，否则 Vue 会报警告 “Component is missing template or render function”。
import { VueUeditorWrap } from 'vue-ueditor-wrap'
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { uploadFile } from '@/api/file'

/**
 * UEditor Plus 适配组件（备用引擎）。
 *
 * <p><b>为什么能不用额外后端：</b>UEditor Plus 4.x 提供了
 * {@code uploadServiceEnable + uploadServiceUpload} 钩子，图片 / 视频 / 附件
 * 都可以交给前端自己的上传函数，不再依赖它自带的 PHP/JSP 上传服务。
 * 这里直接复用 `api/file.js` 的 `uploadFile`，所以 Token、统一错误处理、
 * 文件中心留痕全部与 wangEditor Next 一致。</p>
 *
 * <p><b>静态资源：</b>`public/ueditor-plus` 由 `scripts/sync-ueditor.mjs`
 * 在 `dev` / `build` 前从 npm 包复制，不提交进仓库。</p>
 *
 * <p><b>工具栏：</b>只保留“不依赖服务端配置”的按钮。`insertimage` 多图弹窗
 * 仍会走 UEditor 自己的 serverUrl，因此不放进工具栏；单图上传用 `simpleupload`，
 * 它走上面的自定义上传钩子。</p>
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  height: { type: String, default: '360px' },
  placeholder: { type: String, default: '请输入内容…' }
})

const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const editorId = `lx-uep-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`

const inner = computed({
  get: () => props.modelValue || '',
  set: (value) => emit('update:modelValue', value)
})

const dependencies = ['ueditor.config.js', 'ueditor.all.js']

/** 依赖是否已加载：多个编辑器实例共用一份全局 UE，避免重复插入 script。 */
function dependenciesReady() {
  return Boolean(window.UE && window.UE.getEditor)
}

const frameHeight = Math.max(Number.parseInt(props.height, 10) - 70 || 290, 220)

/** 自定义上传：type 是 UEditor 传的 image / video / audio / attachment。 */
function uploadServiceUpload(type, file, callbacks) {
  // 备注是写进文件中心的数据，跟随当前语言存，方便同一语言下搜索
  const labels = {
    image: t('editor.materialImage'),
    video: t('editor.materialVideo'),
    audio: t('editor.materialAudio'),
    attachment: t('editor.materialAttachment')
  }
  uploadFile(file, {
    bizType: 'notice',
    remark: t('editor.materialRemark', { label: labels[type] || t('editor.materialOther') }),
    onProgress: (percent) => callbacks.progress && callbacks.progress(percent)
  })
    .then((data) => {
      // UEditor 成功回调只要 url 与 original；imageUrlPrefix 为空，直接使用后端地址
      callbacks.success({
        state: 'SUCCESS',
        url: data.url,
        title: data.displayName || data.originalName,
        original: data.originalName || data.displayName,
        type: data.ext ? `.${data.ext}` : '',
        size: data.size
      })
    })
    .catch((error) => {
      ElMessage.error(error.message || t('editor.materialUploadFailed'))
      callbacks.error(error.message || t('editor.uploadFailed'))
    })
}

/**
 * 关掉 `loadConfigFromServer` 的副作用修复。
 *
 * <p>UEditor 的 `loadServerConfig()` 在 `loadConfigFromServer === false` 时直接 return，
 * 既不会把 `_serverConfigLoaded` 置 true，也不会发 `serverConfigLoaded` 事件。
 * 而 `simpleupload`（单图上传）插件是 `afterConfigReady(initUploadBtn)` 才创建
 * 那个透明的 `<input type=file>` 的 —— 于是按钮永远是 disabled，点不出文件选择框。</p>
 *
 * <p>这里在编辑器 ready 之后补一次：置位 + 手动发事件，让等待中的
 * `afterConfigReady` 回调（以及后续注册的）都能跑起来。用 setTimeout(0)
 * 是为了等本次 `ready` 事件分发结束，确保插件的 `simpleuploadbtnready`
 * 已经把监听挂上。</p>
 */
function onReady(editor) {
  window.setTimeout(() => {
    if (typeof editor.isServerConfigLoaded === 'function' && editor.isServerConfigLoaded()) return
    editor._serverConfigLoaded = true
    editor.fireEvent('serverConfigLoaded')
  }, 0)
}

const config = {
  UEDITOR_HOME_URL: '/ueditor-plus/',
  // 所有配置都在前端给全，禁止再向 serverUrl 拉配置（默认值指向 demo PHP 服务）
  serverUrl: '',
  loadConfigFromServer: false,
  zIndex: 3000,
  autoHeightEnabled: false,
  autoFloatEnabled: false,
  initialFrameHeight: frameHeight,
  elementPathEnabled: false,
  wordCount: true,
  maximumWords: 20000,
  catchRemoteImageEnable: false,
  // 关闭 UEditor 自带的自动存稿：正文由父级表单管理，它再往 localStorage 写一份
  // 草稿没有意义；而且切换引擎时销毁实例会有一个 1s 后触发的 save 定时器，
  // 编辑器已销毁再执行会抛 “Cannot read properties of undefined (reading 'nodeType')”。
  autoSaveEnable: false,
  toolbars: [
    [
      'undo',
      'redo',
      '|',
      'bold',
      'italic',
      'underline',
      'strikethrough',
      'removeformat',
      '|',
      'forecolor',
      'backcolor',
      '|',
      'fontfamily',
      'fontsize',
      '|',
      'insertorderedlist',
      'insertunorderedlist',
      '|',
      'justifyleft',
      'justifycenter',
      'justifyright',
      'justifyjustify',
      '|',
      'link',
      'unlink',
      '|',
      'simpleupload',
      'emotion',
      '|',
      'inserttable',
      'deletetable',
      '|',
      'horizontal',
      '|',
      'source',
      'preview',
      'fullscreen'
    ]
  ],
  // 自定义上传
  uploadServiceEnable: true,
  uploadServiceUpload,
  imageActionName: 'uploadimage',
  imageFieldName: 'file',
  imageAllowFiles: ['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp'],
  imageMaxSize: 10 * 1024 * 1024,
  imageUrlPrefix: '',
  imageConfig: { disableOnline: true, disableUpload: false },
  videoConfig: { disableOnline: true, disableUpload: false },
  audioConfig: { disableOnline: true, disableUpload: false },
  // UEditor 没有 placeholder 选项，用初始内容保持空白
  initialContent: ''
}
</script>

<style scoped>
.ueditor-plus {
  width: 100%;
  line-height: normal;
}

.ueditor-plus :deep(.edui-editor) {
  border-radius: 4px;
  border-color: #dcdfe6;
  z-index: 1;
}

.ueditor-plus :deep(.edui-editor-toolbarbox) {
  border-radius: 4px 4px 0 0;
}
</style>
