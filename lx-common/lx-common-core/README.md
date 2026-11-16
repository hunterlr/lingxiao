# lx-common-core · 最底层通用模型

## 这个目录是做什么的

整个工程**依赖链的最底层**：统一响应、错误码、业务异常、全局常量、分页模型。

## 包结构

```
com.lingxiao.common.core
├─ domain/    R<T>（统一响应）、PageResult<T>（分页响应）
├─ enums/     ErrorCode（错误码）
├─ exception/ ServiceException（业务异常）
├─ constant/  CommonConstants（状态、删除标记、安全域、超管 ID）
└─ config/    ConfigProvider（只读系统参数 SPI）
```

`ConfigProvider` 只定义“按 key 读参数”，由 `lx-module-system` 实现，
文件等业务模块用它读取 `sys.file.uploadMaxMb` 一类配置，避免反向依赖系统模块。
接口只读、不缓存、允许返回 `null`，调用方必须给默认值。

## 依赖方向

```
lx-common-core → lombok(provided) + slf4j-api + jackson-annotations
```

**不依赖**：Spring Web、MyBatis、Sa-Token、任何数据库驱动。

> `jackson-annotations` 只用于给 `R.isSuccess()` 加 `@JsonIgnore`，是无传递依赖的注解包，不会把 Web 能力带进来。

## 错误码规则

| 段 | 含义 | 示例 |
|---|---|---|
| 0 | 成功 | — |
| 1xxx | 通用 / 参数 / 权限 | 1002 未登录、1003 无权限 |
| 2xxx | 认证与账号 | 2001 用户名或密码错误 |
| 3xxx | 文件 | 3003 文件超限 |
| 9xxx | 系统内部 | 9000 系统内部错误 |

**错误码只增不改**，前端按 `code` 分支，不要依赖 `msg` 文案。

## 禁止事项

- ❌ 引入 Spring Web / MyBatis / Sa-Token。
- ❌ 在这里写业务枚举（放各自模块）。
- ❌ 修改已发布的错误码数值。
- ❌ 在 `R` 里加分页字段（分页用 `PageResult`）。
