import http from './request'

/**
 * 模块开关列表。
 *
 * <p>路径不带 /api 前缀：axios baseURL 已经是 /api。
 * 后端返回基座模块（system / file）+ 配置里的可选模块，每项含
 * {@code code / name / enabled / description}。</p>
 *
 * <p>{@code silent: true}：模块列表只用于前端路由二次校验，
 * 拿不到时后端下发的菜单仍然是准确的，不应该因为探测失败弹错。</p>
 */
export function listModules() {
  return http.get('/admin/modules', { silent: true })
}
