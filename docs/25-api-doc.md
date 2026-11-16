# 25 · 接口文档维护规范

> 本文回答：“现在接口文档是这些，以后写了新功能，接口文档怎么更新？”
>
> 结论：**接口文档是 SpringDoc 扫代码生成的，不是手写文档。**
> 你只要把 Controller 写规范，文档自动出现；剩下的是“新增接口检查清单”和“发版前核对”。

---

## 1. 现在这套文档是怎么来的

| 组件 | 版本 | 作用 |
|---|---|---|
| SpringDoc | 1.7.0 | 扫描 `@RestController`，生成 OpenAPI 3 JSON |
| Knife4j | 4.4.0 | 把 JSON 渲染成 `/doc.html` 中文界面 |
| Swagger UI | 4.18.2 | `/swagger-ui.html` 备用界面 |

访问地址（开发环境）：

```
http://127.0.0.1:8080/doc.html          # 推荐，Knife4j 界面
http://127.0.0.1:8080/v3/api-docs       # OpenAPI JSON（程序读这个）
http://127.0.0.1:8080/swagger-ui.html   # Swagger UI
```

**当前基线（2026-11-15，开发环境 8080）：76 个 path。**
其中包含 `/api/admin/**` 后台接口与 `/api/file/{fileId}` 公开文件访问。
数字会随功能增加，不应为了“凑数”去改；但发版前要知道当前是多少，防止整组接口漏扫。

---

## 2. 当前文档的“诚实说明”

v1.0 的 Controller **没有加 `@Tag` / `@Operation` / `@Schema` 注解**，所以：

- 分组名来自类名（如 `sys-post-controller`），不是中文“岗位管理”；
- 操作名来自方法名（如 `list`、`add`），不是“分页查询岗位”；
- 字段说明来自 Java 字段名（如 `postCode`），没有中文描述；
- 请求方法、路径、参数、响应结构、`R<T>` 包装都是准确的，**可以直接调试**。

这是刻意取舍：业务模块不依赖 swagger 注解，避免文档框架渗透到领域代码。
如果客户/前端要求“中文接口名”，按第 4 节加注解即可，不影响运行逻辑。

---

## 3. 新增一个接口时，文档会发生什么

只要在 `lx-modules/**/controller` 下新增方法并加 `@GetMapping` / `@PostMapping` 等，
**重启后 `/v3/api-docs` 自动多一个 path，`/doc.html` 自动出现**，不需要改任何文档文件。

示例（现有风格）：

```java
@RestController
@RequestMapping("/api/admin/system/post")
@RequiredArgsConstructor
public class SysPostController {

    @RequiresPermission("platform:system:post:list")
    @GetMapping("/list")
    public R<PageResult<SysPost>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String postCode) {
        return R.ok(postService.page(pageNum, pageSize, postCode, null, null));
    }
}
```

自动生成的内容：

- path：`/api/admin/system/post/list`
- method：`GET`
- 参数：`pageNum` / `pageSize` / `postCode`
- 响应：`RPageResultSysPost`
- 鉴权：不会体现在 OpenAPI 里（Sa-Token 是拦截器），需要看 `@RequiresPermission`

> 注意：**`@RequiresPermission` 不会自动写进文档。** 前端联调时要看代码或本文的权限码表，
> 不要以为文档里没写就不需要权限。

---

## 4. 想让文档变“中文好看”：注解约定（下一轮可选）

### 4.1 先加编译期依赖

`swagger-annotations` 只用于编译，`provided` 不打进 jar：

```xml
<!-- lx-dependencies/pom.xml 统一版本 -->
<dependency>
    <groupId>io.swagger.core.v3</groupId>
    <artifactId>swagger-annotations</artifactId>
    <version>2.2.9</version>
</dependency>
```

业务模块（如 `lx-module-system`）：

```xml
<dependency>
    <groupId>io.swagger.core.v3</groupId>
    <artifactId>swagger-annotations</artifactId>
    <scope>provided</scope>
</dependency>
```

### 4.2 Controller 注解

```java
@Tag(name = "岗位管理", description = "岗位的增删改查")
@RestController
@RequestMapping("/api/admin/system/post")
public class SysPostController {

    @Operation(summary = "分页查询岗位", description = "支持岗位编码、名称、状态过滤")
    @GetMapping("/list")
    public R<PageResult<SysPost>> list(...) { ... }

    @Operation(summary = "新增岗位")
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysPost post) { ... }
}
```

### 4.3 实体 / DTO 注解

```java
@Schema(description = "岗位")
public class SysPost {

    @Schema(description = "岗位 ID", example = "1")
    private Long postId;

    @Schema(description = "岗位编码", example = "ceo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String postCode;
}
```

### 4.4 约定

| 注解 | 放哪 | 写什么 |
|---|---|---|
| `@Tag` | Controller 类 | 中文模块名，和菜单名一致 |
| `@Operation` | 每个公开方法 | 动词开头：分页查询 / 新增 / 修改 / 删除 / 解锁 |
| `@Schema` | 实体、DTO、枚举 | 字段中文名 + 示例值 |
| `@Parameter` | 复杂参数 | 仅当参数名不足以表达含义时加 |
| `@Hidden` | 不想暴露的内部接口 | 慎用，默认都应该可见 |

**不要**在 Controller 里写大段 Markdown 说明；业务说明放 `docs/`，文档只写接口契约。

---

## 5. 新增接口检查清单（每次必做）

| # | 检查项 | 说明 |
|---|---|---|
| 1 | 路径归属 | 后台接口必须 `/api/admin/{模块}/**`；公开接口单独放 `/api/file/**` 等白名单路径 |
| 2 | 权限码 | 加 `@RequiresPermission("platform:{模块}:{资源}:{动作}")`，并写进 Flyway 菜单 SQL |
| 3 | 操作日志 | 写操作加 `@Log(title="...", businessType=...)` |
| 4 | 参数校验 | DTO 加 `@Valid` + `@NotBlank/@Size`，不要手写 if 校验 |
| 5 | 返回结构 | 统一 `R<T>`；分页统一 `R<PageResult<T>>` |
| 6 | 前端 api.js | 新接口同步加到 `lx-ui-admin/src/api/*.js` |
| 7 | 菜单 SQL | 新页面要有 `Vxx__*.sql`，权限码与后端一致 |
| 8 | 文档核对 | 重启后 `/v3/api-docs` 里能找到新 path，`/doc.html` 能调试 |
| 9 | 权限回归 | 超管能调通；无权限账号 403；未登录 401 |
| 10 | 施工记录 | 在 `docs/21-build-log.md` 记一笔；新模块同步更新 `README.md` 的接口分组 |

---

## 6. 发版前怎么核对接口文档

### 6.1 自动检查脚本

已有：`.local/verify-apidoc.ps1`（纯 ASCII，本地专用），覆盖：

- `/doc.html` 200 且无需 Token；
- `/v3/api-docs` 是合法 OpenAPI 3；
- path 数 ≥ 50；
- 关键接口存在（登录、登出、当前用户、菜单、个人资料、改密、任务列表、服务器监控）；
- 每个 path 至少一个 HTTP 方法；
- `swagger-config`、`webjars` 资源可访问。

新增关键接口时，把路径追加到脚本的 `$required` 数组，防止“接口写完但没进文档”。

### 6.2 手工核对（30 秒）

```powershell
$doc = Invoke-RestMethod http://127.0.0.1:8080/v3/api-docs
$doc.paths.PSObject.Properties.Name.Count      # 当前总数
$doc.paths.PSObject.Properties.Name | Sort-Object   # 和上一版 diff
```

建议把“当前 path 数”写进发版记录，例如：
`v1.0.0 → 76 paths；新增 unlock 1 个 path`。

### 6.3 生产环境

生产**必须关闭文档**，配置在 `deploy/config/application-prod.yml`：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
knife4j:
  enable: false
  production: true
lx:
  api-doc:
    enabled: false
```

`lx.api-doc.enabled=false` 时，`ApiDocDisabledFilter` 会把以下路径直接回 404：

```
/doc.html
/v3/api-docs/**
/swagger-ui/**
/webjars/**
```

所以生产访问这些地址应是 **404（不是 401/500）**；发布包抽测已覆盖。

---

## 7. 多域 / 多版本怎么分组

v1.0 只有一个分组：`/api/admin/**`。后续会员域、门户域接进来时，用 `GroupedOpenApi` 拆组，
不要把所有接口堆在一个页面：

```java
@Bean
public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("01-后台管理")
            .pathsToMatch("/api/admin/**")
            .build();
}

@Bean
public GroupedOpenApi appApi() {
    return GroupedOpenApi.builder()
            .group("02-会员端")
            .pathsToMatch("/api/app/**")
            .build();
}

@Bean
public GroupedOpenApi portalApi() {
    return GroupedOpenApi.builder()
            .group("03-门户")
            .pathsToMatch("/api/portal/**")
            .build();
}
```

规则：

- 分组按“调用方”拆，不按技术层拆；
- 平台后台域（`/api/admin`）与会员域（`/api/app`）**不共 Token、不共分组**；
- 公开接口（`/api/file`、`/api/open`）单独组，并明确标注“无需登录”。

### 版本策略

- `v1.x` 内只做**新增**，不改已有 path 的语义；
- 必须破坏性变更时，新开 `/api/admin/v2/**` 或新模块，不原地改；
- 废弃接口先标 `@Deprecated` + `@Operation(deprecated = true)`，至少保留一个版本周期；
- 不在 URL 里塞 `/v1/` 再搞一套网关重写，当前规模不值得。

---

## 8. 代码生成器产出的接口

生成器的 `controller.java.vm` 目前也**不带文档注解**，生成的新模块会以类名/方法名展示。
如果第 4 节的注解约定落地，记得同步改模板：

```
lx-modules/lx-module-generator/src/main/resources/templates/gen/controller.java.vm
```

至少加：

- 类上 `@Tag(name = "${functionName}")`；
- 方法上 `@Operation(summary = "...")`；
- import `io.swagger.v3.oas.annotations.*`。

否则会出现“手写模块有中文、生成模块没有”的不一致。

---

## 9. 文档与其它文档的边界

| 文档 | 写什么 | 不写什么 |
|---|---|---|
| `/doc.html`（自动生成） | 接口契约：路径、方法、参数、响应、调试 | 业务规则、权限矩阵、部署步骤 |
| `README.md` | 接口分组总览、启动方式、账号 | 每个字段的含义 |
| `docs/04-permission.md` | 权限码规范与矩阵 | 接口参数 |
| `docs/21-build-log.md` | 本轮新增/修改了哪些接口 | 全量接口清单 |
| 本文 | 文档怎么生成、怎么更新、怎么核对 | 具体业务接口说明 |

**不要把接口文档当需求文档。** 需求写在 `docs/02-feature-list.md` 和 `docs/17-v1-plan.md`。

---

## 10. 一句话回答

**新功能写完 Controller，文档就自动更新了**；你要做的是三件事：

1. 按规范写 `@RestController` + 权限码 + `@Log` + 校验；
2. 重启后打开 `/doc.html` 确认新 path 在、能调通；
3. 把 path 数写进发版记录，并确保生产是 404。

要中文接口名，就按第 4 节加 `@Tag` / `@Operation` / `@Schema`——那是增强，不是前提。
