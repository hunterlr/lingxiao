/**
 * Token 存取。
 *
 * <p>Token 名由后端登录接口返回（{@code tokenName}），v1.0 固定是
 * {@code lx-platform-token}。前端不写死请求头名，是为了给后续租户域 / 会员域留口子：
 * 换域只换存储的 tokenName，不改拦截器。</p>
 *
 * <p>存储位置用 localStorage：刷新页面不掉登录；XSS 风险由服务端 CSP +
 * 禁内联脚本兜底，v1.0 先接受。</p>
 */

const TOKEN_KEY = 'lx-admin-token'
const TOKEN_NAME_KEY = 'lx-admin-token-name'
const DEFAULT_TOKEN_NAME = 'lx-platform-token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getTokenName() {
  return localStorage.getItem(TOKEN_NAME_KEY) || DEFAULT_TOKEN_NAME
}

export function setToken(token, tokenName) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  }
  if (tokenName) {
    localStorage.setItem(TOKEN_NAME_KEY, tokenName)
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_NAME_KEY)
}
