# 26 · 单元测试与最小 CI

> 本文回答两个问题：**哪些逻辑必须用单测锁住？没有数据库的机器上怎么自动验证？**
>
> 结论：v1.0 先做“纯逻辑 + 安全回归”的 99 个单测（20 个测试类），
> 完全不依赖 MySQL / Redis，`mvn test` 一条命令跑完；
> 再用 GitHub Actions 覆盖 JDK 8/11/17 与前端构建。

---

## 1. 边界：单测不替代什么

| 手段 | 跑什么 | 需要什么 | 什么时候跑 |
|---|---|---|---|
| `mvn test`（本文） | 纯逻辑、安全清洗、命名映射、参数校验 | 只要 JDK + Maven | 每次提交 / CI |
| `.local/verify-*.ps1` | 真实接口、权限、数据范围、会话 | 后端 + MySQL（+ Redis） | 发版前 |
| `.local/uitest/*.mjs` | 真实浏览器：登录、页面、主题、上传 | 后端 + 前端 + Chrome | 发版前 |

**不追求 100% 覆盖率。** v1.0 的目标是把“改一行就可能静默出错”的地方锁住：
错误码、密码策略、HTML 清洗、路径穿越、生成器命名、cron 校验、菜单树。

---

## 2. 怎么跑

### 2.1 全量

```bash
mvn -B -ntp test
```

### 2.2 只跑某个模块 / 某个类

内部模块（`lx-common-*`、`lx-modules-*`）默认没有 `mvn install` 到本地仓库，单独 `-pl`
会因为找不到 `1.0.0-SNAPSHOT` 依赖而失败。加 `-am`（同时构建上游模块）即可：

```bash
# 整个模块
mvn -B -ntp -pl lx-modules/lx-module-system -am test

# 指定测试类（-am 会带到上游模块，上游没有同名类时要用下面这个开关）
mvn -B -ntp -pl lx-modules/lx-module-system -am test \
  -Dtest=PasswordPolicyTest -Dsurefire.failIfNoSpecifiedTests=false

# 多个类（PowerShell 下整段加引号，避免逗号/点号被拆开）
mvn -B -ntp -pl lx-modules/lx-module-generator -am test \
  "-Dtest=NamingUtilsTest,TextUtilsTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

> 只有 `lx-common-core` 这类不依赖内部模块的工程可以省掉 `-am`；
> 想彻底避免每次都带上游模块，可以先执行一次 `mvn -B -ntp -DskipTests install`。

### 2.3 看报告

```
<module>/target/surefire-reports/
  com.lingxiao...Test.txt        # 文本摘要
  TEST-com.lingxiao...Test.xml   # 机器可读，CI 上传这个
```

### 2.4 跳过测试

```bash
mvn -B -ntp -DskipTests package
```

发布打包仍用这条；**CI 不允许跳过**。

---

## 3. 当前测试清单（99 项 / 20 个类）

| 模块 | 测试类 | 项数 | 锁住什么 |
|---|---|---|---|
| `lx-common-core` | `RTest` | 6 | 成功码 0、失败码 1000、data 透传、`isSuccess` 不出现在 JSON |
| `lx-common-core` | `PageResultTest` | 2 | total / pageNum / pageSize / records 契约，空分页返回空列表 |
| `lx-common-core` | `ErrorCodeTest` | 4 | 错误码唯一、文案非空、核心号码不变、码段分布 |
| `lx-common-core` | `ServiceExceptionTest` | 4 | 错误码与自定义消息的优先级、可被全局异常处理器捕获 |
| `lx-common-core` | `ConfigProviderTest` | 3 | 参数脏值（空串 / 非数字 / 大小写）回退默认值 |
| `lx-module-system` | `PasswordPolicyTest` | 5 | off / basic / strong 三档、非法值回退 basic、错误码 1001 |
| `lx-module-system` | `SessionPolicyTest` | 3 | 参数缺失 / 空白 / 非法值都按单会话处理，只有 `true` 才放开多端 |
| `lx-module-system` | `HtmlSanitizerTest` | 9 | script、事件属性、伪协议、iframe、内联样式、安全标签保留 |
| `lx-module-system` | `SysMenuServiceTest` | 4 | 两层树、父节点缺失作为根、空列表、详情 1004 |
| `lx-common-core` | `MessageUtilsTest` | 6 | 语言包未接入 / 解析异常时回退默认文案，不污染主流程 |
| `lx-common-web` | `I18nMessageSourceTest` | 5 | 中英文资源齐全、参数占位、缺 key 回退、编码正常 |
| `lx-common-web` | `LingxiaoLocaleResolverTest` | 7 | `X-Lang` > `?lang=` > `Accept-Language` > `zh-CN` 的优先级 |
| `lx-module-system` | `DataScopeServiceTest` | 6 | 自定义部门命中 / 空集合收紧为仅本人 / 超管放行 / 4 > 2、3 > 2 |
| `lx-module-system` | `SysRoleServiceTest` | 2 | 自定义范围空部门集合拒绝保存、`deptIds` 为空时不误清历史关联 |
| `lx-module-file` | `LocalStorageServiceTest` | 4 | store/load/delete 闭环、路径穿越拒绝、文件不存在异常 |
| `lx-module-job` | `SysJobServiceTest` | 7 | 空 cron、5 段 cron、执行器不存在、重名、状态/并发枚举 |
| `lx-module-generator` | `NamingUtilsTest` | 5 | 下划线 / 连字符 / 多段命名、Java 关键字避让 |
| `lx-module-generator` | `JavaTypeResolverTest` | 5 | MySQL / PG / 达梦常见类型映射，未知类型回退 String |
| `lx-module-generator` | `TextUtilsTest` | 8 | 注释折叠、Javadoc 截断、JS / HTML / Java / SQL 四类转义 |
| `lx-boot` | `AppProfileTest` | 4 | 品牌名 trim、中英文名互相兜底、版本空值转空串 |

合计：**Tests run: 99, Failures: 0, Errors: 0, Skipped: 0**（2026-11-20，JDK 8 跑通；66 项基线于 2026-11-15 在 JDK 8 / 11 各跑一遍）。

---

## 4. 测试依赖（全部 test scope）

由根聚合模块 `lx-common` / `lx-modules` 统一继承，子模块不需要再写：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

实际解析到的关键版本：

| 组件 | 版本 | 用途 |
|---|---|---|
| JUnit Jupiter | 5.8.2 | 测试框架（`@Test` / `@DisplayName` / `@TempDir`） |
| Mockito | 4.5.1 | 隔离 Mapper / 配置读取 |
| AssertJ | 3.22.0 | 流式断言（`assertThat`） |
| Spring Test | 5.3.31 | 为将来 `@SpringBootTest` 预留，当前未使用 |
| JSONassert / JsonPath | 1.5.1 / 2.7.0 | 为接口契约测试预留 |
| Hamcrest | 2.2 | 部分第三方断言兼容 |

**重要：test scope 不进 `BOOT-INF/lib`，运行时 jar 仍是 102 个**（见 `docs/22-dependencies.md`）。

### 刻意没引的测试轮子

| 候选 | 为什么现在不引 |
|---|---|
| H2 | 方言与 MySQL 差异大，用它测 SQL 会得到“假通过”；真实 SQL 交给 `verify-*.ps1` |
| Testcontainers | 需要 Docker，CI 变重；等要做 Mapper / Flyway 集成测试时再引 |
| JaCoCo | 覆盖率数字容易变成 KPI；当前优先补“关键路径”而不是刷百分比 |
| 前端 Vitest | 前端已有 Puppeteer 真实浏览器回归，纯函数（`theme.js`）按需再补 |

---

## 5. 编写规范（新增测试照这个来）

1. **命名**：`<被测类>Test`，方法名用英文动词，`@DisplayName` 写中文场景；
2. **结构**：准备 → 执行 → 断言，不在测试里写循环造数据（用私有 `xxxFixture()`）；
3. **不连外部服务**：MySQL / Redis / 网络都不许出现；文件用 `@TempDir`，时间用固定值；
4. **不 `Thread.sleep`**：单测里没有异步等待，慢就是设计有问题；
5. **一个用例只锁一个行为**：失败时能直接看出哪里坏了；
6. **不打印敏感信息**：测试日志里不出现真实密码、Token；
7. **异常断言用 `catchThrowableOfType`**：同时校验错误码与消息，别只断言“抛了”；
8. **不为通过而改断言**：先确认是代码错还是预期错，改预期要写进施工记录。

### 5.1 新增功能时的测试清单

| 新增内容 | 至少补什么 |
|---|---|
| 新错误码 | `ErrorCodeTest` 的号码与文案断言 |
| 新参数 / 开关 | 默认值、非法值回退 |
| 新密码 / 安全规则 | 正例 + 每个拒绝分支 + 错误码 |
| 新 HTML / 文件入口 | 恶意载荷、路径穿越、空值 |
| 新生成器模板变量 | 命名、类型映射、转义 |
| 新定时任务字段 | 枚举校验、空值、默认值 |
| 新树 / 递归算法 | 正常树、孤儿节点、空列表、成环防护 |

---

## 6. 最小 CI

文件：`.github/workflows/ci.yml`

| Job | 内容 | 目的 |
|---|---|---|
| `backend` | 矩阵 JDK **8 / 11 / 17**；`mvn -B -ntp test`；JDK 8 额外 `package` | 证明同一份源码在三个 LTS 上都能编译、测试、打包 |
| `frontend` | Node 20；`npm ci` + `npm run build` | 证明前端锁文件可复现、构建不依赖本机缓存 |

触发条件：

- push 到 `main` / `master`；
- 任意 Pull Request；
- 手动 `workflow_dispatch`。

构建产物：

- `surefire-jdk8/11/17`：各 JDK 的测试报告（保留 7 天）；
- `lx-ui-admin-dist`：前端 `dist`（保留 7 天）。

### 6.1 启用前提

当前工作目录**尚未初始化 git**（`git init` 都还没做），所以这份流水线不会自动跑。
推到 GitHub 后需要：

1. `git init` + 首次提交（`.gitignore` 已就绪，`target/`、`node_modules/`、`release/`、`data/`、`.local/` 都不会入库）；
2. 在仓库 `Settings → Actions → General` 允许 Actions 运行；
3. 首次 push 到 `main` / `master`，或在 Actions 页手动 `Run workflow`。

私有仓库的 Actions 分钟数按账号套餐计算；只跑 `mvn test` + `npm run build`，
单次约 3~5 分钟（含 Maven / npm 缓存命中）。

### 6.2 CI 不做什么

- 不启 MySQL / Redis（单测不需要；需要数据库的断言留在 `.local/verify-*.ps1`）；
- 不跑 Puppeteer（headless Chrome 在 CI 里成本高，且页面回归依赖真实后端）；
- 不发布镜像 / 不推 release（发布仍由 `deploy/package.ps1|sh` 人工执行）。

### 6.3 为什么矩阵里没有 JDK 21

Spring Boot 2.7 / Spring Framework 5.3 的官方支持上限是 JDK 17；
JDK 21 能跑不代表兼容，等升级 Boot 3 时再纳入矩阵（见 `docs/11-jdk-engineering.md`）。

---

## 7. 已知未覆盖（如实记录）

| 未覆盖 | 原因 | 计划 |
|---|---|---|
| Controller 层参数绑定 / 序列化 | 需要 `@SpringBootTest` + MockMvc，成本高于收益 | 下一轮按模块补 1~2 个冒烟接口 |
| Mapper / Flyway SQL | 依赖真实数据库，H2 会掩盖方言问题 | 引 Testcontainers 后做 MySQL 8 集成测试 |
| 权限 / 数据范围 | 依赖 Sa-Token 登录上下文与真实角色数据 | 继续用 `.local/verify-permission.ps1` / `verify-datascope.ps1` |
| 前端纯函数（`utils/theme.js`） | 当前用 12 项浏览器用例覆盖 | 需要更快反馈时引入 Vitest |
| 并发 / 定时任务真实触发 | 依赖调度线程与数据库锁 | 继续用 `verify-job.ps1`（含集群锁用例） |

---

## 8. 一句话总结

**单测锁“逻辑与安全”，脚本锁“接口与页面”。**
`mvn test` 负责在 15 秒内告诉你“这次改动有没有把错误码、密码策略、
清洗规则、命名映射弄坏”；CI 负责在你合并前把这件事在三个 JDK 上再做一遍。
