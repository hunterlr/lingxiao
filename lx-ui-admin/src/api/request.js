import axios from 'axios'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { clearToken, getToken, getTokenName } from '@/utils/auth'

/**
 * 统一 HTTP 客户端。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>成功时直接返回后端 {@code R.data}，业务代码不用每次 {@code res.data.data}；</li>
 *   <li>失败时抛出带 {@code code} 的 Error，页面可按错误码分支；</li>
 *   <li>HTTP 401 / 业务码 1002：清 Token 并跳登录页，只提示一次；</li>
 *   <li>请求配置可带 {@code silent: true}，用于“失败不弹提示”的探测型请求；</li>
 *   <li>每个请求自动带 {@code X-Lang}，后端据此返回对应语言的 msg。</li>
 * </ul>
 *
 * <p>注意：后端把 Long 序列化成字符串，前端拿到的 ID 全是字符串，
 * 不要做 {@code parseInt}，否则 19 位雪花 ID 会丢精度。</p>
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 不设全局 Content-Type：
 * axios 对普通对象自动用 application/json，对 FormData 让浏览器自动补
 * `multipart/form-data; boundary=...`。写死 JSON 会让文件上传丢 boundary，
 * 后端只能看到 “Current request is not a multipart request”。
 */
const http = axios.create({
  baseURL: BASE_URL,
  timeout: 20000
})

/** 业务异常：保留 code，方便调用方区分 2003（验证码过期）等场景。 */
export class BizError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

/** 当前语言；i18n 未初始化时也保证返回一个合法标签。 */
function currentLang() {
  return i18n.global.locale.value || 'zh-CN'
}

/** 取当前语言的文案，key 缺失时用 fallback。 */
function t(key, fallback) {
  const { t: translate, te } = i18n.global
  return te(key) ? translate(key) : fallback
}

let redirecting = false

function redirectToLogin() {
  clearToken()
  if (redirecting) return
  redirecting = true
  const current = window.location.pathname + window.location.search
  const target = current.startsWith('/login')
    ? '/login'
    : `/login?redirect=${encodeURIComponent(current)}`
  // 用整页跳转而不是 router.push：此时 Pinia / 动态路由可能处于半初始化状态
  window.location.replace(target)
}

function notify(message) {
  if (!message) return
  ElMessage.closeAll()
  ElMessage.error(message)
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers[getTokenName()] = token
  }
  config.headers['X-Lang'] = currentLang()
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 文件流 / 非 R 结构（理论上没有）原样返回
    if (!body || typeof body.code === 'undefined') {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 1002) {
      redirectToLogin()
      return Promise.reject(new BizError(body.code, body.msg || t('common.sessionExpired', '登录已过期')))
    }
    if (!response.config.silent) {
      notify(body.msg || t('common.requestFailed', '请求失败'))
    }
    return Promise.reject(new BizError(body.code, body.msg || t('common.requestFailed', '请求失败')))
  },
  (error) => {
    const status = error.response && error.response.status
    const body = error.response && error.response.data
    const message = (body && body.msg) || error.message || t('common.networkError', '网络异常')

    if (status === 401) {
      redirectToLogin()
      return Promise.reject(new BizError(1002, message))
    }
    if (status === 403) {
      const forbidden = t('common.noPermission', '没有权限执行该操作')
      if (!error.config || !error.config.silent) {
        notify(forbidden)
      }
      return Promise.reject(new BizError(1003, forbidden))
    }
    if (!error.config || !error.config.silent) {
      notify(message)
    }
    return Promise.reject(new BizError(status || 9000, message))
  }
)

export default http
