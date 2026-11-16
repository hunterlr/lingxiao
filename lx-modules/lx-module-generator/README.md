# lx-module-generator · 代码生成器模块

## 这个目录是做什么的

把一个已有的数据库表变成一套能直接落进工程、能编译、能调试的 CRUD 代码：

```
导入表 → 配表信息 → 配字段 → 预览 → 下载 ZIP → 放入模块 → 执行菜单 SQL
```

**v1.0 已实装**：表元数据读取、表 / 字段配置、命名与类型校验、Velocity 模板渲染、
7 个文件的在线预览、ZIP 下载、菜单 / 按钮 SQL 生成、模块开关、按钮级权限拆分。

**不做**：往工程目录写文件、多表 / 主子表 / 树表、DTO / VO / MapStruct、i18n 模板、
在线编辑模板、生成前端路由注册代码。原因见文末「已知限制」。

## 边界：只预览 + 下载，不写盘

写盘要处理包名白名单、覆盖确认、IDE 热加载、Windows 文件占用、目录不存在的回滚等一堆问题。
v1.0 先把「生成结果正确」做扎实：下载后由开发者决定放进哪个模块，路径问题交给人。

这样也自然没有路径穿越风险：模板只渲染字符串，不碰文件系统。

## 包结构

```
com.lingxiao.modules.generator
├─ controller/
│  └─ GenController              /api/admin/tool/gen/**
├─ service/
│  ├─ GenTableService            导入 / 表配置 / 字段配置 / 删除
│  ├─ DatabaseMetaService        JDBC 元数据读取（表、列、主键、注释）
│  └─ CodeGenService             Velocity 上下文、预览、ZIP、生成前校验
├─ support/
│  ├─ GenConstants               查询方式 / HTML 控件 / Java 类型 / 命名正则
│  ├─ JavaTypeResolver           数据库类型 → Java 类型（归一化后匹配）
│  ├─ NamingUtils                下划线 → PascalCase / camelCase，关键字避让
│  └─ TextUtils                  按输出语境清洗文本（Java / HTML / JS / SQL）
├─ entity/      GenTable、GenTableColumn
├─ mapper/      GenTableMapper、GenTableColumnMapper
├─ dto/         DbTableVO、TableMeta、ImportTableRequest、ImportResultVO、GenPreviewVO
└─ resources/
   ├─ db/migration/generator/V12__generator_schema.sql
   └─ templates/gen/*.vm        7 个模板
```

依赖方向：只依赖 `lx-common-*`，**不依赖 `lx-module-system`**。
生成物作者通过 `common-core` 的 `ConfigProvider` 读 `sys.gen.author`；
没有系统模块（裁剪部署）时回退当前登录用户，再回退 `lingxiao`。

## 表结构（V12）

| 表 | 用途 | 删除方式 |
|---|---|---|
| `gen_table` | 表级配置：类名、模块名、业务名、功能名、作者、路由 | 物理删除 |
| `gen_table_column` | 字段级配置：Java 类型 / 字段名、表单 / 列表 / 查询标记、控件、字典、排序 | 随表配置物理删除 |

两个刻意的选择：

- **不建逻辑删除**：`del_flag` 会让唯一索引 `uk_gen_table_name` 顶住——
  删掉配置后同名表再也导不进来。工具表没有审计追溯需求，直接物理删。
- **不建外键**：删配置时 Service 先删子表再删主表，与 `sys_user_role` 等关联表保持一致，
  便于后续切库。

同一次迁移里还补了：

| 变更 | 说明 |
|---|---|
| 参数 `sys.gen.author = lingxiao`（text） | 导入表时写入 `function_author`，`CodeGenService` 会读 |
| 按钮权限 2201~2205 | import / edit / remove / preview / download 分开授权 |
| 超管授权 | `INSERT ... SELECT ... NOT EXISTS`，可重复执行不报错 |

菜单 22 已存在（`/tool/gen` → `tool/gen/index`，`platform:tool:gen:list`），V12 只补按钮。

## 接口

全部挂在 `/api/admin/tool/gen`，响应仍是 `{code,msg,data}`。

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/list` | `platform:tool:gen:list` | 分页；按表名 / 表注释模糊查 |
| GET | `/db/list` | `platform:tool:gen:list` | 当前库可导入表；已导入的标 `imported=true`，前端置灰 |
| GET | `/{tableId}` | `platform:tool:gen:list` | 表配置 + 字段配置（按 sort） |
| POST | `/import` | `platform:tool:gen:import` | 逐表导入，逐表反馈成功 / 跳过原因 |
| PUT | `/{tableId}` | `platform:tool:gen:edit` | 改表级配置 |
| PUT | `/{tableId}/columns` | `platform:tool:gen:edit` | 批量改字段配置（全量校验后逐行更新） |
| DELETE | `/{tableId}` | `platform:tool:gen:remove` | 只删配置，**不删物理表** |
| GET | `/preview/{tableId}` | `platform:tool:gen:preview` | 返回 7 个文件的路径、语言、内容 |
| GET | `/download/{tableId}` | `platform:tool:gen:download` | 返回 ZIP（`application/octet-stream`） |

`/download` 故意不加 `@Log`：操作日志切面会把返回值序列化进 `json_result`，
ZIP 字节数组会被 Base64 撑大再截断，纯属浪费内存。生成内容已在预览接口留痕。

### 导入的逐表反馈

某张表没主键不应该让另外九张也导不进来，所以是「逐表 try/catch + 逐表反馈」：

```json
{
  "imported": 1,
  "skipped": [
    "gen_verify_nopk：表 gen_verify_nopk 没有主键，v1.0 生成器不支持无主键表",
    "gen_verify_composite：表 gen_verify_composite 是复合主键，v1.0 生成器只支持单主键",
    "gen_verify_demo：重复选择"
  ]
}
```

跳过原因包括：重复选择、表名不合法、已导入、表不存在、无主键、复合主键、主键类型不支持、
无法推导合法类名 / 业务名。未捕获的数据库异常仍整批回滚，不会留下「表配置有了、字段写一半」的脏数据。

## 元数据怎么读

用 JDBC `DatabaseMetaData`，不写 `information_schema` 查询。达梦 / 金仓 / PostgreSQL 的元数据表
名和字段都不一样，写四套查询等于写四个生成器；JDBC 规范把这层差异抹平了。

原则是「读不到就给默认值，绝不让导入失败」：

- 表注释 / 列注释读不到时为空字符串，生成时按列名兜底；
- 只读当前连接对应的库，不接收 catalog / schema 参数，避免被拿来读别的库；
- `gen_table`、`gen_table_column`、`flyway_schema_history`、`qrtz_locks` 不参与生成。

**MySQL 必须带 `useInformationSchema=true`**，否则驱动走 `SHOW TABLE STATUS`，
`REMARKS`（表注释）读出来是空的。该参数已写进 `lx-boot` 的 JDBC URL。

## 类型与命名

| 数据库类型（归一化后） | Java 类型 |
|---|---|
| `bigint` / `int8` / `serial8` / `bigserial` | `Long` |
| `int` / `integer` / `smallint` / `tinyint` / `year` / `serial` | `Integer` |
| `decimal` / `numeric` / `number` / `money` | `BigDecimal` |
| `double` / `float8` | `Double` |
| `float` / `real` / `float4` | `Float` |
| `bit` / `bool` / `boolean` | `Boolean` |
| `date` | `LocalDate` |
| `datetime` / `timestamp` / `timestamptz` / `datetime2` | `LocalDateTime` |
| `time` / `timetz` | `LocalTime` |
| `binary` / `varbinary` / `blob` / `bytea` / `image` | `byte[]` |
| 其它 | `String` |

归一化会去掉长度、`unsigned`、`zerofill`；匹配不到统一回退 `String`——
宁可生成一个字符串字段让人手动改，也不要生成编译不过的类型。

生成物里所有会进入代码的文本都按语境清洗（`TextUtils`）：

| 场景 | 处理 |
|---|---|
| Java 字符串 / javadoc | 去掉引号、反斜杠、`*/`，避免截断注释或字符串 |
| HTML 文本 | 转义 `& < > " '` |
| JS 单引号字符串 | 去引号 / 反斜杠，`< >` 换成 `‹ ›`（`</script>` 会截断 Vue SFC） |
| SQL 字面量 | 转义单引号，长度截断 |

## 生成物（固定 7 个文件）

| # | 文件 | 说明 |
|---|---|---|
| 1 | `src/main/java/.../entity/{Class}.java` | 按真实列生成，**不继承 BaseEntity**；自增主键用 `@TableId(type = IdType.AUTO)` |
| 2 | `src/main/java/.../mapper/{Class}Mapper.java` | MyBatis-Plus 空接口 |
| 3 | `src/main/java/.../service/{Class}Service.java` | 具体类（与 `SysJobService` 风格一致），不做 interface + impl 双份 |
| 4 | `src/main/java/.../controller/{Class}Controller.java` | `/api/admin/{module}/{business}`、`@RequiresPermission`、`@Log`、`@DateTimeFormat` |
| 5 | `web/src/api/{business}.js` | 走基座 `request.js`，路径 `/admin{apiPath}`（响应已解包） |
| 6 | `web/src/views/{module}/{business}/index.vue` | 查询 + 表格 + 新增 / 编辑弹窗 + 删除；有字典时用 `DictSelect` / `DictTag` |
| 7 | `sql/{table}_menu.sql` | 菜单 + 3 个按钮 + 角色授权，全部用占位符 |

ZIP 内的根目录是表名，例如 `biz_order/src/main/java/...`。

**模板变量**（`CodeGenService.prepare`）：`className / moduleName / businessName / functionName`、
`packageName`、`permissionPrefix = platform:{module}:{business}`、`apiPath`、`routePath`、`viewPath`、
`pk / pkField / pkType`、`columns / listColumns / queryColumns / formColumns`、`hasDict / hasStringQuery`、
各语境下的功能名（`functionNameHtml / Java / Js / Sql`）。

### 菜单 SQL 的占位符

```sql
{menuId}          -- 菜单主键
{parentMenuId}    -- 上级目录
{buttonAddId} / {buttonEditId} / {buttonRemoveId}
{roleId}          -- 要授权的角色
```

不用 `LAST_INSERT_ID()`：菜单 ID 由应用侧雪花算法生成，迁移脚本保持「逻辑 SQL + 占位符」，
在 MySQL / PostgreSQL / 达梦 / 金仓上都能执行。

## 模板文件

`src/main/resources/templates/gen/`：

| 模板 | 输出 |
|---|---|
| `entity.java.vm` | Entity |
| `mapper.java.vm` | Mapper |
| `service.java.vm` | Service（`hasStringQuery` 才 import `StringUtils`；日期类型固定 import） |
| `controller.java.vm` | Controller（日期参数用 `@DateTimeFormat`） |
| `api.js.vm` | 前端接口封装 |
| `index.vue.vm` | 前端页面 |
| `menu.sql.vm` | 菜单 / 按钮 / 授权 SQL |

Velocity 2.3，`RUNTIME_REFERENCES_STRICT=false`：注释、字典这类可选变量为空时输出空串而不是抛异常。
模板从 classpath 加载，改模板要重新打包（v1.0 不做运行时热加载）。

**生成前再校验一次命名**：导入时校验过，但配置可以被接口直接改、DB 也可能被人工改。
生成是所有模板变量的最后一道出口，这里不校验就等于把风险交给模板。

## 模块开关

```yaml
lx:
  module:
    modules:
      generator:
        enabled: true
```

- `enabled=true`：Controller / Service / Mapper Bean 全部装配；
- `enabled=false`：`/api/admin/tool/gen/**` 返回 404，菜单落到前端占位页，**基座照常启动**；
- 生产环境建议关闭：它能把整个库的表结构、字段和代码暴露给有权限的后台账号。

## 新增一个模板

1. 在 `templates/gen/` 放 `.vm`；
2. 在 `CodeGenService.specs()` 追加一条 `TemplateSpec(模板路径, 输出路径, 显示名, 语言)`；
3. 需要新变量就在 `prepare()` 里 `context.put(...)`，并在 `TextUtils` 里加对应的清洗方法；
4. 跑 `.local/verify-gen.ps1`，它会断言预览文件数、路径、关键内容，并 javac 编译生成的 Java。

## 已知限制

| 项 | 说明 |
|---|---|
| 数据库 | 只在 MySQL 5.7 实测；其它库靠 `DatabaseMetaData` 兜底，注释 / 自增可能缺失 |
| 表形态 | 仅单表、单主键；无主键 / 复合主键在导入时拒绝 |
| 主键类型 | 仅 `Long / Integer / String`（Controller 路径参数与 Service 入参都用它） |
| 生成能力 | 无主子表、树表、DTO / VO、MapStruct、OpenAPI 注解、i18n 模板 |
| 查询条件 | `EQ / LIKE / GT / GE / LT / LE`；`LIKE` 只允许字符串列；不支持 `BETWEEN` |
| 写入工程 | 只下载 ZIP，需人工放进模块并编译；不改 `lx-boot` 依赖，也不注册前端路由 |
| 模板热加载 | 不支持，改模板要重新打包 |
| 字段元数据 | 列名、主键、自增在导入时固定，更新接口只改展示与生成策略 |

## 验证

- `.local/verify-gen.ps1`：**105 项**，覆盖 V12 迁移、注释读取、导入跳过、默认值、配置校验、
  预览内容与转义、ZIP 与预览一致、菜单 SQL 事务执行、javac 编译 4 个类、删除保留物理表、
  权限拆分（只读角色 403）、匿名 401、fixture 清理。
- `.local/uitest/gen.mjs`：**9 项**，覆盖页面渲染、导入、配置抽屉、字段配置保存、预览 7 个文件、
  下载提示、删除、控制台 0 error。

编译验证方式：解压 `lx-boot/target/lingxiao.jar` 的 `BOOT-INF/lib` + Lombok 1.18.30 作 classpath，
用 `javac -encoding UTF-8 -source 1.8 -target 1.8` 编译生成的 4 个 Java 文件。
