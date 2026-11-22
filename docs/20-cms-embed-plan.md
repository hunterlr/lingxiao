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
lx-ui-admin/src/modules/cms/
├─ routes.ts       路由 + 菜单元信息
├─ api.ts          接口封装
├─ views/          页面
├─ components/     业务组件
└─ index.ts        模块导出
```

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

**权限码示例：**

```text
platform:cms:site:list|create|update|delete
platform:cms:category:list|create|update|delete
platform:cms:content:list|create|update|delete|publish|offline
platform:cms:tag:list|create|update|delete
```

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

- [ ] 基座不 import 任何 CMS / 会员类。
- [ ] CMS 关闭：启动成功、无 `cms_*` 表、接口 404、菜单 / 路由不出现。
- [ ] CMS 打开：迁移、菜单、权限、发文、发布、门户可见全链路通过。
- [ ] CMS 关闭再打开：数据与引用关系完整。
- [ ] 会员 Token 与后台 Token 互不认。
- [ ] 门户只调 `/api/portal`，不直连数据库。
- [ ] 文件只走 `fileId`，CMS / 会员无自己的文件表。
- [ ] 无跨模块外键；删除走应用层校验。
- [ ] 菜单 SQL 幂等，重复执行不产生重复菜单。
- [ ] 关闭态与打开态都进 CI。

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
