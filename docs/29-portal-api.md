# 29 · CMS 门户接口（`/api/portal/cms/**`）

> 状态：v1.1 第一版已落地（2026-11-22），只读、只出已发布内容。
> v1.3 门户第一刀已按本契约接入（Nuxt3，见 `docs/31-portal-frontend.md`）；
> 本文仍是门户侧唯一需要遵守的接口契约。
> 后台维护接口在 `/api/admin/cms/**`，门户**禁止**调用。

---

## 1. 边界

| 事项 | 约定 |
|---|---|
| 路径 | 全部在 `/api/portal/cms/**`，不经过 Sa-Token 登录拦截 |
| 鉴权 | 无需登录、无需 Token；公开只读 |
| 模块开关 | `lx.module.modules.cms.enabled=false` 时整组 Controller 不装配，返回 404 |
| 数据范围 | 只返回启用站点、启用栏目、启用标签、已发布文章 |
| 字段 | 不直接返回实体，统一转 `Portal*VO`，后台字段（`status / delFlag / createBy / remark` 等）不外泄 |
| 正文 | 详情接口返回**已清洗**的 HTML；门户可以直接渲染 |
| 分页 | `pageNum` 从 1 开始；`pageSize` 上限 50，超过按 50 处理 |
| 错误 | 站点 / 栏目 / 标签 / 文章不可见时统一 `code=1004`，不区分“不存在”和“无权限看” |

响应外层沿用全站约定：

```json
{ "code": 0, "msg": "操作成功", "data": {} }
```

`data` 为对象、数组或分页结构：

```json
{ "total": 12, "pageNum": 1, "pageSize": 10, "records": [] }
```

> `total / pageNum / pageSize` 经 Jackson 序列化后可能是字符串，前端比较时先 `Number()`。

---

## 2. 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/portal/cms/sites` | 启用站点列表（多站点切换） |
| GET | `/api/portal/cms/sites/{siteKey}` | 单站点信息（名称 / 域名 / Logo / SEO 默认值） |
| GET | `/api/portal/cms/sites/{siteKey}/categories` | 栏目导航树 |
| GET | `/api/portal/cms/sites/{siteKey}/tags` | 启用标签列表 |
| GET | `/api/portal/cms/sites/{siteKey}/articles` | 已发布文章分页 |
| GET | `/api/portal/cms/sites/{siteKey}/articles/{articleId}` | 文章详情（按 ID） |
| GET | `/api/portal/cms/sites/{siteKey}/articles/batch?ids=1,2,3` | 按 ID 批量取摘要（最多 50 条） |
| GET | `/api/portal/cms/sites/{siteKey}/articles/by-slug/{slug}` | 文章详情（按 slug，适合伪静态） |
| GET | `/api/portal/cms/sites/{siteKey}/search` | 关键字搜索（标题 / 摘要） |
| GET | `/api/portal/cms/sites/{siteKey}/pages/{pageKey}` | 已发布单页 |
| GET | `/api/portal/cms/sites/{siteKey}/navs/{navKey}` | 启用导航及有效导航项树 |
| GET | `/api/portal/cms/sites/{siteKey}/blocks/{blockKey}` | 启用区块（正文已清洗） |
| GET | `/api/portal/cms/sites/{siteKey}/banners?groupKey=home` | 启用且在时间窗内的轮播 |
| GET | `/api/portal/cms/preview/{ticket}` | 一次性草稿预览（不经过 siteKey） |

`siteKey` 是后台“站点标识”，不是数据库主键；站点不存在或停用一律 `1004`。

### 2.1 文章列表参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | int | 否 | 默认 1 |
| `pageSize` | int | 否 | 默认 10，上限 50 |
| `categoryId` | long | 否 | 栏目 ID；包含整棵子树 |
| `categoryKey` | string | 否 | 栏目站点内唯一标识；与 `categoryId` 同时传时 ID 优先 |
| `tagId` | long | 否 | 标签 ID |
| `tagKey` | string | 否 | 标签站点内唯一标识；与 `tagId` 同时传时 ID 优先 |
| `keyword` | string | 否 | 标题 / 摘要模糊匹配，最长 50 字符，超出截断 |

筛选用的栏目 / 标签必须属于当前站点且启用，否则 `1004`，不会退化成“忽略该条件”。

搜索接口只接受 `pageNum / pageSize / keyword`，规则与列表一致。

---

## 3. 可见性规则

这是门户最容易踩错的部分，按下面的顺序理解：

1. **站点**：`status=1`（停用）的站点，所有接口都当不存在。
2. **文章**：只有 `status=1`（已发布）可见；草稿、已下线、其它站点的文章一律 `1004`。
3. **栏目**：
   - 文章所属栏目必须启用；**任一祖先栏目停用，整棵子树都不可见**（包含文章详情）。
   - 导航树只输出“启用 + 显示”的栏目；停用栏目及其后代整体剔除。
   - 隐藏栏目（`visible=1`）本身不出现在导航树，但它的可见子栏目会上提为根节点或挂到最近可见父级。
     这样“隐藏容器、保留子页面”不需要额外字段。
4. **标签**：只输出启用标签；文章详情 / 列表中会自动过滤停用标签和跨站点标签。
5. **单页**：只有 `status=1`（已发布）可见；草稿 / 已下线一律 `1004`。
6. **导航**：导航与导航项都必须启用且显示；目标为栏目时栏目必须启用，目标为单页时必须已发布；
   目标失效的导航项整项不下发，父项被过滤时子项上提为根。
7. **区块 / 轮播**：区块只出 `status=0`（启用）；轮播还要求当前时间在 `start_time` /
   `end_time` 之间（留空表示不限）。
8. **排序**：栏目按 `sort`、`categoryId`；文章按 `sort`、`publishTime`、`articleId` 倒序；
   导航项 / 区块 / 轮播按 `sort` 升序。
9. **预览**：票据一次性消费，10 分钟过期；成功后才返回草稿，且不累加阅读量。

> 直接改库把栏目状态改成停用时，闭包表可能没同步。列表 SQL 的祖先判断依赖
> `cms_category_closure`；如果运维手工改库，改完必须调用一次后台的栏目编辑
> （触发按站点重建闭包），否则祖先停用判断可能失效。

---

## 4. 字段与渲染

### 4.1 站点

`siteKey / siteName / domain / logo / seoTitle / seoKeywords / seoDescription`

`logo` 兼容两种值：

- 纯数字：`sys_file.file_id`，门户拼文件中心访问地址；
- `http(s)://`：外链，直接使用。

后台写入时已拒绝其它协议，门户不需要再判协议（判一次也无害）。

### 4.2 文章摘要（列表）

不含 `content`，避免一页把 6 篇长文全部拖出来。带 `categoryId / categoryKey / categoryName`
和 `tags`，前端不需要再逐篇查栏目或标签。

### 4.3 文章详情

`content` 是 `HtmlSanitizer` 清洗后的 HTML；`category` 是完整栏目节点（含 SEO 与封面）；
`tags` 只含启用标签。`viewCount` 是本次访问累加后的值。

### 4.4 批量摘要

`GET /articles/batch?ids=1,2,3` 返回与列表相同的摘要结构，用于“只有文章 ID”的场景
（典型是会员收藏列表）。规则：

- 最多 50 个 ID，重复 ID 去重，非正数忽略；
- 只返回当前站点且已发布的文章，按请求顺序输出；
- 不可见 / 已下线的 ID 直接省略，不报错，门户据此显示“内容已下线”；
- **不累加阅读量**，因此收藏列表不能改用逐篇详情接口补标题。

### 4.5 单页 / 导航 / 区块 / 轮播

- **单页**：`pageId / pageKey / pageName / title / summary / cover / content / template /
  seoTitle / seoKeywords / seoDescription / publishTime / ext`；
  `content` 同样经过 `HtmlSanitizer`，`ext` 为 page 模型扩展字段。
- **导航**：`navId / navKey / navName / position / items[]`；每个导航项包含
  `itemId / itemName / targetType / targetId / url / icon / sort / children[]`。
  `url` 由服务端解析好：栏目固定 `/category/{categoryKey}`（`cms_category.path` 允许运营填自定义值，
  但门户路由以稳定键为准，不直接透出，避免相对 path 拼出 `/news` 死链）；
  单页为 `/page/{pageKey}`；外链原样返回。
- **区块**：`blockId / blockKey / blockName / blockType / content / cover / linkUrl / sort`。
  `blockType=html` 时 `content` 已清洗；`blockType=image` 用 `cover` + `linkUrl`。
- **轮播**：`bannerId / bannerGroup / title / image / linkUrl / sort`。
  `groupKey` 默认 `home`。

### 4.6 草稿预览

`GET /api/portal/cms/preview/{ticket}` 不需要 `siteKey`：票据本身绑定站点与目标。
返回 `targetType`（`article` / `page`）以及 `article` 或 `page` 对象，字段与正式接口一致。
票据由后台接口 `POST /api/admin/cms/article/{id}/preview-ticket` 或
`POST /api/admin/cms/page/{id}/preview-ticket` 生成，TTL 10 分钟，只能消费一次；
无效 / 过期 / 已使用统一按参数错误处理。预览不校验发布状态，也不累加阅读量。

**阅读量**：详情接口每调用一次 `view_count = view_count + 1`（单条 UPDATE 自增，
不先查后写）。当前没有防刷策略，门户上线前需要按 IP / 会话做限流或改异步统计。

---

## 5. 调用示例

```bash
# 站点
curl http://127.0.0.1:8080/api/portal/cms/sites/main

# 导航树
curl http://127.0.0.1:8080/api/portal/cms/sites/main/categories

# 栏目下文章（含子栏目）
curl "http://127.0.0.1:8080/api/portal/cms/sites/main/articles?categoryKey=news&pageNum=1&pageSize=10"

# 标签筛选
curl "http://127.0.0.1:8080/api/portal/cms/sites/main/articles?tagKey=java"

# 搜索
curl "http://127.0.0.1:8080/api/portal/cms/sites/main/search?keyword=发布"

# 详情
curl http://127.0.0.1:8080/api/portal/cms/sites/main/articles/1234567890
curl http://127.0.0.1:8080/api/portal/cms/sites/main/articles/by-slug/hello-world

# 批量摘要（不累加阅读量）
curl "http://127.0.0.1:8080/api/portal/cms/sites/main/articles/batch?ids=1234567890,1234567891"

# 单页 / 导航 / 区块 / 轮播
curl http://127.0.0.1:8080/api/portal/cms/sites/main/pages/about
curl http://127.0.0.1:8080/api/portal/cms/sites/main/navs/main
curl http://127.0.0.1:8080/api/portal/cms/sites/main/blocks/home-hero
curl "http://127.0.0.1:8080/api/portal/cms/sites/main/banners?groupKey=home"

# 草稿预览（票据先由后台接口生成，10 分钟内有效且只能用一次）
curl http://127.0.0.1:8080/api/portal/cms/preview/9f2c1a7b4e
```

多语言：门户接口本身不做内容翻译。错误文案受 `X-Lang / ?lang / Accept-Language` 影响，
和后台一致；业务字段（标题 / 正文）按写入时的语言原样返回。

---

## 6. 缓存建议（未实现）

当前每次请求都查库，没有缓存层。门户接入时建议：

| 数据 | 建议 |
|---|---|
| 站点 / 栏目树 / 标签 | 进程内短 TTL（30~60s）或 Redis，发布 / 下线 / 栏目变更后主动失效 |
| 文章列表 | 按 `siteKey + category + tag + page` 做键，TTL 60s |
| 文章详情 | 按 `siteKey + articleId` 做键，发布 / 下线 / 编辑后失效 |
| 阅读量 | 不同步进缓存；限流后异步累加 |

缓存键必须带 `siteKey`：同一 slug 在不同站点可以重复，只按 slug 缓存会串站。

---

## 7. 明确不做（留给后续）

- 单页 / 导航 / 区块 / 轮播：等 CMS 第三刀提供只读接口（`docs/20` §21.4），
  门户随后渲染页脚、关于我们与首页推荐位。
- 模板：栏目 `template` 目前只透传；第四刀按“模板编码 + Nuxt 组件映射”生效
  （`docs/20` §21.5），不引入服务端模板引擎。
- 素材选择器已在后台落地；门户仍可能读到存量的 `fileId` 或 URL，消费端两种都要兼容。
- 内容级多语言：走后续 `i18n_*` 内容翻译阶段。
- 评论、点赞、会员可见性：属于 v1.2 会员互动。
- sitemap / RSS：门户已预留 `robots.txt` 注释位，随 CMS 第四刀补齐；
  Open Graph / JSON-LD 已由门户第一刀在页面侧输出。
- 全量静态化、ES 搜索、CDN：不在本期范围，静态化排到 P6 可选能力。

---

## 8. 门户接入状态（2026-11-22）

`lx-ui-portal` 已按本契约完成第一、二、三刀：

| 页面 | 使用的接口 |
|---|---|
| 首页 | `GET /sites/{siteKey}`、`/categories`、`/articles?pageNum=1&pageSize=7` |
| 栏目列表 | `/categories`、`/articles?categoryKey=...&pageNum=...&pageSize=...` |
| 文章详情 | `/articles/{articleId}`、`/categories`、同栏目 `/articles`；会员评论 `/api/app/public/comments/articles/{articleId}` 与登录发表 |
| 站内搜索 | `/search?keyword=...&pageNum=...&pageSize=...` |
| 个人中心 · 我的收藏 | 会员 `/api/app/interactions/favorites` + `/articles/batch` 补标题 / 封面 |

- 浏览器与 SSR 都请求同源 `/api/**`，由 Nitro 代理到 Spring Boot；
- `1004` 统一转 404，其它异常转 502；
- 正文直接使用详情接口返回的已清洗 HTML；
- 封面 / Logo 兼容 `fileId`、`/api/file/{id}` 与 http(s) 外链。

详见 `docs/31-portal-frontend.md` 与 `lx-ui-portal/README.md`。
