import http from './request'

/**
 * 应用名片接口。
 *
 * <p>路径不带 /api 前缀：axios baseURL 已经是 /api。
 * 后端在 {@code SaTokenConfigure} 里放行，未登录（登录页）也能读取。</p>
 *
 * <p>{@code silent: true}：品牌信息是装饰性的，后端不可用时不应该弹错误提示，
 * 前端回退到语言包里的默认文案即可。</p>
 */
export function getAppProfile() {
  return http.get('/admin/app/profile', { silent: true })
}
