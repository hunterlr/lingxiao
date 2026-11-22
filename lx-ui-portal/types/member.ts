/**
 * 门户会员数据类型。
 *
 * <p>会员接口在 `/api/app/**`，与内容接口 `/api/portal/cms/**` 分开：
 * 内容允许匿名读，会员接口走 `lx-member-token`，未登录返回 HTTP 401。
 * ID、积分等字段可能按字符串返回，展示前统一转字符串或 `Number()`。</p>
 */

export interface MemberSession {
  tokenName: string
  tokenValue: string
  tokenTimeout: number
  memberId?: string | number | null
  username?: string | null
  nickname?: string | null
  avatar?: string | null
  levelId?: string | number | null
  levelName?: string | null
  points?: number | string | null
  growth?: number | string | null
}

export interface MemberProfile {
  memberId?: string | number | null
  username?: string | null
  nickname?: string | null
  avatar?: string | null
  mobile?: string | null
  email?: string | null
  /** 0 男 / 1 女 / 2 未知 */
  sex?: string | null
  levelId?: string | number | null
  levelName?: string | null
  points?: number | string | null
  growth?: number | string | null
  status?: string | null
  registerTime?: string | null
  loginTime?: string | null
}

export interface MemberCaptcha {
  enabled: boolean
  key?: string | null
  image?: string | null
  expireIn?: number | null
}

export interface MemberLoginForm {
  account: string
  password: string
  captchaKey?: string
  captchaCode?: string
}

export interface MemberRegisterForm {
  username: string
  password: string
  nickname?: string
  mobile?: string
  email?: string
  captchaKey?: string
  captchaCode?: string
  agreement: boolean
}

export interface MemberProfileForm {
  nickname: string
  avatar?: string
  sex?: string
}

export interface MemberPasswordForm {
  oldPassword: string
  newPassword: string
}

export interface MemberResetPasswordForm {
  account: string
  code: string
  newPassword: string
}

/** 单篇文章的收藏 / 点赞状态；匿名访问时 favorited / liked 固定 false。 */
export interface MemberInteraction {
  articleId: string | number
  favoriteCount: number | string
  likeCount: number | string
  favorited: boolean
  liked: boolean
}

/** 我的收藏条目：只有文章 ID 与收藏时间，标题由 CMS 门户接口补齐。 */
export interface MemberFavoriteItem {
  articleId: string | number
  createTime?: string | null
}
