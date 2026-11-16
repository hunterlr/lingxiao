import { computed, reactive } from 'vue'
import { getPublicConfig } from '@/api/config'

/**
 * 富文本编辑器引擎选择。
 *
 * <p>三个来源，优先级从高到低：</p>
 * <ol>
 *   <li><b>浏览器偏好</b>：编辑器右上角「切换编辑器」写入 localStorage，只影响当前浏览器；</li>
 *   <li><b>平台默认</b>：系统参数 {@code sys.editor.type}（参数设置页可改），影响没有本机偏好的用户；</li>
 *   <li><b>构建默认</b>：环境变量 {@code VITE_RICH_EDITOR}，读不到平台配置时兜底。</li>
 * </ol>
 *
 * <p>为什么不让所有人实时跟随平台默认：运营改一次参数就强制刷新所有正在编辑的人，
 * 会把没保存的草稿打断。偏好放在本机，是“谁想换谁换”，平台默认只管新用户 / 新设备。</p>
 *
 * <p>平台参数走 `/system/config/public/value`，因为公告管理员不一定有
 * `platform:system:config:list` 权限；服务端只对白名单 key 返回值。</p>
 */

/** 支持的引擎；新增引擎时这里加一项，并在 RichEditor 里加一个分支。 */
export const EDITOR_ENGINES = [
  {
    value: 'wangeditor-next',
    label: 'wangEditor Next',
    hint: '默认，轻量，粘贴干净'
  },
  {
    value: 'ueditor-plus',
    label: 'UEditor Plus',
    hint: '备用，Word 粘贴与表格更强'
  }
]

const STORAGE_KEY = 'lx-editor-engine'
const VALID = EDITOR_ENGINES.map((item) => item.value)
const FALLBACK = 'wangeditor-next'

function normalize(value) {
  return VALID.includes(value) ? value : ''
}

const state = reactive({
  /** 本机偏好，空串表示跟随平台默认 */
  override: normalize(readStorage()),
  /** 平台默认，空串表示未配置 / 未加载 */
  serverDefault: '',
  loaded: false
})

/** 当前引擎，组件模板直接绑定。 */
export const editorEngine = computed(
  () => state.override || state.serverDefault || normalize(import.meta.env.VITE_RICH_EDITOR) || FALLBACK
)

function readStorage() {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch (e) {
    return ''
  }
}

let pending = null

/**
 * 加载平台默认引擎。多次调用只会发一次请求；失败静默（用构建默认值）。
 * 返回 Promise 便于测试与首屏预加载。
 */
export function loadEditorEngine() {
  if (state.loaded) {
    return Promise.resolve(editorEngine.value)
  }
  if (!pending) {
    pending = getPublicConfig('sys.editor.type')
      .then((value) => {
        state.serverDefault = normalize(value)
      })
      .catch(() => {
        state.serverDefault = ''
      })
      .then(() => {
        state.loaded = true
        return editorEngine.value
      })
  }
  return pending
}

/** 切换本机引擎；`__default__` 表示清除本机偏好、跟随平台默认。 */
export function setEditorEngine(value) {
  if (value === '__default__') {
    clearEditorEngine()
    return editorEngine.value
  }
  const engine = normalize(value)
  if (!engine) return editorEngine.value
  state.override = engine
  try {
    localStorage.setItem(STORAGE_KEY, engine)
  } catch (e) {
    // 隐私模式 / 存储被禁用时只影响记忆，不影响本次切换
  }
  return editorEngine.value
}

export function clearEditorEngine() {
  state.override = ''
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) {
    // ignore
  }
}

/** 是否设置了本机偏好（决定“跟随平台默认”是否可点）。 */
export function hasEditorOverride() {
  return Boolean(state.override)
}

export function engineLabel(value) {
  const found = EDITOR_ENGINES.find((item) => item.value === value)
  return found ? found.label : value
}
