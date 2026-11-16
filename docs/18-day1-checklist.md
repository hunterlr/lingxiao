# 18 · Day-1 开工清单（2026-11-12）

> 下班前必须达成：**能启动、能登录、越权 403、关掉 `lx-demo` 可选模块仍能启动、跨域 Token 被拒**。
> 今天之前工作区是纯文档仓库、零代码（无 `pom.xml` / `src`）。
> **已确认决策（23:45）**：基础后台不引入 `tenant_id`；租户独立入口 / 独立验证 / 独立 RBAC。Day-1 因此比原计划少三件事（租户上下文、租户插件、租户前缀缓存），优先把登录闭环做扎实。

---

## 0. 从 0 写还是拉框架：借代码，不借架构

**结论：不要从 0 手写工具层，也不要 fork 一份现成后台直接改。走第三条路。**

| 路线 | 判断 |
|---|---|
| 纯从 0 写 | ❌ 登录 / 权限 / 字典 / 日志 / 全局异常 / 分页这些成熟轮子，2~3 周才能跑通，第一天全耗在脚手架上 |
| fork 一份 RuoYi 类后台改 | ❌ Vue2 + Element UI 单端、单权限域、MySQL 专属 SQL、单体无边界；三套独立安全域和多模块要推倒重来，反而被结构锁死 |
| **自己建骨架 + 搬工具层代码** | ✅ 工程结构按 `17` 第 5 节自己定义；与架构无关的工具类逐个搬进来改写；前端另起 Vue3 |

| 借（搬代码 / 借思路） | 不借（必须自己写对） |
|---|---|
| 统一响应、全局异常、XSS 过滤、幂等 Token、分页封装 | 单域权限模型 → 改成**三套独立域**（platform / tenant / member） |
| 字典 / 参数 / 日志切面 / 在线用户 / 登录锁定的实现思路 | Vue2 + Element UI 前端 → 另起 Vue3 + Vite + Element Plus |
| 数据范围 5 档的思路（但改用闭包表） | `find_in_set` / `ON DUPLICATE KEY` 等 MySQL 专属 SQL |
| 前端动态路由与按钮权限的思路 | 单体包结构、service 互相直接注入、把三套 role/menu 抽成共用表 |
| 代码生成器模板（v1.0 要，搬模板思路，生成器自己写） | 无状态能力与权限模型混在一起 |

**落地方式**：把参考源码放在仓库外的临时目录，按文件逐个拷工具类 / 切面 / 模板，拷完即删；仓库里不留第三方包名、不留来源不明的片段。本地若已有源码，直接指路径；没有就明早拉一份**只读参考**，**不要把它作为 git 基线**。

---

## 1. 上午：地基

- [ ] 建仓库骨架：根 `pom.xml`、`lx-dependencies`(BOM)、`lx-common` 六个子模块、`lx-framework`、`lx-boot`、`lx-modules/{system,file,lx-demo}`、`lx-api/admin`；一次只建这些，别铺 20 个。**v1.0 不建 cms / member / portal。**
- [ ] 写 `db/schema.yaml`：按 `17` 3.4 定义第一批 19 张表，**全部 `scope: platform`、不带 `tenant_id`**；唯一键 `uk_user_username(username)`、`uk_role_key(role_key)`、`uk_menu_key(menu_key)`；审计四字段 + `del_flag` + `version`；主键用全局唯一雪花 ID（为将来身份映射留路）；文件表含 `original_name` / `display_name` / `remark`，另建 `sys_file_name_log`。
- [ ] 跑生成器产出 MySQL 8 DDL + Flyway 迁移，`mvn flyway:migrate` 建库成功；Flyway 目录先只定「按模块」一维（`db/migration/mysql/{module}/`）。
- [ ] **不写 `TenantContext`、不接租户插件、缓存 Key 不带租户前缀**（平台后台域不需要；租户应用立项时再加）。
- [ ] 缓存 Key 与权限缓存按域分命名空间：`lx:platform:*`（v1.0 只这一套），为将来 `lx:tenant:*` / `lx:member:*` 留位置；权限码统一 `platform:` 前缀。

## 2. 下午：登录闭环

- [ ] `api/error-codes.yaml`（错误码 + `messageKey`，不存明文）、统一响应 `R`、全局异常、参数校验、XSS、幂等 Token。
- [ ] Sa-Token：`StpPlatformUtil`（v1.0 唯一实装），`StpTenantUtil` / `StpMemberUtil` **只留接口与占位**（不建表、不加依赖）。
- [ ] 登录接口：账号密码 + 图形验证码、密码策略、失败锁定、登录日志、Token 12h。
- [ ] 最小角色/菜单查询接口 + `@SaCheckPermission` 注解链路（先用手写两条菜单数据，不急着做管理页）。
- [ ] CI 三门禁：`mvn verify` / 关闭 `lx-demo` 后启动 / 扫 `schema.yaml` 的 `scope` 与权限码命名空间。

## 3. 下班前验收

- [ ] `java -jar lx-boot.jar` 启动成功，`/actuator/health` 正常。
- [ ] 初始管理员能登录，错误密码触发锁定，登录日志落库。
- [ ] 未授权接口直接调用返回 403。
- [ ] `lx.module.demo.enabled=false` 时仍能启动，demo 接口 404、Bean 不加载。
- [ ] Token 命名空间隔离：伪造别的域 Token / 权限码前缀，直接拒绝。
- [ ] CI 静态检查通过：`scope=platform` 的表不含隔离列，权限码命名空间无混用。

## 4. 明天明确不做

- 不写 CMS 业务、不写前台页面；**不建 `cms_*` / `mem_*` 表、不建 `lx-ui-portal`**。
- **不建 `sys_tenant`、套餐、`tenant_*` 用户表、`mem_*` 表；不建 `TenantContext`；不接租户插件。**
- 不接 Flowable、不接 MinIO、不接 ES、不做多数据库、不做 Docker / K8s。
- 不换技术栈（Boot 3 / JDK 17 的评估另开分支）。
- 不一次性建 20 个模块、不先写功能清单文档（契约文件优先）。

## 5. 开工前 10 分钟确认三件事

1. 技术基线是否按 `17` 第 4 节主线（Boot 2.7.18 + `release=8`）起步；
2. 本地参考源码路径（没有就现拉一份只读参考，不进仓库）；
3. 是否接受 v1.0 收缩到 `17` 第 2 节的范围。

> 租户应用立项时再定的一项（**不阻塞明天**）：多租户是「共用一套租户应用 + 共享库 + `tenant_id` 逻辑隔离」，还是「每租户独立实例」。前者租户应用内部仍要隔离列，后者不需要但要 N 套实例运维。见 `16` 3.3。
