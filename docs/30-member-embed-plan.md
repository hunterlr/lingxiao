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
```

### 4.2 会员端（必须登录）

```text
GET  /api/app/profile
PUT  /api/app/profile
PUT  /api/app/profile/password
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
超管角色在迁移里自动授权。

---

## 6. 参数

全部在「系统管理 → 参数设置」，键名前缀 `mem.`：

| 键 | 控件 | 默认 | 用途 |
| --- | --- | --- | --- |
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
| 3 | 评论 / 收藏 / 点赞 / 消息 | 待排期 |
| 4 | 第三方登录绑定（`mem_oauth`） | 待排期 |
| 5 | 会员角色与细粒度权限 | 待排期 |

**第二刀后仍未做**：真实短信 / 邮件服务商实现（通道默认 `mode=dev` 只写日志，
后台「会员管理 → 渠道配置」可运行期切换，但生产仍需先实现
`MemberSmsSender` / `MemberEmailSender`）、等级自动升级、积分规则、内容级多语言。

---

## 9. 验收基线

- 单测：会员模块共 49 项 —— `MemberUserServiceTest` 19 项、
  `MemberPasswordPolicyTest` 5 项、`MemberCaptchaServiceTest` 7 项、
  `MemberRateLimitServiceTest` 4 项、`RoutingMemberResetCodeSenderTest` 5 项、
  `MemberChannelConfigServiceTest` 9 项。
- 全仓：**205 项 / 34 类**，JDK 8 下失败 0 错误 0。
- 接口：`.local/member-check.mjs` **69 项全部通过**（域隔离、注册登录、找回密码、
  短信 / 邮件开发通道、渠道配置、锁定、启停、删除、菜单下发、验证码开关、
  注册 / 登录 / 找回 / 重置限流）。
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
