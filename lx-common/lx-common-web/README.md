# lx-common-web · Web 层公共能力

## 这个目录是做什么的

处理所有 Controller 共用的 Web 规则：异常出口、JSON 格式、跨域、请求上下文。

## 包结构

```
com.lingxiao.common.web
├─ handler/GlobalExceptionHandler   全局异常 → 统一 R
├─ config/JacksonConfig             Long→String、时间格式
├─ config/CorsConfig                开发期跨域
└─ util/ServletUtils                取 IP、User-Agent、当前请求
```

## 关键规则

### 1. Long 全部序列化为字符串

雪花 ID 是 19 位，JavaScript `Number` 只能安全表示 2^53-1（16 位）。不转字符串会出现：

- 详情查不到（ID 被四舍五入）
- 删除删错行
- 前端传回后 ID 不一致

副作用：`size`、`refCount` 等 Long 字段也会变成字符串，前端按字符串接收或自行转数字。

### 2. 异常出口只有两个

| 异常类型 | 处理者 | 返回 |
|---|---|---|
| `ServiceException` | `GlobalExceptionHandler` | 按异常里的 code/msg |
| 参数校验失败 | 同上 | 1001 + 字段错误 |
| 系统异常 | 同上 | 9000（堆栈只进日志） |
| `NotLoginException` / `NotPermissionException` | `SaTokenExceptionHandler`（在 security 模块，优先级更高） | 401 / 403 |

### 3. IP 取值

按 `X-Forwarded-For` → `X-Real-IP` → `remoteAddr` 顺序。**生产必须由 Nginx 覆盖该头**，否则客户端可伪造。

## 禁止事项

- ❌ 在异常处理里把 `e.getMessage()`、SQL、类名返回给前端。
- ❌ 把 CORS 的 `allowedOriginPatterns("*")` 直接带到生产（应收敛到实际域名）。
- ❌ 在这里写登录鉴权逻辑（属于 `lx-common-security`）。
