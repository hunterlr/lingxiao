# 28 · 第三方登录对接设计（预留）

> 状态：schema 已预留（`V19__system_user_social.sql`），Provider 实现未开工。
> 本文只定边界与数据流，避免接入时把字段硬塞进 `sys_user`，或把会员登录
> 与后台登录混成一套 Token。

---

## 1. 结论先说

| 事项 | 决定 |
|---|---|
| 后台用户头像 | 复用 `sys_user.avatar`，值兼容 `fileId` 与 `http(s)` 外链 |
| 第三方绑定 | 独立表 `sys_user_social`，一个后台账号可绑多个平台 |
| 登录域 | 后台第三方登录仍发 `platform` 域 Token；会员第三方登录走 `mem_*` + 独立 Token |
| 首次建号 | 默认“先绑定后登录”，不自动创建后台账号（后台账号涉及权限，不能自助产生） |
| 头像落库 | 优先转存文件中心拿 `fileId`；下载失败再保留平台 URL，不阻塞登录 |
| 密码 | 第三方账号也要有密码策略：可登录但未设密码的账号禁止密码登录 |

---

## 2. 表结构

`V19__system_user_social.sql`：

| 字段 | 说明 |
|---|---|
| `social_id` | 主键 |
| `user_id` | 关联 `sys_user.user_id`，不建外键 |
| `provider` | `wechat_mp` / `wechat_open` / `qq` / `github` / `gitee` / `dingtalk` |
| `open_id` | 平台内唯一标识 |
| `union_id` | 跨应用统一标识（微信等提供） |
| `nickname` / `avatar` | 首次授权时的快照，只用于建号/展示兜底 |
| `raw_data` | 平台原始 JSON，排障用，接口不下发 |
| `last_login_time` | 最近一次第三方登录时间 |

约束：

- `UNIQUE (provider, open_id)`：同一第三方账号只能绑一个后台用户；
- `INDEX (user_id)`：查某用户绑定了哪些平台；
- `INDEX (union_id)`：同平台多应用时按 union_id 归并。

---

## 3. 登录流程

```text
浏览器 → GET /api/admin/auth/oauth/{provider}/authorize
       ← 302 到平台授权页（带 state，state 存 Caffeine/Redis，5 分钟有效）
平台   → GET /api/admin/auth/oauth/{provider}/callback?code&state
服务端 → 校验 state → 换 access_token → 拉用户信息（openId / unionId / 昵称 / 头像）
       → 查 sys_user_social(provider, open_id)
           ├─ 已绑定：取 user_id，走现有登录逻辑发 platform Token
           ├─ 未绑定 + 已登录：进入“绑定”流程，写 sys_user_social
           └─ 未绑定 + 未登录：拒绝，提示先登录后台再绑定
```

**不自动建号**：后台账号关联角色、部门、数据范围，第三方授权只能证明
“这个人是谁”，不能证明“他该有哪些权限”。需要开放注册时，走邀请码 /
管理员审批，再绑定。

### 3.1 回调后的会话

- 继续用 `AuthService` 的统一登录入口：校验账号状态 → 记录登录日志 →
  按 `sys.account.multiDeviceLogin` 决定是否踢旧 → 发 `platform` Token；
- 必须写 `sys_login_log`，`login_type` 可扩展为 `oauth:<provider>`；
- 解绑：删 `sys_user_social` 行，同时注销该用户所有 Token（与改密同规则）。

---

## 4. 头像处理

平台返回的头像 URL 有两种情况：

1. **可下载**：服务端下载并调用文件中心 `FileService.upload(bytes, "avatar")`，
   把 `fileId` 写入 `sys_user.avatar`。优点是统一存储、可清理、无外链失效；
2. **不可下载 / 防盗链**：保留 `http(s)` URL。`sys_user.avatar` 与前端
   `avatarSrc()` 已兼容两种形态，不需要额外字段。

约束：

- 只接受 `http/https` 与纯数字 `fileId`，其它协议（`javascript:` / `data:`）
  一律拒绝，避免 `<img src>` 注入；
- 头像大小走文件中心统一上限，建议第三方头像下载限制 2 MB；
- 用户之后在个人中心改过头像，以 `sys_user.avatar` 为准，第三方登录
  **不回写覆盖**（避免每次登录把用户自己换的头像冲掉）。

---

## 5. 配置项（实现时再加，不在本轮空转）

等 Provider 落地时，在 `sys_config` 或 `application.yml` 增加并按需读取：

| key | 说明 |
|---|---|
| `sys.oauth.wechat_mp.enabled` | 是否启用公众号登录 |
| `sys.oauth.<provider>.clientId` | AppID |
| `sys.oauth.<provider>.clientSecret` | AppSecret（只走环境变量，不入库明文） |
| `sys.oauth.<provider>.redirectUri` | 回调地址，必须与平台配置一致 |

**规则**：没有实现的 Provider 不提前写配置项，避免“参数页有开关、点了没反应”。
密钥统一走环境变量 / 密钥管理，`sys_config` 只存非敏感项。

---

## 6. 与会员域的关系

| | 后台 | 会员 |
|---|---|---|
| 用户表 | `sys_user` | `mem_user`（v1.2） |
| 绑定表 | `sys_user_social` | `mem_user_social` |
| Token | `lx-platform-token` | 独立 Token |
| 建号 | 管理员 / 邀请 | 可自助注册 |
| 权限 | RBAC + 数据范围 | 会员等级 / 内容可见性 |

两边表结构可以相似，但**不共用表、不共用拦截器、不共用 Token**。
后台第三方登录先做，会员域复用 OAuth 协议代码时只共用工具类，不共用数据。

---

## 7. 本轮已落地 / 未落地

已落地：

- `sys_user.avatar` 可自助上传 / 清除（个人中心），兼容 `fileId` 与外链；
- `POST /api/admin/file/avatar`：登录即可上传，限制图片扩展名，不走文件中心权限；
- `ProfileUpdateRequest.avatar` 校验长度与非白名单协议；
- `V19__system_user_social.sql` 建表。

未落地（等排期）：

- OAuth Provider 实现与回调接口；
- state 存储（Redis 开启时用 Redis，否则 Caffeine）；
- 第三方头像转存文件中心；
- 登录页第三方按钮与绑定管理页。
