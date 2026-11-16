# 22 · 依赖清单（全部“轮子”）

> 口径：**2026-11-15 从构建产物里实际解包统计**，不是凭 pom 猜的。
>
> - 后端：`lx-boot/target/lingxiao.jar` → `BOOT-INF/lib/` 共 **102 个运行时 jar**（含 13 个项目自研 jar）。
> - 前端：`lx-ui-admin/package.json` 声明 + `package-lock.json` 实际锁定版本。
>
> 本文是“我用了哪些轮子、为什么用、升级改哪里”的唯一台账。新增第三方依赖必须同步本文件；
> 只改 `pom.xml` / `package.json` 不更新这里，视为施工未完成。
>
> 换一台电脑 clone 仓库后如何复现这份清单、如何启动，见
> [`docs/27-github-onboarding.md`](27-github-onboarding.md)。

---

## 1. 依赖治理规则（先看这一节）

| 规则 | 说明 |
|---|---|
| 版本只有一个入口 | 后端所有第三方版本写 `lx-dependencies/pom.xml`，子模块不准写 `<version>` |
| 升级必须成对改 | 改 BOM 的同时改本文件 + `docs/11-jdk-engineering.md` 的依赖矩阵 |
| 运行时为准 | 本文件后半段的 102 个 jar 是“真正进了发布包”的闭包，不是声明列表 |
| 前端区分声明/锁定 | `package.json` 是范围（`^`），`package-lock.json` 是实际版本；排查问题看后者 |
| 测试依赖单列 | JUnit / Mockito / AssertJ 等只允许 `test` scope，不计入运行时 jar 数量 |
| 不引“顺手”依赖 | 能 50 行写完的不引库；引之前先问：会不会把 JDK 8 兼容性搞坏？ |
| JDK 8 红线 | 任何新依赖必须能在 JDK 8 上运行（`release=8` 编译通过只是第一步，还要看字节码版本） |

---

## 2. 后端 102 个运行时 jar

### 2.1 项目自研模块（13）

| artifactId | 版本 | 作用 |
|---|---|---|
| `lx-admin-api` | 1.0.0-SNAPSHOT | 后台域跨模块 API（DTO / 查询对象） |
| `lx-common-core` | 1.0.0-SNAPSHOT | 统一响应 `R`、分页 `PageResult`、异常、工具 |
| `lx-common-log` | 1.0.0-SNAPSHOT | `@Log` 注解 + 操作日志切面 |
| `lx-common-mybatis` | 1.0.0-SNAPSHOT | MyBatis-Plus 配置、分页、自动填充、数据范围 |
| `lx-common-redis` | 1.0.0-SNAPSHOT | Redis 开关装配、Sa-Token 会话/验证码存储 |
| `lx-common-security` | 1.0.0-SNAPSHOT | Sa-Token 配置、`@RequiresPermission`、登录拦截 |
| `lx-common-web` | 1.0.0-SNAPSHOT | 全局异常、CORS、Jackson、Web 工具 |
| `lx-framework` | 1.0.0-SNAPSHOT | 公共能力聚合（模块开关、基础配置） |
| `lx-module-demo` | 1.0.0-SNAPSHOT | 可选模块关闭演示（默认关闭） |
| `lx-module-file` | 1.0.0-SNAPSHOT | 文件中心、上传、图片衍生图 |
| `lx-module-generator` | 1.0.0-SNAPSHOT | 代码生成器（导入表 / 预览 / ZIP） |
| `lx-module-job` | 1.0.0-SNAPSHOT | 定时任务（Spring Task + 数据库锁） |
| `lx-module-system` | 1.0.0-SNAPSHOT | 登录、用户、角色、菜单、部门、岗位、字典、参数、公告、日志、监控 |

### 2.2 Spring Boot / Spring Framework（18）

| artifactId | 版本 | 作用 |
|---|---|---|
| `spring-boot` | 2.7.18 | Boot 核心 |
| `spring-boot-autoconfigure` | 2.7.18 | 自动装配 |
| `spring-boot-jarmode-layertools` | 2.7.18 | 分层 jar（`layertools`，部署脚本未强依赖） |
| `spring-aop` | 5.3.31 | AOP（日志切面） |
| `spring-beans` | 5.3.31 | IoC 容器 |
| `spring-context` | 5.3.31 | 应用上下文 |
| `spring-context-support` | 5.3.31 | 缓存/邮件等上下文支持 |
| `spring-core` | 5.3.31 | Spring 基础 |
| `spring-expression` | 5.3.31 | SpEL |
| `spring-jcl` | 5.3.31 | Jakarta Commons Logging 桥 |
| `spring-jdbc` | 5.3.31 | JdbcTemplate / 事务基础设施 |
| `spring-oxm` | 5.3.31 | OXM（Flyway / 部分库的传递依赖） |
| `spring-tx` | 5.3.31 | 声明式事务 |
| `spring-web` | 5.3.31 | Web 基础 |
| `spring-webmvc` | 5.3.31 | MVC / 拦截器 |
| `spring-data-commons` | 2.7.18 | Spring Data 公共抽象 |
| `spring-data-keyvalue` | 2.7.18 | Key-Value 抽象（Redis 基础） |
| `spring-data-redis` | 2.7.18 | RedisTemplate / 连接工厂 |

### 2.3 Web 容器与参数校验（8）

| artifactId | 版本 | 作用 |
|---|---|---|
| `tomcat-embed-core` | 9.0.83 | 内嵌 Tomcat |
| `tomcat-embed-el` | 9.0.83 | EL 表达式 |
| `tomcat-embed-websocket` | 9.0.83 | WebSocket（Boot Web 默认带入，当前未用） |
| `hibernate-validator` | 6.2.5.Final | `@Valid` / `@NotBlank` 实现 |
| `jakarta.validation-api` | 2.0.2 | Bean Validation 2.0 API |
| `jakarta.annotation-api` | 1.3.5 | `@PostConstruct` 等 |
| `jboss-logging` | 3.4.3.Final | Validator 日志门面 |
| `classmate` | 1.5.1 | Validator 类型解析 |

### 2.4 JSON / YAML / XML（10）

| artifactId | 版本 | 作用 |
|---|---|---|
| `jackson-annotations` | 2.13.5 | Jackson 注解 |
| `jackson-core` | 2.13.5 | 流式 JSON |
| `jackson-databind` | 2.13.5 | JSON 序列化/反序列化 |
| `jackson-dataformat-yaml` | 2.13.5 | YAML（Boot 配置） |
| `jackson-datatype-jdk8` | 2.13.5 | `Optional` 等 |
| `jackson-datatype-jsr310` | 2.13.5 | `LocalDateTime` 等 |
| `jackson-module-parameter-names` | 2.13.5 | 构造器参数名 |
| `snakeyaml` | 1.30 | YAML 解析 |
| `jakarta.activation-api` | 1.2.2 | 邮件/附件类型（传递依赖） |
| `jakarta.xml.bind-api` | 2.3.3 | JAXB API（传递依赖） |

### 2.5 持久层与数据库（11）

| artifactId | 版本 | 作用 |
|---|---|---|
| `mybatis` | 3.5.13 | MyBatis 核心 |
| `mybatis-plus` | 3.5.3.2 | MP 核心 |
| `mybatis-plus-annotation` | 3.5.3.2 | `@TableName` / `@TableId` |
| `mybatis-plus-boot-starter` | 3.5.3.2 | MP 自动装配 |
| `mybatis-plus-core` | 3.5.3.2 | MP 核心实现 |
| `mybatis-plus-extension` | 3.5.3.2 | 分页、条件构造器扩展 |
| `mybatis-spring` | 2.1.1 | MyBatis 与 Spring 事务整合 |
| `jsqlparser` | 4.6 | SQL 解析（MP 分页/数据范围用） |
| `HikariCP` | 4.0.3 | 默认连接池 |
| `flyway-core` | 7.15.0 | 数据库版本迁移 |
| `mysql-connector-j` | 8.0.33 | MySQL 驱动（runtime） |

### 2.6 认证与密码（6）

| artifactId | 版本 | 作用 |
|---|---|---|
| `sa-token-core` | 1.37.0 | Sa-Token 核心 |
| `sa-token-servlet` | 1.37.0 | Servlet 环境适配 |
| `sa-token-spring-boot-autoconfig` | 1.37.0 | Boot 自动装配 |
| `sa-token-spring-boot-starter` | 1.37.0 | Starter |
| `sa-token-redis-jackson` | 1.37.0 | 会话落 Redis + Jackson 序列化 |
| `spring-security-crypto` | 5.7.11 | BCrypt 密码哈希（不引整个 Spring Security） |

### 2.7 Redis / 缓存 / 异步（11）

| artifactId | 版本 | 作用 |
|---|---|---|
| `caffeine` | 2.9.3 | 本地缓存（验证码、参数缓存） |
| `lettuce-core` | 6.1.10.RELEASE | Redis 客户端 |
| `netty-buffer` | 4.1.101.Final | Lettuce 传输依赖 |
| `netty-codec` | 4.1.101.Final | 编解码 |
| `netty-common` | 4.1.101.Final | Netty 基础 |
| `netty-handler` | 4.1.101.Final | 连接处理 |
| `netty-resolver` | 4.1.101.Final | 地址解析 |
| `netty-transport` | 4.1.101.Final | 传输层 |
| `netty-transport-native-unix-common` | 4.1.101.Final | Unix Domain Socket 支持 |
| `reactor-core` | 3.4.34 | 响应式基础（Lettuce 依赖） |
| `reactive-streams` | 1.0.4 | 响应式流规范 |

### 2.8 接口文档（10）

| artifactId | 版本 | 作用 |
|---|---|---|
| `knife4j-core` | 4.4.0 | Knife4j 核心 |
| `knife4j-openapi3-spring-boot-starter` | 4.4.0 | Knife4j Starter |
| `knife4j-openapi3-ui` | 4.4.0 | `/doc.html` 前端资源 |
| `springdoc-openapi-common` | 1.7.0 | SpringDoc 公共 |
| `springdoc-openapi-ui` | 1.7.0 | Swagger UI 集成 |
| `springdoc-openapi-webmvc-core` | 1.7.0 | 扫描 MVC 生成 OpenAPI |
| `swagger-annotations` | 2.2.9 | OpenAPI 注解（`@Tag` 等，当前业务模块未直接依赖） |
| `swagger-core` | 2.2.9 | OpenAPI 模型构建 |
| `swagger-models` | 2.2.9 | OpenAPI 数据模型 |
| `swagger-ui` | 4.18.2 | Swagger UI 静态资源 |

### 2.9 业务工具与能力（7）

| artifactId | 版本 | 作用 |
|---|---|---|
| `hutool-core` | 5.8.22 | 通用工具（日期、字符串等） |
| `hutool-captcha` | 5.8.22 | 图形验证码 |
| `commons-lang3` | 3.12.0 | 字符串/对象工具（传递依赖） |
| `jsoup` | 1.17.2 | 公告富文本 XSS 清洗 |
| `aspectjweaver` | 1.9.7 | `@Log` 切面织入 |
| `velocity-engine-core` | 2.3 | 代码生成模板引擎 |
| `thumbnailator` | 0.4.8 | 图片缩略图/压缩 |

### 2.10 日志（6）

| artifactId | 版本 | 作用 |
|---|---|---|
| `slf4j-api` | 1.7.36 | 日志门面 |
| `logback-classic` | 1.2.12 | Logback 实现 |
| `logback-core` | 1.2.12 | Logback 核心 |
| `jul-to-slf4j` | 1.7.36 | JUL 桥接 |
| `log4j-api` | 2.17.2 | Log4j API（Tomcat 传递） |
| `log4j-to-slf4j` | 2.17.2 | Log4j 桥到 SLF4J |

### 2.11 编译期注解（2）

| artifactId | 版本 | 作用 |
|---|---|---|
| `checker-qual` | 3.19.0 | 可空性注解（传递依赖，运行时无感） |
| `error_prone_annotations` | 2.10.0 | Error Prone 注解（传递依赖） |

**合计：13 + 18 + 8 + 10 + 11 + 6 + 11 + 10 + 7 + 6 + 2 = 102 个 jar。**
与 `BOOT-INF/lib/*.jar` 的实际数量一致（`BOOT-INF/lib/` 目录条目本身不算 jar）。

> 自查命令（Windows PowerShell）：
>
> ```powershell
> Add-Type -AssemblyName System.IO.Compression.FileSystem
> $z=[System.IO.Compression.ZipFile]::OpenRead('lx-boot\target\lingxiao.jar')
> ($z.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' }).Count
> $z.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' } |
>   ForEach-Object { [System.IO.Path]::GetFileName($_.FullName) } | Sort-Object
> $z.Dispose()
> ```

### 2.12 测试依赖（test scope，不计入运行时 jar）

由 `lx-common/pom.xml` 与 `lx-modules/pom.xml` 统一继承 `spring-boot-starter-test:2.7.18`，
子模块不单独声明。实际解析到的关键组件：

| 组件 | 版本 | 用途 |
|---|---|---|
| `junit-jupiter` | 5.8.2 | 测试框架（`@Test` / `@DisplayName` / `@TempDir`） |
| `mockito-core` | 4.5.1 | 隔离 Mapper / 参数读取 |
| `assertj-core` | 3.22.0 | 流式断言 |
| `spring-test` | 5.3.31 | 为 `@SpringBootTest` 预留，当前未使用 |
| `jsonassert` / `json-path` | 1.5.1 / 2.7.0 | 为接口契约测试预留 |
| `hamcrest` | 2.2 | 第三方断言兼容 |

这些依赖 **test scope 不进 `BOOT-INF/lib`**，所以运行时仍是上面统计的 **102 个 jar**。
测试范围、运行方式与 CI 说明见 [`docs/26-testing-ci.md`](26-testing-ci.md)。

---

## 3. 前端依赖

### 3.1 直接依赖（13）

| 包 | package.json 声明 | 实际锁定 | 作用 |
|---|---|---|---|
| `vue` | `^3.5.13` | 3.5.45 | 视图框架 |
| `vue-router` | `^4.4.5` | 4.6.5 | 路由 / 动态菜单 |
| `pinia` | `^2.2.6` | 2.3.1 | 状态管理 |
| `element-plus` | `^2.8.8` | 2.14.8 | UI 组件库 |
| `@element-plus/icons-vue` | `^2.3.1` | 2.3.2 | 图标 |
| `axios` | `^1.7.7` | 1.21.1 | HTTP 客户端 |
| `vue-i18n` | `^9.14.4` | 9.14.4 | 界面多语言（L1，见 `docs/23`） |
| `@wangeditor-next/editor` | `^6.6.0` | 6.6.0 | 富文本编辑器（默认） |
| `@wangeditor-next/editor-for-vue` | `^6.6.0` | 6.6.0 | wangEditor 的 Vue3 封装 |
| `vue-ueditor-wrap` | `^3.0.8` | 3.0.8 | UEditor Plus 的 Vue3 封装 |
| `ueditor-plus-main` | `^4.5.0` | 4.5.0 | UEditor Plus 运行时资源 |
| `vite`（dev） | `^5.4.11` | 5.4.21 | 构建/开发服务器 |
| `@vitejs/plugin-vue`（dev） | `^5.2.1` | 5.2.4 | Vue SFC 编译插件 |

### 3.2 关键传递依赖（排查问题时看）

| 包 | 实际版本 | 来源 / 作用 |
|---|---|---|
| `dayjs` | 1.11.23 | Element Plus 日期组件 |
| `lodash-es` | 4.18.1 | Element Plus 工具函数 |
| `@vueuse/core` | 15.0.0 | Element Plus 组合式工具 |
| `@floating-ui/dom` | 1.8.0 | 浮层定位 |
| `async-validator` | 4.2.5 | 表单校验 |
| `@ctrl/tinycolor` | 4.3.1 | 主色派生 |
| `memoize-one` | 6.0.0 | Element Plus 内部缓存 |
| `normalize-wheel-es` | 1.2.0 | 滚轮归一化 |
| `@popperjs/core` | 2.11.8 | 旧版浮层（兼容） |
| `@vue/devtools-api` | 6.6.4 | Pinia 调试 |
| `@vue/compiler-sfc` | 3.5.45 | Vue 单文件组件编译 |
| `esbuild` | 0.21.5 | Vite 依赖预构建 |
| `rollup` | 4.68.3 | 生产打包 |
| `postcss` | 8.5.33 | CSS 处理 |

### 3.3 前端工程里容易忽略的两件事

1. `scripts/sync-ueditor.mjs` 在 `predev` / `prebuild` 自动跑，把 `ueditor-plus-main` 的静态资源
   同步到 `public/ueditor-plus`。换 UEditor 版本后必须重新 `npm run sync:ueditor`。
2. `package.json` 的 `allowScripts` 只放行 `esbuild` 与 `vue-demi`，其他包禁止执行安装脚本，
   这是供应链安全底线，不要为了方便关掉。

---

## 4. 刻意没有引的“轮子”

| 候选 | 为什么不用 |
|---|---|
| Quartz | v1.0 只需要分钟级定时任务，Spring Task + 数据库锁足够；Quartz 会引入 11 张表和一套调度线程模型 |
| Spring Security 全家桶 | 只需要 BCrypt，单独引 `spring-security-crypto` 即可，避免过滤器链与 Sa-Token 打架 |
| Redis 客户端 Redisson | 当前只有会话/验证码缓存，Lettuce 已随 Boot 带入 |
| 前端 UI 二次封装库（如 vue-element-plus-x） | 组件封装要可控，避免版本升级被第三方绑架 |
| 国际化运行时（vue-i18n） | L1 已按 `docs/23-i18n-plan.md` 引入 `vue-i18n@9`（`src/i18n/`）。**没有**引 `@intlify/unplugin-vue-i18n`：语言包是普通 JS 对象，不需要编译期提取，少一个构建插件 |
| 图表库（ECharts） | 首页/监控没有 Chart 需求，避免 1MB 级体积 |
| H2 内存库 | 方言与 MySQL 差异大，用它测 SQL 会“假通过”；真实 SQL 仍由 `verify-*.ps1` 验证 |
| Testcontainers | 需要 Docker，会把最小 CI 变重；等做 Mapper / Flyway 集成测试时再引 |
| JaCoCo | 覆盖率容易变成数字游戏，当前优先锁关键路径而不是刷百分比 |

---

## 5. 升级操作手册

### 5.1 后端

1. 只改 `lx-dependencies/pom.xml` 的 `<properties>` 或 `dependencyManagement`；
2. `mvn -DskipTests package`；
3. 解包核对 jar 数量与版本；
4. 跑 `.local/verify-*.ps1` 回归；
5. 更新本文件 + `docs/11-jdk-engineering.md`；
6. 在 `docs/21-build-log.md` 追加一条“依赖升级”记录。

### 5.2 前端

1. 改 `package.json` 后在 `lx-ui-admin` 执行 `npm install`（会更新 lock）；
2. `npm run build`；
3. 跑 `.local/uitest/smoke.mjs` + `theme.mjs`；
4. 更新本文件 3.1 / 3.2 的锁定版本；
5. 大版本升级（Vue / Element Plus / Vite）必须单独一轮，不和其他功能混做。

### 5.3 安全底线

- 不引入来源不明的包；
- 不用 `npm install --force` 绕过 peer 冲突；
- 后端新增依赖必须检查 CVE 与 JDK 8 字节码；
- 生产包不允许出现 `SNAPSHOT` 之外的动态版本（当前内部模块是 SNAPSHOT，发版时统一改 release）。

---

## 6. 一句话总结

**后端 102 个 jar 里，真正“选型”的只有 12 个左右**（Boot、MP、Sa-Token、Flyway、Knife4j、
Hutool、Jsoup、Velocity、Thumbnailator、Caffeine、MySQL 驱动、spring-security-crypto），
其余都是它们的传递闭包。前端同理：10 个直接依赖 + Vite 两个开发依赖。
测试依赖（JUnit / Mockito / AssertJ）只存在于 `test` scope，不在发布包内，
单独见本文 2.12 与 [`docs/26-testing-ci.md`](26-testing-ci.md)。
把这张表当“房产证”看：以后谁再问“项目用了什么”，直接翻这里。
