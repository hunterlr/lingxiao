# 30 · 会员模块嵌入方案（v1.2）

> 定位：会员是**前台用户体系**，与平台后台账号物理隔离。
> 加依赖 + 开开关 + 跑迁移 = 可用；关闭后基座照常启动，已建表与数据保留。
> 本文档记录 v1.2 会员模块的边界、数据模型、接口、权限码与切片顺序。

---

## 1. 边界

| 项目 | 平台后台 | 前台会员 |
| --- | --- | --- |
| 账号表 | `sys_user` | `mem_user` |
| Token 名 | `lx-platform-token` | `lx-member-token` |
| StpLogic 类型 | `platform` | `member` |
| 拦截路径 | `/api/admin/**` | `/api/app/**` |
| 权限模型 | 角色 + 菜单权限码 | v1.2 只有登录态 |
| 头像 | `sys_user.avatar` | `mem_user.avatar`（`fileId` 或外链） |

- 两套安全域只共享底层存储，不共享 Session 命名空间；
  会员 Token 访问后台接口、后台 Token 访问会员接口都返回 401。
- 会员模块**不依赖** `lx-module-system`；跨模块只依赖 `lx-common-*` 与 `lx-framework`。
- 会员头像复用 `sys_file`，只存 `file_id`，**不建** `mem_file` 表。

---

## 2. 模块开关与迁移

```yaml
lx:
  module:
    modules:
      member:
        enabled: true
```

- 启动期生效，改完必须重启；不是运行期热插拔。
- `false`：`db/migration/member` 不扫描、Controller 不装配
  （`/api/app/**` 与 `/api/admin/member/**` 返回 404）、会员菜单与权限码不下发；
  已建表和数据保留。
- 迁移目录名必须等于模块编码 `member`。

---

## 3. 数据模型

### 3.1 `mem_level` 会员等级

| 字段 | 说明 |
| --- | --- |
| `level_id` | 主键 |
| `level_code` | 编码，业务唯一 |
| `level_name` | 名称 |
| `min_growth` / `max_growth` | 成长值区间，`max_growth` 为空表示无上限 |
| `benefits_json` | 权益占位，后续切片使用 |
| `sort` / `status` | 排序 / 启停 |
| `tenant_id` | 单租户固定 0，给 SaaS 留位 |
| `del_flag` / 审计字段 / `version` | 基座统一约定 |

种子：`1 registered 注册会员 0~999`、`2 active 活跃会员 1000~4999`、
`3 vip VIP 会员 5000~无上限`。

### 3.2 `mem_user` 会员账号

| 分组 | 字段 |
| --- | --- |
| 登录 | `username`、`password`（BCrypt）、`mobile`、`email` |
| 资料 | `nickname`、`avatar`、`sex`（0 男 / 1 女 / 2 未知）、`birthday`、`remark` |
| 等级 | `level_id`、`points`、`growth` |
| 状态 | `status`（0 正常 / 1 停用）、`login_fail_count`、`lock_time` |
| 来源 | `register_channel`、`register_ip`、`last_login_time`、`last_login_ip` |
| 通用 | `tenant_id`、`del_flag`、审计字段、`version` |

- **不建** 用户名 / 手机号 / 邮箱的数据库唯一键：逻辑删除后要允许同名重新注册，
  带 `del_flag` 的唯一键会在两条已删除记录上互相冲突；唯一性由应用层校验。
- 删除为逻辑删除，列表、登录查询、唯一性校验都忽略已删除记录。

### 3.3 `mem_password_reset` 找回密码验证码

| 字段 | 说明 |
| --- | --- |
| `reset_id` | 主键 |
| `member_id` | 会员 |
| `code_hash` | BCrypt 哈希，不存明文 |
| `used` | 0 未用 / 1 已用 |
| `expire_time` | 过期时间 |
| `create_time` | 申请时间 |

---

## 4. 接口

### 4.1 会员端（`/api/app`，无需登录）

```text
GET  /api/app/auth/captcha?scene=login|register|reset
POST /api/app/auth/register
POST /api/app/auth/login
POST /api/app/auth/logout
POST /api/app/auth/forgot-password
POST /api/app/auth/reset-password

# 公开只读：匿名可看总数，带会员 Token 时附带个人状态
GET  /api/app/public/interactions/articles/{articleId}
```

### 4.2 会员端（必须登录）

```text
GET  /api/app/profile
PUT  /api/app/profile
PUT  /api/app/profile/password

GET    /api/app/interactions/favorites
PUT    /api/app/interactions/articles/{articleId}/favorite
DELETE /api/app/interactions/articles/{articleId}/favorite
PUT    /api/app/interactions/articles/{articleId}/like
DELETE /api/app/interactions/articles/{articleId}/like
```

### 4.3 后台（`/api/admin/member`，平台域）

```text
GET    /api/admin/member/list
GET    /api/admin/member/level-options
GET    /api/admin/member/{memberId}
POST   /api/admin/member
PUT    /api/admin/member/{memberId}
PUT    /api/admin/member/{memberId}/status
PUT    /api/admin/member/{memberId}/password
DELETE /api/admin/member/{memberId}
```

---

## 5. 权限码

| 权限码 | 含义 |
| --- | --- |
| `platform:member:user:list` | 会员列表 |
| `platform:member:user:query` | 会员详情 |
| `platform:member:user:add` | 新增会员 |
| `platform:member:user:edit` | 编辑会员 |
| `platform:member:user:status` | 启停会员 |
| `platform:member:user:resetPwd` | 重置密码 |
| `platform:member:user:remove` | 删除会员 |

菜单：`4000 member` → `4010 member:user`（`member/user/index`）→ `4011~4016` 按钮。
`4020 member:channel`（`member/channel/index`）→ `4021~4022` 按钮；
`4030 member:setting`（`member/setting/index`）→ `4031` 保存按钮，
权限沿用 `platform:system:config:list|edit`（编辑的仍是 `sys_config`）。
超管角色在迁移里自动授权。

---

## 6. 参数

会员参数从通用参数页拆成两处维护：

- **总开关**留在「系统管理 → 参数设置」，只有 `mem.enabled` 一行；
- **其余参数**集中在「会员管理 → 会员设置」（`/member/setting`），按安全 / 账号 /
  注册 / 找回分组展示，一次保存只提交改动项。

通用参数页通过 `excludeConfigKeyPrefix=mem.` 过滤，会员设置页通过
`configKeyPrefix=mem.` 读取；以后新增 `mem.*` 参数会自动出现在设置页的「其他」分组。

| 键 | 控件 | 默认 | 用途 |
| --- | --- | --- | --- |
| `mem.enabled` | switch | true | 运行期总开关；关闭后会员端与后台会员接口返回 1003，会员菜单不下发 |
| `mem.security.captchaEnabled` | switch | true | 会员验证码开关 |
| `mem.security.passwordPolicy` | select | basic | 密码复杂度（off / basic / strong） |
| `mem.account.maxRetryCount` | number | 5 | 登录失败锁定阈值 |
| `mem.account.lockMinutes` | number | 10 | 锁定时长（分钟） |
| `mem.account.multiDeviceLogin` | switch | false | 是否允许多端登录 |
| `mem.register.mobileRequired` | switch | false | 注册手机号是否必填 |
| `mem.register.emailRequired` | switch | false | 注册邮箱是否必填 |
| `mem.register.resetCodeExpireMinutes` | number | 10 | 找回密码验证码有效期 |
| `mem.security.captchaMaxAttempts` | number | 3 | 同一图形验证码允许输错次数 |
| `mem.register.rateLimitCount` | number | 5 | 注册限流：单用户名 / IP 窗口内请求数 |
| `mem.register.rateLimitMinutes` | number | 10 | 注册限流窗口（分钟） |
| `mem.account.loginRateLimitCount` | number | 30 | 登录限流：单账号 / IP 窗口内请求数 |
| `mem.account.loginRateLimitMinutes` | number | 10 | 登录限流窗口（分钟） |
| `mem.register.resetRateLimitCount` | number | 5 | 找回 / 重置密码限流：单账号 / IP 窗口内请求数 |
| `mem.register.resetRateLimitMinutes` | number | 10 | 找回 / 重置密码限流窗口（分钟） |

删除参数时模块回退代码里的默认值，不会出现空指针。

**两级开关的区别：**

- `lx.module.modules.member.enabled`：Spring 启动期开关，决定会员 Bean 是否装配，
  改动需要重启；关闭时接口 404、菜单不下发，数据和表保留。
- `mem.enabled`：运行期业务开关，改完立即生效；关闭时 `/api/app/**` 与
  `/api/admin/member/**` 返回 `1003 会员功能已关闭`，会员菜单不下发，
  但平台后台、CMS 与参数页本身保持可用。会员 Bean 仍在，重新开启不需要重启。

---

## 7. 安全约束

- 密码只存 BCrypt 哈希；响应体不返回密码字段。
- 登录失败计数落库（`login_fail_count` / `lock_time`），锁定期内不再比对密码。
- 注册 / 登录 / 找回密码 / 重置密码有独立限流：账号（用户名 / 手机号 / 邮箱）与 IP
  两个维度，超限返回 `1006`；限流是内存固定窗口，重启清零，不替代登录失败锁定。
- 验证码存 Sa-Token DAO，按场景（login / register / reset）隔离；同一验证码允许
  输错 `mem.security.captchaMaxAttempts` 次，达到上限立即作废，验证通过也立即作废。
- 找回密码验证码只存 BCrypt 哈希；账号不存在时静默成功，不暴露账号是否注册。
- 找回密码验证码按账号选择通道：手机号走短信、邮箱走邮件、用户名优先手机号；
  开发环境默认写日志，生产需实现 `MemberSmsSender` / `MemberEmailSender`。
- 停用 / 删除 / 重置密码都会踢掉该会员全部会话。
- 多端登录默认关闭（新登录踢旧会话）；开启后同一账号可多端并存。
- 会员验证码受总闸 `lx.captcha.enabled` 与参数 `mem.security.captchaEnabled` 双重控制。

---

## 8. 切片顺序

| 切片 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 表结构、独立 StpLogic、注册 / 登录 / 找回密码、个人中心、后台 CRUD | v1.2 第一刀，已完成 |
| 1.5 | 注册 / 登录 / 找回密码限流、验证码错误次数、短信 / 邮件通道接口与开发实现 | v1.2 第二刀，已完成 |
| 2 | 会员等级升级任务、积分 / 成长值规则 | 待排期 |
| 3 | 评论 / 收藏 / 点赞 / 消息 | 收藏 + 点赞已完成（2026-11-22），评论 / 消息待排期 |
| 4 | 第三方登录绑定（`mem_oauth`） | 待排期 |
| 5 | 会员角色与细粒度权限 | 待排期 |

**第二刀后仍未做**：真实短信 / 邮件服务商实现（通道默认 `mode=dev` 只写日志，
后台「会员管理 → 渠道配置」可运行期切换，但生产仍需先实现
`MemberSmsSender` / `MemberEmailSender`）、等级自动升级、积分规则、内容级多语言。

---

## 9. 验收基线

- 单测：会员模块共 55 项 —— `MemberUserServiceTest` 19 项、
  `MemberPasswordPolicyTest` 5 项、`MemberCaptchaServiceTest` 7 项、
  `MemberRateLimitServiceTest` 4 项、`RoutingMemberResetCodeSenderTest` 5 项、
  `MemberChannelConfigServiceTest` 9 项、`MemberInteractionServiceTest` 6 项。
- 全仓：**211 项 / 35 类**，JDK 8 下失败 0 错误 0。
- 接口：`.local/member-check.mjs` **69 项全部通过**（域隔离、注册登录、找回密码、
  短信 / 邮件开发通道、渠道配置、锁定、启停、删除、菜单下发、验证码开关、
  注册 / 登录 / 找回 / 重置限流）；`.local/member-interaction-check.mjs`
  **20 项全部通过**（收藏 / 点赞、域隔离、幂等、公开统计、批量摘要）。
- 浏览器：`.local/uitest/member.mjs` 12 项全部通过（列表、筛选、编辑、启停、
  重置密码、删除、无前端报错）。
- 关闭态：`.local/member-off-check.mjs` 7 项全部通过。`member.enabled=false` 重启后
  `/api/app/**` 与 `/api/admin/member/**` 返回 404，会员菜单不下发，平台后台与 CMS 正常，
  已建表与数据保留。

---

## 10. v1.2 第二刀：会员安全补口（2026-11-22）

第一刀打通账号闭环后，第二刀只补安全缺口，不扩业务面：

- 注册 / 登录 / 找回密码 / 重置密码限流；
- 图形验证码错误次数上限；
- 短信 / 邮件通道接口化，开发环境保留日志实现，生产可替换；
- V26 参数迁移、单测与接口验收补齐。

### 10.1 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `db/migration/member/V26__member_security_hardening.sql` | 参数 19~25：验证码尝试次数、注册 / 登录 / 找回限流 |
| 限流 | `MemberRateLimitService` | 内存固定窗口，按动作 + 账号 + IP 计数，超限 1006 |
| 验证码 | `MemberCaptchaService` | 同一 key 允许输错 N 次，达到上限立即作废 |
| 通道接口 | `MemberSmsSender` / `MemberEmailSender` | 短信、邮件真实通道 SPI |
| 服务商 | `DevMemberSmsSender` / `DevMemberEmailSender` | 只写应用日志，保留可解析的验证码行 |
| 装配 | `MemberChannelConfiguration` | 始终装配开发实现，作为 `mode=dev` 与生产实现缺失时的兜底 |
| 路由 | `RoutingMemberResetCodeSender` | 手机号走短信、邮箱走邮件、用户名优先手机号 |
| 业务 | `MemberUserService` | 注册 / 登录 / 找回 / 重置入口统一先限流 |
| 单测 | `MemberRateLimitServiceTest` / `RoutingMemberResetCodeSenderTest` | 窗口边界、Key 隔离、通道路由 |
| 渠道配置 | `MemberChannelConfigService` + `V27__member_channel_config.sql` | 后台独立页面维护 dev / prod、启停、服务商与密钥 |

### 10.2 关键决策

1. **限流与锁定分层。**
   限流挡短时间脚本重放，内存固定窗口、重启清零；账号锁定仍落
   `mem_user.login_fail_count / lock_time`，重启不丢，两者互不替代。
2. **账号 + IP 双维度。**
   每个动作同时记账号键与 IP 键；IP 阈值自动放宽为账号阈值的 10 倍，
   正常用户不误伤，批量脚本会先撞 IP 上限。
3. **动作独立计数。**
   注册、登录、找回、重置分别计数；找回验证码错误不会占用登录额度。
4. **验证码有限重试。**
   同一个验证码允许输错 `mem.security.captchaMaxAttempts` 次（默认 3），
   达到上限立即删除验证码；验证成功也立即删除，仍是一次性使用。
5. **通道接口与业务解耦。**
   业务层只调用 `MemberResetCodeSender`；生产实现短信 / 邮件接口即可，
   不需要改注册、找回或后台管理逻辑。
6. **开发通道可验收。**
   开发实现按原格式输出 `memberId / username / code / expireMinutes`，
   本地脚本可直接从日志取验证码；生产必须在后台切 `mode=prod` 并确认真实实现已装配。
7. **参数缺失回退默认值。**
   新增 7 个参数全部走 `ConfigProvider`，删除参数不会导致空指针或登录失败。
8. **通道配置独立成页。**
   短信 / 邮件不适合混在通用参数设置里；V27 新建 `mem_channel_config`，
   后台「会员管理 → 渠道配置」运行期维护启停、dev / prod、服务商与密钥，
   并提供测试发送。切换 prod 前校验真实 Bean，缺失时拒绝保存。

### 10.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -q -DskipTests package` 通过 |
| Flyway | `V26 - member security hardening` 成功；参数 19~25 已落库；`V27 - member channel config` 成功 |
| 单测 | 会员 49 项；全仓 **205 项 / 34 类**，失败 0 错误 0 |
| 接口验收 | `.local/member-check.mjs` **69/69**；新增短信 / 邮件通道、渠道配置、注册 / 登录 / 找回 / 重置限流 |
| 验证码次数 | 单测覆盖：前两次错误可重试，第 3 次作废，之后正确码也不能用 |
| 平台隔离 | 后台会话、会员菜单、CMS 不受影响 |

### 10.4 复验命令

```powershell
# 后端 8080（关闭验证码）
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
node .local\member-check.mjs

# 全仓单测
mvn -q test
```

### 10.5 渠道配置页面（2026-11-22 追加）

短信 / 邮件不再依赖 yml 开关，改为后台独立页面「会员管理 → 渠道配置」
（`/member/channel`），运行期修改立即生效。

- **表**：`mem_channel_config`（V27），短信 / 邮件各一行；字段
  `mode / provider / endpoint / access_key / access_secret / sign_name /
  template_code / sender / status`。
- **接口**：`GET /api/admin/member/channel`、
  `PUT /api/admin/member/channel/{configId}`、
  `POST /api/admin/member/channel/{configId}/test`。
- **权限**：`platform:member:channel:query|edit|test`，菜单 4020~4022。
- **密钥**：`accessSecret` 只写不读，响应不回显；保存时留空表示沿用原值。
- **测试发送**：dev 模式回显验证码并写应用日志；prod 模式只返回提交结果。
  通道停用或手机号 / 邮箱格式错误会明确报错。
- **生产保护**：切到 `prod` 前检查部署方是否已提供
  `MemberSmsSender` / `MemberEmailSender` Bean；缺失时拒绝保存，避免找回密码静默失效。
- **默认值**：两个通道均为 `mode=dev`、`status=0`（启用），与第一刀行为一致。

---

## 11. v1.2 参数页重组与运行期总开关（2026-11-22）

会员参数原来和系统、CMS 参数混在一张表里，共 15 行 `mem.*`，运营定位困难。
本次只做“拆分 + 总开关”，不改业务逻辑。

### 11.1 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `db/migration/member/V28__member_settings.sql` | `mem.enabled`（configId 26）；菜单 4030 / 4031 |
| 查询 | `SysConfigService.page` / `SysConfigController.list` | 新增 `configKeyPrefix` / `excludeConfigKeyPrefix` |
| 批量保存 | `SysConfigService.updateValues` + `PUT /api/admin/system/config/batch` | 先整体校验再写库，只改值 |
| 通用参数页 | `views/system/config/index.vue` | 顶部总开关卡片；列表排除 `mem.` |
| 会员设置页 | `views/member/setting/index.vue` | 按分组渲染 `mem.*`，只提交改动项 |
| 运行期开关 | `MemberEnabledInterceptor` + `MemberSaTokenConfigure` | 拦截 `/api/app/**` 与 `/api/admin/member/**` |
| 菜单过滤 | `AuthService.isModuleEnabled` | 会员模块额外检查 `mem.enabled` |

### 11.2 关键决策

1. **总开关不能只放在会员设置页。** 关闭后会员菜单会隐藏，用户无法再从设置页打开；
   开关必须留在始终可见的「系统管理 → 参数设置」。
2. **用前缀而不是硬编码键名。** 通用页排除 `mem.`，设置页读取 `mem.`，新增参数无需改页面；
   未归类的参数落到「其他会员参数」，不会被静默藏起来。
3. **批量保存先校验后写库。** 一半参数成功、一半失败会让页面状态与库不一致；
   服务端先全量校验，任何一项不合法都整体拒绝。
4. **开关是运行期参数，不是 Spring 模块开关。** 会员 Bean 仍由
   `lx.module.modules.member.enabled` 启动期装配；`mem.enabled` 只控制对外可用性，
   因此可以改完立即生效。
5. **后台会员管理也受运行期开关控制。** 否则“关闭会员”后后台仍能新增 / 编辑会员，
   语义不一致；平台后台与 CMS 不受影响。

### 11.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -DskipTests compile`、`mvn -DskipTests package` 通过 |
| 全仓单测 | **205 项 / 34 类**，失败 0 错误 0 |
| 前端构建 | `npm run build` 通过，生成 `member/setting` 独立 chunk |
| Flyway | V28 成功，`mem.enabled` 落库；菜单 4030 / 4031 已授权超管 |
| 接口 - 过滤 | 全量 26、排除 `mem.` 后 10、前缀 `mem.` 16 |
| 接口 - 批量 | 合法批量保存成功；含非法值整批拒绝且前一项未被修改 |
| 接口 - 开关 | 关闭后 app 接口与后台会员接口均 1003，会员菜单隐藏，参数页仍可读；重新开启恢复 |
| 浏览器 | 总开关卡片、10 行非会员参数、分组设置页、保存提示、关闭后菜单隐藏与路由 404 均通过 |

---

## 12. v1.2 第三刀（前半）：收藏与点赞（2026-11-22）

切片 3 原本是「评论 / 收藏 / 点赞 / 消息」。本轮只做收藏与点赞：
不涉及内容审核，读写模型简单，风险最小，同时为评论复用同一套
「会员 Token + 互动表 + 公开统计」骨架。

### 12.1 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `db/migration/member/V29__member_interaction.sql` | `mem_favorite`、`mem_like`，会员 + 文章唯一键 |
| 实体 | `MemFavorite` / `MemLike` | 只存 `tenant_id / member_id / article_id / create_time` |
| Mapper | `MemFavoriteMapper` / `MemLikeMapper` | 原生 `BaseMapper`，不做逻辑删除 |
| 服务 | `MemberInteractionService` | 统计、收藏 / 取消、点赞 / 取消、我的收藏分页 |
| 控制器 | `MemberInteractionController` | 公开统计 + 登录写操作，只挂 `/api/app/**` |
| 白名单 | `MemberSaTokenConfigure` | 显式放行 `/api/app/public/**`，写接口仍需登录 |
| DTO | `MemberInteractionVO` / `MemberFavoriteVO` | 统计状态；收藏只返回文章 ID 与时间 |
| CMS | `CmsPortalController` / `CmsPortalService` | 新增 `articles/batch` 批量摘要，不累加阅读量 |
| 门户 | `useMemberAuth` / `usePortalApi` / `types/member.ts` | 互动请求与批量摘要封装 |
| 页面 | `pages/article/[id].vue` / `pages/account.vue` | 文章页收藏 / 点赞；个人中心「我的收藏」 |
| 样式 | `assets/css/main.css` | 互动按钮、收藏列表、空状态、分页 |
| 单测 | `MemberInteractionServiceTest` | 6 项：匿名、幂等、取消、非法 ID、分页 |

### 12.2 关键决策

1. **只存 ID，不复制文章字段。**
   `mem_favorite / mem_like` 只有 `member_id + article_id`，不建跨模块外键、
   不存标题 / 封面。文章改标题或下线后，收藏列表不会展示旧文案。
2. **取消即删，不做逻辑删除。**
   收藏 / 点赞是轻量关系，物理删除后可以重新收藏；若用 `del_flag`，
   再次收藏会与唯一键冲突。并发重复插入由唯一键兜底，捕获
   `DuplicateKeyException` 视为成功。
3. **写接口幂等。**
   重复收藏 / 点赞、取消不存在的记录都返回成功后的最新状态，
   前端不需要先查再写，弱网重试也不会报错。
4. **公开读独立前缀。**
   统计接口放在 `/api/app/public/interactions/**` 并显式加入会员拦截器白名单；
   写操作仍在 `/api/app/interactions/**`，必须先登录。匿名访问统计时
   `favorited / liked` 固定 `false`，不会泄露个人状态。
5. **会员 Token 与后台 Token 严格分离。**
   控制器只用 `MemberLoginHelper` 取会员 ID，不读平台 `LoginHelper`、
   不接受前端传 `memberId`。后台 Token 调会员接口返回 401 / `1002`，
   会员 Token 调 `/api/admin/**` 同样不通。
6. **收藏列表分两步取。**
   会员接口只给文章 ID；门户再调 CMS `articles/batch` 补标题 / 封面 / 栏目。
   批量接口按请求顺序返回、忽略不可见 ID、**不累加阅读量**，避免收藏页
   把阅读量刷高。下线条目在门户显示「内容已下线或不可见」。
7. **SSR 取互动，失败不阻塞正文。**
   文章页在 setup 里取一次统计；会员模块关闭或网络异常时回落为 0，
   正文照常渲染。`1002` 会话失效时清 Cookie 并带 `redirect` 回登录页。
8. **未登录操作先跳登录。**
   门户按钮不静默失败：未登录点击收藏 / 点赞跳
   `/login?redirect=/article/{id}`，登录后回到原文。

### 12.3 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -q -DskipTests package` 通过 |
| Flyway | `V29 - member interaction` 成功，两表与唯一键已落库 |
| 单测 | `MemberInteractionServiceTest` 6 项通过 |
| 接口验收 | `.local/member-interaction-check.mjs` **20/20**：域隔离、匿名统计、幂等、取消、分页、批量摘要、非法 ID |
| 门户构建 | `npm run build` 通过 |
| 浏览器 | 登录 → 收藏 / 点赞 → 刷新仍保留 → 个人中心列表 → 取消收藏 → 退出 → 未登录点击跳登录，控制台无错误 |
| SSR | 带会员 Cookie 的文章页首屏含「已收藏 / 已点赞」与会员昵称，不再出现登录提示 |

### 12.4 复验命令

```powershell
# 后端 8080（关闭验证码），门户 3000
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
node .local\member-interaction-check.mjs
node .local\portal-interaction-ssr.mjs

# 单测
mvn -q -pl lx-modules/lx-module-member -am test -Dtest=MemberInteractionServiceTest -DfailIfNoTests=false
```

### 12.5 遗留

| 项 | 说明 |
|---|---|
| 评论 / 消息 | 切片 3 剩余部分，复用本刀的会员会话与互动表风格 |
| 点赞防刷 | 当前一个会员对一篇文章只能点一次；未做频率限制 |
| 计数缓存 | 统计每次 `COUNT`，热门文章可加计数冗余或缓存 |
| 文章下线清理 | 收藏关系保留，由门户按批量摘要结果隐藏 |
| 批量上限 | 收藏页单页 6 条，批量接口上限 50 条 |
