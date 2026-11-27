# 17 · v1.0 整体规划与需求设计（可开工版）

> 定稿：2026-11-11 夜 · 供 2026-11-12 开工
> 与 `01`~`15` 的关系：那是**全量能力蓝图**，本文是**首个可交付版本的收缩计划**；冲突处以本文为准。
> 前置决策见 `16-capability-tiers.md`（A 内核 / B 模块 / C 产品分层）。
> **决策变更（23:45）**：基础后台不引入 `tenant_id` 隔离列；租户走**独立入口 / 独立验证 / 独立 RBAC**。见 3.4.1。`16` 号中「所有业务表带 `tenant_id`」的口径**不再适用于平台后台域**，仅适用于租户应用内部。
> **范围变更（v1.0 定稿）**：**v1.0 只做后台基座，不做 CMS、不做门户、不做会员**；后台做厚（系统管理 / 监控 / 工具 / 文件中心）。CMS、会员、门户作为后续模块嵌入，见 [20-cms-embed-plan.md](20-cms-embed-plan.md)。
>
> **状态（2026-11-27）**：本文是 v1.0 的**原始规划稿**，已按此执行完毕（施工记录见 `21-build-log.md`）。
> §6 排期、§7 门禁勾选保持当时原样，不再回填；实际版本划分（v1.1 CMS → v1.2 会员 →
> v1.3 门户 → v1.4 国际化 / 审批 → v2.0 SaaS）以 README 路线图与 `13-roadmap.md` §6 为准，
> 与 §10 的初版表略有出入。当前待办见 `13-roadmap.md` §12.28。

---

## 0. 为什么必须重做

| 原规划问题 | 证据 |
|---|---|
| 估算与排期差 2~3 倍 | `13:145` 53~66 人月 vs `01:3` 2~3 人 6~8 个月 |
| 范围无边界 | `02` 421 个功能点、128 条 P0，无验收标准 / 工时 / 依赖 |
| 需求侧整块缺失 | 无目标客户、无用户故事、无原型、无契约实例（openapi / 错误码 / 权限码 / schema.yaml） |
| 文档已漂移 | README 与 13 阶段周期不一致、02 与 03 无同步机制 |

**重做目标**：把「什么都要」改成「4~6 周能上线、能验收、能给别人看」的一个版本。

---

## 1. 产品定位

**v1.0 = 一套可私有化部署的「后台基座」。**

- 卖给谁：需要快速拥有后台管理底座的中小客户 / 私有化项目；后续要接 CMS、会员、门户的客户。
- 解决什么：从零搭登录、权限、组织、字典、参数、日志、文件、监控、代码生成太慢。
- 不解决：CMS、会员、门户、工作流、多租户、交易。它们作为后续模块嵌入，不塞进 v1.0。

**成功标准**：一台 2C/4G 服务器 + MySQL 8 + Redis，`java -jar` + Nginx 起得来；管理员 10 分钟内完成「建角色 → 配菜单 → 建用户 → 登录 → 看到授权菜单」；越权 403；**关闭任意可选模块后系统仍能启动**；后台菜单覆盖系统管理 / 系统监控 / 系统工具 / 文件中心 / 个人中心。

---

## 2. 范围（这一版只做这些）

| 做 | 明确不做（写进 README） |
|---|---|
| 认证：登录 / 退出 / 图形验证码 / 密码策略 / 失败锁定 / Token / 在线用户 | CMS、会员、门户（后续模块嵌入，见 `20`） |
| 系统管理：用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告 | 工作流、SaaS、ERP、商城、公众号、支付 |
| 系统监控：在线用户 / 登录日志 / 操作日志 / 服务监控 / 缓存监控 | 多租户（**租户应用独立立项，v1.0 完全不建**） |
| 系统工具：文件中心 / 文件库 / 图片基础处理 / 定时任务 / 代码生成 / 接口文档 | i18n 内容翻译（框架留 `messageKey`，只 `zh-CN`） |
| 个人中心：资料 / 改密 / 头像 | 多数据库（只 MySQL 8）、uni-app、静态化、ES、CDN |
| 工程：Maven 多模块 / BOM / Flyway / `schema.yaml` / CI 三门禁 | Docker / K8s / 微服务 / 开放平台 |

> **“后台丰富”的边界：** 只做“通用后台能力”，不做业务内容。判断标准：这个菜单换到任何后台项目都能用 → 进 v1.0；只对内容站有意义 → 进 CMS 模块。
>
> 收缩原则：v1.0 只验证「基座 + 可插拔模块机制」。CMS / 会员 / 门户不是砍掉，是**下一版本以模块嵌入**。

---

## 3. 需求设计

### 3.1 角色

| 角色 | 谁 | 关心 |
|---|---|---|
| 平台超级管理员 | 部署方 | 全部菜单、系统参数、日志、监控 |
| 系统管理员 | 客户 IT | 用户、角色、菜单、部门、岗位、字典 |
| 文件 / 运营管理员 | 客户运营 | 文件库、图片处理、公告 |
| 普通后台用户 | 客户员工 | 被授权的菜单与数据范围 |

### 3.2 核心用例（每条都有可验收标准）

| # | 用例 | 主流程 | 验收标准 |
|---|---|---|---|
| U1 | 登录 | 账号密码 + 图形验证码 | 密码错误锁定生效、Token 12h、登录日志落库、Token 命名空间隔离 |
| U2 | 建角色配菜单 | 勾选菜单 + 按钮权限 | 该角色登录后只见授权菜单；直接调未授权接口 403 |
| U3 | 建用户 | 指定角色 / 部门 / 岗位 | 新用户可登录，权限与角色一致 |
| U4 | 部门与数据范围 | 建部门树 | 「本部门」「本部门及以下」生效；**走闭包表，不用 `find_in_set`** |
| U5 | 字典与参数 | 维护字典 / 系统参数 | 前端下拉与后端取值一致；修改后缓存主动失效 |
| U6 | 岗位与公告 | 建岗位、发公告 | 用户关联岗位；公告可定向部门 / 全员，已读未读可查 |
| U7 | 文件上传与图片处理 | 上传图片 → 缩略图 / 裁剪 / 限宽 / 去 EXIF | 原图不变；生成 thumb / medium；返回 `fileId`；超限拒绝 |
| U8 | 文件库 | 搜索 / 筛选 / 预览 / 改名 | 原名 / 现名 / 备注可搜；改名后原名不变、历史可查；被引用不可删 |
| U9 | 日志与在线用户 | 查登录 / 操作日志、强退在线用户 | 日志可按人 / 时间 / 类型筛选；强退后 Token 立即失效 |
| U10 | 服务与缓存监控 | 看 CPU / 内存 / JVM / 磁盘 / Redis | 指标可刷新；不暴露敏感配置；只读 |
| U11 | 定时任务 | 建任务、启停、执行一次、看日志 | 集群不重复执行；失败有日志；关闭 `job` 模块后不加载 |
| U12 | 代码生成与接口文档 | 导入表 → 配字段 → 预览 → 生成 | 生成物可编译；菜单权限 SQL 可执行；Knife4j 可调试 |
| U13 | 越权与域隔离 | 伪造其他域 Token / 权限码前缀 | 403；关闭可选模块后仍能启动、接口 404、Bean 不加载 |

### 3.3 核心流程

```text
权限闭环：登录 → 建角色 → 配菜单 → 建用户 → 登录 → 看到菜单 → 越权 403
文件闭环：上传 → 校验 → 缩略图 / 裁剪 → 文件库 → 改名留痕 → 被业务引用
模块闭环：关闭 lx-demo → 启动成功 → 接口 404 / Bean 不加载 / 迁移跳过 → 再打开数据不丢
```

> v1.0 不出现“发文 → 前台可见”链路；这条链路属于 CMS 模块，见 `20`。

### 3.4 第一批数据实体（平台后台域，不带隔离列）

```text
sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu,
sys_dept, sys_dept_closure, sys_post, sys_user_post,
sys_dict_type, sys_dict_data, sys_config, sys_notice,
sys_login_log, sys_oper_log, sys_file, sys_file_name_log,
sys_job, sys_job_log
```

约束（第一天写进 `schema.yaml`，由生成器产出 DDL）：

- **v1.0 全部为平台域表，不带 `tenant_id`**；每张表标注 `scope: platform | tenant | shared`，生成器按 scope 决定是否加隔离列，CI 按 scope 检查。
- 唯一键按域定：平台域 `uk_user_username(username)`、`uk_role_key(role_key)`、`uk_menu_key(menu_key)`；租户域将来为 `(tenant_id, username)`。
- 审计四字段 + `del_flag` + `version`；雪花 ID（**全局唯一**，便于将来身份映射）；`datetime(3)`；无外键。
- 状态统一 `char(1)` + 字典，禁止 `char(1)` 与 `varchar(20)` 混用（`05:17` vs `05:727`）。
- **不建 `sys_tenant` / 套餐 / 租户用户表；不建 `TenantContext`；不接租户插件；缓存 Key 不带租户前缀。**
- **不建任何 `cms_*` / `mem_*` 表**；它们属于后续模块，见 `20`。

### 3.4.1 三套安全域的边界（必须守住）

| 域 | 入口 | 主体表 | Token / RBAC | 隔离列 |
|---|---|---|---|---|
| 平台后台 | `/api/admin/**`（v1.0）→ 将来 `/api/platform/**` | `sys_*` | `StpPlatformUtil` + 独立 role/menu | **无** |
| 租户应用 | 独立域名或 `/api/tenant/**`，**网关层分开** | `tenant_*` | `StpTenantUtil` + 独立 role/menu | **内部仍需 `tenant_id`**（若多租户共用一套应用/库） |
| 会员前台 | `/api/app/**` | `mem_*` | `StpMemberUtil` + 会员权限 | 按业务定 |

六条约束：

1. **两个 `tenant_id` 不是一回事**：平台侧 `sys_tenant.tenant_id` 是「被管理对象的主键」，将来必须有；`sys_user.tenant_id` 是「隔离列」，本项目不做。别因为「不混租户 ID」把租户档案表也省掉。
2. **三套 Token 互不认**：`StpLogic` 三套、Redis 前缀三套、权限缓存三套；平台 Token 拿到租户应用必须直接拒绝，反之亦然。入口隔离做在 Nginx / 网关，不能只靠 Controller 注解。
3. **租户应用内部仍需隔离**：只要多个租户共用一套租户应用 / 库，其用户、内容、文件表就要带 `tenant_id`。只有「每租户独立实例 / 独立库」才不需要 —— 那是运维形态决策，见 `16` 3.3，**租户应用立项时再定**。
4. **平台侧上下文只在代操作时出现**：超管代登录 / 跨租户查询走 `@CrossTenant` + 强制审计（`10:206`），不做全局 `TenantContext` 常驻。基础后台的缓存 Key、文件路径、异步传播因此**不必带租户前缀**，地基税大幅降低。
5. **共享内核只共享无状态能力**：认证框架、错误码、日志切面、文件抽象、Outbox、schema 生成器放 `lx-common` / `lx-framework`；**权限模型三套各自独立**，不要抽成同一套 role/menu 表。
6. **预留身份映射**：三套主体主键都用全局唯一雪花 ID，将来做 SSO / 代登录 / 会员升级时加一张 `identity_mapping` 表即可，现在不建。

**代价（已确认接受）**：三套 RBAC、三套登录 / 密码策略 / 找回 / 日志 / Token；通用后台能力（用户、角色、菜单、操作日志）要靠代码生成或模板复用，否则手写三遍。

> 历史说明：早期版本曾建议「所有表统一带 `tenant_id`、共用 `sys_user` 用值区分」，该建议已被本决策取代，不再执行。

### 3.4.2 文件与图片（v1.0 最小集）

**三条硬规则：**

1. **原图不可变**：裁剪 / 水印 / 缩放只生成衍生文件，`derived_from` 指向原图。
2. **存储名与展示名分离**：路径用 UUID，不用原文件名；改名只改 `display_name`，不动 `path` / `file_name` / `original_name`。
3. **改名留痕**：每次改名写 `sys_file_name_log`，只追加不修改；详情页展示「原名 / 当前名 / 曾用名」。

`sys_file` 在 v1.0 至少要有：

| 字段 | 说明 |
|---|---|
| `file_id` | 雪花 ID，引用主键 |
| `original_name` | 上传时的原始文件名，**不可改** |
| `display_name` | 当前展示名，**可改**；为空时回退 `original_name` |
| `file_name` | 存储文件名（UUID），**不可改** |
| `path` / `url` | 存储路径与访问地址 |
| `thumb_path` | 缩略图路径（列表用） |
| `ext` / `mime` / `size` / `hash` | 类型、大小、SHA-256（秒传 / 去重） |
| `width` / `height` | 图片尺寸 |
| `group_id` / `biz_type` | 分组、业务场景（notice / avatar / cms_content 后续） |
| `ref_count` / `derived_from` | 引用数、衍生来源 |
| `remark` | 人可读描述，解决「不知道这张图是什么」 |
| `status` / `uploader_id` / 审计字段 | 状态、上传人、时间 |

`sys_file_name_log`：

```text
id, file_id, old_name, new_name, operator_id, operator_name, create_time
索引：(file_id, create_time)
```

**上传处理（v1.0 同步，限制 10MB / 50MP）：**

1. 扩展名 + MIME + 魔数 + 实际可解码，四者都过。
2. 按 EXIF 方向自动旋转，**剥离 EXIF / GPS**（隐私）。
3. 生成 `thumb`（320px）与 `medium`（1280px）；原图超过 2560px 时另存限宽衍生图，原图保留。
4. 可选水印：默认关闭，只加在衍生图上，原图永不加水印。
5. 返回 `fileId` / `url` / `thumbUrl` / `width` / `height`。

**裁剪：** 后台用 `vue-cropper` 传 `x/y/w/h/rotate`，服务端校验边界后生成新衍生文件，返回新 `fileId`；原图不动。编辑器默认插入新图，不覆盖旧图。

**文件库（v1.0 最小可用）：**

- 默认**网格缩略图**，不是纯文件名列表。
- 搜索：`display_name` / `original_name` / `remark`。
- 筛选：类型、大小、上传人、时间、分组、引用状态（已引用 / 未引用）。
- 详情：预览、尺寸、大小、哈希、上传人、引用它的业务对象（公告 / 后续 CMS 内容）、改名历史。
- 上传时预填 `remark`：调用方带 `bizTitle`（如公告标题），默认「《标题》配图」，减少“文件太多不知道是什么”。

### 3.4.3 富文本组件（v1.0 基座内）

**选 WangEditor 5**（`@wangeditor/editor` + `@wangeditor/editor-for-vue`）：

- MIT、Vue3 原生支持、中文文档、体积小、图片上传钩子够用。
- v1.0 先用于**公告 / 帮助文档**；CMS 模块直接复用同一组件，不另造编辑器。
- 用 `EditorProvider` 适配器隔离，后续可加 Tiptap；TinyMCE / CKEditor 不作为首发（体积、许可、定制成本）。
- 图片：自定义 `uploadImage` → `/api/admin/file/upload` → 插入
  `<img src="/api/file/{fileId}?size=medium" data-file-id="{fileId}" alt="">`。
  **URL 里带 `fileId`**，即使编辑器丢掉 `data-*` 属性，也能从 `src` 反解。公开内容图走 `/api/file/**`，私有附件走鉴权下载。
- 粘贴 / 拖拽自动上传；**拒绝 base64 图片**；远程图片抓取 v1.0 默认关闭（SSRF 风险），需要时走服务端代理 + 域名白名单。
- 服务端用 Jsoup / OWASP Sanitizer 做白名单消毒，保留 `data-file-id`、`alt`、`width`、`height`，禁 `script` / `iframe` / `on*`。
- 正文存 HTML + 纯文本摘要；后续门户渲染前再消毒一次。

### 3.5 契约文件（第一天就要有文件，不是文档描述）

```text
db/schema.yaml          唯一事实源；含 scope 标记，DDL 由它生成
api/openapi.yaml        v1.0 只实装 /api/admin；/api/portal、/api/app 先占位
api/error-codes.yaml    错误码 + messageKey（不存明文）
api/permission-codes.yaml  按域分命名空间（platform: / tenant: / member:）
module.yaml             可选模块描述：code / requires / apiPrefix / migrations / menus / permissions
i18n/zh-CN.yaml
```

`02` / `03` / CSV / HTML 后续由这些文件生成，不再手维护多份真相源。

### 3.6 非功能

单节点 2C/4G；MySQL 8 + Redis；接口 P95 读 < 200ms、写 < 500ms；每日全量备份；日志脱敏；HTTPS 由 Nginx 终止。

---

## 4. 技术基线

| 项 | 建议 | 理由 |
|---|---|---|
| 主线 | **Spring Boot 2.7.18 + `release=8`**（按 `11` 原规划） | 明天就能开工，不动 `11`/`14` 依赖矩阵 |
| 备选 | JDK 17 + Boot 3 + Flowable 7 | 更现代，但要重写 `11` 矩阵；v1.0 不上工作流，**不阻塞，别在第一天换栈** |
| 认证 | Sa-Token 三套 `StpLogic`（v1.0 只实装 `StpPlatformUtil`） | 三套安全域互不认，见 3.4.1 |
| ORM | MyBatis-Plus；**v1.0 不接租户插件** | 平台后台无隔离列；租户应用立项时再引入 |
| 数据库 | 只 MySQL 8 | 多库适配后置；Flyway 先定死「按模块目录」一维 |
| 前端 | Vue3 + Vite + Element Plus（仅后台管理） | 门户 Nuxt3、H5 属后续模块，v1.0 不建 |
| 富文本 | **WangEditor 5**，`EditorProvider` 适配 | MIT、Vue3、中文文档；先服务公告，CMS 复用 |
| 图片处理 | **Thumbnailator**（默认），`ImageIO` 读尺寸 / EXIF | 纯 Java、JDK 8 兼容；高并发再切 libvips |
| 图片裁剪（前端） | **vue-cropper** | 后台裁剪框，服务端最终处理 |
| CI | 编译 / 关闭态启动 / `scope` 与命名空间静态检查 | 三门禁，缺一不合并 |

---

## 5. 工程骨架（v1.0 只建这些）

```text
lingxiao/
├─ pom.xml
├─ lx-dependencies/            BOM，统一版本
├─ lx-common/                  core / redis / mybatis / web / security / log
├─ lx-framework/               自动装配、模块注册表、全局异常
├─ lx-modules/                 lx-system / lx-file / lx-demo（可选模块契约验证，生产不打包）
├─ lx-api/                     lx-admin-api
├─ lx-boot/                    启动、Profile、打包
├─ lx-ui-admin/                Vue3 + Element Plus
├─ db/                         schema.yaml / generator / migration/mysql
├─ deploy/                     nginx / start.sh
├─ api/                        openapi.yaml / error-codes.yaml / permission-codes.yaml / module.yaml
└─ docs/
```

- **不建 `lx-cms`、`lx-member`、`lx-ui-portal`**；它们属于后续模块，见 `20`。
- `lx-demo` 只用于证明“可选模块关闭后基座仍能启动”，不含业务；CMS 上线后可删除。
- 依赖方向：`modules → common/framework`；模块间只走 `api` 包或领域事件；`lx-boot` 只装配不写业务。

---

## 6. 里程碑（6 周，后台做厚）

| 周 | 交付 | 门禁 |
|---|---|---|
| W1（含 Day-1） | 工程骨架 + 登录 / 权限闭环 + 三域隔离地基 + CI 三门禁 | 能登录、越权 403、关闭 `lx-demo` 可启动 |
| W2 | 用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告 | 权限闭环、数据范围、字典缓存失效 |
| W3 | 文件中心 / 文件库 / 图片基础处理 | 上传 → 缩略图 / 裁剪 / 去 EXIF → 改名留痕 |
| W4 | 登录日志 / 操作日志 / 在线用户 / 服务监控 / 缓存监控 / 定时任务 | 强退生效、任务集群不重复、监控只读 |
| W5 | 代码生成 / 接口文档 / 个人中心 / 导入导出 / 前端细节 | 生成物可编译、Knife4j 可调试、菜单全覆盖 |
| W6 | 越权专项 / 性能冒烟 / 安全加固 / 部署脚本 / 手册 / buffer | v1.0 发布 |

> 6 周 / 2~3 人 + AI。CMS、会员、门户、工作流、SaaS、i18n 内容、多库适配全部不进这个排期。

---

## 7. 阶段门禁（可自动化）

- [ ] `mvn verify` 通过
- [ ] 关闭 `lx-demo` 后仍能启动，接口 404、Bean 不加载、迁移跳过
- [ ] CI 扫 `schema.yaml`：`scope` 标记正确，`scope=platform` 的表不含隔离列，权限码命名空间无混用
- [ ] 跨域 Token 不互认：伪造别的域 Token / 权限码前缀直接拒绝
- [ ] A 角色直接调 B 角色接口 403；数据范围「本部门 / 本部门及以下」生效
- [ ] 后台菜单覆盖：系统管理 / 系统监控 / 系统工具 / 文件中心 / 个人中心
- [ ] 上传 10MB 图片：生成缩略图 / 中图、剥离 EXIF、返回 `fileId`；改名后 `original_name` 不变且历史可查
- [ ] 代码生成：导入表 → 生成 → 编译通过 → 菜单权限 SQL 可执行
- [ ] 仓库无 `cms_*` / `mem_*` 表、无门户路由；`java -jar` + Nginx 可启动

---

## 8. 风险

| 风险 | 对策 |
|---|---|
| “后台丰富”变成无限加菜单 | 第 2 节冻结菜单清单；新增能力走下一版本评审 |
| 范围反弹（又想加 CMS） | CMS 已排到 v1.1，见 `20`；v1.0 只做基座 |
| 开工第一天换技术栈 | 按第 4 节主线起步，Boot 3 另开分支评估 |
| 三套域串权 | 网关层分开入口 + 三套 Token 命名空间 + 跨域 Token 用例 + CI 检查 |
| 图片处理打满 CPU | 10MB / 50MP 上限、同步限流、后续切异步 / libvips |
| 代码生成模板不可编译 | 生成物必须过 `mvn verify`；模板纳入 CI |
| 可选模块契约漂移 | `module.yaml` + 关闭态 CI + 模块准入 4 条 |
| AI 生成代码漏校验 | 权限 / SQL / 上传 / 密钥相关代码逐行人工评审 |

---

## 9. 明天做什么

见 `18-day1-checklist.md`。

---

## 10. 后续版本（模块嵌入，不改基座）

| 版本 | 内容 | 交付形态 |
|---|---|---|
| v1.1 | CMS：站点 / 栏目 / 文章 / 标签 / 编辑器 / 发布 / 下线 + 后台管理页 | `lx-module-cms` + `lx-ui-cms` 模块嵌入 |
| v1.2 | 会员：注册 / 登录 / 找回 / 个人中心 / 评论收藏 | `lx-module-member` + `/api/app` |
| v1.3 | 门户：Nuxt3 SSR 列表 / 详情 / 栏目 / SEO | 独立 `lx-ui-portal`，只调 `/api/portal` |
| v1.4 | 工作流 / i18n 内容翻译 / 支付 | 可选模块 |
| v2.0 | SaaS 多租户 / 运营控制台 | 模块 + 独立部署单元 |

**嵌入契约、数据边界、启用步骤与验收清单见 [20-cms-embed-plan.md](20-cms-embed-plan.md)。**
