# lx-common-redis · Redis 公共能力（v1.0 可选）

## 这个目录是做什么的

把「Redis 是可选项」这件事收在一个模块里，默认关闭，打开后提供：

| 能力 | 实现 | 装配条件 |
|---|---|---|
| 通用缓存模板 | `RedisConfig` → `RedisTemplate<String, Object>`（String Key + JSON Value） | `lx.redis.enabled=true` |
| Sa-Token 会话 / Token | `SaTokenRedisConfig` → `SaTokenDaoRedisJackson` | `lx.redis.enabled=true` |

验证码的 Redis 存储（`RedisCaptchaStore`）在 `lx-module-system`，
用 Spring Boot 自带的 `StringRedisTemplate`，同样受 `lx.redis.enabled` 控制。
这样「一个开关同时决定会话和验证码」不会出现一半内存、一半 Redis。

## 默认关闭

`lx-boot/src/main/resources/application.yml`：

```yaml
lx:
  redis:
    enabled: false
```

关闭时：

- Sa-Token 使用内存存储（重启后 Token 失效，单机可接受）；
- 验证码使用 Caffeine 本地缓存；
- **不需要安装 Redis，应用照常启动**。

## 启用步骤

1. 准备好 Redis（本地测试用 3.0.504 通过；生产建议 5.0+）。
2. `application.yml` 填写连接信息：

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: ""
    database: 0
    timeout: 3000ms

lx:
  redis:
    enabled: true
```

3. 重启应用。启动日志会出现：

```text
Sa-Token 会话存储：Redis（lx.redis.enabled=true）
存储模式 | Redis 开关=true | Sa-Token 会话=Redis | 验证码=Redis
```

4. 用 `redis-cli keys '*'` 应能看到：

```text
lx-platform-token:platform:token:<token>
lx-platform-token:platform:session:1
lx:captcha:<captchaKey>
```

## 为什么排除 Sa-Token 的自动装配

`sa-token-redis-jackson` 在自己的 `META-INF/spring.factories` 里把
`cn.dev33.satoken.dao.SaTokenDaoRedisJackson` 注册成自动装配类。
只要依赖在 classpath 上，它就会**无条件**生效，`lx.redis.enabled=false`
也会去连 Redis，部署环境没装 Redis 就直接起不来。

所以 `application.yml` 显式排除：

```yaml
spring:
  autoconfigure:
    exclude: cn.dev33.satoken.dao.SaTokenDaoRedisJackson
```

再由 `SaTokenRedisConfig` 按 `lx.redis.enabled` 手动注册。
这是一个容易被“优化”掉的细节：删掉 exclude 后开关会失效。

## 启动即探测

`SaTokenRedisConfig` 在注册 DAO 前会执行一次 `PING`：

- `lx.redis.enabled=true` 且 Redis 不可用 → **启动失败**，日志给出 `lx.redis.enabled=true 但 Redis 不可用`；
- `lx.redis.enabled=false` → 不创建连接，不探测。

会话存储不是热开关：让它在启动期失败，比运行期“A 节点登录、B 节点 401”容易排查。

## Key 约定

| 前缀 | 内容 | 过期 |
|---|---|---|
| `lx-platform-token:platform:token:*` | Sa-Token Token → 账号 | 12 小时（`sa-token.timeout`） |
| `lx-platform-token:platform:session:*` | Sa-Token Session | 随会话 |
| `lx:captcha:*` | 图形验证码文本 | 300 秒，一次性 |

Sa-Token 的前缀由 `sa-token.token-name` 决定，所以换 Token 名会同时换 Key 前缀，
不要在运维脚本里写死。

## 禁止事项

- 不要把 Redis 开关做成参数设置里的热开关：切换时旧 Token 留在原存储，会造成随机 401；
- 不要用 JDK 序列化存 Value（版本兼容差、有反序列化风险）；
- 不要在业务代码里直接依赖 `RedisTemplate` 而不做抽象：验证码已经走 `CaptchaStore`，
  后续业务缓存建议同样先定接口，再选 Caffeine / Redis 实现；
- 不要假设 Redis 3.x：本地只用于验证兼容性，生产用 5.0+ 更稳妥。
