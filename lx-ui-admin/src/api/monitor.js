import http from './request'

/**
 * 系统监控接口（W4）。
 *
 * <p>三个页面各一组：在线用户、服务监控、缓存监控。</p>
 *
 * <p><b>强退为什么传 tokenId 数组：</b>后端列表返回的是脱敏会话标识
 * （首 4 + 末 4），真实 Token 不出服务端；强退时原样传回，后端再反查。
 * 因此这个字段看起来像 Token，实际不可用于冒充登录。</p>
 */

/** 在线用户分页：{ pageNum, pageSize, username, ip } */
export function listOnlineUsers(params) {
  return http.get('/admin/monitor/online/list', { params })
}

/** 强制下线：支持单个 / 批量，返回 { successCount, failureCount, failures } */
export function kickoutOnlineUsers(tokenIds) {
  return http.post('/admin/monitor/online/kickout', { tokenIds })
}

/** 服务监控快照：CPU / 物理内存 / JVM / 系统 / 磁盘 */
export function getServerInfo() {
  return http.get('/admin/monitor/server')
}

/** 缓存监控快照：验证码存储统计 + Redis INFO 摘要（未启用 Redis 时为本地统计） */
export function getCacheInfo() {
  return http.get('/admin/monitor/cache')
}
