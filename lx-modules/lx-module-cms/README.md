# lx-module-cms · 内容管理模块

## 这个目录是做什么的

后台内容管理：站点 → 栏目树 → 文章 → 标签。v1.1 已做**后台维护链路 + 门户只读接口 +
素材选择器**，不包含门户渲染、内容版本、回收站和内容级多语言。

**已实装（v1.1 第一刀 + 门户 API，2026-11-22）**：

- 站点 CRUD（站点标识、名称、域名、Logo、SEO、状态）；
- 栏目树 CRUD（`parent_id` + 闭包表，成环校验、跨站点父级拒绝）；
- 文章 CRUD + 草稿 / 发布 / 下线状态流转，正文写入与读取双侧 HTML 清洗；
- 标签 CRUD，文章多标签，标签不能跨站点复用；
- 删除保护：站点 / 栏目 / 标签被引用时拒绝删除；
- 后台页面四个：站点、栏目、文章、标签；菜单与按钮权限随模块开关下线；
- 门户只读接口 `/api/portal/cms/**`：站点、导航树、标签、文章列表 / 详情 / 搜索；
- 素材选择器：站点 Logo、栏目封面、文章封面接文件中心图片库。

**明确未做（后置）**：

- Nuxt3 门户工程、SEO / sitemap / 静态化（v1.3 前）；
- 内容版本、回收站、定时发布；
- `cms_content_ext` 自定义字段；
- 内容级多语言（L3，`i18n_*` 表）。

## 模块开关

```yaml
lx:
  module:
    modules:
      cms:
        enabled: true
```

- 启动期生效，改完必须重启；不是运行期热插拔。
- `false`：`cms_*` 迁移不扫描、Controller 不装配（`/api/admin/cms/**` 404）、
  CMS 菜单与权限码不下发、前端路由落 404；**已建表与数据保留**。
- 迁移目录固定为 `db/migration/cms`，目录名必须等于模块编码 `cms`。

## 包结构

```text
com.lingxiao.modules.cms
├─ controller/
│  ├─ CmsSiteController      站点列表 / 选项 / 详情 / 增删改
│  ├─ CmsCategoryController  栏目树 / 平铺列表 / 详情 / 增删改
│  ├─ CmsTagController       标签分页 / 选项 / 增删改
│  ├─ CmsArticleController   文章分页 / 详情 / 增删改 / 发布 / 下线
│  └─ CmsPortalController    门户只读接口（/api/portal/cms/**，无需登录）
├─ service/
│  ├─ CmsSiteService         站点 CRUD、删除保护
│  ├─ CmsCategoryService     栏目树、闭包全量重建、成环与跨站点校验
│  ├─ CmsTagService          标签 CRUD、被引用检查
│  ├─ CmsArticleService      正文清洗、状态流转、slug 唯一、标签同站校验
│  └─ CmsPortalService       门户可见性、字段白名单、阅读量、子树 / 标签筛选
├─ dto/portal/               门户 VO（站点 / 栏目 / 标签 / 摘要 / 详情）
├─ entity/                   CmsSite / CmsCategory / CmsArticle / CmsTag
├─ mapper/                   MyBatis-Plus Mapper（含 hardDelete、标签 EXISTS 过滤）
└─ resources/db/migration/cms/
   ├─ V20__cms_schema.sql    6 张 cms_* 表
   └─ V21__cms_menu.sql      菜单 3000+ 与按钮权限，module_code='cms'
```

## 数据模型

| 表 | 说明 |
|---|---|
| `cms_site` | 站点根节点；`site_key` 全局唯一 |
| `cms_category` | 栏目；`parent_id=0` 表示根，`(site_id, category_key)` 唯一 |
| `cms_category_closure` | 栏目闭包：`category_id / ancestor_id / depth`，只用于子树 / 祖先查询 |
| `cms_article` | 文章；`status` 0 草稿 / 1 已发布 / 2 已下线 |
| `cms_tag` | 标签；`(site_id, tag_key)` 唯一 |
| `cms_article_tag` | 文章与标签多对多 |

**没有跨模块外键。** 文章的作者只存 `author_id / author_name` 快照，
封面 / Logo 只存 `fileId` 或 URL，不建 CMS 自己的文件表；
删除引用由服务层校验，不依赖数据库外键。

### 栏目闭包为什么每次全量重建

栏目增删改后，`CmsCategoryService#rebuildClosure` 按站点删掉旧闭包、按父链重新插入。
栏目数量是“几十到几百”的量级，全量重建比增量维护更不容易写错：

- 自身深度 0，父级 1，祖父 2……；
- 移动栏目后子树深度自动跟着变；
- 建环（把父栏目挂到自己的子孙下）在写入前拒绝，不会产生无限递归。

## 文章状态机

```text
新建 ──→ 草稿(0)
          │  publish
          ▼
       已发布(1) ── offline ──→ 已下线(2)
          ▲                        │
          └──────── publish ───────┘

编辑保存：不改变状态
```

- **新建一律草稿**，请求里带 `status=1` 也不采纳；
- 编辑只改内容，不改状态，避免“改个错别字把下线文章又发出去”；
- 发布写 `publish_time`，下线保留 `publish_time` 作为历史；
- 发布 / 下线走独立接口与独立权限码。

## 富文本清洗

文章正文在**写入和读取两侧**都过 `com.lingxiao.common.web.util.HtmlSanitizer`（Jsoup 白名单）：

- 写入：防止脏 HTML 落库；
- 读取：兼容历史数据与直接改库的数据；
- 后台预览用服务端清洗后的内容渲染，预览与门户看到的一致。

清洗工具已从 `lx-module-system` 上移到 `lx-common-web`，公告与 CMS 共用一份实现。

## 接口与权限码

| 接口 | 权限码 |
|---|---|
| `GET /api/admin/cms/site/list`、`/options`、`/{id}` | `platform:cms:site:list` |
| `POST/PUT/DELETE /api/admin/cms/site` | `site:add` / `site:edit` / `site:remove` |
| `GET /api/admin/cms/category/tree`、`/list`、`/{id}` | `platform:cms:category:list` |
| `POST/PUT/DELETE /api/admin/cms/category` | `category:add` / `category:edit` / `category:remove` |
| `GET /api/admin/cms/article/list`、`/{id}` | `platform:cms:article:list` |
| `POST/PUT/DELETE /api/admin/cms/article` | `article:add` / `article:edit` / `article:remove` |
| `PUT /api/admin/cms/article/{id}/publish` | `platform:cms:article:publish` |
| `PUT /api/admin/cms/article/{id}/offline` | `platform:cms:article:offline` |
| `GET /api/admin/cms/tag/list`、`/options` | `platform:cms:tag:list` |
| `POST/PUT/DELETE /api/admin/cms/tag` | `tag:add` / `tag:edit` / `tag:remove` |

权限码沿用 v1.0 的 `list / add / edit / remove` 风格；
文章额外拆 `publish` / `offline`，方便“编辑能改稿、发布只给主编”。

## 门户接口（公开只读）

| 接口 | 说明 |
|---|---|
| `GET /api/portal/cms/sites` | 启用站点列表 |
| `GET /api/portal/cms/sites/{siteKey}` | 站点信息 |
| `GET /api/portal/cms/sites/{siteKey}/categories` | 栏目导航树（显示且启用） |
| `GET /api/portal/cms/sites/{siteKey}/tags` | 启用标签 |
| `GET /api/portal/cms/sites/{siteKey}/articles` | 已发布文章分页（栏目子树 / 标签 / 关键字） |
| `GET /api/portal/cms/sites/{siteKey}/articles/{articleId}` | 文章详情 |
| `GET /api/portal/cms/sites/{siteKey}/articles/by-slug/{slug}` | 按 slug 取详情 |
| `GET /api/portal/cms/sites/{siteKey}/search` | 标题 / 摘要搜索 |

- 不需要登录、不走后台权限；可见性由 `CmsPortalService` 自己兜：
  站点启用、文章已发布、栏目**及所有祖先**启用。
- 停用栏目整棵子树不可见；隐藏栏目本身不进导航，可见子栏目上提。
- 只返回 `Portal*VO` 白名单字段；列表不带 `content`，详情返回清洗后的 HTML。
- `pageSize` 上限 50；`keyword` 最长 50 字符；阅读量单条 UPDATE 自增。
- 完整契约、参数、缓存建议见 [`docs/29-portal-api.md`](../../docs/29-portal-api.md)。

## 素材选择器

站点 Logo、栏目封面、文章封面统一使用 `lx-ui-admin/src/components/MediaPicker.vue`：

- 浏览文件中心需要 `platform:file:list`，上传需要 `platform:file:upload`；
  没有权限时对应按钮不渲染，字段仍可手工粘贴外链，不会把编辑流程卡死。
- 选中后写 `fileId`，不把当前环境域名写进业务数据；存量 `http(s)` 外链继续兼容。
- 粘贴 `/api/file/{id}` 形式的地址时，失焦自动归一成 `fileId`。
- 列表请求带 `imageOnly=true`，后端按图片扩展名过滤，非图片文件不占分页。
- 预览走 `/api/file/{fileId}?size=thumb`，与文件中心使用同一套公开访问接口。

## 前端页面

```text
lx-ui-admin/src/views/cms/
├─ site/index.vue       站点：搜索、表格、弹窗表单
├─ category/index.vue   栏目：树表、新增根 / 子栏目、父栏目树选择
├─ article/index.vue    文章：筛选、列表、富文本编辑、发布 / 下线 / 预览
└─ tag/index.vue        标签：分页、增删改
```

接口封装在 `lx-ui-admin/src/api/cms.js`；菜单键 `menu.cms:*` 与页面文案 `page.cms.*`
中英各一份。文章页的栏目过滤按父栏目包含子栏目（后端闭包子查询）。

## 删除保护

| 对象 | 拒绝条件 |
|---|---|
| 站点 | 仍有栏目或文章 |
| 栏目 | 仍有子栏目或文章 |
| 标签 | 仍被文章引用 |
| 文章 | 直接物理删除，先删 `cms_article_tag` 再删正文 |

## 验证

```powershell
# 单元测试（不依赖 MySQL）
mvn -B -ntp -pl lx-modules/lx-module-cms -am test

# 真实接口：34 项
node .local\cms-api-check.mjs

# 门户接口：38 项
node .local\portal-api-check.mjs

# 素材筛选与 fileId 落库：16 项
node .local\media-picker-check.mjs

# 浏览器：13 项
cd .local\uitest; node cms-ui.mjs

# 素材选择器：6 项
cd .local\uitest; node media-picker.mjs

# 模块关闭：接口 404、菜单 / 路由消失、数据保留
cd .local\uitest; node cms-disabled.mjs
node .local\portal-disabled.mjs
```

2026-11-22 结果：22 项单测（后台 14 + 门户 8）；后台接口 34/34；门户接口 38/38；
素材选择器接口 16/16、浏览器 6/6；后台浏览器 13/13；
关闭态门户 / 后台接口均 404（5/5），重新开启后数据保留。
