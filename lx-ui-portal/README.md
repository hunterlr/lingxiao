# lx-ui-portal · CMS 门户

凌霄的公开内容门户，Nuxt 3 实现。内容页**只读**消费 `/api/portal/cms/**`；
会员页调用自助接口 `/api/app/**`，不触碰任何 `/api/admin/**` 接口。

## 这个目录做什么

- 站点首页：最新文章、栏目快捷入口；
- 栏目列表：按 `categoryKey` 拉取文章，支持分页与面包屑；
- 文章详情：渲染后端已清洗的 HTML，展示作者、时间、阅读量、标签、同栏目推荐；
- 站内搜索：按标题 / 摘要关键字搜索已发布文章；
- 会员注册 / 登录 / 退出 / 找回密码；
- 个人中心：资料展示、改昵称 / 头像 / 性别、修改密码；
- 文章收藏 / 点赞，个人中心「我的收藏」；
- 文章评论：匿名可读、登录后发表，提交后待后台审核；
- SEO：`title` / `description` / `keywords` / `canonical` / Open Graph / 文章 JSON-LD；
- 404 与接口不可用兜底：后台返回 `1004` 统一按 404 处理。

**不做**（后续切片）：消息通知、手机号 / 邮箱换绑、第三方登录、
多站点切换、标签页、sitemap / RSS、内容级多语言、全量静态化。

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

会员功能依赖后台「系统管理 → 参数设置」的 `mem.enabled` 开关（默认开启）；
评论还受「会员管理 → 会员设置 → 评论功能开关」`mem.comment.enabled` 控制。
本地验收可在启动后端时加 `--lx.captcha.enabled=false` 关闭图形验证码；
关闭后门户登录 / 注册页会自动隐藏验证码输入框。

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
| `/login` | 会员登录 |
| `/register` | 会员注册 |
| `/forgot-password` | 找回密码（申请验证码 → 重置） |
| `/account` | 个人中心（需登录，含我的收藏） |

栏目列表与搜索页按 `route.fullPath` 作为页面 key，查询参数变化时重新取数。
会员页面统一 `noindex`，已登录访问登录 / 注册页会直接跳到 `/account`。

## 会员会话

| 项 | 说明 |
|---|---|
| 本地 Cookie | `lx-portal-member-token`，`SameSite=Lax`、`path=/`、7 天 |
| 请求头 | `lx-member-token`（后端只读请求头，不读 Cookie） |
| 登录态 | layout 在 SSR 调 `GET /api/app/profile`，首屏直接渲染昵称 / 头像 |
| 会话失效 | HTTP 401 / `code=1002` 清 Cookie，回到匿名态 |
| 会员关闭 | `code=1003` 时登录 / 注册页提示「会员功能已关闭」，提交禁用 |
| 验证码 | `GET /api/app/auth/captcha?scene=login`，`enabled=false` 时不渲染输入框 |

会员接口封装在 `composables/useMemberAuth.ts`，与只读的 `usePortalApi` 分开。
注册必须勾选协议；手机号 / 邮箱是否必填由后台会员设置决定，前端不硬编码。
修改密码或重置密码成功后，后端会注销该会员全部会话，门户同步清 Cookie 并回登录页。

## 收藏与点赞

| 动作 | 接口 | 说明 |
|---|---|---|
| 读统计 | `GET /api/app/public/interactions/articles/{articleId}` | 匿名可读；带 Token 时附带个人状态 |
| 我的收藏 | `GET /api/app/interactions/favorites` | 只返回文章 ID 与收藏时间 |
| 收藏 / 取消 | `PUT / DELETE /api/app/interactions/articles/{articleId}/favorite` | 幂等 |
| 点赞 / 取消 | `PUT / DELETE /api/app/interactions/articles/{articleId}/like` | 幂等 |

- 未登录点击收藏 / 点赞跳 `/login?redirect=/article/{id}`；`1002` 同样回登录页。
- 收藏列表先取会员接口，再调 CMS `GET /articles/batch?ids=...` 补标题 / 封面；
  批量摘要不累加阅读量，文章下线时条目显示「内容已下线或不可见」。
- 会员功能关闭或统计接口异常时，文章页计数回落 0，正文不受影响。

## 评论

| 动作 | 接口 | 说明 |
|---|---|---|
| 读评论 | `GET /api/app/public/comments/articles/{articleId}` | 匿名可读，只返回已审核通过的评论 |
| 发表 | `POST /api/app/comments/articles/{articleId}` | 必须登录，提交后待审核 |

- 文章页在 SSR 取第一页评论，「加载更多」追加下一页，不改 URL。
- 未登录只显示登录引导；登录后显示 1000 字输入框，提交成功提示
  「审核通过后会展示在下方」，不做乐观插入。
- `1002` 提交时回登录页；`1003`（评论开关关闭）隐藏输入框，
  只显示「评论功能已关闭，请先在会员设置中开启」。
- 评论读取失败只降级评论区，正文、收藏与点赞仍正常渲染；评论按纯文本显示。

## 目录结构

```text
lx-ui-portal/
├─ app.vue                    加载条 + 布局出口
├─ error.vue                  404 / 502 兜底页
├─ layouts/default.vue        站点头、导航、搜索、会员入口、页脚、SEO 默认值
├─ pages/
│  ├─ index.vue               首页
│  ├─ category/[key].vue      栏目列表
│  ├─ article/[id].vue        文章详情 + JSON-LD
│  ├─ search.vue              站内搜索
│  ├─ login.vue               会员登录
│  ├─ register.vue            会员注册
│  ├─ forgot-password.vue     找回密码
│  └─ account.vue             个人中心
├─ components/
│  ├─ ArticleCard.vue         文章卡片
│  ├─ PortalPager.vue         分页（保留查询参数）
│  └─ EmptyState.vue          空状态
├─ composables/
│  ├─ usePortalApi.ts         内容接口封装与 1004 → 404
│  └─ useMemberAuth.ts        会员会话、Token Cookie、资料、密码、收藏 / 点赞 / 评论
├─ server/api/[...path].ts    同源 API 反向代理
├─ utils/                     图片地址、时间/数字格式化、栏目树
├─ types/
│  ├─ portal.ts               内容接口数据类型
│  └─ member.ts               会员接口数据类型
└─ assets/css/main.css        全站样式（无 UI 框架）
```

## 渲染与安全

- 默认 SSR；页面数据在服务端取，首屏 HTML 带完整正文与 SEO 标签。
- 文章 `content` 由后端 `HtmlSanitizer` 白名单清洗，门户用 `v-html` 直接渲染，不再二次解析。
- Logo / 封面兼容 `fileId` 与 http(s) 外链；其它协议直接忽略。
- 搜索页与会员页输出 `robots: noindex`，避免薄内容进索引。
- 会员 Token 存在同源 Cookie，仅用于向 `/api/app/**` 发请求；不写进 URL，
  也不转发到后台管理接口。Cookie 不是 `httpOnly`，因为客户端跳转后仍需自行发请求。

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
# 未登录访问个人中心显示守卫页
node .local\portal-ssr-member.mjs
# 带会员 Cookie 验证文章页收藏 / 点赞 SSR
node .local\portal-interaction-ssr.mjs
# 评论 SSR：匿名 / 会员评论框、登录引导、已通过评论
node .local\portal-comment-ssr.mjs
```

浏览器验收覆盖：首页 → 栏目 → 详情 → 搜索、分页、无前端报错、移动端布局；
会员注册 / 登录 / 改资料 / 改密 / 找回密码 / 退出、收藏 / 点赞 / 我的收藏、
评论发表 / 待审不公开 / 后台审核 / 评论开关关闭提示、`mem.enabled` 关闭提示。
后端会员总开关在「系统管理 → 参数设置 → `mem.enabled`」，关闭后 `/api/app/**`
返回 `1003`，门户显示明确提示而不是白屏。
