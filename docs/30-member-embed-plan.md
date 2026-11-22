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

删除参数时模块回退代码里的默认值，不会出现空指针。

---

## 7. 安全约束

- 密码只存 BCrypt 哈希；响应体不返回密码字段。
- 登录失败计数落库（`login_fail_count` / `lock_time`），锁定期内不再比对密码。
- 验证码存 Sa-Token DAO，按场景（login / register / reset）隔离，一次性使用。
- 找回密码验证码只存 BCrypt 哈希；账号不存在时静默成功，不暴露账号是否注册。
- 停用 / 删除 / 重置密码都会踢掉该会员全部会话。
- 多端登录默认关闭（新登录踢旧会话）；开启后同一账号可多端并存。
- 会员验证码受总闸 `lx.captcha.enabled` 与参数 `mem.security.captchaEnabled` 双重控制。

---

## 8. 切片顺序

| 切片 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 表结构、独立 StpLogic、注册 / 登录 / 找回密码、个人中心、后台 CRUD | v1.2 第一刀，已完成 |
| 2 | 会员等级升级任务、积分 / 成长值规则 | 待排期 |
| 3 | 评论 / 收藏 / 点赞 / 消息 | 待排期 |
| 4 | 第三方登录绑定（`mem_oauth`） | 待排期 |
| 5 | 会员角色与细粒度权限 | 待排期 |

**第一刀明确未做**：真实短信 / 邮件通道（当前 `LogMemberResetCodeSender` 只写日志）、
等级自动升级、积分规则、内容级多语言。

---

## 9. 验收基线

- 单测：`MemberUserServiceTest` 15 项、`MemberPasswordPolicyTest` 5 项、
  `MemberCaptchaServiceTest` 6 项，共 26 项。
- 接口：`.local/member-check.mjs` 46 项全部通过（域隔离、注册登录、找回密码、
  锁定、启停、删除、菜单下发、验证码开关）。
- 浏览器：`.local/uitest/member.mjs` 12 项全部通过（列表、筛选、编辑、启停、
  重置密码、删除、无前端报错）。
- 关闭态：`.local/member-off-check.mjs` 7 项全部通过。`member.enabled=false` 重启后
  `/api/app/**` 与 `/api/admin/member/**` 返回 404，会员菜单不下发，平台后台与 CMS 正常，
  已建表与数据保留。
