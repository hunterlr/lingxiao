# lx-module-member · 会员模块

## 这个目录是做什么的

前台会员体系：注册、登录、找回密码、个人中心，以及后台会员管理。
会员与平台后台**物理隔离**——独立账号表 `mem_user`、独立 Token 名
`lx-member-token`、独立 StpLogic（`member` 域），两边账号不能互相登录。

**v1.2 第一刀已实装**（2026-11-22）：

- 会员注册（图形验证码、协议勾选、手机号 / 邮箱按参数选填、默认等级）；
- 会员登录 / 登出（用户名 / 手机号 / 邮箱三选一，失败计数落库、达到阈值锁定）；
- 找回密码（验证码只存 BCrypt 哈希、一次性、过期作废、用完踢掉全部会话）；
- 个人中心（资料查看 / 修改昵称头像性别 / 自助改密）；
- 后台会员管理（分页搜索、新增、编辑、启停、重置密码、逻辑删除）；
- 多端登录策略参数 `mem.account.multiDeviceLogin`，默认关闭（新登录踢旧会话）；
- 会员头像只存 `fileId` 或外链，复用文件中心，不建 `mem_file`；
- 会员参数、菜单与按钮权限随模块开关一起上线 / 下线。

**明确未做（后续切片）**：

- 会员等级升级任务 / 积分规则 / 成长值衰减（表与字段已预留）；
- 评论、收藏、点赞、消息通知；
- 第三方登录绑定（`mem_oauth` 预留）；
- 真实短信 / 邮件通道：当前 `LogMemberResetCodeSender` 只写应用日志，
  生产上线前必须替换；
- 会员角色 / 细粒度权限（当前只有登录态，没有会员权限码）。

## 模块开关

```yaml
lx:
  module:
    modules:
      member:
        enabled: true
```

- 启动期生效，改完必须重启；不是运行期热插拔。
- `false`：`mem_*` 迁移不扫描、Controller 不装配（`/api/app/**`、
  `/api/admin/member/**` 返回 404）、会员菜单与权限码不下发；
  **已建表与数据保留**。
- 迁移目录固定为 `db/migration/member`，目录名必须等于模块编码 `member`。

## 两套安全域

| 项目 | 平台后台 | 前台会员 |
| --- | --- | --- |
| 账号表 | `sys_user` | `mem_user` |
| Token 名 | `lx-platform-token` | `lx-member-token` |
| StpLogic 类型 | `platform` | `member` |
| 拦截路径 | `/api/admin/**` | `/api/app/**` |
| 有效期 | 全局配置 | 7 天，2 小时不活跃失效 |
| 权限模型 | 角色 + 菜单权限码 | v1.2 只有登录态 |

会员 Token 访问后台接口、后台 Token 访问会员接口都会 401；两套
`StpLogic` 只共享底层存储，不共享 Session 命名空间。

## 接口

### 会员端（无需登录，除登出）

```text
GET  /api/app/auth/captcha?scene=login|register|reset
POST /api/app/auth/register
POST /api/app/auth/login
POST /api/app/auth/logout
POST /api/app/auth/forgot-password
POST /api/app/auth/reset-password
```

### 会员端（必须登录）

```text
GET  /api/app/profile
PUT  /api/app/profile
PUT  /api/app/profile/password
```

### 后台（平台域 + `platform:member:user:*`）

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

## 包结构

```text
com.lingxiao.modules.member
├─ config/
│  └─ MemberSaTokenConfigure   独立 Token 配置 + /api/app/** 拦截器与白名单
├─ controller/
│  ├─ MemberAuthController     注册 / 登录 / 登出 / 验证码 / 找回密码
│  ├─ MemberProfileController  个人中心资料与改密
│  └─ MemberAdminController    后台会员列表与维护
├─ dto/                        请求 / 响应对象（含后台与会员端两套重置密码请求）
├─ entity/                     MemUser / MemLevel / MemPasswordReset
├─ mapper/                     MyBatis-Plus Mapper
├─ security/
│  ├─ MemberLoginHelper        会员 Session 读写（与平台 LoginHelper 分离）
│  └─ MemberPasswordEncoder    模块自带 BCrypt
├─ service/
│  ├─ MemberUserService        会员端 + 后台全部账号逻辑
│  ├─ MemberLevelService       等级默认值 / 选项 / 名称映射
│  ├─ MemberCaptchaService     会员验证码（SaTokenDao 存储，按场景隔离）
│  ├─ MemberPasswordPolicy     密码复杂度策略（mem.security.passwordPolicy）
│  ├─ MemberTokenIssuer        Token 签发与多端登录策略
│  └─ LogMemberResetCodeSender 开发环境验证码下发（只写日志）
└─ resources/db/migration/member/
   └─ V25__member_schema.sql   mem_* 表、参数 11~18、菜单 4000 段与授权
```

## 参数

全部在「系统管理 → 参数设置」里，键名前缀 `mem.`：

| 键 | 控件 | 默认 | 用途 |
| --- | --- | --- | --- |
| `mem.security.captchaEnabled` | switch | true | 会员验证码开关 |
| `mem.security.passwordPolicy` | select | basic | 会员密码复杂度 |
| `mem.account.maxRetryCount` | number | 5 | 登录失败锁定阈值 |
| `mem.account.lockMinutes` | number | 10 | 锁定时长（分钟） |
| `mem.account.multiDeviceLogin` | switch | false | 是否允许多端登录 |
| `mem.register.mobileRequired` | switch | false | 注册手机号是否必填 |
| `mem.register.emailRequired` | switch | false | 注册邮箱是否必填 |
| `mem.register.resetCodeExpireMinutes` | number | 10 | 找回密码验证码有效期 |

所有键都由会员模块读取；删除参数会让模块回退代码里的默认值。

## 数据约定

- `tenant_id` 单租户部署固定 0，所有查询都带租户条件，给后续 SaaS 化留位。
- 用户名 / 手机号 / 邮箱**不建数据库唯一键**：逻辑删除后要允许同名重新注册，
  带 `del_flag` 的唯一键会在两条已删除记录上互相冲突；唯一性由应用层校验。
- 删除会员是逻辑删除（`del_flag=1`）：列表、登录查询与唯一性校验都会忽略它，
  用户名 / 手机号 / 邮箱可以重新注册；历史业务数据仍指向原 `member_id`。
- 密码只存 BCrypt 哈希；`MemUser.password` 标注 `WRITE_ONLY`，列表和详情不会返回。
- 找回密码验证码只存 BCrypt 哈希，一次性使用，过期或使用后立即失效。
