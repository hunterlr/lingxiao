# 29 · CMS 门户接口（`/api/portal/cms/**`）

> 状态：v1.1 第一版已落地（2026-11-22），只读、只出已发布内容。
> 门户工程（Nuxt3）尚未开工，本文是门户侧唯一需要遵守的接口契约。
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
| GET | `/api/portal/cms/sites/{siteKey}/articles/by-slug/{slug}` | 文章详情（按 slug，适合伪静态） |
| GET | `/api/portal/cms/sites/{siteKey}/search` | 关键字搜索（标题 / 摘要） |

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
5. **排序**：栏目按 `sort`、`categoryId`；文章按 `sort`、`publishTime`、`articleId` 倒序。

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

- 定时发布、内容版本、回收站：后台尚未实现，门户自然读不到。
- 素材选择器已在后台落地；门户仍可能读到存量的 `fileId` 或 URL，消费端两种都要兼容。
- 内容级多语言：走后续 `i18n_*` 内容翻译阶段。
- 评论、点赞、会员可见性：属于 v1.2 会员互动。
- sitemap / RSS / Open Graph 生成：属于 v1.3 门户工程。
- 全量静态化、ES 搜索、CDN：不在本期范围。
