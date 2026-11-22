import { getRequestURL, proxyRequest } from 'h3'

/**
 * 同源 API 代理。
 *
 * <p>门户页面只请求 `/api/**`，由 Nitro 转发到 Spring Boot。
 * 这样浏览器不需要跨域，SSR 与客户端使用同一套相对地址，
 * 部署时只改 `NUXT_BACKEND_BASE` 即可。</p>
 */
export default defineEventHandler((event) => {
  const config = useRuntimeConfig()
  const url = getRequestURL(event)
  const target = `${config.backendBase}${url.pathname}${url.search}`
  return proxyRequest(event, target)
})
