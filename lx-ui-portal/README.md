# lx-ui-portal · CMS 门户

凌霄的公开内容门户，Nuxt 3 实现，**只读**消费 `/api/portal/cms/**`。

## 这个目录做什么

- 站点首页：最新文章、栏目快捷入口；
- 栏目列表：按 `categoryKey` 拉取文章，支持分页与面包屑；
- 文章详情：渲染后端已清洗的 HTML，展示作者、时间、阅读量、标签、同栏目推荐；
- 站内搜索：按标题 / 摘要关键字搜索已发布文章；
- SEO：`title` / `description` / `keywords` / `canonical` / Open Graph / 文章 JSON-LD；
- 404 与接口不可用兜底：后台返回 `1004` 统一按 404 处理。

**不做**（后续切片）：会员登录与个人中心、评论 / 收藏 / 点赞、多站点切换、标签页、
sitemap / RSS、内容级多语言、全量静态化。

## 快速开始

前置：后端已启动（默认 `http://127.0.0.1:8080`），后台建好站点并发布至少一篇文章。

```powershell
cd lx-ui-portal
Copy-Item .env.example .env   # 按需改站点标识
npm install
npm run dev                   # http://127.0.0.1:3000
```

默认站点标识是 `main`，必须与后台「CMS → 站点」里的 `siteKey` 一致；
不一致时接口返回 `1004`，门户显示 404。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `NUXT_BACKEND_BASE` | `http://127.0.0.1:8080` | Spring Boot 根地址，Nitro 代理由此转发 `/api/**` |
| `NUXT_PUBLIC_SITE_KEY` | `main` | 门户默认站点标识 |
| `NUXT_PUBLIC_SITE_URL` | `http://127.0.0.1:3000` | 对外访问地址，用于 canonical / og:url |
| `NUXT_PUBLIC_PAGE_SIZE` | `9` | 列表每页条数，接口上限 50 |

浏览器不直接访问后端：`server/api/[...path].ts` 把同源 `/api/**` 转发到
`NUXT_BACKEND_BASE`，因此不需要 CORS，部署时也只需改环境变量。

## URL 方案

| 路径 | 页面 |
|---|---|
| `/` | 首页 |
| `/category/{categoryKey}` | 栏目文章列表（`?page=2` 分页） |
| `/article/{articleId}` | 文章详情 |
| `/search?keyword=xxx` | 站内搜索（`noindex`） |

栏目列表与搜索页按 `route.fullPath` 作为页面 key，查询参数变化时重新取数。

## 目录结构

```text
lx-ui-portal/
├─ app.vue                    加载条 + 布局出口
├─ error.vue                  404 / 502 兜底页
├─ layouts/default.vue        站点头、导航、搜索、页脚、SEO 默认值
├─ pages/
│  ├─ index.vue               首页
│  ├─ category/[key].vue      栏目列表
│  ├─ article/[id].vue        文章详情 + JSON-LD
│  └─ search.vue              站内搜索
├─ components/
│  ├─ ArticleCard.vue         文章卡片
│  ├─ PortalPager.vue         分页（保留查询参数）
│  └─ EmptyState.vue          空状态
├─ composables/usePortalApi.ts 接口封装与 1004 → 404
├─ server/api/[...path].ts    同源 API 反向代理
├─ utils/                     图片地址、时间/数字格式化、栏目树
├─ types/portal.ts            接口数据类型
└─ assets/css/main.css        全站样式（无 UI 框架）
```

## 渲染与安全

- 默认 SSR；页面数据在服务端取，首屏 HTML 带完整正文与 SEO 标签。
- 文章 `content` 由后端 `HtmlSanitizer` 白名单清洗，门户用 `v-html` 直接渲染，不再二次解析。
- Logo / 封面兼容 `fileId` 与 http(s) 外链；其它协议直接忽略。
- 搜索页输出 `robots: noindex,follow`，避免薄内容进索引。

## 构建与部署

```powershell
npm run build
$env:NUXT_BACKEND_BASE='http://127.0.0.1:8080'
$env:NUXT_PUBLIC_SITE_KEY='main'
$env:NUXT_PUBLIC_SITE_URL='https://www.example.com'
node .output/server/index.mjs
```

生产建议 Nginx 反代门户 Node 进程，并把 `/api/**` 一并交给门户的 Nitro 代理；
如果直接由 Nginx 转发 `/api/**` 到后端，也可以保留门户内的代理作为本地开发兜底。

## 验收

```powershell
npm run build
# SSR 首页包含站点名与已发布文章标题
curl.exe -s http://127.0.0.1:3000/ | Select-String 'article-title|hero-title'
# 未发布 / 不存在的文章返回 404
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:3000/article/1
```

浏览器验收覆盖：首页 -> 栏目 -> 详情 -> 搜索、分页、无前端报错、移动端布局。
