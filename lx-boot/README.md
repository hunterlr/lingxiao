# lx-boot · 启动与装配模块

## 这个目录是做什么的

**唯一可执行的 Spring Boot 应用**。职责只有四件事：

1. 启动类、端口、Profile；
2. 依赖装配（common / framework / modules）；
3. Flyway 迁移位置与数据源；
4. 打包（`spring-boot-maven-plugin`，产物 `lingxiao.jar`）。

## 目录结构

```
lx-boot
├─ pom.xml
└─ src/main
   ├─ java/com/lingxiao/boot/LingxiaoApplication.java
   └─ resources/application.yml
```

## 启动类

```java
@SpringBootApplication(scanBasePackages = "com.lingxiao")
@MapperScan(basePackages = "com.lingxiao.modules",
            annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class LingxiaoApplication { ... }
```

- `scanBasePackages = "com.lingxiao"`：新增模块只要加依赖，**不改启动类**；
- `annotationClass = Mapper.class`：只扫显式标注 `@Mapper` 的接口，避免把普通接口误当 Mapper。

> 踩坑记录：最初没写 `@MapperScan`，MyBatis 只扫描 `com.lingxiao.boot`，启动报
> `No qualifying bean of type SysUserMapper`。加扫描后解决。

## 配置要点（application.yml）

| 配置 | 值 | 说明 |
|---|---|---|
| `server.port` | 8080 | |
| `spring.datasource.url` | `127.0.0.1:3307/lingxiao` | 可用 `DB_HOST/DB_PORT/DB_NAME` 覆盖 |
| `spring.flyway.locations` | `db/migration/system,db/migration/file` | 按模块分目录 |
| `sa-token.token-name` | `lx-platform-token` | 平台域 Token 头 |
| `lx.module.modules.demo.enabled` | `false` | 演示模块默认关闭 |
| `lx.file.local-path` | `./data/upload` | 上传根目录 |
| `lx.captcha.enabled` | 默认 `true` | **生产禁止关闭** |

## 依赖装配原则

`lx-boot` 只依赖模块的 **jar**，不写业务代码：

```
common-web / common-security / common-mybatis / common-log
framework
module-system / module-file / module-demo
```

`lx-common-redis` **故意不加入**：本机没有 Redis，加进来会导致启动失败。启用步骤见 `lx-common/lx-common-redis/README.md`。

## 构建与启动

```powershell
mvn -DskipTests package
java -jar lx-boot\target\lingxiao.jar

# 本地联调临时关验证码（生产禁止）
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
```

## 禁止事项

- ❌ 在 `lx-boot` 里写 Controller / Service / Mapper。
- ❌ 在启动类里写 `@Bean` 业务对象（放对应模块的 `@Configuration`）。
- ❌ 把数据源密码写死（用环境变量覆盖）。
- ❌ 生产环境开启 `spring.flyway.clean-disabled=false`（会允许清库）。
