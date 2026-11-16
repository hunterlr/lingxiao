# lx-api · 对外契约层

## 这个目录是做什么的

放**跨模块共享的接口模型**，不放实现、不放 Mapper、不放 Controller。

## 为什么单独一层

1. 模块之间只依赖契约，不依赖对方实现；
2. 后续拆进程 / 拆 RPC 时，这些 DTO 可以直接复用；
3. 生成 OpenAPI 文档时有稳定的模型来源。

## 子模块

| 子模块 | 内容 | 状态 |
|---|---|---|
| `lx-admin-api` | 后台接口请求 / 响应模型 | ✅ v1.0 |
| `lx-portal-api` | 门户接口契约 | ⬜ 后续 |
| `lx-app-api` | 移动端 / 会员接口契约 | ⬜ v1.2 |

## 依赖方向

```
lx-api/* → lx-common-core + validation-api + lombok
```

**不依赖 Spring Web**：避免 DTO 被 Web 容器绑死。

## 禁止事项

- ❌ 放 `@RestController`、`@Service`、Mapper。
- ❌ 放数据库实体（实体属于各业务模块，契约层只放对外模型）。
- ❌ 在这里写业务逻辑。
- ❌ 让 DTO 携带密码、盐、内部存储路径等敏感字段。
