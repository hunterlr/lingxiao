# lx-common-security · 认证与安全域

## 这个目录是做什么的

Sa-Token 的**平台后台域**实现，以及登录上下文、鉴权异常处理、拦截器配置。

## 三套安全域（物理隔离）

| 域 | 标识 | 状态 | 说明 |
|---|---|---|---|
| 平台后台 | `platform` | ✅ v1.0 实装 | 本目录 |
| 租户应用 | `tenant` | ⬜ 后续 | 独立立项后再建 |
| 前台会员 | `member` | ⬜ v1.2 | 模块嵌入 |

三套域**各自持有独立 `StpLogic`**，Token 互不认。平台 Token 拿去访问会员接口必须 401。

## 包结构

```
com.lingxiao.common.security
├─ util/StpPlatformUtil          平台域 Token 唯一入口
├─ util/LoginHelper              从 Session 读当前用户
├─ annotation/RequiresPermission 平台域权限码注解（固定 platform 域）
├─ aspect/PermissionAspect       先校验登录、再校验权限码
├─ config/SaTokenConfigure       拦截 /api/admin/**，放行登录/验证码/demo
└─ handler/SaTokenExceptionHandler  401 / 403（优先级最高）
```

## 接口鉴权用法

```java
@RequiresPermission("platform:system:user:add")
@PostMapping
public R<Long> add(@Valid @RequestBody SysUser user) { ... }
```

- 未登录 → `401 / 1002`；
- 已登录无权限 → `403 / 1003`；
- 超管 `*:*:*` 由 Sa-Token 通配匹配放行。

**为什么不用 `@SaCheckPermission`**：它默认 `type = "login"` 域，后台每个方法都要显式写 `type = "platform"`，漏写就校验错域。`@RequiresPermission` 把域固定死。

## 为什么不用 `StpUtil`

`StpUtil` 是 Sa-Token 默认域（登录类型 `login`），三套域共用它会互相顶掉 Token。因此：

- 后台代码**只允许**通过 `StpPlatformUtil` 取登录态；
- 禁止在 Controller / Service 里直接 `import cn.dev33.satoken.stp.StpUtil`。

## 放行路径

```
/api/admin/auth/login
/api/admin/auth/captcha
/api/admin/demo/**      ← 仅用于验证模块关闭态，生产不打包
```

## Token 配置（application.yml）

```yaml
sa-token:
  token-name: lx-platform-token   # 请求头名称
  timeout: 43200                  # 12 小时
  is-concurrent: true             # 允许同账号多端
  is-share: false                 # 每次登录发新 Token
  is-read-cookie: false           # 只读请求头，减少 CSRF 面
```

## 禁止事项

- ❌ 直接使用 `StpUtil`。
- ❌ 用 `@SaCheckPermission`（默认 `login` 域），统一用 `@RequiresPermission`。
- ❌ 修改 `StpPlatformUtil.TYPE`（等于让所有已发 Token 失效）。
- ❌ 在 `StpInterfaceImpl` 里忽略 `loginType` 判断（会导致串域拿到后台权限）。
- ❌ 在会员 / 租户接口复用本拦截器。

## 验证方式

```powershell
# 不带 Token
Invoke-WebRequest http://127.0.0.1:8080/api/admin/auth/info   # → 401 / code 1002
```
