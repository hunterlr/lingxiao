# 20 · CMS / 会员 / 门户模块化嵌入方案

> 定位：v1.0 只做后台基座；**CMS、会员、门户作为后续模块嵌入，不改基座代码**。
> 目标：加依赖 + 开开关 + 跑迁移 + 导菜单 = 可用；关闭后基座照常启动，数据保留。
> 原则：**模块是代码边界，不是微服务**；先同进程嵌入，确有独立售卖 / 独立扩容 / 独立团队再拆项目。

---

## 1. 版本与交付物

| 版本 | 交付 | 形态 |
|---|---|---|
| v1.0 | 后台基座（系统管理 / 监控 / 工具 / 文件中心） | 单 `lx-boot` |
| v1.1 | CMS 后台 + 内容 API | `lx-module-cms` + 后台路由模块 |
| v1.2 | 会员注册登录 + 互动 | `lx-module-member` + `/api/app` |
| v1.3 | PC 门户 | 独立 `lx-ui-portal`（Nuxt3 SSR） |
| v1.4 | 工作流 / i18n 内容 / 支付 | 可选模块 |

```text
lingxiao/
├─ lx-modules/
│  ├─ lx-system/                 v1.0 必装
│  ├─ lx-file/                   v1.0 必装
│  ├─ lx-cms/                    v1.1 可选
│  └─ lx-member/                 v1.2 可选
├─ lx-api/
│  ├─ lx-admin-api/              /api/admin
│  ├─ lx-portal-api/             /api/portal（v1.1 起）
│  └─ lx-app-api/                /api/app（v1.2 起）
├─ lx-ui-admin/                  v1.0 后台
│  └─ src/modules/cms/           v1.1 后台路由模块（动态注册）
├─ lx-ui-portal/                 v1.3 Nuxt3
├─ db/migration/mysql/
│  ├─ system/  file/             v1.0
│  ├─ cms/                       v1.1
│  └─ member/                    v1.2
├─ menu/cms-menu.sql             v1.1
├─ api/module-cms.yaml           v1.1 模块契约
└─ api/module-member.yaml        v1.2 模块契约
```

> 后台前端先走**单仓模块目录 + 动态路由**，不急着发 npm 包；等多产品复用再抽 `@lingxiao/ui-cms`。

---

## 2. 模块契约（`module.yaml`）

每个可选模块必须提供一份描述，基座只读契约，不读模块代码：

```yaml
code: cms
name: 内容管理
version: 1.1.0
requires: [system, file]
optional: [member, workflow, i18n]
apiPrefix: /api/admin/cms
portalPrefix: /api/portal/cms
migrations: db/migration/mysql/cms
menus: menu/cms-menu.sql
permissions: api/permission-cms.yaml
frontend:
  admin: src/modules/cms
  portal: lx-ui-portal
config:
  lx.module.cms.enabled: false
events:
  publishes: [cms.content.published, cms.content.offline]
  consumes: [file.deleted]
```

**基座职责：**

- 读取 `module.yaml`，生成模块注册表。
- 按 `lx.module.*.enabled` 决定是否装配 Bean、Controller、菜单、路由。
- 提供 SPI：`FileService`、`AdminUserService`、`DeptService`、`DictService`、`ConfigService`、`MessageService`、`EventBus`。
- 不 import 任何 `lx-cms` / `lx-member` 的类。

**模块职责：**

- 只依赖 `system` / `file` 的 `api` 包，不直接访问 `sys_*` Mapper。
- 自己的表、自己的 Flyway、自己的菜单与权限码。
- 关闭时无悬空引用；重新打开数据完整。

---

## 3. 后端嵌入契约

### 3.1 依赖与装配

```xml
<dependency>
  <groupId>com.lingxiao</groupId>
  <artifactId>lx-module-cms</artifactId>
</dependency>
```

```java
@Configuration
@ConditionalOnProperty(name = "lx.module.cms.enabled", havingValue = "true")
public class CmsAutoConfiguration {
    // Controller / Service / Mapper 只在本配置下装配
}
```

- `lx-boot` 只加依赖，不写 CMS 业务代码。
- 关闭时：Bean 不加载、接口 404、Flyway 跳过、菜单不返回、前端路由不注册。
- 打开时：执行模块迁移、导入菜单与权限、注册路由。

### 3.2 模块间调用

| 场景 | 允许 | 禁止 |
|---|---|---|
| 取文件 | `FileService.get(fileId)` | 直接查 `sys_file` |
| 取后台用户 | `AdminUserService.get(userId)` | 直接查 `sys_user` |
| 取字典 | `DictService.items(type)` | 复制一份字典表 |
| 发消息 | `MessageService.send(...)` | 自己接短信 / 邮件 |
| 跨模块通知 | `EventBus.publish(...)` | 直接调对方 Service 改数据 |

- 禁止跨模块外键；只用 ID + 应用层校验。
- 禁止模块反向依赖 `lx-boot`。
- 跨模块一致性用本地事务 + Outbox 事件，不引入分布式事务。

### 3.3 权限与菜单

- 权限码统一 `platform:cms:<resource>:<action>`，例如 `platform:cms:content:publish`。
- 菜单带 `module_code=cms`；关闭模块时菜单不下发。
- 菜单 SQL 可重复执行（按 `menu_key` 幂等），不依赖自增 ID。
- 角色授权不得超过当前模块可用范围。

---

## 4. 前端嵌入契约

### 4.1 后台路由模块

```text
lx-ui-admin/src/
├─ views/cms/      页面（site / category / article / tag）
└─ api/cms.js      接口封装
```

> **实施偏差（v1.1 第一批）**：原计划放 `src/modules/cms/` 并带独立 `routes.ts`。
> 实际 v1.0 的路由是“后端菜单驱动 + `views` 路径映射”，
> 页面放 `src/views/cms`、接口放 `src/api/cms.js` 与其它模块保持一致；
> 模块开关仍由后端 `module_code='cms'` 控制，不额外引入前端模块注册表。
> 构建期裁剪（`VITE_MODULES`）继续后置。

```ts
// src/modules/registry.ts
export const modules = {
  cms: () => import('./cms'),
  member: () => import('./member'),
}
```

- 构建期用 `VITE_MODULES=cms,member` 决定打包哪些模块（v1.1 暂不做构建裁剪）。
- 运行期用后端 `/api/admin/modules` 返回的启用列表决定注册哪些路由
  （已落地：`store/app.js#loadModules` + `router/dynamic.js` 二次校验）。
- 关闭模块：前端无路由、无菜单、无按钮权限；直接访问 URL 落 404。
- 共享组件：文件选择器、富文本、字典下拉、用户选择器，由基座提供，CMS 不重复造。

### 4.2 门户

- `lx-ui-portal` 是独立 Nuxt3 应用，只调 `/api/portal/**`，不直连数据库。
- CMS 模块提供 portal API；会员模块提供 `/api/portal/member/**`。
- 门户可独立部署、独立扩容，不影响后台。
- SEO：sitemap / robots / canonical / hreflang（i18n 后置）。

---

## 5. 数据边界

### 5.1 表归属

| 模块 | 表 |
|---|---|
| 基座 | `sys_user` / `sys_role` / `sys_menu` / `sys_dept` / `sys_post` / `sys_dict_*` / `sys_config` / `sys_notice` / `sys_log*` / `sys_file*` / `sys_job*` |
| CMS | `cms_site` / `cms_category` / `cms_category_closure` / `cms_content` / `cms_content_ext` / `cms_tag` / `cms_content_tag` / `cms_content_version` |
| 会员 | `mem_user` / `mem_role` / `mem_permission` / `mem_user_role` / `mem_role_permission` / `mem_login_log` / `mem_favorite` / `mem_comment` |
| 门户 | 无表，只读 API + 缓存 |

### 5.2 硬规则

1. **后台用户与会员不共表**：`sys_user` 只放后台用户，`mem_user` 只放会员。
2. **文件只归基座**：CMS / 会员通过 `fileId` 引用，不建自己的文件表。
3. **CMS 不拥有后台用户**：作者 / 审核人只存 `user_id`，展示时调 `AdminUserService`。
4. **会员不拥有后台权限**：`mem_*` 独立 RBAC，Token 用 `StpMemberUtil`。
5. **无跨模块外键**：删除 / 禁用走应用层校验与事件。
6. **租户后置**：租户应用立项时，CMS / 会员表再加 `tenant_id`；v1.1 不加。

---

## 6. 启用与关闭

### 6.1 启用 CMS（v1.1）

1. `lx-boot` 加 `lx-module-cms` 依赖（或构建 Profile）。
2. `application.yml` 设 `lx.module.cms.enabled=true`。
3. 重启：Flyway 自动把 `classpath:db/migration/cms` 加入扫描并执行迁移
   （目录不存在则跳过；基座 `system` / `file` 永远参与）。
4. 迁移脚本里按 `menu_key` 幂等导入 CMS 菜单与权限码，`module_code='cms'`。
5. 后端启动后 `/api/admin/modules` 返回 `cms=true`，`/api/admin/auth/menus`
   自动下发 CMS 菜单；前端按菜单注册路由，不需要改 `dynamic.js`。
6. 需要门户时部署 `lx-ui-portal`，配置 `/api/portal` 地址。

### 6.2 关闭

- 关开关、去掉依赖、重启。
- 结果：CMS 接口 404、Bean 不加载、菜单不返回、前端无路由、定时任务跳过。
- **数据保留**；重新打开后内容、栏目、标签、引用关系完整。

### 6.3 验收矩阵

| 场景 | 期望 |
|---|---|
| 基座 + CMS 关闭 | 启动成功；无 `cms_*` 表；`/api/admin/cms/**` 404；后台无 CMS 菜单 |
| 基座 + CMS 打开 | 迁移成功；菜单 / 权限可用；发文 → 发布 → 门户可见 |
| CMS 关闭再打开 | 数据不丢；引用关系可恢复；无重复菜单 |
| 会员 Token 调后台 | 401 |
| 后台 Token 调会员 | 401 |
| 门户下线内容 | 详情 404；列表不返回 |
| 删除被引用文件 | 拒绝，提示引用数 |

---

## 7. CMS 功能范围（v1.1）

**做：**

- 站点：名称、域名、Logo、SEO 默认值、状态。
- 栏目：树（闭包表）、排序、模板、SEO、隐藏 / 显示。
- 内容：标题、slug、摘要、封面、正文（WangEditor）、作者、来源、标签、发布时间、状态。
- 内容扩展：`cms_content_ext` JSON，存自定义字段，不频繁改表。
- 标签：标签 CRUD、内容打标、标签聚合页。
- 发布：草稿 → 发布 → 下线；定时发布后置。
- 版本：保存时留版本，可回滚（最小实现）。
- 回收站：软删除、恢复、彻底删除（检查引用）。
- 后台页面：站点 / 栏目 / 内容 / 标签 / 素材选择 / 预览。
- 门户 API：站点、栏目树、内容列表、内容详情、标签、搜索（MySQL LIKE 起步）。

**不做（后置）：**

- 工作流审核（v1.4 接 Flowable）。
- 多语言内容翻译（i18n 模块后置）。
- 静态化 / ES / CDN。
- 评论（放 v1.2 会员互动）。
- 复杂内容模型 / 自定义表单。

**权限码（v1.1 实际落地）：**

```text
platform:cms:site:list|add|edit|remove
platform:cms:category:list|add|edit|remove
platform:cms:article:list|add|edit|remove|publish|offline|recycle|restore|purge
platform:cms:tag:list|add|edit|remove
```

> 与草案的差异：动词统一为 v1.0 的 `add / edit / remove`（不是 `create / update / delete`），
> 内容实体命名为 `article`（不是 `content`）。按钮权限已在 `V21__cms_menu.sql` 落库，
> 复用现有角色授权界面即可分配。

**实施状态（2026-11-22）**：

| 范围 | 状态 | 说明 |
|---|---|---|
| 站点 / 栏目 / 文章 / 标签后台 CRUD | ✅ 已完成 | 见 §14 |
| 草稿 → 发布 → 下线 | ✅ 已完成 | 发布 / 下线独立接口与权限码 |
| 栏目闭包与成环校验 | ✅ 已完成 | 变更后按站点全量重建 |
| 标签聚合查询 | ✅ 已完成（后台筛选 + 门户按标签筛选） | 门户聚合页 UI 后置 |
| 门户 API `/api/portal/cms/**` | ✅ 已完成 | 只读、只出已发布内容（§15） |
| 文章回收站 | ✅ 已完成（2026-11-22） | 软删除 + 恢复回草稿 + 彻底删除（§16） |
| 定时发布 | ⏳ 未开始 | 依赖 `publish_time`，任务扫描后置 |
| 内容版本 | ⏳ 未开始 | 表与页面均未建 |
| `cms_content_ext` 自定义字段 | ⏳ 未开始 | 避免过早固定内容模型 |
| 素材选择器 | ✅ 已完成（2026-11-22） | 站点 Logo / 栏目封面 / 文章封面接文件中心；仍兼容 URL 手填 |
| 内容级多语言 | ⏳ 未开始 | 走后续 `i18n_*` 内容翻译阶段 |

---

## 8. 会员功能范围（v1.2）

**做：**

- 注册：用户名 / 手机 / 邮箱（按配置）、验证码、协议勾选。
- 登录：账号密码 + 图形验证码；Token 7 天；`StpMemberUtil`。
- 找回密码：邮箱 / 短信通道（先留接口，开发环境用日志）。
- 个人中心：资料、头像（裁剪）、改密、注销。
- 互动：评论、收藏（先做基础，审核后置）。
- 内容权限：免费 / 登录可见；VIP / 等级 / 付费字段预留。

**硬规则：**

- `mem_*` 与 `sys_*` 物理隔离，不共表、不共 Token、不共权限缓存。
- 会员头像走 `sys_file` + `fileId`，不建 `mem_file`。
- 会员评论只存 `member_id` + `content_id`，跨模块不建外键。

---

## 9. 门户功能范围（v1.3）

- Nuxt3 SSR：首页、栏目页、列表页、详情页、标签页、搜索页。
- SEO：title / description / canonical / Open Graph / sitemap.xml / robots.txt。
- 缓存：Redis + 短 TTL；发布 / 下线主动失效。
- 响应式：PC + H5 同一套 Nuxt 页面；小程序 / App 后置。
- 部署：独立 Node 进程或静态产物 + Nginx；只调 `/api/portal`。
- 不做：静态化集群、CDN 自动刷新、多语言 hreflang（后置）。

---

## 10. 排期与依赖

| 版本 | 周期 | 前置 | 关键交付 |
|---|---|---|---|
| v1.0 基座 | 6 周 | 无 | 登录 / 权限 / 组织 / 字典 / 日志 / 文件 / 监控 / 任务 / 代码生成 |
| v1.1 CMS | 4~5 周 | v1.0 稳定；文件中心可用 | 站点 / 栏目 / 内容 / 标签 / 发布 / 后台页 / portal API |
| v1.2 会员 | 3~4 周 | v1.0；可与 v1.1 并行 | 注册 / 登录 / 找回 / 个人中心 / 评论收藏 |
| v1.3 门户 | 2~3 周 | v1.1 + v1.2 | Nuxt3 列表 / 详情 / SEO / 缓存 |
| v1.4 增强 | 持续 | 按客户 | 工作流 / i18n / 支付 / 搜索 |

> 2~3 人 + AI 估算；CMS 与会员可在基座稳定后并行，门户最后联调。

---

## 11. 验收清单

- [x] 基座不 import 任何 CMS / 会员类。
- [x] CMS 关闭：启动成功、接口 404、菜单 / 路由不出现；Flyway 不扫描 `cms` 目录
      （已建表与数据保留，不做物理删除）。空白库关闭态建表由迁移目录跳过保证。
- [x] CMS 打开：迁移、菜单、权限、发文、发布、**门户可见**全链路通过。
      后台链路见 §14.5，门户读取见 §15。
- [x] CMS 关闭再打开：数据与引用关系完整。
- [ ] 会员 Token 与后台 Token 互不认（会员模块未开工）。
- [x] 门户接口只出白名单字段、只出已发布内容；门户工程本身未开工，
      落地时仍须只调 `/api/portal`（契约见 `docs/29-portal-api.md`）。
- [x] 文件只走 `fileId`，CMS / 会员无自己的文件表（封面 / Logo 存 `fileId` 或 URL）。
- [x] 无跨模块外键；删除走应用层校验。
- [x] 菜单 SQL 幂等，重复执行不产生重复菜单（`INSERT IGNORE` + `menu_key` 唯一键）。
- [ ] 关闭态与打开态都进 CI（当前靠 `mvn test` 的模块单测 + 本地真实接口 / 浏览器验收）。

---

## 12. 反模式

- CMS 直接改 `sys_user` / `sys_menu` / `sys_file`。
- 基座硬编码 CMS 菜单、路由、权限码。
- 门户调后台 `/api/admin` 接口。
- 会员与后台用户共用 `sys_user`。
- CMS 自己建一套文件表 / 用户表。
- 用独立项目逃避模块化：边界没定清就拆进程，复制一套权限、文件、i18n。
- 用模块开关假装产品隔离：能单独卖的 ERP / 商城塞进同一 jar。

---

## 13. 基座前置能力（已落地，2026-11-22）

CMS 开工前先把“关掉模块还会漏菜单 / 漏迁移 / 漏权限”的最后一公里补完。
本轮只动基座，不引入任何 `cms_*` 代码。

### 13.1 落地内容

| 能力 | 实现 | 行为 |
|---|---|---|
| 模块启用判定 | `lx-common-core` 的 `ModuleProvider` + `lx-framework` 的 `ModuleRegistry` | `system` / `file` 永远启用；其它模块必须显式 `enabled=true`；未配置按关闭 |
| 菜单过滤 | `AuthService#getMenus` | 按 `sys_menu.module_code` 过滤；目录子节点全被过滤后，空目录一起隐藏 |
| 权限过滤 | `AuthService#resolvePermissions`、`StpInterfaceImpl` | 关闭模块的权限码不再下发，避免“菜单没了、权限还在” |
| 迁移目录 | `ModuleMigrationLocations` + `FlywayConfigurationCustomizer` | 只扫描已启用且目录存在的模块；`system` / `file` 固定参与 |
| 模块接口 | `GET /api/admin/modules` | 返回基座 + 配置模块的 `code / name / enabled / description` |
| 前端路由 | `store/app.js#loadModules` + `router/dynamic.js` | 启动时拉模块列表，按 `enabled` 二次校验菜单路由；接口失败时不做过滤，以后端菜单为准 |
| 菜单归属 | `V18__menu_module_code.sql` | 定时任务 → `job`，代码生成 → `generator`，不再挂在 `system` 下 |

### 13.2 关闭 / 开启的实测结果

以 `job` 模块为例（`--lx.module.modules.job.enabled=false`）：

| 检查项 | 结果 |
|---|---|
| 启动 | 成功；Flyway 只扫描 system / file / generator |
| 迁移校验 | `Successfully validated 18 migrations`（已执行的 V11 被 `ignore-missing-migrations` 放过） |
| `/api/admin/monitor/job/**` | 404 |
| 超管菜单 | 无“定时任务”；“系统工具”仍保留文件中心 / 代码生成 / 接口文档 |
| 普通角色权限 | 即使角色授权了 job 菜单，`/auth/info` 也不再返回 `platform:monitor:job:*` |
| 前端菜单 / 路由 | 侧边栏无“定时任务”；直接访问 `/tool/job` 落 404 |
| 重新开启 | 菜单与接口恢复，数据与迁移记录不丢 |

### 13.3 两个必须接受的取舍

1. **Flyway 打开 `out-of-order`**
   模块可能在基座升级后才启用，它的迁移版本号会小于当前最大版本。
   不打开的话，`job` 的 V11 在 V18 之后启用会被 Flyway 拒绝。
   代价是迁移不再严格按版本顺序执行，模块迁移必须保持“只动自己的表”。

2. **Flyway 打开 `ignore-missing-migrations`**
   模块关闭后，它已执行的迁移在 classpath 里“消失”，默认校验会失败。
   这是 Flyway Community 可用的旧开关（`ignore-migration-patterns` 是 Teams 能力）。
   代价是“本地误删迁移文件”也不会报错，需要靠代码评审和发布包核对兜底。

### 13.4 明确不做

- **运行期热插拔**：模块开关仍是启动期生效，改完必须重启；
- **前端构建裁剪**：`VITE_MODULES` 后置，v1.1 先保证运行期路由正确；
- **菜单管理页过滤**：菜单管理仍展示全部菜单（含关闭模块），方便管理员预配置；
  侧边栏与权限接口才做过滤；
- **模块依赖解析**：`module.yaml` 的 `requires` / `optional` 仍未读取，
  v1.1 先靠人工保证“CMS 依赖 system + file”。

---

## 14. v1.1 CMS 第一批落地（2026-11-22）

本批先交付“后台内容维护链路”；门户 API 与素材选择器已在后续批次补齐，
版本、回收站、内容多语言仍后置。
数据库目标仍是 MySQL 起步、SQL 避免方言：建表、分页、标签 `EXISTS`、闭包重建都用标准 SQL。

### 14.1 代码落点

| 层 | 文件 |
|---|---|
| 后端模块 | `lx-modules/lx-module-cms`（包 `com.lingxiao.modules.cms`） |
| 模块注册 | `lx-modules/pom.xml`、`lx-dependencies/pom.xml`、`lx-boot/pom.xml` |
| 模块开关 | `lx-boot/src/main/resources/application.yml` 的 `lx.module.modules.cms` |
| 表结构 | `db/migration/cms/V20__cms_schema.sql`（6 张 `cms_*`） |
| 菜单权限 | `db/migration/cms/V21__cms_menu.sql`（菜单 3000+，`module_code='cms'`） |
| 后端接口 | `CmsSiteController` / `CmsCategoryController` / `CmsArticleController` / `CmsTagController` |
| 前端接口 | `lx-ui-admin/src/api/cms.js` |
| 前端页面 | `lx-ui-admin/src/views/cms/{site,category,article,tag}/index.vue` |
| 富文本清洗 | `com.lingxiao.common.web.util.HtmlSanitizer`（从 system 模块上移，公告 + CMS 共用） |
| 校验文案 | `messages.properties` / `messages_en_US.properties` 的 `valid.cms.*` |

### 14.2 关键决策

1. **栏目层级用 `parent_id` 表达，闭包表只做查询加速。**
   每次栏目变更按站点全量重建 `cms_category_closure`；成环在写入前拒绝。
   文章按父栏目筛选时用闭包子查询拿到整棵子树，而不是递归 SQL。
2. **文章状态只能通过发布 / 下线接口改变。**
   新建强制草稿；编辑保存不改状态；发布写 `publish_time`。
3. **正文双侧清洗。**
   写入与读取都过同一份 Jsoup 白名单，历史脏数据与直接改库的数据也能兜住。
4. **标签不跨站点复用。**
   同一句“Java”在中文站和英文站是两条数据，各自有 key 与 URL；
   删除前检查 `cms_article_tag` 引用。
5. **删除保护全部在服务层。**
   站点 / 栏目 / 标签没有跨模块外键，服务层拒绝“删了会让数据变孤儿”的操作。
6. **权限码复用 v1.0 风格。**
   `list / add / edit / remove`，文章额外拆 `publish / offline`。

### 14.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 | `mvn -q -DskipTests package` 通过 |
| Flyway | 自动执行 `V19 - system user social`、`V20 - cms schema`、`V21 - cms menu` |
| 单测 | CMS 22 项；全仓 134 项 / 28 个测试类，JDK 8 与 JDK 11 各跑一遍均通过 |
| 接口验收 | `.local/cms-api-check.mjs` 34/34（CRUD、闭包、成环、跨站点、清洗、状态、删除保护） |
| 浏览器验收 | `.local/uitest/cms-ui.mjs` 13/13（菜单、四个页面、新增、预览、发布、删除、清理） |
| 素材选择器 | `.local/media-picker-check.mjs` 16/16；`.local/uitest/media-picker.mjs` 6/6 |
| 关闭模块 | `/api/admin/cms/**` 404；菜单 / 前端路由消失；直接访问 `/cms/site` 落 404 |
| 重新开启 | 关闭前创建的站点仍在，菜单与接口恢复 |
| 前端校验 | `check-i18n-keys.mjs` 无 missing / dotted-leaf；`npm run build` 通过 |

### 14.4 当前限制

- Logo / 封面已接文件中心素材选择器，选中写 `fileId`；手填 `https://` 外链仍兼容。
  浏览素材需要 `platform:file:list`，上传需要 `platform:file:upload`；没有文件权限的
  CMS 编辑仍可手填外链，不会被完全卡死。
- 没有内容版本；文章回收站已补齐（§16），站点 / 栏目 / 标签仍是物理删除 + 引用保护。
- CMS 菜单默认只授权给超管角色（`role_id=1`），其它角色需在角色管理里分配。

### 14.5 复验命令

```powershell
# 后端 8080（关闭验证码）+ 前端 5173
node .local\cms-api-check.mjs
cd .local\uitest; node cms-ui.mjs

# 关闭态
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false --lx.module.modules.cms.enabled=false
cd .local\uitest; node cms-disabled.mjs
```

---

## 15. v1.1 CMS 门户 API 落地（2026-11-22）

本批给已发布内容补上对外读取入口：`/api/portal/cms/**`，只读、无需登录、
只出白名单字段。接口契约与缓存建议单独写在 [`29-portal-api.md`](29-portal-api.md)，
本节只记录实现落点、决策与验证。

### 15.1 代码落点

| 层 | 文件 |
|---|---|
| 门户 Controller | `CmsPortalController`（`/api/portal/cms/**`，`@ConditionalOnProperty(cms.enabled)`） |
| 门户 Service | `CmsPortalService`（可见性、白名单映射、阅读量） |
| 门户 VO | `dto/portal/PortalSiteVO`、`PortalCategoryVO`、`PortalTagVO`、`PortalArticleSummaryVO`、`PortalArticleDetailVO` |
| 标签批量查询 | `CmsArticleTagMapper#selectRowsByArticleIds` + `dto/ArticleTagRow`（避免逐篇 N+1） |
| 阅读量自增 | `CmsArticleMapper#increaseViewCount`（单条 UPDATE） |
| 单测 | `CmsPortalServiceTest`（8 项） |

### 15.2 关键决策

1. **公开接口不依赖后台鉴权。** `/api/portal/**` 不在 `SaTokenConfigure` 的拦截路径里，
   所以 Service 自己把住三层可见性：站点启用、文章已发布、栏目（含祖先）启用。
2. **祖先栏目停用 = 整棵子树不可见。**
   列表 SQL 用闭包表 `NOT EXISTS` 排除“任一祖先停用”的文章；详情与筛选条件走
   `isCategoryVisible`（查闭包祖先再查状态）。只判文章直接栏目会在“停用父栏目”时漏内容。
3. **列表不查正文。** `select` 排除 `content`，详情才返回；避免分页把 longtext 全拖出来。
4. **字段白名单。** 统一转 `Portal*VO`，后台内部字段一律不下发；门户不需要也不应该
   依赖实体的字段顺序。
5. **分页与关键字有上限。** `pageSize` 最大 50，`keyword` 超过 50 字符截断，
   防止公开接口被一页拉空或被超长 LIKE 拖慢。
6. **隐藏栏目语义。** 停用栏目整棵子树剔除；隐藏栏目本身不出现在导航树，
   可见子栏目上提为根或挂到最近可见父级，不需要额外字段。
7. **阅读量用单条 UPDATE 自增。** 并发访问不会互相覆盖；返回值是累加后的值。
   防刷与异步统计留到门户上线前。
8. **跨站点隔离。** `siteKey` 解析站点 ID 后，栏目 / 标签 / 文章都再校验 `siteId`，
   slug 只在站点内唯一。

### 15.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 | `mvn -q -DskipTests package` 通过 |
| 单测 | CMS 22 项（新增门户 8 项）；全仓 134 项 / 28 个测试类，JDK 8 与 JDK 11 各跑一遍全部通过 |
| 接口验收 | `.local/portal-api-check.mjs` 38/38（站点、导航、标签、已发布过滤、子树、标签筛选、搜索、详情、阅读量、slug、停用 / 跨站点 404、分页上限、清理） |
| 关闭模块 | `.local/portal-disabled.mjs` 5/5：模块列表 `cms.enabled=false`；门户与后台 CMS 接口均 404 |
| 重新开启 | 门户接口恢复，关闭态不产生额外迁移 |
| 前端 | 无需改动（门户工程未开工） |

### 15.4 当前限制

- 无缓存、无限流：每次请求直查数据库，详情阅读量可被脚本刷高。
- 手工改库把栏目改成停用后，闭包表不会自动同步；需要走一次后台栏目编辑触发重建，
  否则列表的“祖先停用”判断可能失效。
- 内容版本、定时发布、内容级多语言仍未实现，门户读不到；回收站已实现（§16），
  回收中的文章不会被门户返回。
- 素材选择器已在后台完成（§14）；门户只返回 `fileId` / URL 原值，消费端负责解析。
- 门户工程（Nuxt3）、sitemap / RSS / Open Graph、ES 搜索、CDN 属 v1.3。

### 15.5 复验命令

```powershell
# 后端 8080（CMS 开启、验证码关闭）
node .local\portal-api-check.mjs

# 关闭态
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false --lx.module.modules.cms.enabled=false
node .local\portal-disabled.mjs
```

---

## 16. v1.1 CMS 文章回收站（2026-11-22）

内容生命周期的第一刀：把文章删除从物理删除改为逻辑删除，补上后台可操作的回收站。
本批只做**文章**——站点 / 栏目 / 标签已有引用保护，误删概率低；文章是高频内容，
且删除后需要能找回标签、发布时间等上下文。

### 16.1 代码落点

| 层 | 文件 |
|---|---|
| 迁移 | `db/migration/cms/V22__cms_article_recycle.sql`（3 列 + 索引 + 菜单 3050~3052） |
| 实体 | `CmsArticle` 增加 `deleteBy` / `deleteByName` / `deleteTime`（`@TableField(select=false)`） |
| Mapper | `CmsArticleMapper`：软删除 / 恢复 / 回收站物理删除 / 读回收站记录 / 回收站分页 / 含回收站的 slug·站点·栏目计数 |
| Service | `CmsArticleService`：`delete` 改软删除，新增 `recyclePage` / `restore` / `purge` |
| Controller | `CmsArticleController`：`GET /recycle/list`、`PUT /{id}/restore`、`DELETE /{id}/purge` |
| 前端接口 | `lx-ui-admin/src/api/cms.js`：`listRecycleArticles` / `restoreArticle` / `purgeArticle` |
| 前端页面 | `lx-ui-admin/src/views/cms/recycle/index.vue`；文章页工具栏加入口 |
| 单测 | `CmsArticleServiceTest`（12 项）、`CmsSiteServiceTest` / `CmsCategoryServiceTest` 改断言 |

### 16.2 关键决策

1. **软删除保留标签关联。**
   进回收站只改 `del_flag` 与删除审计，`cms_article_tag` 不动；恢复后标签立即可用。
   只有彻底删除才先清关联、再物理删正文。
2. **删除人存快照，不跨模块 JOIN。**
   `delete_by` + `delete_by_name` + `delete_time` 一次写入；昵称取 Session，
   取不到回退用户名，再回退 `system`。CMS 不依赖 system 模块的表。
3. **恢复一律回草稿，保留 `publish_time`。**
   不该“恢复即重新对外发布”；`publish_time` 是首次发布时间，重新发布沿用。
4. **slug 唯一性与删除保护都包含回收站。**
   回收中的 slug 不允许新文章占用，否则恢复时撞车；站点 / 栏目下只要还有回收站文章
   也不允许删除，避免恢复目标消失。
5. **恢复前二次校验。**
   站点必须存在、栏目必须仍属于该站点；slug 不能与其它文章冲突。栏目被删时提示
   直接彻底删除，不留“恢复一半”的状态。
6. **回收站列表不返回正文。**
   只查标题、slug、站点 / 栏目名、删除前状态与删除审计；站点 / 栏目用 LEFT JOIN，
   栏目被手工删掉也不至于整行消失。
7. **权限拆三档。**
   `platform:cms:article:recycle` 进入页面，`restore` 恢复，`purge` 物理删除；
   可以只给主编恢复权限，不开放彻底删除。

### 16.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -q -DskipTests package` 通过 |
| Flyway | `V22 - cms article recycle` 成功；菜单 3050 / 3051 / 3052 与超管授权已落库 |
| 单测 | CMS 29 项（文章 12 项）；全仓 141 项 / 28 类，JDK 8 与 JDK 11 各跑一遍全部通过 |
| 接口验收 | `.local/cms-recycle-check.mjs` 33/33：软删除、门户隐藏、列表字段、关键字、删除保护含回收站、slug 占用、恢复回草稿并保留标签 / 发布时间、重新发布、彻底删除、slug 复用、清理 |
| 浏览器验收 | `.local/uitest/cms-recycle.mjs` 12/12：菜单、列表字段、搜索 / 空态、恢复、文章页删除进回收站、跳转、彻底删除、后台与门户双端消失 |
| 前端 | `check-i18n-keys.mjs` zh=1039、en=1039，无 missing / dotted-leaf；`npm run build` 通过 |
| 关闭模块 | 不新增外部依赖；关闭 CMS 时回收站接口随 `/api/admin/cms/**` 一起 404 |

### 16.4 当前限制

- 回收站只覆盖文章；站点 / 栏目 / 标签仍是物理删除 + 引用保护。
- 没有保留期与自动清理：进回收站的内容会一直占库，需要运营手动彻底删除。
- 回收站查询暂未接数据范围（CMS 列表当前都未接），后续权限细化时一起做。
- 恢复不判断栏目祖先是否停用——恢复结果是草稿，重新发布会走既有的栏目校验链路。

### 16.5 复验命令

```powershell
# 后端 8080（关闭验证码）+ 前端 5173
node .local\cms-recycle-check.mjs
cd .local\uitest; node cms-recycle.mjs
```
