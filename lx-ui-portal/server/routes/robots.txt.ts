import type { H3Event } from 'h3'

/**
 * 门户 robots.txt。
 *
 * <p>内容页全部允许抓取；预览、登录、注册与个人中心不收录。
 * sitemap 地址跟随站点对外地址，部署换域名时只改环境变量。</p>
 */
export default defineEventHandler((event: H3Event) => {
  const { siteUrl } = feedConfig(event)
  setHeader(event, 'content-type', 'text/plain; charset=utf-8')
  setHeader(event, 'cache-control', 'public, max-age=300')
  return [
    'User-agent: *',
    'Allow: /',
    'Disallow: /preview/',
    'Disallow: /account',
    'Disallow: /login',
    'Disallow: /register',
    'Disallow: /oauth/',
    '',
    `Sitemap: ${absoluteUrl(siteUrl, '/sitemap.xml')}`,
    ''
  ].join('\n')
})
