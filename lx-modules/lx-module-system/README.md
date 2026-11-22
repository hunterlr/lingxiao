# lx-module-system · 系统管理模块

## 这个目录是做什么的

后台的“骨架中的骨架”：认证、用户、角色、菜单、部门、岗位、字典、参数、公告、日志。

**v1.0 已实装**：认证闭环（验证码 / 登录 / 登出 / 当前用户 / 菜单树）、个人中心（自助改昵称 / 邮箱 / 手机号、校验原密码改密并注销全部会话）、平台域权限查询、用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告八组 CRUD、操作日志落库与两个日志查询页、日志保留清理（Service + 条件化兜底调度 + 定时任务执行器）、公告富文本 Jsoup 白名单清洗、用户 / 部门 / 角色数据范围过滤（1 / 2 / 3 / 4 / 5）、系统监控三页（在线用户 / 强退、服务监控、缓存监控）。

## 包结构

```
com.lingxiao.modules.system
├─ controller/   AuthController、CaptchaController、
│               SysUserController、SysRoleController、SysMenuController、SysDeptController、
│               SysPostController、SysDictController、SysConfigController、SysNoticeController、
│               SysOperLogController、SysLoginLogController、
│               MonitorOnlineController、MonitorServerController、MonitorCacheController
├─ service/      AuthService、CaptchaService、
│               SysUserService、SysRoleService、SysMenuService、SysDeptService、
│               SysPostService、SysDictService、SysConfigService、SysNoticeService、
│               SysOperLogService、SysLoginLogService、DataScopeService、
│               LogRetentionService（日志清理共用逻辑）、
│               OnlineUserService、ServerMonitorService、CacheMonitorService
│  ├─ captcha/   CaptchaStore（接口）、CaffeineCaptchaStore、RedisCaptchaStore
│  └─ log/       SysOperLogStore（OperLogStore 的落库实现）
├─ support/      HtmlSanitizer（Jsoup 富文本白名单清洗）
├─ task/         LogRetentionTask（job 模块关闭时的 @Scheduled 兜底）、
│               SystemLogCleanJobHandler（job 模块开启时的 systemLogClean 执行器）
├─ security/     StpInterfaceImpl（平台域权限提供者）
├─ entity/       SysUser、SysRole、SysMenu、SysDept、SysDeptClosure、SysPost、
│               SysDictType、SysDictData、SysConfig、SysNotice、
│               SysUserRole、SysUserPost、SysRoleMenu、SysRoleDept、
│               SysLoginLog、SysOperLog
├─ mapper/       对应 16 个 Mapper（关联表用纯注解，不继承 BaseMapper）
├─ dto/          TreeSelect、PasswordResetRequest、OnlineUserVO、KickoutRequest、
│               KickoutResultVO、ServerInfoVO、CacheInfoVO、MenuPermRow
├─ config/       PasswordConfig（BCrypt）
└─ resources/db/migration/system/   V1 建表、V2 种子、V4 按钮权限、V8 日志清空权限、
                                   V9 参数控件、V10 在线强退权限、V14 登录安全、V15 自定义数据范围、
                                   V16 会话策略、V17 参数名乱码修复、V18 菜单模块归属
                                   （V11 定时任务相关结构由 lx-module-job 提供）
```

## 数据边界

**只操作 `sys_*`（不含 `sys_file*`）**。CMS / 会员表一律不碰。

## 依赖方向

```
lx-module-system → lx-common-{core,web,security,mybatis,log,redis} + lx-admin-api
```

**不依赖 `lx-module-file`**。需要文件时只存 `fileId`，不调用文件模块的 Service。

## 关键实现说明

### 登录

`AuthService.login` 顺序：验证码 → 查用户 → BCrypt 校验 → 状态校验 → 更新登录信息 → 踢旧会话（可选）→ 发 Token → 写 Session → 写登录日志。

- 验证码**先校验**，避免用登录接口做用户名枚举；
- 登录失败也写 `sys_login_log`（含失败原因），为后续账号锁定留数据；
- 日志写入失败只打 warn，不影响登录主流程；
- 会话策略由 `sys.account.multiDeviceLogin` 控制：默认 `false`（单会话，新登录调用
  `logoutByUserId` 踢掉旧 Token），改成 `true` 才允许多端同时在线；
  参数每次登录实时读取，不需要重启。

### 会话失效（什么时候必须踢人）

| 操作 | 行为 | 原因 |
|---|---|---|
| 新登录（默认单会话） | `logoutByUserId` 注销该账号全部旧 Token | 后台默认一人一个有效会话 |
| 自助改密 | 先落新密码，再注销全部会话 | 旧密码会话不能继续存活 |
| 管理员重置密码 | 落新密码后注销全部会话 | 同上，避免旧密码继续用 |
| 停用账号 | `logoutByUserId` 立即失效 | 停用必须立刻生效，不能等 Token 过期 |
| 删除账号 | 物理删除后注销全部会话 | 防止在线列表出现“空白账号” |

> 会话清理放在数据库操作**之后**：写库失败时不应该先把人踢下线。

### 个人中心（自助资料 / 改密）

| 接口 | 行为 |
|---|---|
| `PUT /api/admin/auth/profile` | 只改昵称 / 邮箱 / 手机号；DTO 没有 username / status，传了也忽略；成功后把昵称写回 Session |
| `PUT /api/admin/auth/password` | 原密码 BCrypt 校验失败返回 **2005**；新旧相同 / 新密码太短返回 1001；成功后 BCrypt 落库并 `logoutByUserId` 注销该账号**全部**会话 |

两个接口都标 `@Log(saveRequestData = false)`：审计只记“谁在什么时候改了个人资料 / 密码”，
不存请求体。`LogAspect` 在 `proceed()` 前捕获 username，所以“改密把会话踢掉”不会让审计丢归属。
改密顺序固定为“校验原密码 → 落新密码 → 踢会话”，不能先踢会话，否则失败时用户已经被登出。

### 验证码存储

`CaptchaService` 只依赖 `CaptchaStore` 接口，实现按 `lx.redis.enabled` 选择：

| 开关 | 实现 | 适用 |
|---|---|---|
| false / 未配置 | `CaffeineCaptchaStore` | 单机，进程重启后验证码失效 |
| true | `RedisCaptchaStore`（Key `lx:captcha:*`，TTL 300 秒） | 多实例，A 节点发码、B 节点可校验 |

验证码开关本身是两级的：`lx.captcha.enabled`（总闸）+ `sys.account.captchaEnabled`（运行期参数）。

### 操作日志与登录日志

| 项 | 做法 |
|---|---|
| 采集 | `lx-common-log` 的 `LogAspect`，`@Log` 标注在写接口上 |
| 落库 | `SysOperLogStore` 实现 `OperLogStore` SPI，写 `sys_oper_log` |
| 失败处理 | 切面 catch 后只 warn，不影响业务；`OperLogStore` 实现不得抛异常 |
| 查询 | `SysOperLogService` / `SysLoginLogService`，只读 + 清空，不提供修改 |
| 清空 | 独立权限码 `platform:monitor:operlog:clean` / `loginlog:clean`（Flyway V8） |
| 保留 | `LogRetentionService` 是唯一清理逻辑；job 模块开启时由 `systemLogClean` 任务驱动，关闭时由 `LogRetentionTask`（每天 03:30）兜底；参数非法时跳过 |
| 登录日志 | 写入在 `AuthService` 登录流程，成功与失败都记 |

清空的 wrapper 必须带 `isNotNull(主键)`：`BlockAttackInnerInterceptor` 会拦截无 where 的 delete。
清空动作自身也会被切面记一条 `business_type=8` 的日志，便于追“谁清的”。

### 日志保留：三条入口，一份逻辑

引入定时任务模块后，清理动作有两个触发方式，不能写成两套口径：

| 类 | 注册条件 | 作用 |
|---|---|---|
| `LogRetentionService` | 始终 | 唯一清理逻辑：读 `sys.log.keepDays`，删操作日志 / 登录日志，返回结果摘要 |
| `LogRetentionTask` | `lx.module.modules.job.enabled=false`（含未配置） | `@Scheduled(cron = lx.log.cleanCron)` 兜底 |
| `SystemLogCleanJobHandler` | `lx.module.modules.job.enabled=true` | `systemLogClean` 执行器，由页面任务驱动 |

job 模块开启时运维能改 cron、能暂停、能看到每次删除条数；关闭时审计表仍不会无限增长。
两条路都调用 `LogRetentionService.cleanNow()`，不会出现“页面删 180 天、兜底删 365 天”的偏差。

### 系统监控（W4）

三页都读真实运行数据，不建“在线表”，也不返回占位数字。

| 页面 | 数据来源 | 关键约束 |
|---|---|---|
| 在线用户 | 枚举平台域 Sa-Token 会话（`StpPlatformUtil.searchTokenValue`） | 一个 Token 一行；列表只下发脱敏 `tokenId`（首 4 + `****` + 末 4）；禁止强退当前会话；非超管不能强退 `user_id=1` |
| 服务监控 | JDK `OperatingSystemMXBean` / `MemoryMXBean` / `ThreadMXBean` / `ClassLoadingMXBean` / `RuntimeMXBean` + `FileStore` | 不引入 OSHI / Prometheus；比例统一 0~100；取不到返回 -1，前端显示“不可用” |
| 缓存监控 | `CaptchaStore.stats()` + Redis `INFO`（`lx.redis.enabled=true` 时） | Redis 关闭时展示 Caffeine 并提示单机限制；读取失败只降级，不 500 |

**在线会话的两个设计点：**

1. 登录 IP / 浏览器写在 **Token-Session**（`LoginHelper.setLoginContext`），不是账号 Session；
   同一账号多端登录才能各记各的来源。
2. 分页在“枚举 → 补用户资料 → 过滤 → 排序”之后于内存中做，总数与页码始终准确；
   过滤必须放在补资料之后，否则用户名只存在账号 Session，按账号筛选会全部落空。

**强退接口**：`POST /api/admin/monitor/online/kickout` 用请求体传脱敏 `tokenIds`，
并标注 `@Log(..., saveRequestData = false)`，避免会话标识进入操作日志。
批量逐条执行、逐条返回失败原因，不做整批回滚。

**CPU 反射坑**：JDK 14+ 是 `getCpuLoad()`，8~13 是 `getSystemCpuLoad()`。
必须从公开接口 `com.sun.management.OperatingSystemMXBean.class` 取 `Method`，
用运行时实现类取方法在 JDK 8 下会因类非 public 抛 `IllegalAccessException`，表现为恒返回 -1。

**缓存统计口径**：Caffeine 零请求时 `CacheStats.hitRate()` 返回 1.0，
`stats()` 在 `requestCount == 0` 时改返回 0；Redis 用 SCAN（上限 10000）统计 `lx:captcha:*`，不用 `KEYS`。

### 公告富文本 XSS 清洗

`support/HtmlSanitizer` 用 Jsoup `Safelist.relaxed()` 做白名单，**不用正则删标签**：

| 点 | 做法 |
|---|---|
| 清洗入口 | `SysNoticeService.create / update`（写入）+ `page / detail`（读取，兜历史脏数据） |
| 危险内容 | `script` / `iframe` / `onerror` / `onclick` / `javascript:` 全部丢弃 |
| 保留内容 | 常规排版标签、`img alt`、相对 `src` / `href`、`video` / `source`、表格 `colspan` |
| 链接 | `a[target]` 保留，并强制 `rel="nofollow noopener noreferrer"` |
| 样式 | 不放行 `style` / `class`：CSS 里同样能藏 `expression()` / `url()` |
| 相对地址 | 清洗传占位基准地址 `https://lingxiao.local/` + `preserveRelativeLinks(true)`，既不丢相对图片，也不改写成假域名 |

### 菜单树

- 超管（`user_id=1`）直接取全部 `M`/`C` 菜单；
- 普通用户走 `sys_user_role → sys_role_menu → sys_menu`；
- 父节点不在结果集时作为根节点返回，避免“授权了却看不到”。

### 权限

`StpInterfaceImpl` **只处理 `platform` 域**，其他域返回空列表，防止 Token 串域。

接口鉴权统一用 `@RequiresPermission("platform:system:xxx:action")`（`lx-common-security`），由 `PermissionAspect` 先校验登录、再校验权限码：

- 未登录 → 401 / 1002；
- 无权限 → 403 / 1003。

不用 Sa-Token 的 `@SaCheckPermission`，因为它默认校验 `login` 域，容易写漏 `type`。

### 系统管理 CRUD 约定

| 约定 | 说明 |
|---|---|
| 物理删除 | `sys_user` / `sys_role` / `sys_menu` / `sys_post` / `sys_dict_type` / `sys_config` / `sys_notice` 均物理删除；逻辑删除会与唯一索引冲突，删了 `test` 就不能再建 `test` |
| 唯一性校验 | 用户名、角色标识、菜单标识、岗位编码、字典类型编码、参数键名，改前先查重（排除自身） |
| 内置保护 | `user_id=1` 不可停用/删除；不能停用或删除当前登录账号；`role_id=1` 不可删除/停用/改标识；`config_type='Y'` 不可改 key、控件类型、选项，不可删除 |
| 关联清理 | 删用户清 `user_role` / `user_post`；删角色清 `role_menu` / `role_dept`；删菜单清 `role_menu`；删部门清闭包表与 `sys_role_dept`；岗位被占用时**拒绝删除**而非静默解除 |
| 授权方式 | 角色 `menuIds` / `deptIds`、用户 `roleIds` / `postIds` 随保存一起提交，Service 先清后插；字段为 `null` 表示“不修改授权” |
| 角色授予 | 非超管不能新增 `role_id=1`，也不能新增 `data_scope=1` 的角色（只校验本次新增，避免正常编辑被历史高权限角色卡死） |
| 数据范围 | 角色接受 1 / 2 / 3 / 4 / 5；`2 自定义` 必须至少勾 1 个部门，且只能勾自己可见范围内的部门；非超管不能把角色范围提升为 1 |
| 密码 | 新增必填（6~64）BCrypt；修改资料不碰密码；重置密码独立接口且用请求体；实体 `password` 只写不回 |
| 分页上限 | 所有列表 `pageSize` 夹在 1~100，防止被拉全表 |

### 参数控件（control_type / config_options）

参数不是“一律文本框”。`sys_config` 用两列描述怎么编辑（V9）：

| 控件 | 选项格式 | 值校验 |
|---|---|---|
| `text` | 忽略，保存时清空 | 非空 |
| `number` | `最小值:最大值`，可省略一侧 | 整数；配置了范围时不能越界 |
| `switch` | `开值:关值`，默认 `true:false` | 必须等于开值或关值 |
| `select` | `值:显示名`，逗号 / 换行分隔 | 必须在选项内；选项值不能空、不能重复 |

- 校验在 `SysConfigService#normalizeAndValidateControl`，**新增与修改都走**；前端校验只是少一次往返。
- 内置参数（`config_type='Y'`）的控件类型与选项被锁定：前端置灰，后端强制还原，
  防止把开关改成文本框、或删掉下拉选项导致已有值不可解析。
- 列表接口支持 `controlType` 筛选；前端选项解析在 `src/utils/config-control.js`。

`SysConfigService` 同时实现 `lx-common-core` 的 `ConfigProvider`（只读 SPI），
文件模块通过 `ObjectProvider<ConfigProvider>` 读取 `sys.file.uploadMaxMb`：
参数设置 > `lx.file.max-size-mb` > 容器 multipart 上限，读不到或值非法时回退 YAML。

### 部门闭包表

`sys_dept_closure(ancestor_id, descendant_id, depth)` 维护三个时机：

1. **新增**：`(self,self,0)` + 父级祖先链 `(ancestor,self,ancestor.depth+1)`；
2. **移动**：取子树快照 → 留存内部行 → 删涉及该子树的行 → 写回内部行 → 补新父级外部链接；
3. **删除**：确认无子部门、无用户后清理相关行。

“本部门及以下”查询走 `selectSubtree` 一条 SQL，不用递归、不用 `find_in_set`。

> 历史坑：早期移动实现把子树行全删再只补根节点，导致子树内部层级丢失、成环校验失效。修复见 `docs/21-build-log.md` 第 17.1 节。

### 数据范围（DataScopeService）

`sys_role.data_scope` 不只是存值，用户相关入口统一走 `DataScopeService`：

| 值 | 含义 | 行为 |
|---|---|---|
| 1 | 全部 | 不加条件；超管等价于此 |
| 2 | 自定义部门 | `sys_role_dept` 中勾选的部门集合，精确匹配、不含下级 |
| 3 | 本部门 | `dept_id = 当前用户部门` |
| 4 | 本部门及以下 | 闭包表取子树（含自己） |
| 5 | 仅本人 | `user_id = 当前用户` |

- 多角色取最大范围（1 > 4 > 3 > 2 > 5），自定义范围跨角色取并集；停用角色不参与；
- 无上下文 / 无角色 / 无部门 / 自定义未勾部门 → 仅本人，**失败收紧，不放开**；
- 作用点：用户列表 / 详情 / 写操作，部门列表 / 树 / 详情 / 写操作；
  角色列表与下拉对非全部范围账号只展示“可授权”角色（隐藏内置超管与全部数据角色）；
- 非超管授予角色时，禁止新增 `role_id=1` 或 `data_scope=1` 的角色；
  保存自定义范围时也不能勾选自己数据范围之外的部门；
- 自定义范围是精确集合：在自定义部门下新建子部门不会自动进入范围，
  部门树维护建议交给全部数据范围管理员；删除部门会同步清理 `sys_role_dept`；
- 导出接口尚未实现；实现时必须复用 `applyUserScope`。

### 按钮权限

`V4__system_button_perms.sql` 补 25 条 `menu_type='F'` 记录（`add/edit/remove/resetPwd` 等），ID 按菜单分段（1xx 用户 … 8xx 公告），并授权给 `role_id=1`；`V8__monitor_log_perms.sql` 再补两条日志清空权限（1401 / 1501）；`V10__monitor_online_kickout.sql` 补在线强退权限（1301）；定时任务的 7 个按钮权限（2101~2107）由 `lx-module-job` 的 `V11` 补入。

按钮不进侧边栏，只提供 `perms`；前端用全局指令 `v-hasPerm`（`src/directives/permission.js`）
按 `/auth/info` 返回的 `permissions` 控制按钮显隐，超管 `*:*:*` 直接放行；
行内 `el-switch`（用户启停用、任务启停）用 `hasPermission(...) + disabled` 做只读态。
覆盖范围：用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告 / 任务 / 生成器 /
文件中心 / 在线用户 / 操作日志 / 登录日志。前端隐藏只减少误操作，鉴权仍在后端。

### 接口一览

| 前缀 | 覆盖操作 |
|---|---|
| `/api/admin/auth` | 验证码 / 登录 / 登出 / 当前用户 / 菜单树 / 本人资料 / 自助改密 |
| `/api/admin/system/user` | 分页 / 详情 / 增改删 / 重置密码 / 启停用 |
| `/api/admin/system/role` | 分页 / 下拉 / 详情 / 增改删（含菜单授权） |
| `/api/admin/system/menu` | 平铺 / 树（可选只含 M+C）/ 详情 / 增改删 |
| `/api/admin/system/dept` | 平铺 / 树 / 详情 / 增改删（含移动） |
| `/api/admin/system/post` | 分页 / 下拉 / 详情 / 增改删 |
| `/api/admin/system/dict` | 类型与数据两组：分页 / 下拉 / 详情 / 增改删 |
| `/api/admin/system/config` | 分页 / 详情 / 按键取值 / 增改删 |
| `/api/admin/system/notice` | 分页 / 详情 / 增改删 |
| `/api/admin/system/oper-log` | 分页 / 详情 / 清空（`platform:monitor:operlog:*`） |
| `/api/admin/system/login-log` | 分页 / 清空（`platform:monitor:loginlog:*`） |
| `/api/admin/monitor/online` | 在线列表（`platform:monitor:online:list`）/ 强制下线（`platform:monitor:online:kickout`） |
| `/api/admin/monitor/server` | 服务监控快照（`platform:monitor:server:list`） |
| `/api/admin/monitor/cache` | 缓存监控快照（`platform:monitor:cache:list`） |

## 菜单约定

| 类型 | 含义 | 是否进侧边栏 |
|---|---|---|
| M | 目录 | ✅ |
| C | 菜单（对应页面） | ✅ |
| F | 按钮（只提供 perms） | ❌ |

`menu_key` 是幂等导入用的稳定键（如 `system:user:list`），`module_code` 标记归属模块，模块关闭时不下发。

## 禁止事项

- ❌ 在 `AuthService` 里堆用户 CRUD（应拆 `SysUserService`）。
- ❌ 用 MD5 / SHA1 存密码（必须 BCrypt）。
- ❌ 在 Mapper 里写 `find_in_set` / `group_concat`（多库不兼容）。
- ❌ 让 `StpInterfaceImpl` 忽略 `loginType`。
- ❌ 把 `password` 字段返回给前端（实体已限制，接口用 DTO）。

## 默认账号

`admin / admin123`（BCrypt 哈希写在 `V2__system_seed.sql`）。**首次部署后立即改密码。**
