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

# 公开只读：只返回已审核通过的评论
GET  /api/app/public/comments/articles/{articleId}?pageNum=&pageSize=
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

POST   /api/app/comments/articles/{articleId}
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

GET    /api/admin/member/comment/list
PUT    /api/admin/member/comment/{commentId}/audit
DELETE /api/admin/member/comment/{commentId}
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
| `platform:member:comment:list` | 评论列表 |
| `platform:member:comment:audit` | 通过 / 拒绝评论 |
| `platform:member:comment:remove` | 删除评论 |

菜单：`4000 member` → `4010 member:user`（`member/user/index`）→ `4011~4016` 按钮。
`4020 member:channel`（`member/channel/index`）→ `4021~4022` 按钮；
`4030 member:setting`（`member/setting/index`）→ `4031` 保存按钮，
权限沿用 `platform:system:config:list|edit`（编辑的仍是 `sys_config`）；
`4040 member:comment`（`member/comment/index`）→ `4041~4042` 审核 / 删除按钮。
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
| `mem.comment.enabled` | switch | true | 评论开关；关闭后门户评论读取 / 提交返回 1003，后台审核仍可用 |

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
- 评论先审后发：公开接口只返回 `status=1`，待审 / 拒绝不对外；评论内容按纯文本保存，
  门户用文本插值渲染，不二次 `v-html`。

---

## 8. 切片顺序

| 切片 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 表结构、独立 StpLogic、注册 / 登录 / 找回密码、个人中心、后台 CRUD | v1.2 第一刀，已完成 |
| 1.5 | 注册 / 登录 / 找回密码限流、验证码错误次数、短信 / 邮件通道接口与开发实现 | v1.2 第二刀，已完成 |
| 2 | 会员等级升级任务、积分 / 成长值规则 | 待排期 |
| 3 | 评论 / 收藏 / 点赞 / 消息 | 收藏 + 点赞已完成（2026-11-22）；评论 + 后台审核已完成（2026-11-22）；消息待排期 |
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

---

## 13. v1.2 第三刀（后半）：评论 + 后台审核（2026-11-22）

评论是会员侧第一个“先审后发”的写入。本轮同时补上评论运行期开关，
与已有会员总开关形成两级控制。

### 13.1 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `db/migration/member/V30__member_comment.sql` | `mem_comment`；`mem.comment.enabled`（configId 27）；菜单 4040~4042 |
| 实体 | `MemComment` | `member_id + article_id`、内容、审核状态 / 人 / 时间 / 备注、逻辑删除 |
| Mapper | `MemCommentMapper` | 原生 `BaseMapper` |
| 服务 | `MemberCommentService` | 发表默认待审、公开分页、后台筛选、审核、逻辑删除 |
| 会员接口 | `MemberCommentController` | 公开读 + 登录发表 |
| 后台接口 | `MemberCommentAdminController` | 列表 / 通过 / 拒绝 / 删除 |
| 开关 | `MemberCommentEnabledInterceptor` | 只拦 `/api/app/comments/**` 与 `/api/app/public/comments/**` |
| 后台页面 | `views/member/comment/index.vue`、`views/member/setting/index.vue` | 评论管理列表；评论设置分组 |
| 门户 | `pages/article/[id].vue`、`useMemberAuth`、`types/member.ts` | 评论区、请求封装、DTO |
| 单测 | `MemberCommentServiceTest` | 7 项 |

### 13.2 数据模型

`mem_comment` 关键字段：

| 字段 | 说明 |
|---|---|
| `comment_id` | 主键 |
| `article_id` / `member_id` | 只存 ID，不建跨模块外键 |
| `content` | 纯文本，最多 1000 字；门户按文本渲染 |
| `status` | `0` 待审核 / `1` 已通过 / `2` 已拒绝 |
| `audit_by` / `audit_time` / `audit_remark` | 审核人、时间、备注；备注不展示给会员 |
| `create_ip` | 提交 IP，用于排查与后续风控 |
| `del_flag` | 后台删除为逻辑删除，保留审核痕迹 |

索引：`(tenant_id, article_id, status, create_time)` 服务公开列表；
`(tenant_id, status, create_time)` 服务后台待审列表；
`(tenant_id, member_id, create_time)` 为后续“我的评论”预留。

### 13.3 关键决策

1. **先审后发，默认待审。**
   门户提交成功只提示“审核通过后展示”，不做乐观插入；公开列表只查 `status=1`。
   拒绝保留记录与备注，方便复核，也避免同一内容反复提交。
2. **评论开关只关门户。**
   关闭后 `/api/app/comments/**` 与 `/api/app/public/comments/**` 返回
   `1003 评论功能已关闭`；后台审核接口不挂该拦截器，仍可处理历史评论。
3. **开关放在会员设置页。**
   `mem.comment.enabled` 与其它 `mem.*` 参数同页，修改后立即生效；
   会员总开关仍在「系统管理 → 参数设置」，避免关闭后菜单隐藏导致无法再打开。
4. **公开接口不返回账号信息。**
   门户 DTO 不含 `memberId`、用户名、审核信息；后台 DTO 才带会员账号与备注。
5. **评论与文章解耦。**
   评论不查 CMS 表、不建外键。文章下线后评论仍在库中；是否展示由文章页是否存在
   决定，CMS 恢复发布后评论自然回来。
6. **删除只做逻辑删除。**
   已删除评论不再出现在后台列表，审核返回 `1004`，不会“复活”。

### 13.4 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -q -DskipTests package` 通过 |
| Flyway | `V30 - member comment` 成功，表、参数与菜单已落库 |
| 全仓单测 | **218 项 / 36 类**，失败 0 错误 0 |
| 评论单测 | `MemberCommentServiceTest` 7 项通过 |
| 接口验收 | `.local/member-comment-check.mjs` **25/25** |
| 门户构建 | `npm run build` 通过，文章页生成独立 chunk |
| 浏览器 | 门户发表 → 待审不公开 → 后台通过 / 拒绝 / 删除 → 门户可见性 → 设置页开关保存与关闭提示 |
| SSR | `.local/portal-comment-ssr.mjs` **6/6**：匿名 / 会员评论框、登录引导、已通过评论与昵称 |
| 控制台 | 无脚本错误 |

### 13.5 复验命令

```powershell
# 后端 8080（关闭验证码），门户 3000
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
node .local\member-comment-check.mjs
node .local\portal-comment-ssr.mjs

# 单测
mvn -q -pl lx-modules/lx-module-member -am test -Dtest=MemberCommentServiceTest -DfailIfNoTests=false
```

### 13.6 遗留

| 项 | 说明 |
|---|---|
| 消息 / 通知 | 评论通过 / 拒绝暂不通知会员，后续接站内信 |
| 回复 / 盖楼 | 当前一级评论，未做 `parent_id` |
| 敏感词 / 自动审核 | 仅人工审核，未接敏感词库 |
| 评论防刷 | 未做频率限制，可复用会员限流服务 |
| 我的评论 | 表已带会员索引，接口与页面留给后续切片 |

---

## 14. v1.2 第四刀：第三方登录（2026-11-22）

第三方登录覆盖 27 个平台，但默认全部停用。代码里先把适配器写完，
运营在后台按需填凭据、打开单平台开关，再打开会员设置里的总开关，
门户才会出现对应按钮。会员第三方账号与后台 `sys_user` / `sys_user_social`
完全隔离，不会因为登录门户获得任何后台权限。

### 14.1 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `V31__member_oauth.sql` | `mem_oauth_provider`、`mem_user_social`、`mem.oauth.*`（configId 28~30）、菜单 4050/4051 |
| 补充迁移 | `V32__member_oauth_wechat_mini.sql` | 微信小程序服务商种子，默认停用 |
| 实体 | `MemOAuthProvider` / `MemUserSocial` | 平台配置；会员绑定关系 |
| 服务 | `MemberOAuthConfigService` | 总开关 / 平台开关 / 凭据读写 / 门户可用列表 / 回调地址 |
| 服务 | `MemberOAuthAccountService` | 第三方登录、自动建号、绑定 / 解绑、绑定列表 |
| 编排 | `MemberOAuthService` | 授权地址、回调、一次性 ticket、token 交换、小程序登录 |
| 适配器 | `oauth/` 包 | `StandardOAuthProvider` + 微信 / 小程序 / QQ / 支付宝 / Apple 专用适配器 |
| 注册表 | `MemberOAuthProviderRegistry` | 27 个平台的端点、字段路径、scope 与分组 |
| 临时凭证 | `MemberOAuthStateStore` | Sa-Token DAO 存 state（10 分钟）与 ticket（2 分钟，一次有效） |
| 会员接口 | `MemberOAuthController` | `/api/app/auth/oauth/**` |
| 后台接口 | `MemberOAuthAdminController` | `/api/admin/member/oauth/**`，权限码 `platform:member:oauth:list|edit` |
| 后台页面 | `views/member/oauth/index.vue` | 国内 / 国外分组、开关、凭据与高级端点 |
| 设置页 | `views/member/setting/index.vue` | 第三方登录分组：总开关 / 自动建号 / 门户地址 |
| 门户 | `MemberOAuthButtons.vue`、`pages/oauth/callback.vue`、`pages/account.vue` | 登录按钮、回调换票、绑定管理 |
| 单测 | `MemberOAuthStateStoreTest` / `MemberOAuthServiceTest` | 4 + 10 项 |

### 14.2 平台清单

| 分组 | 平台 |
|---|---|
| 国内 | 微信开放平台、微信公众号、微信小程序、QQ、新浪微博、支付宝、抖音、钉钉、Gitee |
| 国外 | GitHub、GitLab、Google、Microsoft、Apple、Facebook、X (Twitter)、LinkedIn、Discord、LINE、Kakao、Naver、Amazon、Yahoo、Reddit、Slack、Twitch、Spotify |

- 微信开放平台 / 公众号 / QQ / 支付宝 / Apple 流程不标准，使用独立适配器；
- 其余平台用 `MemberOAuthProviderSpec` 描述端点与字段路径，统一走
  `StandardOAuthProvider`，新增平台只需要加一条描述；
- X 使用 PKCE（`code_verifier` 存在服务端 state 里）；Reddit / Yahoo /
  Spotify 等使用 Token Basic Auth；
- 微信小程序没有网页授权入口：门户不展示按钮，客户端调用 `wx.login()` 后
  把 code 提交到 `POST /api/app/auth/oauth/mini-login`，普通 OAuth 平台
  不允许走这个入口，避免绕过 state。

### 14.3 登录流程

```text
门户 → GET /api/app/auth/oauth/authorize-url?provider=github&redirect=/account
     ← 授权地址（服务端生成 state，10 分钟）
浏览器 → 平台授权页
平台 → GET /api/app/auth/oauth/callback/github?code&state
服务端 → 消费 state → 换用户信息
       ├─ 已绑定：登录会员
       ├─ 未绑定 + autoRegister=true：自动建号并绑定
       └─ 未绑定 + autoRegister=false：not_bound
     → 302 /oauth/callback?ticket=一次性凭证
门户 → POST /api/app/auth/oauth/exchange { ticket }
     ← 会员 Token（写入门户 Cookie）
```

- 会员 Token 从不出现在 URL、回调参数或第三方平台；
- ticket 只存会员 ID 与来源平台，2 分钟过期且只能换一次；
- 绑定模式：已登录会员从个人中心发起，`mode=bind`，会员 ID 只从服务端
  state 读取，不接受 URL / 请求体传入；
- 解绑只删 `mem_user_social`，不影响会员密码登录。

### 14.4 数据模型

`mem_oauth_provider`（主键 `tenant_id + provider`）：

| 字段 | 说明 |
|---|---|
| `provider` | 稳定编码，例如 `wechat_open` / `github` |
| `status` | `0` 启用 / `1` 停用，默认全部停用 |
| `client_id` / `client_secret` | 平台凭据；Secret 只写不读 |
| `redirect_uri` | 留空按 `mem.oauth.portalBaseUrl` 自动生成 |
| `scope` | 留空使用内置默认值 |
| `authorize_url` / `token_url` / `user_info_url` | 可覆写内置端点 |
| `extra_json` | 预留平台差异参数（Apple 团队 ID / Key ID 等） |
| `sort` / `remark` | 排序与备注 |

`mem_user_social`：

| 字段 | 说明 |
|---|---|
| `member_id` | 只关联 `mem_user.member_id`，不建跨模块外键 |
| `provider` / `open_id` / `union_id` | 平台身份；`(provider, open_id)` 唯一 |
| `nickname` / `avatar` | 授权快照，仅用于展示兜底 |
| `raw_data` | 原始 JSON，排障用，接口不下发 |
| `last_login_time` | 最近一次第三方登录时间 |

### 14.5 安全与边界

1. **会员域与平台域物理隔离。** 只写 `mem_user` / `mem_user_social`，
   不触碰 `sys_user` / `sys_user_role`；会员 Token 与后台 Token 互不识别。
2. **未绑定且关闭自动建号时明确报错。** 返回 `1001 not_bound`，提示先登录再绑定，
   不会静默创建一个空账号。
3. **自动建号只复制“平台确认已验证”的邮箱 / 手机号（2026-11-23 补齐）。**
   未验证的联系方式不落库，避免占用唯一键挡住会员自行注册；微信 / QQ 通常
   不返回已验证手机号，因此不参与匹配。
4. **头像同时接受 http/https 或数字 fileId，并优先本地化（2026-11-23 补齐）。**
   第三方头像先下载转存文件中心，存 `fileId`；`mem_user_social.avatar` 保留
   原始外链仅排障。下载失败回退外链，门户 `<img>` 统一加
   `referrerpolicy="no-referrer"`。登录时不覆盖会员自己改过的头像。
5. **state / ticket 一次性。** 回调重放、伪造 state、过期 ticket 都有对应错误码；
   门户按短码翻译，不展示上游错误原文。
6. **总开关 + 单平台开关双层控制。** 总开关关闭时门户不展示任何入口，接口返回
   `1003`；平台未配置 ClientId / Secret 时即使启用也不出现在门户。
7. **绑定 / 解绑需要会员登录。** 绑定列表与解绑由会员拦截器保护；
   发起绑定虽然走公开的 `authorize-url`，但 `mode=bind` 在 Service 内校验登录态。

### 14.6 验证结果

| 项 | 结果 |
|---|---|
| 编译 / 打包 | `mvn -o -DskipTests package` 通过 |
| Flyway | V31、V32 成功，27 个平台、参数与菜单已落库 |
| 全仓单测 | **232 项 / 38 类**，失败 0 错误 0 |
| OAuth 单测 | `MemberOAuthStateStoreTest` 4 项 + `MemberOAuthServiceTest` 10 项 |
| 接口验收 | `.local/member-oauth-check.mjs` **37/37** |
| 后台构建 | `npm run build` 通过 |
| 门户构建 | `npm run build` 通过 |
| 浏览器 | 后台平台列表 / 分组 / 小程序标记 / 配置弹窗；门户登录按钮、回调错误页、账户绑定区、总开关关闭后隐藏 |

### 14.7 复验命令

```powershell
# 后端 8080（关闭验证码），门户 3000
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
node .local\member-oauth-check.mjs

# 单测
mvn -o -pl lx-modules/lx-module-member -am test `
  "-Dtest=MemberOAuthStateStoreTest,MemberOAuthServiceTest" -DfailIfNoTests=false
```

### 14.8 遗留

| 项 | 说明 |
|---|---|
| 真实平台联调 | 适配器与端点已就绪，需在各平台后台登记回调地址并填真实凭据 |
| 小程序端页面 | 后端 `mini-login` 已就绪，小程序页面由小程序工程实现 |
| 第三方头像转存 | 已补齐，见 §15.2 `MemberAvatarLocalizer` |
| 账号合并 | 已补齐后台合并工具，见 §15.4；不自动合并 |
| 解绑前校验 | 已补齐：无密码且解绑后无其它登录方式时禁止解绑 |

---

## 15. v1.2 第五刀：头像本地化、唯一性、绑定与合并（2026-11-23）

在第四刀“27 平台第三方登录”基础上补齐四件事：第三方头像落本地、
账号唯一约束与登录标识分流、同邮箱 / 手机号的验证后绑定流程、后台账号合并工具。
仍然坚持“会员域与后台域物理隔离”：不触碰 `sys_user`，会员 Token 与后台 Token
互不识别。

### 15.1 迁移与数据模型

| 迁移 | 内容 |
|---|---|
| `V33__member_account_uniqueness.sql` | `mem_user.password_set`、用户名 / 手机号 / 邮箱生成列 + 唯一键、`mem_user_social` 联系方式快照与 `(tenant_id, member_id, provider)` 唯一键、`mem_merge_log`、合并按钮权限 4017 |

- 唯一键用生成列排除逻辑删除行：
  `username_key = IF(del_flag='0', username, NULL)`，手机号 / 邮箱再加非空判断。
  MySQL 唯一索引允许多个 `NULL`，所以删除后的用户名可以重新注册。
- `password_set`：`0` 未设置密码（第三方自动建号）/ `1` 已设置；老数据按
  `register_channel` 回填。
- `mem_merge_log`：谁把哪个旧账号并入哪个目标账号、迁移 / 跳过了多少条数据、
  操作备注与时间，只追加不修改。

### 15.2 头像本地化

| 层 | 文件 | 说明 |
|---|---|---|
| 文件中心 | `FileService.uploadBytes(...)` | 从 `upload(MultipartFile)` 抽出字节入库能力，供头像/后台任务复用 |
| 会员 | `MemberAvatarLocalizer` | 下载第三方头像并转存文件中心，写 `fileId` |
| 依赖 | `lx-module-member/pom.xml` | 新增 `lx-module-file`，用 `ObjectProvider<FileService>` 允许文件模块关闭 |

安全边界：

- 只允许 `http(s)`，禁止内网 / 回环 / 链路本地 / 私网 / 带 userInfo 的地址；
  重定向同样逐跳校验，最多 3 次；
- 连接 / 读取各 3 秒，最大 2 MB；
- 只接受 JPEG / PNG / WebP，按文件头识别，拒绝 SVG / HTML；
- 下载或转存失败只记日志并回退原始外链，不影响第三方登录主流程；
- 门户 `<img>` 统一加 `referrerpolicy="no-referrer"` 兜底历史外链。

### 15.3 唯一性与登录标识分流

- `(tenant_id, username)`、`(tenant_id, mobile)`、`(tenant_id, email)` 三组
  唯一键由数据库兜底；写入前仍做友好校验，并把 `DuplicateKeyException`
  翻成具体字段错误（用户名 / 手机号 / 邮箱已存在）。
- 登录按输入形态只查一个字段：含 `@` 只查邮箱；纯数字 / 带前导 `+` 只查手机号；
  其它只查用户名。
- 用户名注册时禁止包含 `@` 或全部为数字，避免出现“这个账号永远无法用用户名登录”。
- 第三方自动建号若拿到已验证联系方式则写入并占用唯一键，否则留空；
  未设密码账号密码登录返回 `2006`，并引导先设置密码。
- `PUT /api/app/profile/password/set` 只允许 `password_set='0'` 的账号调用；
  已设置密码必须走“修改密码”并校验原密码。

### 15.4 同邮箱 / 手机号的绑定与合并

第三方登录命中已有会员时**不静默自动登录**，而是返回一次性 `bindTicket`：

```text
第三方回调 → 已验证邮箱 / 手机号命中已有会员
          → 302 /login?oauthBindTicket=...
门户      → 用户先登录原账号
          → POST /api/app/auth/oauth/bind-ticket { ticket }
          → 校验当前会员 == ticket.targetMemberId → 写入绑定
```

- `bindTicket` 2 分钟有效且只能用一次，存 provider / openId / targetMemberId /
  `OAuthUserInfo`；
- 同一第三方 openId 已绑定其它会员时明确报错；
- 解绑前若会员没有密码且这是最后登录方式，禁止解绑，避免把自己锁在门外；
- 历史重复账号不自动合并。后台提供受权限保护、事务化、可审计的合并工具：

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /api/admin/member/merge/preview` | `platform:member:user:merge` | 只读统计收藏 / 点赞 / 评论 / 绑定的迁移与去重数量 |
| `POST /api/admin/member/merge` | `platform:member:user:merge` | 事务内迁移并停用旧账号 |

合并语义：

- 目标账号必须是启用状态，且不能与旧账号相同；
- 收藏 / 点赞按 `(member_id, article_id)` 去重，重叠部分跳过；
- 评论直接改归属；
- 第三方绑定按平台去重，目标已绑定的平台保留目标账号、删除旧账号那条；
- 完成后旧账号 `status='1'`、备注写明合并去向，`logoutAll` 踢掉全部会话；
- 写 `mem_merge_log`，计数与操作人可追溯；不物理删除旧账号。

### 15.5 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `V33__member_account_uniqueness.sql` | 唯一键、密码标记、合并审计、菜单 4017 |
| 会员服务 | `MemberUserService` | 登录分流、唯一性、`passwordSet`、首次设置密码 |
| 头像 | `MemberAvatarLocalizer` | 下载 / 类型 / 大小 / SSRF / 重定向限制 |
| 文件服务 | `FileService.uploadBytes` | 字节上传与统一存储 |
| 第三方 | `MemberOAuthAccountService` / `MemberOAuthService` | 已验证联系方式匹配、bindTicket、解绑保护 |
| 合并 | `MemberMergeService` | 预览、事务迁移、去重、停用、踢会话、审计 |
| 后台接口 | `MemberAdminController` | `/api/admin/member/merge/**` |
| 后台页面 | `views/member/user/index.vue` | 合并入口、目标账号搜索、预览表、确认 |
| 门户 | `login.vue` / `account.vue` / `useMemberAuth.ts` | bindTicket 自动绑定、设置密码、`referrerpolicy` |
| 单测 | `MemberMergeServiceTest` / `MemberOAuthAccountServiceTest` / `MemberAvatarLocalizerTest` / `MemberUserServiceTest` / `MemberOAuthStateStoreTest` | 新增 17 项 |

### 15.6 验证结果（2026-11-23）

| 项 | 结果 |
|---|---|
| Flyway | V33 成功，数据库到 v33；27 个平台、菜单 4017、唯一键与 `mem_merge_log` 已落库 |
| 全仓单测 | **244 项**，失败 0 错误 0 |
| 合并接口 | `.local/member-merge-check.mjs` **24/24**：预览计数、去重迁移、旧账号停用、会话失效、审计落库 |
| 密码流程 | `.local/member-password-set-check.mjs` **10/10**：2006、首次设置、旧密码失效、重复设置被拒 |
| 后台构建 | `npm run build` 通过 |
| 门户构建 | `npm run build` 通过 |
| 浏览器 | 关闭时门户隐藏第三方按钮；开启 GitHub 后登录页出现按钮、账户页出现“绑定其它第三方账号”；`password_set=0` 显示设置密码区块；回调票据无效显示明确错误页，无白屏 |

### 15.7 复验命令

```powershell
# 后端 8080（关闭验证码），门户 3000
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
node .local\member-merge-check.mjs
node .local\member-password-set-check.mjs

# 全仓单测
mvn -o test
```

### 15.8 遗留

| 项 | 说明 |
|---|---|
| 真实第三方联调 | 需要真实 AppID / Secret 与平台回调白名单；本地只用假凭据验证按钮与授权地址 |
| 合并撤销 | `mem_merge_log` 留了审计，但不提供一键回滚；需要时按日志人工恢复 |
| 通知手机号 / 邮箱绑定 | 目前只允许已存在的联系方式匹配，未开放门户内换绑验证 |
