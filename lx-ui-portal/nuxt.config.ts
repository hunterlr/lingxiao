// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2026-11-01',
  ssr: true,
  devtools: { enabled: false },

  css: ['~/assets/css/main.css'],

  devServer: {
    port: 3000
  },

  runtimeConfig: {
    /**
     * Spring Boot 服务根地址。浏览器不直接访问它：
     * server/api/[...path].ts 把同源 /api/** 转发到这里，避免 CORS 与
     * 部署时写死内网地址。
     */
    backendBase: process.env.NUXT_BACKEND_BASE || 'http://127.0.0.1:8080',
    public: {
      /** 后台 CMS 站点的 siteKey */
      siteKey: process.env.NUXT_PUBLIC_SITE_KEY || 'main',
      /** 门户对外地址，用于 canonical / og:url */
      siteUrl: process.env.NUXT_PUBLIC_SITE_URL || 'http://127.0.0.1:3000',
      /** 列表每页条数，接口上限 50 */
      pageSize: Number(process.env.NUXT_PUBLIC_PAGE_SIZE || 9)
    }
  },

  app: {
    head: {
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      htmlAttrs: { lang: 'zh-CN' },
      link: [{ rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }]
    }
  },

  nitro: {
    compressPublicAssets: true,
    externals: {
      /**
       * Windows 未开启开发者模式时，Nitro 为多版本依赖创建 symlink 会 EPERM，
       * 导致 .output/server/node_modules 缺包。entities 体积很小，直接内联，
       * 既绕开该限制，也不影响其他依赖的外置策略。
       */
      inline: ['entities']
    }
  }
})
