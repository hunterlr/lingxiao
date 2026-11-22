# lx-module-cms · 内容管理模块

## 这个目录是做什么的

后台内容管理：站点 → 栏目树 → 文章 → 标签。v1.1 已做**后台维护链路 + 门户只读接口 +
素材选择器 + 文章回收站 + 定时发布 + 内容版本**，不包含门户渲染和内容级多语言。

**已实装（v1.1 第一刀 + 门户 API + 回收站 + 定时发布 + 内容版本，2026-11-22）**：

- 站点 CRUD（站点标识、名称、域名、Logo、SEO、状态）；
- 栏目树 CRUD（`parent_id` + 闭包表，成环校验、跨站点父级拒绝）；
- 文章 CRUD + 草稿 / 发布 / 下线 / 定时待发布状态流转，正文写入与读取双侧 HTML 清洗；
- 标签 CRUD，文章多标签，标签不能跨站点复用；
- 删除保护：站点 / 栏目 / 标签被引用时拒绝删除；
- 文章回收站：软删除、记录删除人 / 时间、恢复回草稿、彻底删除；
- 定时发布：一次性排期，后台任务到点自动发布，支持改期 / 取消；
- 内容版本：创建 / 编辑 / 发布 / 回滚 / 恢复自动快照，可按版本回滚；
- 后台页面五个：站点、栏目、文章、标签、回收站；菜单与按钮权限随模块开关下线；
- 门户只读接口 `/api/portal/cms/**`：站点、导航树、标签、文章列表 / 详情 / 搜索；
- 素材选择器：站点 Logo、栏目封面、文章封面接文件中心图片库。

**明确未做（后置）**：

- Nuxt3 门户工程、SEO / sitemap / 静态化（v1.3 前）；
- 回收站保留期 / 自动清理、定时下线 / 周期发布、版本对比（diff）；
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
│  ├─ CmsArticleController   文章分页 / 详情 / 增删改 / 发布 / 下线 / 定时 / 版本 / 回收站
│  └─ CmsPortalController    门户只读接口（/api/portal/cms/**，无需登录）
├─ service/
│  ├─ CmsSiteService         站点 CRUD、删除保护
│  ├─ CmsCategoryService     栏目树、闭包全量重建、成环与跨站点校验
│  ├─ CmsTagService          标签 CRUD、被引用检查
│  ├─ CmsArticleService      正文清洗、状态流转、slug 唯一、标签同站校验、回收站、定时发布、内容版本
│  └─ CmsPortalService       门户可见性、字段白名单、阅读量、子树 / 标签筛选
├─ task/CmsScheduleTask      到点扫描与自动发布（CMS 开启才注册）
├─ dto/CmsArticleRecycleVO   回收站列表行
├─ dto/CmsArticleScheduleDTO 定时发布请求
├─ dto/CmsArticleVersionVO   版本列表行（不带正文）
├─ dto/portal/               门户 VO（站点 / 栏目 / 标签 / 摘要 / 详情）
├─ entity/                   CmsSite / CmsCategory / CmsArticle / CmsTag / CmsArticleVersion
├─ mapper/                   MyBatis-Plus Mapper（含 hardDelete、标签 EXISTS 过滤、回收站 / 排期 / 版本 SQL）
└─ resources/db/migration/cms/
   ├─ V20__cms_schema.sql    6 张 cms_* 表
   ├─ V21__cms_menu.sql      菜单 3000+ 与按钮权限，module_code='cms'
   ├─ V22__cms_article_recycle.sql  回收站审计列、索引、菜单 3050~3052
   ├─ V23__cms_article_schedule.sql 排期时间、索引、按钮 3053
   └─ V24__cms_article_version.sql  内容版本表、按钮 3054~3055
```

## 数据模型

| 表 | 说明 |
|---|---|
| `cms_site` | 站点根节点；`site_key` 全局唯一 |
| `cms_category` | 栏目；`parent_id=0` 表示根，`(site_id, category_key)` 唯一 |
| `cms_category_closure` | 栏目闭包：`category_id / ancestor_id / depth`，只用于子树 / 祖先查询 |
| `cms_article` | 文章；`status` 0 草稿 / 1 已发布 / 2 已下线 / 3 定时待发布；`schedule_time` 仅状态 3 有效 |
| `cms_article_version` | 文章内容快照；`(article_id, version_no)` 唯一，`content_hash` 用于相邻去重 |
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
          │  schedule                ┌── 到点扫描 ──┐
          ▼                          ▼               │
       定时待发布(3) ─────────────→ 已发布(1) ── offline ──→ 已下线(2)
          │                          ▲                        │
          └──── cancelSchedule ──→ 草稿                       │
                                     └──────── publish ───────┘
          ▲
          └── 已下线也能 schedule

编辑保存：不改变状态
```

- **新建一律草稿**，请求里带 `status=1` 也不采纳；
- 编辑只改内容，不改状态，避免“改个错别字把下线文章又发出去”；
- 发布写 `publish_time`，下线保留 `publish_time` 作为历史；
- 发布 / 下线 / 定时走独立接口与独立权限码；
- 删除进回收站（逻辑删除），恢复一律回草稿并保留 `publish_time`，需要重新发布才对外可见。

## 定时发布

- `PUT /api/admin/cms/article/{id}/schedule`：草稿 / 已下线 / 已排期可设置或修改计划时间；
  已发布必须先下线；时间必须晚于当前；
- `PUT /api/admin/cms/article/{id}/schedule/cancel`：定时待发布 → 草稿，并清空计划时间；
- `CmsScheduleTask` 默认每 30 秒扫描一次，用条件 UPDATE 把到点文章从 `3` 改成 `1`；
  多实例同时扫描只有一台成功，不依赖 job 模块或外部锁；
- 到点发布时 `publish_time = COALESCE(publish_time, schedule_time)`，随后清空 `schedule_time`；
- 手动发布 / 下线 / 回收站恢复都会清空 `schedule_time`；
- 扫描参数在 `application.yml`：`lx.cms.schedule-scan-interval-ms`（默认 30000）、
  `schedule-scan-initial-delay-ms`（默认 15000）、`schedule-batch-size`（默认 200）。

## 内容版本

- 创建 / 编辑 / 发布 / 回滚 / 回收站恢复后自动写一条不可变快照；
- 快照只覆盖内容字段（标题 / slug / 摘要 / 封面 / 正文 / 来源 / 栏目 / 标签 / 状态），
  排序 / 备注 / 作者等元数据不进版本；
- `content_hash` 对内容 + 标签 + 状态做 SHA-256，连续保存相同内容不产生新版本；
- `GET /api/admin/cms/article/{id}/versions`：分页列表，不返回正文；
- `GET /api/admin/cms/article/{id}/versions/{versionId}`：详情，正文读取时再清洗；
- `PUT /api/admin/cms/article/{id}/versions/{versionId}/rollback`：回滚；
- 回滚统一回草稿、保留 `publish_time`、清空 `schedule_time`；
  版本必须属于该文章，栏目必须存在且同站点，slug 不能被其它文章占用；
- 标签已删除或跨站点时跳过，不阻断正文回滚；
- 默认每篇保留最近 30 个版本（`lx.cms.version-keep`），彻底删除文章时清空版本。

## 回收站

- `DELETE /api/admin/cms/article/{id}` 只写 `del_flag='1'` 与删除人 / 时间快照，
  `cms_article_tag` 保留，恢复后标签不丢；
- 回收站列表不返回正文，站点 / 栏目名在 CMS 内 `LEFT JOIN`；
- 恢复前校验站点 / 栏目仍存在、slug 未被占用；栏目已删则提示直接彻底删除；
- 彻底删除只允许操作回收站记录：先清标签关联，再物理删正文；
- slug 唯一性、站点 / 栏目删除保护都包含回收站文章，避免“回收中占坑 / 恢复无归属”。

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
| `PUT /api/admin/cms/article/{id}/schedule` | `platform:cms:article:schedule` |
| `PUT /api/admin/cms/article/{id}/schedule/cancel` | `platform:cms:article:schedule` |
| `GET /api/admin/cms/article/{id}/versions` | `platform:cms:article:version` |
| `GET /api/admin/cms/article/{id}/versions/{versionId}` | `platform:cms:article:version` |
| `PUT /api/admin/cms/article/{id}/versions/{versionId}/rollback` | `platform:cms:article:rollback` |
| `GET /api/admin/cms/article/recycle/list` | `platform:cms:article:recycle` |
| `PUT /api/admin/cms/article/{id}/restore` | `platform:cms:article:restore` |
| `DELETE /api/admin/cms/article/{id}/purge` | `platform:cms:article:purge` |
| `GET /api/admin/cms/tag/list`、`/options` | `platform:cms:tag:list` |
| `POST/PUT/DELETE /api/admin/cms/tag` | `tag:add` / `tag:edit` / `tag:remove` |

权限码沿用 v1.0 的 `list / add / edit / remove` 风格；
文章额外拆 `publish` / `offline` / `schedule` / `version` / `rollback` /
`recycle` / `restore` / `purge`，方便“编辑能改稿、发布只给主编、回滚和彻底删除只给管理员”。

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
├─ article/index.vue    文章：筛选、列表、富文本编辑、发布 / 下线 / 定时、历史版本、预览、回收站入口
├─ recycle/index.vue    回收站：删除审计、恢复、彻底删除
└─ tag/index.vue        标签：分页、增删改
```

接口封装在 `lx-ui-admin/src/api/cms.js`；菜单键 `menu.cms:*` 与页面文案 `page.cms.*`
中英各一份。文章页的栏目过滤按父栏目包含子栏目（后端闭包子查询）。

## 删除保护

| 对象 | 拒绝条件 |
|---|---|
| 站点 | 仍有文章（含回收站）或栏目 |
| 栏目 | 仍有子栏目或文章（含回收站） |
| 标签 | 仍被文章引用（回收站文章也算） |
| 文章 | 删除只进回收站；彻底删除仅限回收站记录，先清 `cms_article_tag` 与 `cms_article_version` 再删正文 |

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

# 回收站：33 项
node .local\cms-recycle-check.mjs

# 定时发布：39 项
node .local\cms-schedule-check.mjs

# 内容版本：42 项
node .local\cms-version-check.mjs

# 浏览器：13 项
cd .local\uitest; node cms-ui.mjs

# 素材选择器：6 项
cd .local\uitest; node media-picker.mjs

# 回收站浏览器：12 项
cd .local\uitest; node cms-recycle.mjs

# 定时发布浏览器：10 项
cd .local\uitest; node cms-schedule.mjs

# 内容版本浏览器：9 项
cd .local\uitest; node cms-version.mjs

# 模块关闭：接口 404、菜单 / 路由消失、数据保留
cd .local\uitest; node cms-disabled.mjs
node .local\portal-disabled.mjs
```

2026-11-22 结果：44 项单测（后台 36 + 门户 8）；后台接口 34/34；门户接口 38/38；
素材选择器接口 16/16、浏览器 6/6；回收站接口 33/33、浏览器 12/12；
定时发布接口 39/39、浏览器 10/10；内容版本接口 42/42、浏览器 9/9；后台浏览器 13/13；
关闭态门户 / 后台接口均 404（5/5），重新开启后数据保留。
