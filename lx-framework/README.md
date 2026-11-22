# lx-framework · 模块机制与自动装配

## 这个目录是做什么的

后台**基座的“插拔器”**：读取 `lx.module.*` 开关、生成模块注册表、向前端下发启用状态。

## 包结构

```
com.lingxiao.framework
├─ module/LxModuleProperties          配置绑定（Map<String, ModuleSwitch>）
├─ module/ModuleRegistry              查询是否启用 / 列出全部模块（实现 ModuleProvider）
├─ module/ModuleMigrationLocations    按开关拼装 Flyway 迁移目录
├─ module/ModuleInfo                  只读视图
├─ module/ModuleController            GET /api/admin/modules
└─ config/LxFrameworkAutoConfiguration 配置绑定 + Flyway customizer
```

`ModuleProvider` 接口在 `lx-common-core`，这样 `lx-module-system` 判断权限时
可以依赖接口而不是 `lx-framework`，保持“基座不 import 业务、业务不 import 基座实现”的单向依赖。

## 开关怎么写

```yaml
lx:
  module:
    modules:
      demo:
        enabled: false
        name: 演示模块
        description: 验证可选模块关闭后 Bean 不加载、接口 404
```

**新增模块只加一段 YAML，不改基座代码。** 这就是用 `Map` 而不是给每个模块写字段的原因。

## 启用判定规则

| 情况 | 结果 |
|---|---|
| `system` / `file` | 永远启用（基座模块，写 `false` 也不生效） |
| 显式 `enabled=true` | 启用 |
| 显式 `enabled=false` | 关闭 |
| 没有配置（如已移除依赖的 `cms`） | 关闭，库里残留菜单也不下发 |
| `sys_menu.module_code` 为空 | 按启用处理，兼容旧数据 |

关闭后不只是接口 404：

1. `sys_menu` 里属于该模块的菜单不下发；
2. 目录的子节点全被过滤时，空目录一起剪掉；
3. `StpInterfaceImpl` / `/auth/info` 不再返回该模块的权限码；
4. Flyway 不扫描 `db/migration/<code>`；
5. 前端 `/api/admin/modules` 拿到 `enabled=false`，不注册对应路由，直接访问落 404。

## Flyway 目录怎么来的

`ModuleMigrationLocations` 拼装 `classpath:db/migration/<code>`：

- `system`、`file` 固定参与；
- 其它模块必须 `enabled=true` **且** 目录真实存在（不存在就跳过，避免 Flyway 报 location 不存在）；
- 结果按模块编码排序，日志里能看到最终列表。

配套两个 Flyway 开关（`application.yml`）：

```yaml
spring:
  flyway:
    out-of-order: true            # 模块后启用时，旧版本号迁移仍能补跑
    ignore-missing-migrations: true  # 模块关闭后，已执行的迁移不在 classpath 也不报错
```

注意：`ignore-migration-patterns` 是 Flyway Teams 能力，Community 版只能用
`ignore-missing-migrations`。

## 关闭模块时发生什么

不是“隐藏菜单”，而是：

1. 模块自己的 `@ConditionalOnProperty` 不满足 → **Bean 不装配**；
2. 接口不存在 → 返回 **404**（不是“接口在但抛异常”）；
3. `/api/admin/modules` 返回 `enabled=false` → 前端不注册路由、不显示菜单；
4. 生产可在 Maven 打包阶段直接不引入该模块 jar。

## 依赖方向

```
lx-framework → lx-common-core + lx-common-web + spring-boot-starter-web
```

**不 import 任何 `lx-module-*` 的类。** 基座只认“模块编码 + 开关”。

## 禁止事项

- ❌ 在 `lx-framework` 里 `import com.lingxiao.modules.*`（基座会被具体模块绑架）。
- ❌ 让模块注册表负责实例化模块 Bean（应由模块自己的条件注解控制）。
- ❌ 把模块开关做成数据库表却不做缓存（启动期就要用，不能依赖运行期查库）。
