# lx-admin-api · 后台接口契约

## 这个目录是做什么的

后台接口的请求 / 响应模型，供 `lx-module-system` 等模块共享。

## 当前模型

| 类 | 用途 | 关键约束 |
|---|---|---|
| `LoginRequest` | 登录请求 | 用户名 / 密码 / 验证码标识 / 验证码均非空 |
| `LoginResponse` | 登录响应 | `tokenName`、`tokenValue`、`expiresIn` |
| `UserInfoResponse` | 当前用户 | 基础资料 + `roles` + `permissions` |
| `MenuVO` | 菜单树节点 | 只含目录（M）与菜单（C），按钮（F）不进侧边栏 |
| `CaptchaResponse` | 验证码 | `image` 是 data URI，前端直接放 `img.src` |

## 约定

1. **Token 只走响应体**，不塞 URL（避免被日志 / Referer 泄露）。
2. **Long 字段序列化为字符串**（全局 Jackson 规则），前端按字符串接收。
3. 权限码格式：`platform:system:user:list`；超管返回 `*:*:*`。
4. 菜单树的 `children` 初始为空列表，不用 `null`（前端少一层判空）。

## 禁止事项

- ❌ 在响应模型里放 `password`、`salt`、`hash`、存储路径。
- ❌ 直接把数据库实体当接口模型返回。
- ❌ 在这里加 Spring Web 注解（`@RestController` 等）。
- ❌ 为了让某个页面方便，往 `LoginResponse` 里塞菜单 / 权限（应各自独立接口）。
