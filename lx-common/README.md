# lx-common · 通用能力聚合

## 这个目录是做什么的

放**与业务无关、换任何后台项目都能用**的代码。子模块如下：

| 子模块 | 职责 | 是否加入 boot |
|---|---|---|
| `lx-common-core` | 统一响应、错误码、业务异常、常量、分页 | ✅ |
| `lx-common-web` | Web 配置、全局异常、Jackson、CORS、请求工具 | ✅ |
| `lx-common-security` | Sa-Token 平台域、登录上下文、鉴权异常 | ✅ |
| `lx-common-mybatis` | MyBatis-Plus 插件、审计填充、`BaseEntity` | ✅ |
| `lx-common-redis` | `RedisTemplate` 配置 | ❌ v1.0 可选 |
| `lx-common-log` | `@Log` 注解与切面，落库由 `OperLogStore` SPI 交给业务模块 | ✅ |

## 依赖方向（严格单向）

```
core  ←  web
  ↑      ↑
  └── security
  ↑
mybatis      redis      log(→ security + web)
```

- `core` 不依赖 Spring Web、不依赖数据库、不依赖 Sa-Token。
- `mybatis` 不依赖 `security`（否则审计填充要反向依赖登录态）。
- `log` 依赖 `security`（需要取当前用户名）与 `web`（需要取 IP）。

## 数据边界

**没有数据表**。本目录不写任何 Mapper、不写任何 SQL。

## 禁止事项

- ❌ 放 CMS / 会员 / 文件 / 系统管理等业务代码。
- ❌ 在 `core` 里引入 Spring Web 或 MyBatis（会污染所有引用方）。
- ❌ 在 `common` 里写 `@RestController`。
- ❌ 把 Redis 设为强制依赖（本机没有 Redis 时应用必须能启动）。

## 关键类索引

- `com.lingxiao.common.core.domain.R` — 所有接口的统一响应体。
- `com.lingxiao.common.core.enums.ErrorCode` — 错误码，只增不改。
- `com.lingxiao.common.web.handler.GlobalExceptionHandler` — 兜底异常，绝不返回堆栈。
- `com.lingxiao.common.security.util.StpPlatformUtil` — 平台域 Token 唯一入口。
- `com.lingxiao.common.mybatis.domain.BaseEntity` — 审计四字段 + 逻辑删除 + 乐观锁。
