import type {
  MemberCaptcha,
  MemberCommentItem,
  MemberFavoriteItem,
  MemberInteraction,
  MemberLoginForm,
  MemberPasswordForm,
  MemberProfile,
  MemberProfileForm,
  MemberRegisterForm,
  MemberResetPasswordForm,
  MemberSession
} from '~/types/member'
import type { PageResult } from '~/types/portal'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface MemberRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: Record<string, unknown>
  query?: Record<string, unknown>
}

/** 会员接口业务错误；页面只展示 message，按 code 做登录态 / 功能开关判断。 */
export class MemberApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'MemberApiError'
    this.code = code
  }
}

/** 把未知异常转成可直接展示的文案。 */
export function memberErrorMessage(error: unknown, fallback = '操作失败，请稍后再试'): string {
  if (error instanceof MemberApiError && error.message) return error.message
  return fallback
}

const TOKEN_COOKIE = 'lx-portal-member-token'
const TOKEN_NAME_COOKIE = 'lx-portal-member-token-name'
const DEFAULT_TOKEN_NAME = 'lx-member-token'
const COOKIE_MAX_AGE = 7 * 24 * 60 * 60

/**
 * 门户会员会话。
 *
 * <p>Token 由后端 `/api/app/auth/login` 或 `/register` 返回，落在同源 Cookie 里，
 * 请求时再放进 `lx-member-token` 请求头：后端会员域只读 Header，不读 Cookie。
 * 之所以不用 httpOnly：浏览器端客户端跳转后仍需自己发请求；门户正文由后端
 * 清洗，脚本注入面已经收窄，Cookie 使用 `SameSite=Lax`。</p>
 *
 * <p>SSR 时布局会调用一次 `ensureProfile()`，把昵称 / 头像直接渲染进首屏；
 * 客户端跳转复用同一份 `useState`，不会重复取资料。</p>
 */
export function useMemberAuth() {
  const requestFetch = useRequestFetch()
  const token = useCookie<string | null>(TOKEN_COOKIE, {
    path: '/',
    sameSite: 'lax',
    maxAge: COOKIE_MAX_AGE
  })
  const tokenName = useCookie<string | null>(TOKEN_NAME_COOKIE, {
    path: '/',
    sameSite: 'lax',
    maxAge: COOKIE_MAX_AGE
  })

  const profile = useState<MemberProfile | null>('portal-member-profile', () => null)
  const loaded = useState<boolean>('portal-member-loaded', () => false)
  const sessionError = useState<string>('portal-member-error', () => '')

  function buildPath(path: string, query?: Record<string, unknown>): string {
    if (!query) return path
    const search = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue
      search.set(key, String(value))
    }
    const qs = search.toString()
    return qs ? `${path}?${qs}` : path
  }

  async function request<T>(path: string, options: MemberRequestOptions = {}): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.body) headers['Content-Type'] = 'application/json'
    if (token.value) {
      headers[tokenName.value || DEFAULT_TOKEN_NAME] = token.value
    }

    let response: ApiEnvelope<T>
    try {
      response = await requestFetch<ApiEnvelope<T>>(`/api/app${buildPath(path, options.query)}`, {
        method: options.method || 'GET',
        headers,
        body: options.body
      })
    } catch (cause) {
      // 401 等非 2xx 响应会被 $fetch 抛成 FetchError，业务体仍在 data 里
      const body = (cause as { data?: ApiEnvelope<unknown> })?.data
      if (body && typeof body === 'object' && typeof body.code === 'number') {
        throw new MemberApiError(body.code, body.msg || '请求失败')
      }
      throw new MemberApiError(0, '网络异常，请稍后再试')
    }

    if (response.code !== 0) {
      throw new MemberApiError(response.code, response.msg || '请求失败')
    }
    return response.data
  }

  function applySession(session: MemberSession) {
    token.value = session.tokenValue
    tokenName.value = session.tokenName || DEFAULT_TOKEN_NAME
    profile.value = {
      memberId: session.memberId,
      username: session.username,
      nickname: session.nickname,
      avatar: session.avatar,
      levelId: session.levelId,
      levelName: session.levelName,
      points: session.points,
      growth: session.growth
    }
    // 登录 / 注册响应只有会话摘要，手机号、注册时间等要再取一次完整资料
    loaded.value = false
    sessionError.value = ''
  }

  function clearSession() {
    token.value = null
    tokenName.value = null
    profile.value = null
    loaded.value = true
    sessionError.value = ''
  }

  async function login(form: MemberLoginForm): Promise<MemberSession> {
    const session = await request<MemberSession>('/auth/login', {
      method: 'POST',
      body: { ...form }
    })
    applySession(session)
    return session
  }

  async function register(form: MemberRegisterForm): Promise<MemberSession> {
    const session = await request<MemberSession>('/auth/register', {
      method: 'POST',
      body: { ...form }
    })
    applySession(session)
    return session
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await request<void>('/auth/logout', { method: 'POST' })
      }
    } catch {
      // 退出以清本地会话为准；后端已失效时不再弹错
    } finally {
      clearSession()
    }
  }

  /**
   * 取当前会员资料。
   *
   * <p>只在有 Token 时请求；401 / 1002 说明会话已失效，直接清掉本地 Cookie，
   * 让页面回到“未登录”。会员功能被关闭（1003）时保留错误文案，由页面提示。</p>
   */
  async function ensureProfile(force = false): Promise<MemberProfile | null> {
    if (!token.value) {
      profile.value = null
      loaded.value = true
      sessionError.value = ''
      return null
    }
    if (loaded.value && !force) return profile.value

    try {
      profile.value = await request<MemberProfile>('/profile')
      sessionError.value = ''
    } catch (error) {
      if (error instanceof MemberApiError && error.code === 1002) {
        clearSession()
        return null
      }
      profile.value = null
      sessionError.value = error instanceof MemberApiError ? error.message : '登录状态校验失败'
    }
    loaded.value = true
    return profile.value
  }

  async function updateProfile(form: MemberProfileForm): Promise<MemberProfile> {
    const result = await request<MemberProfile>('/profile', {
      method: 'PUT',
      body: { ...form }
    })
    profile.value = result
    loaded.value = true
    sessionError.value = ''
    return result
  }

  /** 修改密码成功后后端会注销全部会话，本地同步清空。 */
  async function changePassword(form: MemberPasswordForm): Promise<void> {
    await request<void>('/profile/password', {
      method: 'PUT',
      body: { ...form }
    })
    clearSession()
  }

  function fetchCaptcha(scene: 'login' | 'register' | 'reset'): Promise<MemberCaptcha> {
    return request<MemberCaptcha>('/auth/captcha', { query: { scene } })
  }

  async function forgotPassword(account: string): Promise<void> {
    await request<void>('/auth/forgot-password', {
      method: 'POST',
      body: { account }
    })
  }

  async function resetPassword(form: MemberResetPasswordForm): Promise<void> {
    await request<void>('/auth/reset-password', {
      method: 'POST',
      body: { ...form }
    })
    // 后端会注销该会员全部会话；本地也立即清掉，避免登录页拿旧 Token 回跳
    clearSession()
  }

  // ==================== 收藏 / 点赞 ====================

  function articleSegment(articleId: string | number): string {
    return encodeURIComponent(String(articleId))
  }

  /** 公开读：匿名也能拿到总数；带 Token 时附带当前会员状态。 */
  function fetchInteraction(articleId: string | number): Promise<MemberInteraction> {
    return request<MemberInteraction>(`/public/interactions/articles/${articleSegment(articleId)}`)
  }

  /** 我的收藏分页；只返回文章 ID 与收藏时间。 */
  function fetchFavorites(pageNum = 1, pageSize = 6): Promise<PageResult<MemberFavoriteItem>> {
    return request<PageResult<MemberFavoriteItem>>('/interactions/favorites', {
      query: { pageNum, pageSize }
    })
  }

  function setFavorite(articleId: string | number, favorited: boolean): Promise<MemberInteraction> {
    return request<MemberInteraction>(`/interactions/articles/${articleSegment(articleId)}/favorite`, {
      method: favorited ? 'PUT' : 'DELETE'
    })
  }

  function setLike(articleId: string | number, liked: boolean): Promise<MemberInteraction> {
    return request<MemberInteraction>(`/interactions/articles/${articleSegment(articleId)}/like`, {
      method: liked ? 'PUT' : 'DELETE'
    })
  }

  // ==================== 评论 ====================

  /** 公开读：只返回已审核通过的评论，匿名可读。 */
  function fetchComments(articleId: string | number, pageNum = 1, pageSize = 5): Promise<PageResult<MemberCommentItem>> {
    return request<PageResult<MemberCommentItem>>(`/public/comments/articles/${articleSegment(articleId)}`, {
      query: { pageNum, pageSize }
    })
  }

  /** 发表评论：必须登录；提交后进入待审核，不会立即出现在公开列表。 */
  function submitComment(articleId: string | number, content: string): Promise<MemberCommentItem> {
    return request<MemberCommentItem>(`/comments/articles/${articleSegment(articleId)}`, {
      method: 'POST',
      body: { content }
    })
  }

  const isLoggedIn = computed(() => Boolean(token.value))

  return {
    token,
    profile,
    sessionError,
    isLoggedIn,
    login,
    register,
    logout,
    ensureProfile,
    updateProfile,
    changePassword,
    fetchCaptcha,
    forgotPassword,
    resetPassword,
    fetchInteraction,
    fetchFavorites,
    setFavorite,
    setLike,
    fetchComments,
    submitComment
  }
}
