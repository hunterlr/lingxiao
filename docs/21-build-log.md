# 21 · v1.0 施工记录（Day 1 ~ Day 3）

> 本文是**施工日志**，不是规划稿。记录每一步“做了什么、用了什么命令、结果如何、遇到什么错、怎么修的”。
> 规划口径看 [17-v1-plan.md](17-v1-plan.md)，Day-1 清单看 [18-day1-checklist.md](18-day1-checklist.md)。
>
> 记录时间：2026-11-15
> 施工范围：v1.0 后台基座（后端 17 个模块 + 后台前端 `lx-ui-admin`）

---

## 0. 结论速览

| 项目 | 状态 | 证据 |
|---|---|---|
| Maven 多模块骨架 | ✅ 完成 | 17 个 reactor 模块，`mvn -DskipTests package` 成功 |
| 数据库迁移（20 张表） | ✅ 完成 | Flyway V1~V11 全部 success，`information_schema` 统计 20 张业务表 + 1 张历史表 |
| 登录闭环 | ✅ 完成 | 验证码 → 登录 → 当前用户 → 菜单树 → 登出 → Token 失效 |
| 平台域权限 | ✅ 完成 | `StpInterfaceImpl` 只响应 `platform` 域，超管返回 `*:*:*` |
| 文件中心 | ✅ 完成 | 上传 / 秒传 / 列表搜索 / 改名留痕 / 公开下载 |
| 模块开关 | ✅ 完成 | demo 关闭时接口 404，开启时返回 `pong` |
| 定时任务模块 | ✅ 完成 | Spring Task + 数据库锁；后端 51 项 + 模块关闭 5 项 + UI 9 项（见第 29 节） |
| 接口文档 | ✅ 完成 | SpringDoc 1.7 + Knife4j 4.4，`/doc.html` 200、68 条路径；后端 26 项 + UI 10 项（见第 30 节） |
| 个人中心自助 | ✅ 完成 | 改资料 / 原密码校验改密 / 全部会话失效；后端 31 项 + UI 13 项（见第 30 节） |
| 会话策略 | ✅ 完成 | 默认单会话踢旧、参数可开多端；删除 / 停用 / 重置立即踢下线；接口 18 项（见第 44 节） |
| 多语言 L1 | ✅ 完成 | 中英界面键集合 903 / 903 对齐；错误码、菜单、参数、字典全走语言包（见第 41 / 45 节） |
| 品牌 / 版本收口 | ✅ 完成 | 唯一真源 `lx.app.*` + 根 pom 版本；公开接口 `/api/admin/app/profile`（见第 46 节） |
| 发布包演练 | ✅ 完成 | `deploy/package.ps1` 打包，空白库 + prod profile + 静态代理跑通登录 / 上传（见第 47 节） |
| 模块插拔最后一公里 | ✅ 完成 | 菜单 / 权限 / 迁移按模块开关过滤；V18 修正菜单归属；112 项单测（见第 48 节） |
| 系统管理 CRUD | ✅ 完成 | 8 个控制器、25 个按钮权限、74 项接口验证全部通过（见第 13 节） |
| 前端 `lx-ui-admin` | ✅ 完成 | 17 个页面、动态路由、22 项 UI 冒烟全通过、验证码开启下真人登录成功（见第 20 / 29 / 30 节） |

---

## 1. 环境确认

### 1.1 做了什么

在动手前先把本机能力摸清，避免“写到一半发现没有依赖”。

### 1.2 命令与结果

```powershell
java -version
mvn -version
node -v; npm -v
mysql --version
```

结果：

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 1.8.0_351 | 与 v1.0 主线一致（JDK 8 字节码） |
| Maven | 3.9.1 | 使用阿里云镜像 |
| Node | 26.7.0 / npm 11.19.0 | 前端阶段使用 |
| MySQL | 5.7.26（phpstudy 自带） | **注意：Flyway 8 已不支持 5.7** |
| Docker | 无 | 不走容器路线，直接本机进程 |
| Redis | 未安装/未启动 | v1.0 先用内存 + Caffeine |

### 1.3 结论

- 不引入 Docker，MySQL 直接用 phpstudy 的二进制。
- 不改动用户已有的 phpstudy MySQL 实例，改为**项目内隔离实例**（见第 2 节）。
- Redis 缺失不阻塞：Sa-Token 用内存存储，验证码用 Caffeine；`lx-common-redis` 保留但不装配。

---

## 2. 本地隔离 MySQL（端口 3307）

### 2.1 为什么隔离

用户本机 `D:\phpstudy_pro\Extensions\MySQL5.7.26` 已有实例，但：

1. 直接启动报 `unknown variable '%23default-storage-engine=MyIsam'`（my.ini 里 `#` 被 URL 编码成 `%23`）；
2. 不想污染用户已有数据库。

因此复制二进制、在项目内新建数据目录，用独立 my.ini 起一个 3307 实例。

### 2.2 做了什么

1. 新建 `.local/mysql/my.ini`：

```ini
[mysqld]
basedir=D:/phpstudy_pro/Extensions/MySQL5.7.26
datadir=E:/java/lingxiao/.local/mysql/data
port=3307
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
default-storage-engine=InnoDB
max_connections=200
explicit_defaults_for_timestamp=1
log-error=E:/java/lingxiao/.local/mysql/mysql.err
```

2. 初始化数据目录（root 空密码）：

```powershell
& "D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysqld.exe" `
  --defaults-file="E:\java\lingxiao\.local\mysql\my.ini" --initialize-insecure --console
```

3. 后台启动：

```powershell
& "D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysqld.exe" `
  --defaults-file="E:\java\lingxiao\.local\mysql\my.ini" --console
```

4. 建库建用户：

```sql
CREATE DATABASE IF NOT EXISTS lingxiao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS 'lingxiao'@'%' IDENTIFIED BY 'lingxiao123';
GRANT ALL PRIVILEGES ON lingxiao.* TO 'lingxiao'@'%';
FLUSH PRIVILEGES;
```

### 2.3 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `unknown variable '%23default-storage-engine=MyIsam'` | phpstudy 的 my.ini 里 `#` 被写成 `%23`，MySQL 不认 | 不用它的 my.ini，自己写一份 |
| `--initialize-insecure` 无输出但退出码 1 | PowerShell 把 stderr 当错误；实际初始化成功 | 看 `mysql.err` 与 data 目录文件确认 |
| `mysql -h127.0.0.1 -P3307` 报 `Unknown MySQL server host '127'` | PowerShell 把 `-h127.0.0.1` 解析成参数 `-h 127` + `.0.0.1` | 改用 `--host=127.0.0.1 --port=3307` 长参数 |

### 2.4 连接信息（开发默认值）

```
host=127.0.0.1  port=3307  db=lingxiao  user=lingxiao  password=lingxiao123
```

> 生产部署时用环境变量覆盖：`DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD`。

---

## 3. 工程骨架（17 个模块）

### 3.1 目录结构

```
lingxiao/
├─ pom.xml                     根聚合：JDK 8、编码、插件、BOM 引入
├─ lx-dependencies/            唯一依赖版本入口（BOM）
├─ lx-common/                  通用能力（6 个子模块）
│  ├─ lx-common-core           响应 / 错误码 / 异常 / 常量 / 分页
│  ├─ lx-common-web            Web 配置、全局异常、Jackson、CORS、请求工具
│  ├─ lx-common-security       Sa-Token 平台域、登录上下文、鉴权异常
│  ├─ lx-common-mybatis        MyBatis-Plus 插件、审计填充、BaseEntity
│  ├─ lx-common-redis          Redis 配置（v1.0 可选，未装配）
│  └─ lx-common-log            @Log 注解与切面
├─ lx-framework/               模块开关、模块注册表、/api/admin/modules
├─ lx-api/
│  └─ lx-admin-api             后台接口契约（登录 / 用户 / 菜单 / 验证码 DTO）
├─ lx-modules/
│  ├─ lx-module-system         认证、用户、角色、菜单、日志
│  ├─ lx-module-file           文件中心
│  └─ lx-module-demo           可选模块关闭态验证
└─ lx-boot/                    唯一启动模块
```

### 3.2 为什么这么分

- **common 不碰业务**：换任何后台项目都能用，避免“工具类里塞业务”。
- **framework 不 import 任何 module**：基座只认“模块编码 + 开关”，模块自己决定 Bean 是否装配。
- **modules 之间不互相依赖**：system 不依赖 file，file 不依赖 system；跨模块只通过 `lx-api` 契约或 fileId 引用。
- **boot 不写业务**：只负责启动、装配、配置、打包。

### 3.3 首次构建报错与修复

**报错：**

```
The dependencies of type=pom and with scope=import form a cycle:
com.lingxiao:lingxiao -> com.lingxiao:lx-dependencies -> com.lingxiao:lx-dependencies
```

**原因：** 根 pom `import` 了 `lx-dependencies`，而 `lx-dependencies` 又 `<parent>` 指向根 pom，形成循环。

**修复：** `lx-dependencies` 不再继承根 pom，独立声明 `groupId/artifactId/version`，并在文件头注释说明原因。

---

## 4. 通用能力（lx-common）

### 4.1 做了什么

| 子模块 | 关键类 | 作用 |
|---|---|---|
| core | `R<T>` / `PageResult<T>` / `ErrorCode` / `ServiceException` / `CommonConstants` | 统一响应与错误码，前端只判断 `code == 0` |
| web | `GlobalExceptionHandler` / `JacksonConfig` / `CorsConfig` / `ServletUtils` | 异常不出堆栈、Long 转字符串、开发期跨域、取真实 IP |
| security | `StpPlatformUtil` / `LoginHelper` / `SaTokenConfigure` / `SaTokenExceptionHandler` | 平台域独立 Token、登录上下文、拦截 `/api/admin/**`、401/403 区分 |
| mybatis | `MybatisPlusConfig` / `AuditMetaObjectHandler` / `BaseEntity` | 分页、乐观锁、防全表更新、审计时间自动填充 |
| redis | `RedisConfig` / `SaTokenRedisConfig` | `lx.redis.enabled=true` 才装配；默认关闭，第 24 节已接入 `lx-boot` |
| log | `@Log` / `BusinessType` / `LogAspect` | 写操作留痕，v1.0 先落应用日志 |

### 4.2 关键决策

1. **Long 全部序列化为字符串**：雪花 ID 19 位，JS `Number` 只能安全表示 16 位，不转字符串会“详情查不到 / 删错行”。
2. **`StpPlatformUtil` 而不是 `StpUtil`**：三套域必须互不认 Token，各自持有独立 `StpLogic`。
3. **`LoginHelper` 从 Session 读用户**：避免每个请求查库；用户改资料时由业务主动刷新 Session。
4. **审计只填时间，不填人**：`common-mybatis` 不应反向依赖 `security`，创建人由业务 Service 显式设置。

### 4.3 踩坑

| 现象 | 原因 | 修复 |
|---|---|---|
| 编译失败：`StpLogic.getSession(false)` 不存在 | Sa-Token 1.37 的 `getSession` 没有布尔重载 | `LoginHelper` 改为 `getSession()` + try/catch，未登录返回 null |
| 响应体多出 `"success": true` | Lombok 生成 `isSuccess()`，被 Jackson 当成属性 | `R.isSuccess()` 加 `@JsonIgnore`，并给 `lx-common-core` 加 `jackson-annotations`（只引注解包） |

---

## 5. 数据库迁移（19 张表）

### 5.1 迁移文件

| 文件 | 内容 | 版本 |
|---|---|---|
| `lx-module-system/.../db/migration/system/V1__system_schema.sql` | 17 张 `sys_*` 表 | V1 |
| `lx-module-system/.../db/migration/system/V2__system_seed.sql` | admin、角色、23 个菜单、字典、参数、公告、定时任务 | V2 |
| `lx-module-file/.../db/migration/file/V3__file_schema.sql` | `sys_file` / `sys_file_name_log` | V3 |

`application.yml` 中按模块分目录：

```yaml
spring.flyway.locations: classpath:db/migration/system,classpath:db/migration/file
```

### 5.2 表清单（19 张业务表）

```
sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu,
sys_dept, sys_dept_closure, sys_post, sys_user_post,
sys_dict_type, sys_dict_data, sys_config, sys_notice,
sys_login_log, sys_oper_log, sys_job, sys_job_log,
sys_file, sys_file_name_log
```

### 5.3 建表约定

1. 平台后台域，**全部不带 `tenant_id`**（会员 / 租户表将来另建）。
2. 主键 `bigint`，应用侧雪花算法（`IdType.ASSIGN_ID`），不用自增。
3. 审计四字段 + `del_flag` + `version`，与 `BaseEntity` 一一对应。
4. **不使用外键**，跨表一致性由应用层保证（便于后续分库 / 多库）。
5. 时间统一 `datetime(3)`，应用侧填充，不用数据库 `now()`。
6. 部门树用**闭包表** `sys_dept_closure`，替代 MySQL 方言 `find_in_set`。

### 5.4 种子数据

- 部门：凌霄科技；岗位：总经理。
- 用户：`admin / admin123`，BCrypt 哈希 `$2a$10$...`（用 Python bcrypt 生成）。
- 角色：`admin`（超级管理员，数据范围=全部），已关联全部 23 个菜单。
- 菜单：4 个根（系统管理 / 系统监控 / 系统工具 / 个人中心）+ 19 个子菜单。
- 字典：`sys_common_status` / `sys_user_sex` / `sys_yes_no`。
- 参数：验证码开关、日志保留天数、上传上限。
- 公告 1 条、定时任务 1 条（默认暂停）。

### 5.5 Flyway 与 MySQL 5.7 的三次交锋

**第一次：Flyway 8.5.13 报不支持**

```
org.flywaydb.core.api.FlywayException: Unsupported Database: MySQL 5.7
```

**第二次：补 `flyway-mysql` 后仍失败**

```
FlywayEditionUpgradeRequiredException: Flyway Teams Edition or MySQL upgrade required:
MySQL 5.7 is no longer supported by Flyway Community Edition, but still supported by Flyway Teams Edition.
```

**根因：** Flyway 8.x Community 主动放弃 MySQL 5.7，与驱动无关，加 `flyway-mysql` 只能解决“识别数据库”，解决不了“版本许可”。

**最终修复：** 降级到 **Flyway 7.15.0**（同时支持 MySQL 5.7 / 8.0 与 JDK 8）。

```xml
<!-- 根 pom.xml -->
<flyway.version>7.15.0</flyway.version>

<!-- lx-boot/pom.xml -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
  <version>7.15.0</version>
</dependency>
```

**取舍：** 生产升级到 MySQL 8 后再升 Flyway 8/9；当前阶段“能跑”优先。此决定已记录，避免后面有人误升。

### 5.6 迁移结果

启动日志：

```
Flyway Community Edition 7.15.0 by Redgate
Database: jdbc:mysql://127.0.0.1:3307/lingxiao (MySQL 5.7)
Successfully applied 3 migrations to schema `lingxiao`, now at version v3
```

数据库核对：

```sql
SELECT version, description, success FROM flyway_schema_history;
-- 1 system schema 1 / 2 system seed 1 / 3 file schema 1

SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lingxiao';
-- 20（19 业务表 + flyway_schema_history）

SELECT user_id, username, nickname, status FROM sys_user;
-- 1 admin 超级管理员 0

SELECT COUNT(*) FROM sys_menu;      -- 23
SELECT COUNT(*) FROM sys_role_menu; -- 23
```

---

## 6. 登录权限闭环

### 6.1 接口

| 方法 | 路径 | 说明 | 是否放行 |
|---|---|---|---|
| GET | `/api/admin/auth/captcha` | 图形验证码（data URI） | 是 |
| POST | `/api/admin/auth/login` | 登录，返回 Token | 是 |
| POST | `/api/admin/auth/logout` | 登出 | 否 |
| GET | `/api/admin/auth/info` | 当前用户 + 角色 + 权限码 | 否 |
| GET | `/api/admin/auth/menus` | 当前用户菜单树 | 否 |
| GET | `/api/admin/modules` | 模块开关状态 | 否 |

### 6.2 登录流程

```
验证码校验（一次性，5 分钟过期）
  → 按 username 查 sys_user
  → BCrypt matches 校验密码
  → 校验 status=0
  → 更新 login_ip / login_time
  → StpPlatformUtil.login(userId)
  → 写 Session（userId / username / nickname）
  → 写 sys_login_log（成功/失败都记）
  → 返回 tokenName / tokenValue / expiresIn
```

### 6.3 关键实现点

1. **验证码先校验**：避免用登录接口做用户名枚举。
2. **失败也写日志**：`sys_login_log.status=1` + 原因，为后续账号锁定、暴力破解分析留数据。
3. **超管判定**：`userId == 1` 直接返回 `*:*:*` 与全部菜单，不依赖 `sys_role_menu` 数据是否完整。
4. **菜单转树容错**：父节点不在结果集时（角色只授权了子菜单）作为根节点返回，避免“授权了却看不到”。
5. **`StpInterfaceImpl` 只认 platform 域**：其他域直接返回空，防止 Token 串域拿到后台权限。
6. **鉴权异常优先级最高**：`@Order(HIGHEST_PRECEDENCE)`，否则被通用 `Exception` 分支吃掉，前端只能看到 9000。

### 6.4 验证码开关（本阶段新增）

为了能脚本化验证登录链路，新增 `lx.captcha.enabled`：

- **默认 `true`**，生产必须保持开启；
- 只在本地联调 / 自动化测试时用启动参数 `--lx.captcha.enabled=false` 临时关闭；
- 代码注释已写明风险，避免有人误关。

### 6.5 踩坑

| 现象 | 原因 | 修复 |
|---|---|---|
| 启动报 `No qualifying bean of type SysUserMapper` | 启动类没有 `@MapperScan`，MyBatis 只扫描了 `com.lingxiao.boot` | 加 `@MapperScan(basePackages="com.lingxiao.modules", annotationClass=Mapper.class)`，只扫显式 `@Mapper` |
| `No MyBatis mapper was found in '[com.lingxiao.boot]'` 警告 | 同上 | 同上，加扫描后消失 |

---

## 7. 文件中心

### 7.1 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/file/upload` | 上传（multipart：file / bizType / remark） |
| GET | `/api/admin/file/list` | 文件库分页，`keyword` 匹配现名 / 原名 / 备注 |
| GET | `/api/admin/file/{fileId}` | 文件详情 |
| PUT | `/api/admin/file/{fileId}/rename` | 改名（只改 `display_name`） |
| GET | `/api/admin/file/{fileId}/name-history` | 改名历史 |
| GET | `/api/file/{fileId}` | 公开访问 / 下载（不在 `/api/admin/**` 下） |

### 7.2 三个名字分离（核心设计）

| 字段 | 是否可变 | 用途 |
|---|---|---|
| `original_name` | **不可变** | 上传时的原始名，回答“原来叫什么” |
| `display_name` | 可变 | 文件库展示名，为空回退 `original_name` |
| `file_name` / `path` | **不可变** | UUID 存储名与相对路径，改名不影响 URL 与引用 |

改名时写 `sys_file_name_log`（旧名 / 新名 / 操作人 / 时间），详情页可展示“曾用名”。

### 7.3 上传流程

```
校验非空 / 大小
  → 取扩展名并比对白名单（SVG 默认不放行，防 XSS）
  → 计算 SHA-256
  → 命中已有 hash → 直接返回已有 fileId（秒传）
  → 生成 UUID 文件名，路径 {bizType}/yyyy/MM/uuid.ext
  → 存储抽象写入（LocalStorageService，路径 normalize + 根目录校验，防穿越）
  → 写 sys_file
```

### 7.4 踩坑

| 现象 | 原因 | 修复 |
|---|---|---|
| `findByHash` 用了 `.last("LIMIT 1")` | 这是 MySQL 方言，与“多库兼容”目标冲突 | 去掉，改为查列表取第一条（hash 已有索引） |

### 7.5 尚未做（W3）

图片宽高、缩略图生成、EXIF 剥离、裁剪、水印、引用计数回收。表字段（`width/height/thumb_path/derived_from/ref_count`）已预留。

---

## 8. 模块开关（可插拔验证）

### 8.1 机制

```yaml
lx:
  module:
    modules:
      demo:
        enabled: false
        name: 演示模块
        description: 验证可选模块关闭后 Bean 不加载、接口 404
```

- `LxModuleProperties` 用 `Map<String, ModuleSwitch>`，新增模块只加 YAML，不改基座代码。
- `ModuleRegistry` 提供 `isEnabled` / `list`，`/api/admin/modules` 下发给前端。
- 模块自己用 `@ConditionalOnProperty` 控制 Bean：关闭时**连类都不装配**，接口 404，而不是“接口在但抛异常”。

### 8.2 验证结果

| 开关 | `/api/admin/demo/ping` | `/api/admin/modules` |
|---|---|---|
| `enabled=false` | **404** | `demo:false` |
| `enabled=true` | `{"code":0,"data":"pong"}` | `demo:true` |

---

## 9. 验证矩阵（实测结果）

启动方式：

```powershell
java -jar lx-boot\target\lingxiao.jar
```

| # | 用例 | 预期 | 实测 |
|---|---|---|---|
| 1 | `GET /api/admin/auth/captcha` | 200 + data URI 图片 | ✅ 200，含 `data:image/png;base64` |
| 2 | 未带 Token 访问 `/api/admin/auth/info` | 401 + code 1002 | ✅ 401 / 1002 |
| 3 | `POST /login`（admin/admin123） | code 0 + token | ✅ `tokenName=lx-platform-token`，`expiresIn=43199` |
| 4 | `GET /info` | 用户 + 角色 + 权限 | ✅ `admin / 超级管理员 / roles=admin / perms=*:*:*` |
| 5 | `GET /menus` | 4 个根节点 | ✅ 系统管理 / 系统监控 / 系统工具 / 个人中心 |
| 6 | `GET /modules` | demo 关闭 | ✅ `demo:false` |
| 7 | 错误密码登录 | code 2001 | ✅ `2001 用户名或密码错误`，且写入登录日志 |
| 8 | 登出后再访问 `/info` | 401 | ✅ 401 |
| 9 | demo 关闭时 `/api/admin/demo/ping` | 404 | ✅ 404 |
| 10 | demo 开启时 `/api/admin/demo/ping` | `pong` | ✅ `pong`，`/modules` 变 `demo:true` |
| 11 | 上传 txt | code 0 + fileId | ✅ fileId=`2121834843644293122`，路径 `notice/2026/11/...txt` |
| 12 | 按备注搜索“施工” | 命中 1 条 | ✅ total=1 |
| 13 | 改名为“凌霄施工记录-已改名.txt” | original 不变 | ✅ `originalName=lx-test.txt`，`displayName` 已变 |
| 14 | 改名历史 | 1 条 old→new | ✅ `lx-test.txt -> 凌霄施工记录-已改名.txt by admin` |
| 15 | `GET /api/file/{fileId}` | 200 + 原文 | ✅ 200 `text/plain`，内容一致 |

---

## 10. 本阶段交付清单

### 代码

- 17 个 Maven 模块，全部带中文类注释与目录 README。
- 5 个 REST 控制器：认证、验证码、文件、公开文件、模块。
- 3 个 Flyway 脚本（19 张表 + 种子数据）。
- 统一响应 / 异常 / 鉴权 / 审计 / 操作日志骨架。

### 配置

- `application.yml`：数据源 3307、Flyway 双目录、Sa-Token、模块开关、文件存储。
- `.gitignore`：排除 `target/`、`.local/`、`data/`、`node_modules/`。

### 文档

- 本文 `docs/21-build-log.md`。
- 各模块 `README.md`：职责、依赖方向、数据边界、关键类、禁止事项。

---

## 11. 下一步（按优先级）

> 本节写于第二轮结束时，保留当时的排期原样，便于回看。
> 第 1、2 条的前端部分已在第三轮完成（见第 20 节）。

1. ~~**前端 `lx-ui-admin`**~~：**已完成**（Vue3 + Element Plus + Vite，登录页 / 动态菜单 / 用户-角色-菜单三张表管理，见第 20 节）。
2. **系统管理 CRUD**：前后端均已完成（后端见第 13 节，前端见第 20.7 节）；剩余**数据范围过滤**与非超管账号的菜单/按钮联调。
3. **日志落库**：`@Log` 切面改为发布事件，异步写 `sys_oper_log`；登录日志查询页。
4. **文件中心 W3**：缩略图、图片宽高、EXIF 剥离、裁剪、水印、引用计数。
5. **监控页**：在线用户、服务监控、缓存监控。
6. **Redis 切换**：部署环境具备 Redis 后，接入 `lx-common-redis` + Sa-Token Redis 存储，验证码迁移。
7. **多库适配**：把 DDL 与 SQL 收敛到方言层，补 PostgreSQL / 达梦 / 人大金仓脚本。

---

## 12. 附：常用命令

```powershell
# 构建（跳过测试）
mvn -DskipTests package

# 启动
java -jar lx-boot\target\lingxiao.jar

# 本地联调时临时关闭验证码（生产禁止）
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false

# 启动隔离 MySQL（后台）
& "D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysqld.exe" `
  --defaults-file="E:\java\lingxiao\.local\mysql\my.ini" --console

# 连接数据库
mysql --host=127.0.0.1 --port=3307 --user=lingxiao --password=lingxiao123 --database=lingxiao
```

> 默认账号：`admin / admin123`。首次部署后请立即改密码。

---
---

# 第二轮：系统管理 CRUD（同日续作）

> 本轮目标：把“只有登录”的后台补成“能真正管人、管权限、管配置”的后台。
> 前端暂缓，先把接口、权限码、数据约束一次性做扎实。

## 13. 本轮交付

### 13.1 一句话结论

8 组系统管理接口 + 1 套按钮权限 + 1 个部门闭包表修复，**74 项接口级断言全部通过**，并顺手修掉一个会让部门树“静默失真”的闭包表缺陷。

### 13.2 新增接口清单

全部挂在 `/api/admin/system/**`，全部要求平台域 Token。

| 模块 | 方法与路径 | 权限码 |
|---|---|---|
| 用户 | `GET /user/list`、`GET /user/{id}` | `platform:system:user:list` |
| 用户 | `POST /user` | `platform:system:user:add` |
| 用户 | `PUT /user/{id}`、`PUT /user/{id}/status` | `platform:system:user:edit` |
| 用户 | `PUT /user/{id}/password` | `platform:system:user:resetPwd` |
| 用户 | `DELETE /user/{id}` | `platform:system:user:remove` |
| 角色 | `GET /role/list`、`/role/options`、`/role/{id}` | `platform:system:role:list` |
| 角色 | `POST /role` / `PUT /role/{id}` / `DELETE /role/{id}` | `platform:system:role:add` / `edit` / `remove` |
| 菜单 | `GET /menu/list`、`/menu/tree`、`/menu/{id}` | `platform:system:menu:list` |
| 菜单 | `POST /menu` / `PUT /menu/{id}` / `DELETE /menu/{id}` | `platform:system:menu:add` / `edit` / `remove` |
| 部门 | `GET /dept/list`、`/dept/tree`、`/dept/{id}` | `platform:system:dept:list` |
| 部门 | `POST /dept` / `PUT /dept/{id}` / `DELETE /dept/{id}` | `platform:system:dept:add` / `edit` / `remove` |
| 岗位 | `GET /post/list`、`/post/options`、`/post/{id}` | `platform:system:post:list` |
| 岗位 | `POST /post` / `PUT /post/{id}` / `DELETE /post/{id}` | `platform:system:post:add` / `edit` / `remove` |
| 字典 | `GET /dict/type/list`、`/type/options`、`/type/{id}` | `platform:system:dict:list` |
| 字典 | `GET /dict/data/list`、`/data/options?dictType=`、`/data/{code}` | `platform:system:dict:list` |
| 字典 | 类型 / 数据的增改删 | `platform:system:dict:add` / `edit` / `remove`（共用） |
| 参数 | `GET /config/list`、`/config/{id}`、`/config/value?configKey=` | `platform:system:config:list` |
| 参数 | `POST /config` / `PUT /config/{id}` / `DELETE /config/{id}` | `platform:system:config:add` / `edit` / `remove` |
| 公告 | `GET /notice/list`、`/notice/{id}` | `platform:system:notice:list` |
| 公告 | `POST /notice` / `PUT /notice/{id}` / `DELETE /notice/{id}` | `platform:system:notice:add` / `edit` / `remove` |

### 13.3 代码结构

```
lx-modules/lx-module-system/src/main/java/com/lingxiao/modules/system
├─ controller/   SysUserController、SysRoleController、SysMenuController、SysDeptController、
│               SysPostController、SysDictController、SysConfigController、SysNoticeController
├─ service/      SysUserService、SysRoleService、SysMenuService、SysDeptService、
│               SysPostService、SysDictService、SysConfigService、SysNoticeService
├─ entity/       SysDept、SysDeptClosure、SysPost、SysUserPost、SysDictType、SysDictData、
│               SysConfig、SysNotice（其余 SysUser/SysRole/SysMenu 重写）
├─ mapper/       11 个 Mapper（含 4 个纯注解关联表 Mapper、闭包表 Mapper）
└─ dto/          TreeSelect、PasswordResetRequest
```

```
lx-common/lx-common-security
├─ annotation/RequiresPermission   平台域权限注解
└─ aspect/PermissionAspect         先校验登录，再校验权限码
```

## 14. 关键设计决策（为什么这么做）

### 14.1 为什么不用 `@SaCheckPermission`

Sa-Token 注解默认 `type = "login"` 域，后台必须每个方法写 `type = "platform"`，**漏写一处就是校验错域**（要么永远通过，要么永远 403）。新增 `@RequiresPermission` 把域固定死，少一个出错点。

`PermissionAspect` 顺序：`checkLogin()` → `checkPermission()`。先判断登录，避免无 Session 时权限查询抛 NPE 变成 500。

### 14.2 为什么系统主数据物理删除

`sys_user` 有 `uk_user_username`，`sys_role` 有 `uk_role_key`，`sys_menu` 有 `uk_menu_key`，`sys_post` 有 `uk_post_code`，`sys_dict_type` 有 `uk_dict_type`，`sys_config` 有 `uk_config_key`。

如果沿用 `@TableLogic` 逻辑删除，删除 `test` 后行还在，**再建 `test` 直接唯一键冲突**，而且报错信息与“用户名重复”完全一样，排查极绕。因此这些主数据一律物理删除，删前由 Service 清理关联表：

| 删除对象 | 前置检查 | 关联清理 |
|---|---|---|
| 用户 | 非内置 admin、非当前登录 | `sys_user_role`、`sys_user_post` |
| 角色 | 非内置、无用户占用 | `sys_role_menu` |
| 菜单 | 无子菜单 | `sys_role_menu` |
| 部门 | 无子部门、无用户 | `sys_dept_closure` |
| 岗位 | **无用户占用（不自动解除）** | 无 |
| 字典类型 | 无字典数据 | 无 |
| 参数 | 非内置（`config_type='Y'`） | 无 |
| 公告 | 无 | 无 |

岗位为什么不像用户那样自动解除关联：用户删除时关联行没有独立意义，必须清；而岗位→用户是明确的业务映射，静默抹掉会让管理员误以为“只是删了个岗位”。直接拒绝更安全。

### 14.3 内置数据保护

| 对象 | 规则 |
|---|---|
| `user_id = 1` | 不能停用、不能删除 |
| 当前登录账号 | 不能停用、不能删除（防止把自己锁在门外） |
| `role_id = 1` | 不能删除、不能停用、不能改 `role_key` |
| `config_type = 'Y'` | 可改值，**不能改 key、不能删除** |

参数 key 不可改的理由：代码里按 key 读（如 `sys.account.captchaEnabled`），key 改掉不会报错，只会在某个功能悄悄失效时暴露。

### 14.4 密码处理

- 新增用户密码必填（6~64），BCrypt 加密；
- 修改用户**不碰密码**，重置密码走独立接口 `PUT /user/{id}/password`；
- 重置密码用请求体而非查询参数（避免进访问日志、浏览器历史）；
- `SysUser.password` 标 `@JsonProperty(access = WRITE_ONLY)`，只收不回；列表接口实测无 `password` 字段。

### 14.5 字典类型改编码要级联

改 `dict_type` 编码时，若不同步更新 `sys_dict_data.dict_type`，类型与数据会瞬间失联，前端永远查不到。`SysDictService.updateType` 在事务里做两件事：改类型 + `UPDATE sys_dict_data SET dict_type = 新值 WHERE dict_type = 旧值`，并打日志记录影响行数。

### 14.6 部门闭包表维护（三个时机）

闭包表 `sys_dept_closure(ancestor_id, descendant_id, depth)`：

- **新增**：插入 `(self,self,0)`，再按父级祖先链补 `(ancestor, self, ancestor.depth+1)`；
- **移动**：取子树快照 → 留存“祖先也在子树内”的内部行 → 删除涉及子树的所有行 → 写回内部行 → 按新父级祖先链补外部行；
- **删除**：确认无子部门、无用户后，删除该节点相关全部行。

不用 `find_in_set`，为 PostgreSQL / 达梦 / 金仓留路。

## 15. 按钮权限（Flyway V4）

### 15.1 问题

V2 只种了 8 个 `platform:system:*:list`。新接口需要 `add/edit/remove/resetPwd`，没有权限码时**除超管外所有人都会 403**。

### 15.2 做法

新增 `V4__system_button_perms.sql`：

- `menu_type='F'`，不进侧边栏，只提供 `perms`；
- `parent_id` 指向对应菜单，角色授权树里挂在菜单下面，取消父级即可整体收回；
- ID 分段：1xx 用户、2xx 角色、3xx 菜单、4xx 部门、5xx 岗位、6xx 字典、7xx 参数、8xx 公告；
- 超管补授用 `INSERT ... SELECT ... WHERE NOT EXISTS`（标准 SQL，不用 `INSERT IGNORE` / `ON DUPLICATE KEY`）。

### 15.3 迁移结果

| 指标 | 数值 |
|---|---|
| `sys_menu` 总数 | 48（23 原有 + 25 按钮） |
| `menu_type='F'` | 25 |
| `sys_role_menu` 中 role 1 的授权 | 48（全覆盖） |
| Flyway 版本 | v4，`success=1` |

## 16. 验证（74 项断言全通过）

### 16.1 方式

因验证码无法脚本识别，用 `--lx.captcha.enabled=false` 启动做自动化验证；**验证完已用默认参数重启，当前运行实例验证码为开启状态**（实测登录返回 `2003 验证码错误或已过期`）。

验证脚本保存在 `.local/verify-crud.ps1`（`.local/` 已 gitignore，不会进版本库），覆盖：登录、权限下发、匿名 401、8 组 CRUD、唯一性冲突、内置保护、闭包表移动、改编码级联、按钮权限 403、清理后同名重建。

复跑方式：

```powershell
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false
powershell -NoProfile -ExecutionPolicy Bypass -File .local\verify-crud.ps1
```

### 16.2 结果分组

| 分组 | 断言数 | 代表用例 |
|---|---|---|
| 认证与权限 | 4 | admin 得 `*:*:*`；匿名访问 401 |
| 用户 | 16 | 新增/重复拒/列表补部门名/**不返回密码**/详情带角色岗位/改资料/清空岗位/重置密码/停用启用/admin 不可停用不可删 |
| 角色 | 5 | 新增带 menuIds / 编辑替换授权 / 内置角色不可删 |
| 菜单 | 6 | 树 4 根 / 树含按钮节点 / 新增编辑删除 / 父级成环拒绝 / 有子菜单不可删 |
| 部门 | 10 | 中文往返 / 子部门 / 子树查用户 / 有子部门不可删 / 移动到根 / 成环拒绝 / **移动后子树仍可查** / 旧根下查不到 |
| 岗位 | 4 | 新增 / 编码重复拒 / 被占用不可删 / 正常删除 |
| 字典 | 8 | 类型+数据 / 按类型取下拉 / 类型不存在拒 / 改编码级联 / 非空类型不可删 / 两级删除 |
| 参数 | 7 | 增改 / 按 key 取值 / 内置 key 不可改 / 内置不可删 / 删除 |
| 公告 | 3 | 增 / 改 / 删 |
| 普通角色 403 | 5 | 仅有 `user:list` 的账号：可查列表，`POST /user` 返回 **403 / 1003** 且未落库，`role/list` 同样 403 |
| 清理与唯一键 | 2 | 全部测试数据删除成功；**同名用户删除后可重建** |

### 16.3 数据收尾

验证结束核对数据库：

| 表 | 残留 |
|---|---|
| `sys_dept` / `sys_dept_closure` | 1 / 1（仅根部门） |
| `sys_user` / `sys_role` / `sys_post` | 1 / 1 / 1（仅 admin / admin 角色 / ceo） |
| `sys_dict_type` / `sys_dict_data` | 3 / 7（种子） |
| `sys_config` / `sys_notice` | 3 / 1（种子） |

## 17. 本轮踩坑

### 17.1 闭包表移动把子树内部层级删没了（真 bug）

**现象**：把部门 A（带子部门 B）移动到根节点后，再尝试把 A 移到 B 下——本应被“不能移到自己下级”拦住，却**成功**了。

**根因**：早期 `rebuildClosure` 把子树涉及的行全部删掉，移动到根时只补了 `(A,A,0)`，**B 与 A 的链接、B 的 self 行一起丢失**。于是查 A 的祖先链为空，成环校验形同虚设；按 A 查子树也查不到 B。

**修复**：先留存“祖先也在子树内”的内部行，删除后原样写回，再补外部祖先链接。修复后验证：

- 移动后按 A 查子树能查到 B 下的用户；
- 按原根 1 查子树查不到该用户；
- 再把 A 移到 B 下 → `1001 上级部门不能是自己的下级`；
- 清理阶段两个部门都能正常删除（不再出现互相为父的死循环）。

**教训**：闭包表“重建”必须区分**内部行**与**外部行**，不能图省事全删重插。

### 17.2 PowerShell 替换把源文件编码写坏

用 `Get-Content -Raw | -replace | Set-Content` 改 `SysRoleService.java` 中的一个常量，PowerShell 5.1 默认按 ANSI 读写，中文字符注释全变乱码，编译报“无法映射字符”。

**处理**：整文件用编辑器重写（UTF-8），并放弃“用 shell 批量改 Java 源码”的做法。**Java 源码一律用编辑工具改，不用 PowerShell 管道。**

### 17.3 测试脚本自身的两个坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 岗位删除用例请求 `/post/5196` | PowerShell 变量名**不区分大小写**，`$pid` 撞上只读自动变量 `$PID` | 改名 `$postId` |
| Long 型 ID 断言误报 | 全局 Jackson 把 Long 序列化为字符串，按字符串排序 `1,101,5` ≠ 期望 `1,5,101` | 断言前统一转字符串并按集合比较 |

这两个都是脚本问题，不是接口问题——记录下来避免下次误判。

## 18. 遗留项（明确未做）

| 项 | 说明 |
|---|---|
| 数据范围过滤 | `sys_role.data_scope` 已存值，查询尚未按“本部门 / 本部门及以下 / 仅本人”过滤；当前所有有 `list` 权限的账号看到全量 |
| 按钮级前端控制 | 权限码已下发（`/auth/info.permissions`）；第三轮已实现 `v-hasPerm` 指令与按钮显隐（见 20.5），但非超管账号未做完整联调 |
| 公告 XSS 清洗 | WangEditor 5 HTML 直接入库，上线前必须在 Service 入口接 Jsoup 白名单 |
| 操作日志落库 | `@Log` 目前只打应用日志，`sys_oper_log` 未写 |
| 参数缓存 | 每次查库；加缓存后必须同步失效 |
| 字典数据类型校验 | 数据挂在不存在类型下已被拒绝，但未校验 `dictValue` 在类型内唯一（表上也无唯一键） |
| 登录日志查询 | 表已写，接口与页面未做 |

> **已补齐（第 25 节）**：`操作日志落库` 与 `登录日志查询 / 两个监控页` 已完成。
> 仍然未做的是：数据范围过滤、公告 XSS 清洗、参数缓存、`dictValue` 唯一校验。

## 19. 本轮常用命令

```powershell
# 构建
mvn -q -DskipTests package

# 默认启动（验证码开启，当前运行状态）
java -jar lx-boot\target\lingxiao.jar

# 自动化验证时临时关闭验证码（生产禁止）
java -jar lx-boot\target\lingxiao.jar --lx.captcha.enabled=false

# 停应用
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like "*lingxiao.jar*" } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

# 核对迁移与权限数据
mysql --host=127.0.0.1 --port=3307 --user=lingxiao --password=lingxiao123 --database=lingxiao `
  -e "SELECT version,description,success FROM flyway_schema_history; SELECT COUNT(*) FROM sys_menu; SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1;"
```

---

## 20. 第三轮：后台前端（lx-ui-admin）

### 20.1 一句话结论

前端从零到可用：登录（图形验证码）→ 后端下发菜单 → 动态路由 → 11 个业务页面 → 401 / 403 / 占位页兜底，
**21 项 UI 冒烟全部通过、浏览器控制台 0 个 error**；默认配置（验证码开启）下人工识别验证码登录成功。

### 20.2 技术栈与取舍

| 层 | 选型 | 版本 |
|---|---|---|
| 框架 | Vue | 3.5.13 |
| 构建 | Vite + @vitejs/plugin-vue | 5.4.11 / 5.2.1 |
| UI | Element Plus + @element-plus/icons-vue | 2.8.8 / 2.3.1 |
| 路由 / 状态 | Vue Router / Pinia | 4.4.5 / 2.2.6 |
| 请求 | axios | 1.7.7 |
| 富文本 | WangEditor 5（editor + editor-for-vue） | 5.1.23 / 5.1.12 |

两个刻意的取舍：

- **Element Plus 全量注册**，不做按需引入。后台页面数量可控，按需引入要额外维护 `unplugin-vue-components` 的自动导入与样式副作用；v1.0 首包体积不是瓶颈，先把“能不能用”做稳。
- **不引入 ESLint / TypeScript**。保持与后端 Java 8 基线一致的“零额外工具链”，避免 npm 插件审批与版本冲突拖慢交付；风格与约定靠目录结构、文件头注释统一。

`npm run build`：1738 modules transformed，产物 `dist/`，构建耗时约 5.5s。

### 20.3 目录结构

```
lx-ui-admin/
├─ index.html                    内联 SVG favicon（消除 404）
├─ vite.config.js                /api 代理到 127.0.0.1:8080
├─ package.json                  依赖 + allowScripts 安装脚本审批
└─ src/
   ├─ main.js                    Element Plus 全量注册、指令、守卫
   ├─ App.vue
   ├─ api/                       request 统一封装 + 10 个接口模块
   │  ├─ request.js              拦截器 / BizError / 401 整页跳转
   │  └─ auth|user|role|menu|dept|post|dict|config|notice|file.js
   ├─ assets/styles/index.css    全局变量与页面卡片、表格、表单基础样式
   ├─ components/                AppIcon / DictTag / DictSelect / RichEditor / FileLibrary
   ├─ composables/useDict.js     字典按类型取用 + 内存缓存
   ├─ directives/permission.js   v-hasPerm 按钮权限
   ├─ layout/                    index / Sidebar / SidebarItem / Navbar / Breadcrumb
   ├─ router/                    index 静态路由 / dynamic 菜单转路由 / guard 守卫
   ├─ store/                     auth（Token、用户、菜单、权限）/ app（侧栏折叠）
   ├─ utils/                     auth（Token 存取）/ tree（树工具）/ index（通用小函数）
   └─ views/                     15 个页面（见 20.7）
```

### 20.4 认证与请求约定

`api/request.js` 是全站唯一出口，约定如下：

| 约定 | 做法 | 原因 |
|---|---|---|
| Token 名 | 登录响应同时给 `tokenName` / `tokenValue`，一起存 localStorage，拦截器按 `tokenName` 设请求头 | 后端换域（平台域 / 会员域 / 租户域）时前端不用改代码 |
| 成功返回 | 拦截器直接返回 `body.data`（`code === 0`） | 页面不写 `res.data.data`，少一层噪音 |
| 业务失败 | 抛 `BizError(code, msg)` 并统一 `ElMessage.error` | 页面仍可按 `2003` 等错误码分支 |
| 登录失效 | HTTP 401 或业务码 `1002` → 清 Token → **整页** `location.replace('/login?redirect=...')` | 此时 Pinia 与动态路由可能是半初始化状态，`router.push` 会 404 |
| 无权限 | HTTP 403 → 提示“没有权限执行该操作”，抛 `1003` | 与 401 分开，避免把 403 也踢回登录页 |
| 静默请求 | 请求配置加 `silent: true` | 首页探测、`/auth/info` 首次加载等场景不弹提示 |
| ID 类型 | 后端 Long 全序列化为字符串，前端只做 `String(a) === String(b)` | 19 位雪花 ID 一旦 `parseInt` 必然丢精度 |

`utils/auth.js` 的 `getTokenName()` 在 localStorage 缺失时回落到 `lx-platform-token`，
保证老数据 / 手工清缓存后仍能工作。

### 20.5 动态路由与按钮权限

后端 `/api/admin/auth/menus` 只返回 `M`（目录）与 `C`（菜单），`F` 是按钮不生成路由。

1. **只给 `C` 建路由**：`collectPageMenus` 深度优先收集 `type=C` 且 `path` 非空节点，全部 `addRoute('Layout', ...)`。
2. **路径用后端给的绝对路径**（`/system/user`），不做父子拼接。路由是扁平的，侧边栏层级由菜单树独立渲染——
   菜单换位置只改 `parent_id`，不影响路由结构，也不会出现“相对路径拼错导致 404”。
3. **组件映射**：`component` 字段相对 `views` 解析（`system/user/index` → `@/views/system/user/index.vue`），
   用 `import.meta.glob('../views/**/*.vue')` 预扫描；**找不到就落占位页**并打 `placeholder: true` 标记。
   这样“菜单先建、页面后补”不会白屏，冒烟里也专门验了这条。
4. **守卫流程**：白名单（`/login` `/403` `/404`）直达 → 无 Token 去登录并带 `redirect` →
   有 Token 但 `!auth.loaded` 时拉 `info` + `menus`、`resetDynamicRoutes()` 后重新注册，
   再 `return { path: to.fullPath, replace: true }` **重进目标路由**。
   必须重进：动态路由是在本次导航判定之后才注册的，直接放行会命中 404。
5. **退出/换账号**：`resetDynamicRoutes()` 按注册时记录的路由名精确移除，防止上一个人的菜单残留在下一个账号里。
6. **按钮权限**：`v-hasPerm="'platform:system:user:add'"`，超管的 `*:*:*` 直接放行；
   指令只做“显示/隐藏”，真正的拦截仍在后端 `@RequiresPermission`。

已验证的行为：深链 `/system/menu` 刷新后仍能进（先加载再重进）；未登录访问业务页跳登录；
登录后按 `redirect` 回到原页；未实现的菜单（如“系统监控”）落到占位页而不是白屏。

### 20.6 布局与通用组件

| 组件 | 职责 | 关键点 |
|---|---|---|
| `layout/index.vue` | 侧栏 + 顶栏 + 内容区 | 折叠状态存 Pinia，刷新保持 |
| `layout/Sidebar.vue` | 渲染菜单树 | 只渲染 `M/C`，按 `visible` 过滤 |
| `layout/SidebarItem.vue` | 递归菜单项 | 有子节点用 `el-sub-menu`，叶子用 `el-menu-item` |
| `layout/Navbar.vue` | 面包屑、全屏、用户下拉 | 退出时清 Token + 重置动态路由 |
| `layout/Breadcrumb.vue` | 由菜单树反查祖先链 | 不依赖路由 `matched`，扁平路由也能出层级 |
| `AppIcon.vue` | 菜单图标字符串 → 组件 | Element Plus 图标名匹配，匹配不到给默认图标，不报错 |
| `DictTag.vue` | 字典值 → 带色标签 | 用 `listClass` 决定 `el-tag` 类型 |
| `DictSelect.vue` | 字典下拉 | 内部走 `useDict`，同一类型只请求一次 |
| `RichEditor.vue` | WangEditor 5 富文本 | 图片上传走后端 `/file/upload`，回填 URL；卸载时 `destroy()` 防内存泄漏 |
| `FileLibrary.vue` | 文件中心 / 文件库共用 | 网格 + 列表、原名/现名/备注搜索、上传、改名并展示原名、下载、删除 |

### 20.7 页面清单

| 分组 | 路由 | 说明 |
|---|---|---|
| 登录 | `/login` | 图形验证码、回车提交、失败自动换一张验证码、按 `redirect` 回跳 |
| 首页 | `/dashboard` | 真实数据：当前用户、菜单数、权限数、快捷入口；**不编造统计数字** |
| 个人中心 | `/profile` | 当前用户信息 + 只读权限码列表 |
| 系统管理 | `/system/user` | 分页、部门树筛选、新增/编辑、重置密码、状态切换、删除 |
| 系统管理 | `/system/role` | 角色 CRUD + 菜单树授权（勾选后提交菜单 ID） |
| 系统管理 | `/system/menu` | 树表、目录/菜单/按钮三种类型、权限码、图标、排序 |
| 系统管理 | `/system/dept` | 树表、新增下级、移动父级（后端闭包表） |
| 系统管理 | `/system/post` | 分页 CRUD、状态切换 |
| 系统管理 | `/system/dict` | 左类型右数据主从联动，按选中类型加载数据 |
| 系统管理 | `/system/config` | 参数 CRUD，内置 key 后端拒改拒删 |
| 系统管理 | `/system/notice` | 公告 CRUD，正文用 WangEditor 5 |
| 文件 | `/tool/file` | 文件中心（上传 + 搜索 + 改名留痕） |
| 文件 | `/tool/file-library` | 文件库（同一组件，偏“挑文件”的浏览形态） |
| 错误 | `/403` `/404` | 独立整页，不套后台布局 |
| 占位 | 菜单指向但组件不存在时 | 显示菜单名、组件路径、后端已下发但前端未实现的提示 |

### 20.8 验证（21 项 UI 冒烟）

脚本 `.local/uitest/smoke.mjs`（gitignored），puppeteer-core 驱动本机 Chrome，全部用 DOM 事件而非真实鼠标点击。
为跑自动化，后端临时以 `--lx.captcha.enabled=false` 启动。

| # | 用例 | 结果 |
|---|---|---|
| 1 | 登录页渲染 | ✅ |
| 2 | 验证码图片加载（`data:image`） | ✅ |
| 3 | 登录成功并进入首页，显示“超级管理员” | ✅ |
| 4 | 侧边栏“系统管理 → 用户管理”跳转 | ✅ |
| 5~15 | 11 个页面深链渲染（用户/角色/菜单/部门/岗位/字典/参数/公告/文件中心/文件库/个人中心） | ✅ 均有数据行或关键文案 |
| 16 | 字典：类型 → 数据主从联动 | ✅ |
| 17 | 未实现菜单落到占位页 | ✅ |
| 18 | 新增岗位（前端 → 后端 → 列表刷新） | ✅ |
| 19 | 删除岗位（清理临时数据） | ✅ |
| 20 | 未登录访问业务页 → 跳登录 | ✅ |
| 21 | 登录后按 `redirect` 回原页，退出登录回登录页 | ✅ |

汇总：`pass=21 fail=0`，浏览器控制台 **0 个 error**。

**验证码开启下的登录**单独验证（因为自动化无法识别图形码）：

- `captcha-check.mjs`：错误验证码 → 后端 `2003` → 页面弹“验证码错误或已过期”；
- `captcha-refresh-check.mjs`：提交失败后重新请求 `/auth/captcha`，2 次请求拿到 2 个不同 `captchaKey`，确认没有缓存旧 key；
- `login-manual-captcha.mjs`：截图验证码，人工把识别结果写入 `answer.txt`，脚本填表提交 →
  `loggedIn=true`、页面显示“超级管理员”、无 console error。

另外单独用 PowerShell 直连接口确认：默认配置下错误验证码返回 `2003`，正确验证码返回
`code=0`、`tokenName=lx-platform-token`、Token 长度 36。

### 20.9 本轮踩坑（前端）

| 现象 | 根因 | 处理 |
|---|---|---|
| `npm install` 后 Vite 报缺少 `esbuild.exe` | npm 11 默认拦截依赖安装脚本，esbuild 的 postinstall 没跑 | 在 `package.json` 写 `allowScripts`：放行 `esbuild@0.21.5`、`vue-demi@0.14.10`，拒绝可疑的 `es5-ext` |
| 首页快捷入口只有图标、没有文字 | 后端菜单字段是 `name`，模板用的是 `entry.title` | `quickEntries` 显式映射 `{ path, title: menu.name, icon: menu.icon }` |
| 控制台一条 `favicon.ico 404` | `index.html` 没有图标声明 | 内联 SVG data URI favicon |
| 刷新页面偶发 404（首次实现时） | 守卫在动态路由注册前就放行 | 注册后 `return { path: to.fullPath, replace: true }` 重进 |
| 表格里 ID 比较总是不相等 | 用 `parseInt` 转了 19 位雪花 ID | 统一字符串比较，写进代码约定 |
| PowerShell 输出中文全是乱码 | 控制台编码不是 UTF-8，**文件本身没问题** | 只影响查看，不用 PowerShell 改前端源码（同 17.2） |

### 20.10 前端遗留项

| 项 | 说明 |
|---|---|
| 非超管账号联调 | 目前冒烟只用超管（`*:*:*`）跑通；普通角色的菜单过滤、按钮隐藏与后端 403 的完整闭环还没做端到端验证 |
| 数据范围过滤 | 依赖后端 `sys_role.data_scope` 落地，前端目前不做额外过滤 |
| 文件缩略图 | 文件库用图标 + 文件名占位，图片缩略图 / 预览大图未做（等后端 W3） |
| 公告 XSS | 富文本直接 `v-html` 渲染，必须与后端 Jsoup 白名单一起上线 |
| 首页统计 | 没有统计接口，首页只展示真实计数与入口，不做假图表 |
| 登录日志页 | ~~后端接口未做，前端也未排期~~ —— 已在第 25 节完成（含操作日志页） |
| 多标签页 / 主题 | 未做，v1.0 保持单标签 + 单主题 |

### 20.11 前端常用命令

```powershell
# 安装依赖（npm 11 会按 package.json 的 allowScripts 审批安装脚本）
cd lx-ui-admin
npm install

# 开发（/api 代理到 8080）
npm run dev        # http://127.0.0.1:5173

# 构建
npm run build

# 预览构建产物
npm run preview    # http://127.0.0.1:4173

# UI 冒烟（需先以 --lx.captcha.enabled=false 启动后端）
cd ..\.local\uitest
node smoke.mjs
```

## 21. 第四轮：富文本编辑器换型与双引擎切换

### 21.1 一句话结论

WangEditor 5 换成 **wangEditor Next**，并接入 **UEditor Plus** 作为备用引擎；
用「本机偏好 > 平台默认 > 构建兜底」三层优先级切换，两个引擎的图片都直接进文件中心（不存 base64）。
UI 切换 **8/8**、图片上传 **5/5**、后端白名单接口 **7/7** 全部通过，控制台 0 error、0 个 4xx/5xx。

### 21.2 为什么换 / 为什么留两套

| 维度 | WangEditor 5 | wangEditor Next | UEditor Plus |
|---|---|---|---|
| 维护状态 | 原作者停止更新 | 社区接管的延续版本，API 基本兼容 | 国内长期维护分支（4.x） |
| Word 粘贴 | 一般 | 一般 | 强（表格、样式保留更好） |
| 体积 | 小 | 小 | 大（含完整 UI 与主题资源） |
| 集成方式 | 原生 Vue 组件 | 原生 Vue 组件 | iframe + 全局 `UE`，需要包装 |
| 上传 | 自定义 `customUpload` | 同左 | `uploadServiceEnable + uploadServiceUpload` 钩子 |

结论：**默认用 Next**（轻、粘贴干净、组件化好维护），**保留 UEditor Plus 作为可切换的备用**，
遇到 Word 复杂表格这类场景时用户自己换，不用等发版。两套都接同一个 `uploadFile`，文件中心行为一致。

### 21.3 依赖版本（踩过版本坑）

| 包 | 版本 | 说明 |
|---|---|---|
| `@wangeditor-next/editor` | 6.6.0 | 注意不是 `@wangeditor/editor` |
| `@wangeditor-next/editor-for-vue` | 6.6.0 | **不能用 6.6.1**：它的 peerDependencies 要求 `@wangeditor-next/editor@6.6.1`，而该版本并不存在 |
| `ueditor-plus-main` | 4.5.0 | npm 包里带 `dist-min` 静态资源 |
| `vue-ueditor-wrap` | 3.0.8 | Vue 3 包装器 |

`package.json` 里同时删掉了旧的 `@wangeditor/editor` / `@wangeditor/editor-for-vue`，
避免两套全局样式互相污染。

### 21.4 三层优先级

```
本机偏好（localStorage: lx-editor-engine）
  ↓ 没有
平台默认（sys_config: sys.editor.type）
  ↓ 没有 / 读取失败
构建兜底（VITE_RICH_EDITOR，默认 wangeditor-next）
```

`composables/useEditorEngine.js` 是唯一判断点：

- `editorEngine` 是 `computed`，`RichEditor.vue` 直接绑定，切换后引擎组件整体替换；
- 切换写 localStorage 的**只是本机偏好**，不影响别人，也不改平台参数；
- 下拉里的「跟随平台默认」= 清掉本机偏好，回到第二层；
- `loadEditorEngine()` 做了单次请求 + 失败静默，`layout/index.vue` 登录后预加载，
  避免打开公告弹窗时才闪一下引擎。

**为什么不让平台默认实时强推**：运营改一次参数就强制刷新所有正在编辑的人，会把没保存的草稿打断。
所以平台默认只管“新用户 / 新设备”，老用户按自己上次的选择。

### 21.5 平台参数怎么下发（白名单）

`sys_config` 里的参数默认要 `platform:system:config:list` 权限才能读，但公告管理员不一定有系统参数权限。
所以新增一个**登录即可读**的接口：

```http
GET /api/admin/system/config/public/value?configKey=sys.editor.type
→ { "code": 0, "data": "wangeditor-next" }
```

- 服务端 `SysConfigService.PUBLIC_CONFIG_KEYS` 是**白名单**，当前只有 `sys.editor.type`；
- 非白名单 key 返回 `null`，**不区分**“没权限”和“不存在”，避免泄露参数名；
- 种子数据在 `V5__system_editor_setting.sql`：`config_id=4`、`config_key=sys.editor.type`、
  `config_type=Y`（内置，参数设置页拒改 key / 拒删，但**值可以改**）。

### 21.6 静态资源同步（UEditor）

UEditor Plus 不是 ES Module 友好的库：它要求 `UEDITOR_HOME_URL` 指向一个含有
`ueditor.config.js`、`ueditor.all.js`、`themes/`、`lang/` 的目录，然后在页面里插 `<script>`。

- `scripts/sync-ueditor.mjs` 把 `node_modules/ueditor-plus-main/dist-min` 复制到
  `public/ueditor-plus/`（237 个文件）；
- `predev` / `prebuild` 自动执行，`npm run sync:ueditor` 可手动执行；
- `public/ueditor-plus/` 已加入 `.gitignore` —— 它完全由 npm 包生成，提交进来只会让仓库变大。

必须显式传的两个点：

```js
editorDependencies: ['ueditor.config.js', 'ueditor.all.js']  // 不能只靠自动探测
loadConfigFromServer: false                                  // 默认会去请求 demo PHP 服务
imageUrlPrefix: ''                                           // 否则图片地址会被拼成 //demo.ueditor.baidu.com/...
```

### 21.7 上传：两个引擎都进文件中心

| 引擎 | 入口 | 配置 |
|---|---|---|
| wangEditor Next | 工具栏「图片 → 上传图片」 | `MENU_CONF.uploadImage.customUpload` → `uploadFile()`，成功回填 `/api/file/{id}` |
| UEditor Plus | 工具栏「单图上传」（`simpleupload`） | `uploadServiceEnable: true` + `uploadServiceUpload(type, file, callbacks)` |

`uploadServiceUpload` 里做三件事：

1. `uploadFile(file, { bizType: 'notice', remark: '富文本图片（UEditor Plus）', onProgress })`；
2. 成功回调 `callbacks.success({ state: 'SUCCESS', url: data.url, original: data.originalName })`；
3. 失败 `ElMessage.error` + `callbacks.error(msg)`。

这样 **Token、错误提示、文件中心留痕、按原名/现名/备注搜索** 与 wangEditor 完全一致，
不引入 UEditor 自带的 PHP/JSP 上传服务。

工具栏刻意**不放** `insertimage`（多图弹窗会走它自己的 `serverUrl`），只放 `simpleupload`。

### 21.8 三个必须知道的坑（都是真 bug）

#### 21.8.1 全局 Content-Type 写死 JSON，FormData 上传丢 boundary

**现象**：文件上传接口返回 `{code:9000, msg:"系统内部错误"}`，后端日志
`MultipartException: Current request is not a multipart request`。

**根因**：`api/request.js` 的 axios 实例上写死了
`headers: { 'Content-Type': 'application/json;charset=utf-8' }`。
axios 对 FormData 本该让浏览器自动补 `multipart/form-data; boundary=...`，
但显式设置的默认头把 boundary 干掉了，Spring 认不出 multipart。

**修复**：删掉这个全局头，交给 axios 按 data 类型自动判定
（普通对象 → JSON，`FormData` → multipart）。这是 v1.0 遗留的真实缺陷，不是编辑器引入的。

#### 21.8.2 `vue-ueditor-wrap` 的默认导出不是组件

```js
import VueUeditorWrap from 'vue-ueditor-wrap'          // ❌ 这是插件对象 { install, version }
import { VueUeditorWrap } from 'vue-ueditor-wrap'      // ✅ 具名导出才是组件
```

用错时 Vue 只给一句 `Component is missing template or render function`，页面表现为**一片空白**，
很容易误判成“UEditor 加载失败”。

#### 21.8.3 关掉 `loadConfigFromServer` 会让「单图上传」永久禁用

**现象**：UEditor 渲染正常、工具栏其他按钮可用，但「单图上传」是灰色的，
点了不弹文件选择框，DOM 里也找不到那个透明 `input[type=file]`。

**根因**（读源码定位）：UEditor 的 `loadServerConfig()` 在
`loadConfigFromServer === false` 时**直接 return**，既不把 `_serverConfigLoaded` 置 true，
也不发 `serverConfigLoaded` 事件；而 `simpleupload` 插件是
`afterConfigReady(initUploadBtn)` 才创建 `<input type=file>` 的 —— 回调永远不执行。

**修复**：编辑器 `ready` 后补一次（`UEditorPlus.vue#onReady`）：

```js
window.setTimeout(() => {
  if (editor.isServerConfigLoaded && editor.isServerConfigLoaded()) return
  editor._serverConfigLoaded = true
  editor.fireEvent('serverConfigLoaded')
}, 0)
```

用 `setTimeout(0)` 是为了等 `ready` 事件分发结束，确保插件的 `simpleuploadbtnready`
已经把 `afterConfigReady` 监听挂上。

另外 `autoSaveEnable: false` 也必须关：UEditor 的自动存稿会在 `ready` 后排一个 1s 定时器，
切换引擎销毁实例后定时器仍会触发，控制台报
`Cannot read properties of undefined (reading 'nodeType')`。

### 21.9 验证结果

**后端接口（`.local/verify-editor-api.ps1`）7/7**

| # | 断言 | 结果 |
|---|---|---|
| 1 | 登录拿 Token | ✅ |
| 2 | `/system/config/public/value?configKey=sys.editor.type` 返回 `wangeditor-next` | ✅ |
| 3 | 非白名单 key（如 `sys.user.initPassword`）返回 `null`，不报错 | ✅ |
| 4 | 匿名访问 `/public/value` 被拦（401/未登录码） | ✅ |
| 5 | `/system/config/value`（需权限）仍受 `config:list` 保护 | ✅ |
| 6 | 参数设置页可把值改成 `ueditor-plus` 并读回 | ✅ |
| 7 | 改回 `wangeditor-next` 成功 | ✅ |

**UI 切换（`.local/uitest/editor-switch.mjs`）8/8**

| # | 用例 | 结果 |
|---|---|---|
| 1 | 公告列表加载 | ✅ |
| 2 | 默认渲染 wangEditor Next，正文含后端数据 | ✅ |
| 3 | 切到 UEditor Plus，`localStorage` 记住偏好 | ✅ |
| 4 | 切回 wangEditor，正文内容不变 | ✅ |
| 5 | 「跟随平台默认」清掉本机偏好 | ✅ |
| 6 | 平台默认改成 `ueditor-plus`、无本机偏好时直接用 UEditor | ✅ |
| 7 | 控制台 0 error | ✅ |
| 8 | 流程中 0 个 4xx/5xx | ✅ |

**图片上传（`.local/uitest/editor-upload.mjs`）5/5**

| # | 用例 | 结果 |
|---|---|---|
| 1 | wangEditor Next 上传成功，`src=/api/file/{id}` | ✅ |
| 2 | UEditor Plus 上传成功，`src=/api/file/{id}` | ✅ |
| 3 | 上传后的图片能 200 取回 | ✅ |
| 4 | 控制台 0 error | ✅ |
| 5 | 0 个 4xx/5xx | ✅ |

> 文件中心按**内容去重**：同一张图重复上传会返回同一个 `fileId`。
> 单测为了让“这次真的上传了”可判定，UEditor 段特意换了一张内容不同的 fixture。

### 21.10 相关文件

| 文件 | 作用 |
|---|---|
| `src/components/RichEditor.vue` | 双引擎外壳 + 右上角「切换编辑器」下拉（含“跟随平台默认”） |
| `src/components/editor/WangEditorNext.vue` | Next 版导入、工具栏、图片上传 |
| `src/components/editor/UEditorPlus.vue` | UEditor 配置、`uploadServiceUpload`、`onReady` 补丁 |
| `src/composables/useEditorEngine.js` | 三层优先级、localStorage、单次加载 |
| `src/api/config.js` | `getPublicConfig(key)` |
| `scripts/sync-ueditor.mjs` | 复制 UEditor 静态资源 |
| `SysConfigService.java` / `SysConfigController.java` | 白名单 + `/public/value` |
| `V5__system_editor_setting.sql` | `sys.editor.type` 种子 |

### 21.11 遗留

| 项 | 说明 |
|---|---|
| 视频 / 附件上传 | 两个引擎目前只打通图片；UEditor 的 `uploadServiceUpload` 已支持 video/audio/attachment，前端 `uploadFile` 未区分 bizType |
| 编辑器内弹窗层级 | UEditor 的 `zIndex: 3000` 是按 Element Plus 弹窗层级给的；如果以后加更高的浮层需要同步调 |
| 粘贴 Word 的样式清洗 | 仍依赖引擎自身；后端 Jsoup 白名单未做（见 18 节） |

## 22. 第五轮：普通角色权限闭环（API 34 + UI 24）

### 22.1 一句话结论

用一个只授「公告管理 + 个人中心」的角色跑通完整闭环：
**侧边栏只出现授权菜单、按钮按权限码显隐、未授权深链落 404、绕过 UI 直打接口仍是 403**；
后端断言 34/34、UI 断言 24/24 全过，控制台 0 error、0 个意外 4xx/5xx。
顺带补上文件接口一直缺失的权限码（Flyway V6）。

### 22.2 权限码是怎么生效的

| 环节 | 做法 |
|---|---|
| 权限存储 | `sys_menu.perms`（F 类型按钮）+ `sys_role_menu`，平台域不写 `tenant_id` |
| Token | 只放登录态与用户 ID；**权限不写进 Token** |
| 每次请求 | `PermissionAspect` 按 用户 → 角色 → 菜单 实时查 DB 解析权限码 |
| 超管 | `permissions = ["*:*:*"]`，切面直接放行 |
| 拒绝 | HTTP **403** + `code=1003` |
| 菜单接口 | `/auth/menus` 只返回该用户可见的 M/C 节点，F 不进树 |
| 前端 | `v-hasPerm` 指令控制按钮；路由只注册 `/auth/menus` 下发的页面 |

**为什么权限不进 Token**：授权 / 收回要即时生效。本轮专门断言了同一枚旧 Token：
授予 `notice:add` 后立刻能发公告，收回后立刻 403 —— 无需重新登录，也没有 Token 缓存。

### 22.3 文件接口权限补齐（Flyway V6）

权限联调时发现 `/api/admin/file/**` 此前**只校验登录**：任何后台账号都能列文件、改名，
连“只负责发公告”的账号也能改别人文件名。补齐如下：

| 接口 | 权限码 | 说明 |
|---|---|---|
| `POST /file/upload` | `platform:file:upload` | 富文本插图也走这里 |
| `GET /file/list` | `platform:file:list` | 文件中心 / 文件库共用 |
| `GET /file/{id}` | `platform:file:list` | 详情 |
| `GET /file/{id}/name-history` | `platform:file:list` | 改名历史 |
| `PUT /file/{id}/rename` | `platform:file:rename` | 会写 `sys_file_name_log` |

`V6__file_button_perms.sql` 新增两个 F 按钮（`menu_id=1801 / 1802`，挂在文件中心 18 下），
并给超管角色补 `sys_role_menu` 关系，保证授权树里可见。

> **授权提醒**：只让用户写公告、不让进文件中心时，单独勾“文件上传”即可。
> （原先这里还提到「文件库」需要 `platform:file:library:list`；该重复菜单已在
> 迁移 V7 删除，见第 23 节。）

### 22.4 API 断言清单（`.local/verify-permission.ps1`，34/34）

| 组 | 断言 |
|---|---|
| 准备 | admin 登录；清理上次残留角色 / 用户 |
| 建数据 | 创建 `notice_reader` 角色（menuIds=1,4,12）；创建 `reader01` 并绑定该角色 |
| 登录 | `reader01` 登录成功，Token 名仍是 `lx-platform-token` |
| 权限解析 | 有 `notice:list`、`profile:view`；无 `user:list`、`notice:add`、`*:*:*` |
| 菜单过滤 | 树里只有 `/system`、`/system/notice`、`/profile`；无用户 / 角色 / 文件菜单 |
| 允许的读 | 公告列表 200；公开参数 `/config/public/value` 200 |
| 拒绝的写 | 公告新增 / 修改 / 删除 → 403 + 1003 |
| 拒绝的读 | 用户列表、角色列表、参数列表、私有参数取值、文件列表 → 403 + 1003 |
| 文件写 | 文件改名 403；真发 multipart 上传 403 |
| 动态授权 | 角色加 `notice:add`（menu 801）→ 旧 Token 立即能发公告 |
| 动态收回 | 去掉 801 → 同一枚旧 Token 立即 403 |
| 清理 | 用户 / 角色均无残留 |

### 22.5 UI 断言清单（`.local/uitest/permission.mjs`，24/24）

| 组 | 断言 |
|---|---|
| 登录 | 受限账号 `reader01` 登录落到 `/dashboard`，首页显示 `Notice Reader` |
| 侧边栏 | 展开「系统管理」后只有「公告管理」；「个人中心」在；用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 文件中心 / 文件库全部不出现 |
| 公告页按钮 | 「新增公告」不存在；行内「编辑」「删除」不存在；「预览」保留 |
| 深链 | 直接访问 `/system/user` 落 **404 兜底页**，不是白屏 / 403 页 |
| 绕过 UI | `fetch` 直打用户列表、公告新增、文件列表 → 403 + 1003；公告列表仍 200 |
| 健康度 | 控制台 0 error；正常路径 0 个意外 4xx/5xx |
| 清理 | 删除测试用户与角色 |

> **为什么未授权深链是 404 而不是 403**：动态路由只注册后端下发的页面，
> 不存在的路径走 `/:pathMatch(.*)*`。403 留给“路由存在但接口拒绝”的场景，
> 两者语义不同：404 = 这个后台没有给你这个入口，403 = 你到了门口但没钥匙。

### 22.6 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| UI 登录变成 `adminreader01`，后端 2001 | 登录页 `username` 默认值就是 `admin`，`elementHandle.type()` 是**追加**不是覆盖 | `Ctrl+A` 全选 + Backspace 后再输入 |
| 侧边栏断言 `公告管理` 失败 | 受限角色只有「系统管理」一个目录，默认收起，子项不进 `innerText` | 先点目录标题展开，再读侧边栏文本 |
| “控制台 0 error” 误报 | 用例故意直打未授权接口，浏览器必然打 403 | 探测阶段开 `probeWindow`，只统计正常路径的错误；403 结论由断言单独判定 |
| PowerShell 脚本里 curl 那行不执行 | 脚本含中文注释，PS 5.1 无 BOM 按 ANSI 解析，吞掉换行 | `.ps1` 一律纯 ASCII（沿用 17.2 教训） |
| curl 返回的 403 JSON 解析失败 | curl 输出 UTF-8，PS 5.1 按 OEM 解码，中文 `msg` 乱码 | 该断言只校验 HTTP 403；`code=1003` 已由其他 JSON 断言覆盖 |

### 22.7 相关文件

| 文件 | 作用 |
|---|---|
| `FileController.java` | 文件上传 / 列表 / 详情 / 改名 / 历史加 `@RequiresPermission` |
| `V6__file_button_perms.sql` | `platform:file:upload`、`platform:file:rename` 种子 + 超管关系 |
| `.local/verify-permission.ps1` | 34 项权限 API 断言 |
| `.local/uitest/permission.mjs` | 24 项权限 UI 断言 |
| `views/system/notice/index.vue` | `v-hasPerm` 控制新增 / 编辑 / 删除 |

### 22.8 遗留（权限相关）

| 项 | 说明 |
|---|---|
| `dataScope` | 角色上只存值，未做数据范围过滤；部门数据仍是全量可见 |
| 接口级按钮粒度 | 目前按“新增 / 修改 / 删除 / 重置密码”切；没有字段级或条件级权限 |
| 菜单缓存 | `/auth/menus` 每次登录拉一次，运行期改菜单需刷新页面；权限判断本身是实时的 |
| 操作日志 | ~~`@Log` 注解已标在接口上，但尚未落 `sys_oper_log`~~ —— 已在第 25 节落库并补齐两个监控页 |
| 文件库授权 | ~~需同时给 `platform:file:library:list` 与 `platform:file:list`~~ —— 该重复菜单已在 V7 删除（见 23 节） |

## 23. 第六轮：菜单去重与开关归属（Redis / 图片处理）

### 23.1 一句话结论

- **Redis 是否启用在配置文件**（`lx.redis.enabled`），不放进参数设置；
- **「文件库」与「文件中心」是重复入口**，已删除文件库；
- **图片处理不是后台栏目**，是上传时的处理能力 + 参数配置，规划在 P5，本轮只删掉误导性的占位菜单。

### 23.2 Redis 开关：配置文件，不是参数设置

先看现状（本轮核实）：

| 项 | 现状 | 位置 |
|---|---|---|
| Redis 是否启用 | `RedisConfig` 上有 `@ConditionalOnProperty(name="lx.redis.enabled")`，但 `application.yml` 里没有这个 key，`lx-boot` 也没依赖 `lx-common-redis` | 配置文件（**尚未接线**） |
| 会话存储 | Sa-Token 默认内存 | 随 Redis 开关切换（待做） |
| 图形验证码 | Caffeine 本地缓存 | 随 Redis 开关切换（待做） |
| 验证码是否校验 | `lx.captcha.enabled`，默认 true | 配置文件（自动化测试临时关） |
| 富文本默认引擎 | `sys.editor.type` | **参数设置**（DB） |
| 日志保留天数 | `sys.log.keepDays` | **参数设置**（DB） |

**结论：基础设施开关放配置文件，业务行为开关放参数设置。**

理由：

1. **启动期就要定 Bean**。用不用 Redis 决定 Sa-Token 的 `SaTokenDao`、验证码存储、连接池是否装配；
   参数设置在 DB 里，要等 DataSource 就绪才能读，顺序是倒的。
2. **会话存储不是热开关**。从内存切到 Redis，旧 Token 留在内存里：要么全员掉线，要么请求一半认一半。
   这种变更必须“改配置 → 重启 → 迁移会话”，不能做成参数页点一下。
3. **多实例会分裂**。各节点读到不同值时，Token / 验证码会写到不同存储，
   “A 节点登录、B 节点 401”，排查成本极高。
4. **故障要暴露在启动/健康检查**。参数一改就静默退化成本地缓存，
   表面上没报错，实际多实例已经错了。

**什么可以放参数设置**：不影响进程装配、可以即时生效、单次请求内自洽的行为，
例如验证码是否开启、编辑器默认引擎、水印开关、日志保留天数。
将来可以在“参数设置”或“缓存监控”里**只读展示**当前存储模式（内存 / Redis），但不提供切换按钮。

**待办（下一轮）**：如果要真正接 Redis，顺序是：

1. `lx-boot` 加 `lx-common-redis` + `sa-token-redis-jackson`；
2. `application.yml` 加 `spring.redis.*` 与 `lx.redis.enabled=false`，并用
   `spring.autoconfigure.exclude` 排除 Sa-Token 的自动装配，改由 `@ConditionalOnProperty` 注册；
3. 验证码改成 `CaptchaStore` 接口，Caffeine / Redis 两个实现按同一开关选择；
4. 启动日志打印“会话存储=内存/Redis、验证码=Caffeine/Redis”，避免部署后靠猜。

> 本轮只做了核实与决策记录，**没有改 Redis 代码**——避免在“是否上 Redis”还没定的情况下引入半套实现。

### 23.3 文件中心 / 文件库确实重复

核实结果：

| 页面 | 组件 | 能力 | 后端接口 |
|---|---|---|---|
| `/tool/file` 文件中心 | `FileLibrary show-upload` | 上传、搜索、改名、历史、详情 | `/file/**` |
| `/tool/file-library` 文件库 | `FileLibrary show-upload` | **完全相同** | 同上 |

连上传按钮都一模一样，不是“一个管理、一个挑选”的差异。菜单 19 的存在只会让授权树多一个
容易漏配的 `platform:file:library:list`。

**处理**：新增 `V7__remove_duplicate_file_menus.sql`，删除菜单 19（文件库）及其角色授权；
删除 `views/tool/file/library.vue`；占位页去掉 `/tool/image` 分支；冒烟用例从 11 页减到 10 页。

以后真要做“素材库 / 引用管理”，应作为独立功能（选择器、引用统计、被引用位置）重新建菜单，
不要复用“文件库”这个名字，否则又会变成一个同数据源、同操作的重复入口。

### 23.4 图片处理：规划在 P5，不是栏目

`docs/09-media.md` 已经写明它是**上传管线**的一部分：

| 能力 | 规划 |
|---|---|
| 缩略图 320 / 中图 1280 / 超 2560 限宽 | P1 起做缩略图，P5 补多规格 / WebP |
| 客户端裁剪 + 服务端最终处理 | P5-M1（`POST /image/crop`） |
| 文字 / 图片水印（默认关，只加衍生图） | P5-M1（`POST /image/watermark`） |
| EXIF 方向纠正、剥离 GPS | P5 |
| 配置 | `sys.image.watermarkEnabled`、`sys.image.watermarkText`、`sys.image.watermarkPosition`、`sys.image.webpEnabled` 等，放**参数设置** |

所以：

- **不做独立“图片处理”栏目**。上传即处理，参数在参数设置里配；
- 本轮删掉占位菜单 20，避免用户点进去看到“建设中”却以为会有一个图片编辑页面；
- 真正的实现等 P5，和文件存储抽象、衍生文件（`derived_from`）、原图不加水印一起做。

### 23.5 验证结果

| 项 | 结果 |
|---|---|
| Flyway | 升到 **v7**，`Successfully applied 1 migration` |
| `/auth/menus` | 只剩 `/tool/file`，无 `/tool/file-library`、`/tool/image` |
| 前端冒烟 `.local/uitest/smoke.mjs` | **20/20 通过**，控制台 0 error |
| 权限 UI `.local/uitest/permission.mjs` | **24/24 通过** |
| `npm run build` | 成功，`index` 1.29 MB / `notice` 660 kB（gzip 420 / 212 kB） |

### 23.6 相关文件

| 文件 | 变更 |
|---|---|
| `V7__remove_duplicate_file_menus.sql` | 删除菜单 19 / 20 及角色授权 |
| `src/views/tool/file/library.vue` | 删除 |
| `src/views/placeholder/index.vue` | 去掉 `/tool/image` 的 W3 文案 |
| `.local/uitest/smoke.mjs` | 页面清单 11 → 10 |
| `README.md` / `lx-ui-admin/README.md` | 文件入口、权限说明、页面清单、图片处理归属 |

### 23.7 当时发现的不一致（已在第 24 节接线）

`sys_config` 里种了 `sys.account.captchaEnabled=true`，但代码实际读的是
`lx.captcha.enabled`（启动参数），**这个参数目前是死的**：

- 参数设置页能改，但对登录没有任何影响；
- 真要让它生效，需要：`CaptchaService` 读 DB → `/auth/captcha` 返回 `enabled` →
  登录页隐藏验证码输入框 → `LoginRequest` 在校验关闭时放宽 `@NotBlank`；
- 这属于“参数驱动前端表单”的完整链路，本轮不做，记在这里避免下次又当成 bug 排查。

> 原则：**参数设置里出现的每个 key，都必须有人读**。要么接线，要么删掉，不能留“装饰性参数”。

> **后续**：第 24.1 节已把这条链路接通 —— `sys.account.captchaEnabled` → `CaptchaService`
> → `/auth/captcha.enabled` → 登录页显隐，参数设置页改完立即生效。

## 24. 第七轮：验证码参数接线与 Redis 可选接入

### 24.1 `sys.account.captchaEnabled` 从“死参数”变成真开关

**改造前**：参数设置页能改 `sys.account.captchaEnabled`，但登录逻辑只读启动参数
`lx.captcha.enabled`，改参数没有任何效果。

**改造后**采用两级开关：

| 层级 | 键 | 位置 | 作用 |
|---|---|---|---|
| 总闸 | `lx.captcha.enabled` | 配置文件 / 启动参数，默认 true | 只给联调与自动化测试用；关掉后无论参数怎么配都关闭 |
| 运行期开关 | `sys.account.captchaEnabled` | 参数设置（DB），默认 true | 平台运营可改，改完刷新登录页生效 |

`CaptchaService.isEnabled()` = 总闸 AND 参数值。读参数异常时**按开启处理**（fail closed）：

```java
if (!masterEnabled) return false;
try {
    String value = configService.getValueByKey("sys.account.captchaEnabled");
    return value == null || !"false".equalsIgnoreCase(value.trim());
} catch (RuntimeException e) {
    log.warn("读取验证码开关失败，按开启处理: {}", e.getMessage());
    return true;
}
```

链路补齐的内容：

| 位置 | 变更 |
|---|---|
| `CaptchaResponse` | 新增 `enabled` 字段与 `disabled()` 静态方法；关闭时 `captchaKey/image` 为空 |
| `CaptchaService` | 注入 `SysConfigService`；`create()` 关闭时直接返回 `disabled`；`verify()` 关闭时放行 |
| `LoginRequest` | 去掉 `captchaKey/captchaCode` 的 `@NotBlank`，改由 `verify()` 做条件校验 |
| 登录页 `login/index.vue` | `v-if="captcha.enabled"` 隐藏输入框与图片；关闭时显示“验证码已由平台关闭”；rules 改 computed；提交时不带验证码字段 |

为什么不在 DTO 上写 `@NotBlank` 再靠分组校验：验证码是否必填取决于运行期参数，
分组校验需要额外分组与顺序，条件放在业务里更直白，也不会出现“参数关了接口仍 400”。

### 24.2 Redis：可选接入，默认关闭

按第 23.2 节的结论落地：**开关在配置文件，不在参数设置**。

**依赖与装配**

| 文件 | 变更 |
|---|---|
| `lx-dependencies/pom.xml` | 增加 `sa-token-redis-jackson:1.37.0` 版本管理 |
| `lx-common-redis/pom.xml` | 增加 `sa-token-redis-jackson`（传递 `spring-boot-starter-data-redis`） |
| `lx-boot/pom.xml` | 增加 `lx-common-redis` |
| `lx-module-system/pom.xml` | 增加 `lx-common-redis`（验证码 Redis 实现用 `StringRedisTemplate`） |
| `application.yml` | `spring.redis.*`、`lx.redis.enabled=false`、排除官方自动装配 |
| `SaTokenRedisConfig`（新增） | `@ConditionalOnProperty(lx.redis.enabled=true)` 注册 `SaTokenDaoRedisJackson` |

**排除自动装配的原因**

`sa-token-redis-jackson` 的 `spring.factories` 直接注册
`cn.dev33.satoken.dao.SaTokenDaoRedisJackson`，依赖在 classpath 上就会无条件生效。
不排除的话，`lx.redis.enabled=false` 也会连 Redis，没装 Redis 的环境启动失败。
所以：

```yaml
spring:
  autoconfigure:
    exclude: cn.dev33.satoken.dao.SaTokenDaoRedisJackson
```

改由 `SaTokenRedisConfig` 手动 `new` 并调用 `init(RedisConnectionFactory)`。
这个 `exclude` 是开关能生效的前提，后续不要当“多余配置”删掉。

**启动即探测（fail fast）**

注册 DAO 前先执行一次 `PING`：

- `enabled=true` + Redis 不可用 → 启动失败，日志明确提示检查 `spring.redis.*` 或改回 false；
- `enabled=false` → 不创建连接，不探测。

理由：会话存储不是热开关，启动期失败比运行期随机 401 好排查。

**验证码存储抽象**

| 文件 | 作用 |
|---|---|
| `service/captcha/CaptchaStore.java` | 窄接口：`put / get / remove / mode` |
| `service/captcha/CaffeineCaptchaStore.java` | `lx.redis.enabled=false` 或未配置时启用；容量 10000，5 分钟过期 |
| `service/captcha/RedisCaptchaStore.java` | `lx.redis.enabled=true` 时启用；Key 前缀 `lx:captcha:`，按条目 TTL |

`CaptchaService` 只依赖 `CaptchaStore`，不再直接持有 Caffeine。
这样多实例部署时“A 节点发码、B 节点登录”不会失败，业务代码零改动。

**启动日志**

`StorageModeReporter`（`lx-boot`）在启动完成后打印一行：

```text
存储模式 | Redis 开关=false | Sa-Token 会话=内存 | 验证码=Caffeine
存储模式 | Redis 开关=true  | Sa-Token 会话=Redis | 验证码=Redis
```

避免部署后靠猜。参数设置 / 缓存监控将来可以**只读展示**这行信息，不提供切换按钮。

**两态行为矩阵**

| `lx.redis.enabled` | Redis 状态 | 启动 | 会话 | 验证码 |
|---|---|---|---|---|
| false | 未安装 / 未启动 | ✅ | 内存 | Caffeine |
| true | 正常 | ✅ | Redis | Redis |
| true | 不可用 | ❌ 启动失败（PING 失败） | — | — |

### 24.3 验证结果（本轮实测）

| 项 | 结果 |
|---|---|
| `mvn -DskipTests package` | 成功 |
| `enabled=false` + 本机无 Redis | 启动成功；日志 `Sa-Token 会话=内存 | 验证码=Caffeine` |
| `enabled=false` 登录 / `/auth/info` | `code=0`，Token 正常 |
| `enabled=true` + 本机 Redis 3.0.504 | 启动成功；日志 `Sa-Token 会话=Redis | 验证码=Redis` |
| Redis 会话 Key | `lx-platform-token:platform:token:<token>`、`...:session:1` |
| Token 跨重启 | 登录拿 Token → 重启应用 → 同一 Token 调 `/auth/info` 仍 `code=0` |
| Redis 验证码 | `GET /auth/captcha` 后从 `lx:captcha:<key>` 读到 4 位码，TTL=300 |
| 用 Redis 中的验证码登录 | `code=0`；同一验证码复用 → `code=2003` |
| `enabled=true` + Redis 停止 | 启动失败，`IllegalStateException: lx.redis.enabled=true 但 Redis 不可用` |
| 前端冒烟 `.local/uitest/smoke.mjs` | **20/20**，控制台 0 error |
| 权限 UI `.local/uitest/permission.mjs` | **24/24** |
| 验证码开关 UI `.local/uitest/captcha-toggle.mjs` | **12/12** |
| `npm run build` | 成功 |

API 手工矩阵（验证码）：

| 总闸 | DB 参数 | `/auth/captcha` | 空验证码登录 |
|---|---|---|---|
| true | true | `enabled=true` + 图片 | `2003` |
| true | false | `enabled=false`，无图片 | `code=0` |
| false | true | `enabled=false`，无图片 | `code=0` |

> **测试侧踩坑**：PowerShell 5.1 的 `Invoke-RestMethod -Body` 发送中文时按 ANSI 编码，
> 把 `config_name / remark` 写成了 `?`。本轮已用 UTF-8 SQL 文件修复。
> 以后调接口改中文内容，统一用 Node 脚本或 UTF-8 SQL 文件，不要用 PS 拼 JSON。

### 24.4 相关文件

| 文件 | 变更 |
|---|---|
| `lx-api/lx-admin-api/.../CaptchaResponse.java` | 新增 `enabled`、`disabled()` |
| `lx-api/lx-admin-api/.../LoginRequest.java` | 验证码字段放宽 |
| `lx-modules/lx-module-system/.../CaptchaService.java` | 两级开关 + `CaptchaStore` |
| `lx-modules/lx-module-system/.../captcha/*.java` | 新增接口与两个实现 |
| `lx-common/lx-common-redis/.../SaTokenRedisConfig.java` | 新增，按开关注册并 PING |
| `lx-common/lx-common-redis/README.md` | 启用步骤、Key 约定、禁止事项 |
| `lx-boot/.../config/StorageModeReporter.java` | 新增，启动打印存储模式 |
| `lx-boot/src/main/resources/application.yml` | Redis 连接、开关、自动装配排除 |
| `lx-ui-admin/src/views/login/index.vue` | 验证码显隐、computed rules |
| `.local/uitest/captcha-toggle.mjs` | 新增，12 项验证码开关用例 |
| `.local/uitest/smoke.mjs`、`permission.mjs` | 适配验证码开 / 关两种登录页 |

### 24.5 遗留

- 本地只验证了 Redis 3.0.504；生产建议 5.0+，并在部署手册里写清版本要求。
- 缓存监控页尚未实现；目前只能看启动日志或 `redis-cli`。
- 业务缓存还没有抽象层；等第一个真实缓存需求出现时再定接口，避免提前设计。
- Spring Boot 3 线（Boot 2.7 的 `spring.redis.*` 在 3.x 改为 `spring.data.redis.*`）尚未处理，属于第二条构建线的迁移项。

## 25. 第八轮：操作日志 / 登录日志闭环

### 25.1 一句话结论

`@Log` 从“只打应用日志”变成**真正落 `sys_oper_log`**；两个监控页从占位页换成查询 / 详情 / 清空；
清空单独一个权限码（V8）；顺带把一直没人读的参数 `sys.log.keepDays` 接上清理任务。
后端断言 **29/29**、前端冒烟 **22/22**（页面 10 → 12）、权限 UI **30/30**、验证码 **12/12**，构建通过。

### 25.2 切面不直接写库：`OperLogStore` SPI

| 层 | 职责 | 不做什么 |
|---|---|---|
| `lx-common-log` | `LogAspect` 采集：谁、何时、URI、IP、耗时、结果、异常 | 不依赖 MyBatis / 业务实体 |
| `lx-common-log` | `OperLogStore` 接口：`save(OperLogRecord)` | 不决定写哪张表 |
| `lx-module-system` | `SysOperLogStore` 实现：映射到 `sys_oper_log` 并 insert | 不采集、不改业务 |

这样做的直接好处：

- 公共模块可裁剪：没有 Store 实现时切面只打一行应用日志，启动不报“缺 Bean”；
- 以后换异步 / 消息队列只改 `SysOperLogStore`，注解与切面不动；
- 避免 `lx-common-log → lx-module-system` 的反向依赖。

**同步写而不是异步**：审计要求“接口返回时日志已经能查到”，异步在进程崩溃时会丢最后几条；
代价是每条写接口多一次 insert，日志表按 `oper_time` 建了索引，v1.0 量级可以接受。
落库异常被切面 catch，只 warn，不影响主业务。

### 25.3 写了什么、没写什么

| 项 | 处理 |
|---|---|
| 业务类型 | 存 `BusinessType.ordinal()`：0 其他 / 1 新增 / 2 修改 / 3 删除 / 4 授权 / 5 导出 / 6 导入 / 7 强退 / 8 清空 |
| 请求参数 | 序列化后**截断到 2000 字符**；`MultipartFile`、`ServletRequest/Response`、`BindingResult`、`byte[]` 直接跳过 |
| 密码 / Token | 三重防线：①`SysUser.password` 是 `@JsonProperty(WRITE_ONLY)`，序列化本就不输出；②字段名含 `password/passwd/secret/token` 的值替换为 `***`；③重置密码接口 `saveRequestData = false`，请求体整段不记 |
| 返回结果 | 截断 2000 字符；异常时不记结果，只记 `error_msg` |
| 字段长度 | 方法 255、URL 500、IP / 操作人 64，与 `V1` 表结构一致，避免插入时被截断报错 |
| 部门名 | `dept_name` 留空：会话里没有部门信息，v1.0 不为了填字段再去查库 |

**为什么密码断言不是“看到 `***`”**：实测发现请求体里根本没有 `password` 字段（Write-Only 生效），
所以 `verify-log.ps1` 断言的是“不含明文密码”，并在文档里说明正则脱敏只是给未来 DTO 兜底。

### 25.4 查询、清空与权限

| 接口 | 权限码 |
|---|---|
| `GET /api/admin/system/oper-log/list` | `platform:monitor:operlog:list` |
| `GET /api/admin/system/oper-log/{operId}` | `platform:monitor:operlog:list` |
| `DELETE /api/admin/system/oper-log/clean` | `platform:monitor:operlog:clean` |
| `GET /api/admin/system/login-log/list` | `platform:monitor:loginlog:list` |
| `DELETE /api/admin/system/login-log/clean` | `platform:monitor:loginlog:clean` |

- 列表支持：模块、操作人、业务类型、状态、时间范围，`pageSize` 夹在 1~100；
- 只提供查询与清空，**不提供新增 / 修改**：审计数据被改过就失去意义；
- 清空动作自身也带 `@Log`，所以删完之后表里会留一条“清空”记录——这是预期，便于追“谁在什么时候清了日志”；
- `BlockAttackInnerInterceptor` 会拦截无 where 的 delete，清空时 wrapper 显式带 `isNotNull(主键)`，
  既表达意图也通过插件检查。

`V8__monitor_log_perms.sql` 在菜单 14（登录日志）/ 15（操作日志）下各补一个 `F` 按钮
（`1401` / `1501`），并按 V4 的 `NOT EXISTS` 写法授予 `role_id=1`。清空与列表**故意分开**：
能看审计数据的人不该自动拥有删审计数据的权力。

### 25.5 顺带接上 `sys.log.keepDays`

参数设置里一直种着 `sys.log.keepDays=180`，但没有任何代码读它——按“每个 key 都必须有人读”的原则，
本轮补 `LogRetentionTask`：

| 项 | 说明 |
|---|---|
| 触发 | `@Scheduled(cron = "${lx.log.cleanCron:0 30 3 * * ?}")`，默认每天 03:30 |
| 依据 | 每次执行时读 `sys.log.keepDays`，不缓存，改完次日生效 |
| 范围 | `sys_oper_log.oper_time`、`sys_login_log.login_time` 早于截止时间 |
| 安全默认值 | 读参数失败 / 非数字 / 小于 1 天 → **跳过本次清理**；上限 3650 天，防止误填巨大值 |
| 必要前提 | `LingxiaoApplication` 加 `@EnableScheduling`，否则任务根本不会跑 |

验证方式（本地）：插入两条 300 天前的日志 → 用 `--lx.log.cleanCron="*/10 * * * * ?"` 启动
→ 10 秒内两条被删，当天的 16 条操作日志 / 7 条登录日志原样保留。

### 25.6 前端两个页面

| 页面 | 路径 | 能力 |
|---|---|---|
| 操作日志 | `/monitor/oper-log` | 模块 / 操作人 / 类型 / 状态 / 时间筛选；详情弹窗（参数、结果、异常高亮）；清空 |
| 登录日志 | `/monitor/login-log` | 账号 / IP / 状态 / 时间筛选；成功失败标签；清空 |

- 列表里的请求地址、方法签名用等宽字体；详情把 JSON 自动格式化，解析失败则原样展示；
- 业务类型序号 → 中文在前端 `businessTypes` 里维护，新增枚举时两端一起改；
- 两个清空按钮都用 `v-hasPerm` 隐藏，后端仍是最终边界。

### 25.7 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| Flyway | 升到 **v8**（`monitor log perms`） |
| `.local/verify-log.ps1` | **29/29**：写操作落库、筛选、详情、无明文密码、重置密码不记 body、清空、登录日志、匿名 401 |
| `.local/uitest/smoke.mjs` | **22/22**，页面 10 → **12**，控制台 0 error |
| `.local/uitest/permission.mjs` | **30/30**：受限账号看不到两个日志菜单，直打列表 / 清空四个接口全部 403 |
| `.local/uitest/captcha-toggle.mjs` | **12/12** |
| `npm run build` | 成功（新增 `operlog` 7.65 kB / `loginlog` 4.37 kB） |
| 日志保留清理 | 300 天前的 2 条被删，当天 16 / 7 条保留 |

### 25.8 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| `captcha-toggle.mjs` 前半通过、开启态全挂 | 后端是用 `--lx.captcha.enabled=false` 起的，总闸关着，参数改 true 也不会出图 | 先按默认配置重启后端再跑；脚本注释里写明这个前置 |
| `--lx.log.cleanCron=*/10 * * * * ?` 启动报 `Cron expression must consist of 6 fields (found 1)` | PowerShell 的 `Start-Process -ArgumentList` 把空格当参数分隔，cron 被拆成 5 个参数 | 整个参数加引号：`'--lx.log.cleanCron="*/10 * * * * ?"'` |
| 删除临时岗位返回 **405** | 复用了同名 `postCode`，第二次新增被业务规则拒绝（HTTP 200 + `code!=0`），`data` 为 null，URL 变成 `/post/` → 405 | 测试数据加时间戳后缀；PS 脚本里对每个响应都断言 `code` |
| `oper_param` 断言 `***` 失败 | 密码字段 Write-Only，序列化后不存在，自然不会出现星号 | 改成断言“不含明文密码”，并把真实现象写进文档 |
| 重定向日志里的中文变成 `?` | JVM 无控制台时 stdout 编码退化为 ASCII 系代码页，不是应用编码问题 | 本地排查用 SQL / 接口断言，不依赖日志里的中文 |

### 25.9 相关文件

| 文件 | 变更 |
|---|---|
| `lx-common-log/.../domain/OperLogRecord.java` | 新增，采集结果 |
| `lx-common-log/.../store/OperLogStore.java` | 新增，存储 SPI |
| `lx-common-log/.../aspect/LogAspect.java` | 重写：脱敏、截断、跳过文件、无实现时退化 |
| `lx-module-system/.../entity/SysOperLog.java`、`mapper/SysOperLogMapper.java` | 新增 |
| `lx-module-system/.../service/log/SysOperLogStore.java` | 新增，落库实现 |
| `lx-module-system/.../service/SysOperLogService.java`、`SysLoginLogService.java` | 新增，查询 / 详情 / 清空 |
| `lx-module-system/.../controller/SysOperLogController.java`、`SysLoginLogController.java` | 新增 |
| `lx-module-system/.../task/LogRetentionTask.java` | 新增，按 `sys.log.keepDays` 清理 |
| `V8__monitor_log_perms.sql` | 新增两个清空权限 + 超管授权 |
| `lx-boot/.../LingxiaoApplication.java` | 加 `@EnableScheduling` |
| `SysUserController.resetPassword` | `@Log` 加 `saveRequestData = false` |
| `lx-ui-admin/src/api/log.js` | 新增 |
| `lx-ui-admin/src/views/monitor/operlog/index.vue`、`loginlog/index.vue` | 新增 |
| `.local/verify-log.ps1` | 新增，29 项后端断言 |
| `.local/uitest/smoke.mjs`、`permission.mjs` | 页面清单 10 → 12；权限断言 24 → 30 |

### 25.10 遗留

| 项 | 说明 |
|---|---|
| 异步落库 | 目前同步 insert；量级上来后改 `SysOperLogStore`（队列 / 批量插入），切面不动 |
| 日志导出 | 未做 Excel / CSV 导出，`BusinessType.EXPORT` 也还没有真实使用点 |
| 登录失败锁定 | 登录日志已记录失败，但未做“连续 N 次失败锁定账号” |
| 部门名 | `dept_name` 为空，会话里补部门信息后才好填 |
| 在线用户 / 服务器 / 缓存监控 | 仍是占位页，等 W4 |

---

## 26. 第九轮：安全加固（公告 XSS 清洗 + 数据范围落地）

### 26.1 一句话结论

公告富文本从“信任前端”改成 **Jsoup 白名单清洗**（写入 + 读取两侧）；
`sys_role.data_scope` 从“只存值”改成**真正参与用户查询与写操作校验**（1 / 3 / 4 / 5 生效，2 自定义明确降级）。
本轮新增后端断言 **35（数据范围）+ 24（XSS）**，回归 `verify-log 29/29`、`smoke 22/22`、
`permission 30/30`、`captcha 12/12`，前端构建通过；测试数据全部清理，运行态恢复默认。

> 2026-11-19 更新：`2 自定义数据范围` 已在第 43 节补齐；本节“2 降级 / 保存拒绝”
> 与 26.5 的“下拉禁用”只代表第九轮当时状态，以第 43 节为准。

### 26.2 公告 XSS：为什么不能用正则

`SysNotice.noticeContent` 是 WangEditor 提交的 HTML。此前注释写的是“上线前必须补”，
本轮把坑填上。清洗类放在 `support/HtmlSanitizer`，**不放在前端**（可绕过），
**也不放在 Controller**（新增接口容易漏）。

| 方案 | 问题 |
|---|---|
| 正则删 `<script>` | `<scr<script>ipt>`、大小写混写、属性引号嵌套、实体编码都能绕过；也分不清文本里的 `&lt;script&gt;` |
| 前端过滤 | 抓包直接调接口即可绕过 |
| 输出转义 | 公告需要按 HTML 渲染，转义后富文本全变源码 |
| **Jsoup 解析后按白名单重建** | 只保留明确允许的标签 / 属性 / 协议，其余丢弃，文本保留 |

白名单基于 `Safelist.relaxed()`，补充：

- 语义 / 媒体标签：`hr`、`s`、`del`、`ins`、`figure`、`figcaption`、`video`、`audio`、`source`；
- 属性：`a[target]`、`img[alt,title,width,height]`、`video[controls,width,height,poster]`、`source[src,type]`、`td/th[colspan,rowspan]`；
- 链接强制 `rel="nofollow noopener noreferrer"`，防止 `target="_blank"` 反向控制 opener；
- 图片 / 视频 / `source` 只允许 `http` / `https`，直接消灭 `javascript:` / `data:` 伪协议；
- **不放行 `style` / `class`**：CSS 里同样能藏 `expression()` / `url()`，没做 CSS 白名单前一律剥离。
  代价是富文本里的颜色、居中等内联样式会丢，这是 v1.0 的明确取舍。

**清洗点有两处**：

1. 写入：`create` / `update` 落库前清洗，拦住新增脏数据；
2. 读取：`page` / `detail` 返回前再洗一次，兜住“接入清洗之前已经入库”的历史数据。

### 26.3 数据范围：支持哪些、怎么解析

| `data_scope` | 含义 | v1.0 行为 |
|---|---|---|
| 1 | 全部数据 | 不加条件（超管等价于此） |
| 2 | 自定义部门 | **降级为“仅本人” + warn**：没有 `sys_role_dept` 表，存了也不会生效，保存接口直接拒绝 |
| 3 | 本部门 | `dept_id = 当前用户部门` |
| 4 | 本部门及以下 | 闭包表 `selectSubtree` 取整棵子树（含自己） |
| 5 | 仅本人 | `user_id = 当前用户` |

解析顺序与兜底：

- 超管（`user_id = 1`）直接 ALL，不查角色；
- 多角色取**最大范围**（1 > 4 > 3 > 5），角色是授权叠加，不取交集；
- 无登录上下文、无角色、部门为空 → **仅本人**；宁可查不到，不能放开；
- 角色停用（`status='1'`）不参与数据范围计算，避免“停用角色还在放大范围”；
- 查询只取 `data_scope` 一列（`SELECT DISTINCT`），不加载整份角色对象。

### 26.4 作用点：不能只过滤列表

| 入口 | 处理 |
|---|---|
| 用户列表 `page` | 在调用方筛选之上追加范围条件；再叠加部门筛选时是**交集**，不能互相绕过 |
| 用户详情 / 修改 / 删除 / 重置密码 / 启停用 | 先 `mustGet` 再 `assertUserVisible`，按 ID 直调同样 403 |
| 新增 / 修改用户的 `deptId` | `assertDeptVisible`：非超管必须指定部门，且只能选范围内部门；范围 5 只能选自己部门 |
| 授予角色 | `assertAssignableRoles`：非超管不能新增 `role_id=1`，也不能新增 `data_scope=1` 的角色 |
| 角色保存 | `dataScope` 只接受 1 / 3 / 4 / 5；非超管不能把角色范围改成 1（防止拿到角色编辑权限后自我提权） |

`assertAssignableRoles` 只检查“本次新增”的角色：编辑用户时前端会回传已有角色，
如果目标本来就持有高权限角色，不应连改手机号都被拒绝；真正的提权动作是把高权限角色**新加进来**。

### 26.5 前端

角色页的数据范围下拉把“自定义数据”改为**禁用项**（`自定义数据（未启用）`），
避免用户选了一个后端不认的值；`dataScopeLabel` 仍能正确显示历史数据里的 `2`。

### 26.6 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| `.local/verify-datascope.ps1` | **35/35**：部门树 A/A1/B + 3/4/5 三种角色 + 6 个用户；列表 4 / 5 / 1、admin 6；越权详情 / 启停用 / 跨部门新增全部 1003；无部门 1001；授 `role_id=1` 1003；scope 2 / 9 保存 1001；范围内新增 / 删除成功 |
| `.local/verify-xss.ps1` | **24/24**：`script` / `onerror` / `onclick` / `javascript:` / `iframe` / `svg` 全部被清；`b`、`img alt`、相对 `src`、相对 `href`、`target`、强制 `rel` 保留；**直接查库确认写入侧已清洗** |
| `.local/verify-log.ps1` | 29/29 |
| `.local/uitest/smoke.mjs` | 22/22，控制台 0 error |
| `.local/uitest/permission.mjs` | 30/30 |
| `.local/uitest/captcha-toggle.mjs` | 12/12 |
| `npm run build` | 成功 |
| 数据清理 | `dsx*` 用户 / 角色 / 部门 0 条，`xss_*` 公告 0 条；`sys.account.captchaEnabled=true` |

### 26.7 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 相对图片 `src="/api/file/1"` 被整条删掉 | `Jsoup.clean(html, "", safelist)` 基准地址为空，相对地址无法解析成 http/https，协议白名单判定失败 | 传入占位基准地址 `https://lingxiao.local/`；`preserveRelativeLinks(true)` 保证输出仍是原相对地址 |
| 首次数据范围断言“本部门只有 2 人”失败，实际 4 人 | 测试设计错误：三个范围管理员本身都建在部门 A，A 里还有目标用户 | 期望值改为 4 / 5；断言同时校验“不含 A1 / B”的用户名 |
| “跨部门新增用户”返回 `code=0` | PS 测试脚本的 `New-User` 帮助函数把 headers 写死成 admin，实际是超管在新增 | 函数增加 `$h` 参数，调用方显式传受限账号 Token |
| `verify-xss.ps1` 在查库步骤中断 | `mysql --password=` 的 stderr 警告被 `$ErrorActionPreference='Stop'` 当成终止错误 | 改用 `MYSQL_PWD` 环境变量，不再把密码写进命令行 |
| 控制台里 `用户管理` 显示成乱码 | PowerShell 5.1 的 `[Console]::OutputEncoding` 是 **936（GBK）**，而终端捕获按 UTF-8 解码；**不是文件或数据库坏了** | 显示前设 `[Console]::OutputEncoding=[Text.Encoding]::UTF8`；用 Node 扫描确认源码均为合法 UTF-8，并比对 `HEX(menu_name)` 与 UTF-8 字节一致 |

### 26.8 相关文件

| 文件 | 变更 |
|---|---|
| `lx-dependencies/pom.xml` | 新增 `jsoup.version=1.17.2` 与 BOM 条目 |
| `lx-module-system/pom.xml` | 引入 `org.jsoup:jsoup` |
| `lx-module-system/.../support/HtmlSanitizer.java` | 新增，白名单构建 + `clean()` |
| `lx-module-system/.../service/SysNoticeService.java` | 写入 / 读取两侧清洗 |
| `lx-module-system/.../entity/SysNotice.java`、`controller/SysNoticeController.java` | 注释去掉“待补” |
| `lx-module-system/.../mapper/SysRoleMapper.java` | 新增 `selectDataScopesByUserId` |
| `lx-module-system/.../service/DataScopeService.java` | 新增，范围解析 / 列表过滤 / 用户与部门断言 |
| `lx-module-system/.../service/SysUserService.java` | 列表过滤 + 写操作断言 + 角色授予守卫 |
| `lx-module-system/.../service/SysRoleService.java` | 数据范围取值校验 + 非超管禁止提升为全部 |
| `lx-module-system/.../entity/SysRole.java` | 注释更新为实现现状 |
| `lx-ui-admin/src/views/system/role/index.vue` | “自定义数据”改为禁用项 |
| `.local/verify-datascope.ps1` | 新增，35 项 |
| `.local/verify-xss.ps1` | 新增，24 项（含直接查库） |

### 26.9 遗留

| 项 | 说明 |
|---|---|
| 2 自定义数据范围 | 需要 `sys_role_dept` 关联表 + 角色-部门授权 UI，v1.1 再开；当前保存被拒绝、历史数据按仅本人 |
| 部门 / 角色列表 | 暂未按数据范围过滤：有 `dept:list` / `role:list` 权限即可看全量；它们不返回用户隐私数据，风险低于用户列表 |
| 导出 | 导出接口尚未实现，实现时必须复用 `applyUserScope`，不能另写一套条件 |
| 数据范围缓存 | 每次请求查 1~2 次（角色范围 + 当前用户部门），v1.0 量级可接受；后续可挂 Caffeine，改角色时主动失效 |
| CSS 白名单 | 富文本样式（颜色 / 居中）会被剥离；要做需引入 CSS 解析与属性白名单，不能简单放开 `style` |

---

## 27. 第十轮：参数设置控件化（文本 / 数字 / 开关 / 下拉）

### 27.1 一句话结论

参数不再一律是文本框：`sys_config` 新增 `control_type` 与 `config_options`，
4 条内置参数分别改成 **开关 / 数字 / 数字 / 下拉**；前端按控件类型渲染，
后端在新增 / 修改时校验“值是否属于该控件允许的范围”。
顺手把一直没人读的 `sys.file.uploadMaxMb` 接到文件上传校验上，
并新增 `ConfigProvider` SPI 让文件模块能读参数又不反向依赖系统模块。
后端断言 **29/29**、前端控件 UI **19/19**、冒烟 **22/22**、构建通过。

### 27.2 为什么把控件类型放数据库

如果前端按 key 硬编码：

```js
if (key === 'sys.account.captchaEnabled') return <Switch />
if (key === 'sys.editor.type') return <Select ... />
```

新增一个参数就要改一次前端并重新发版，而且 CMS / 会员模块以后新增的参数
同样要改基座代码——这正是“模块嵌入不该改基座”要避免的。
把控件类型与选项作为数据，前端只认 4 种控件，参数自身描述“怎么编辑”。

`config_type`（Y 内置 / N 普通）保持不变，它回答的是“能不能改 key / 删除”，
与“用什么控件编辑”是两件事，没有复用同一列。

### 27.3 选项格式：一个小约定，前后端各实现一次

| 控件 | `config_options` 格式 | 示例 |
|---|---|---|
| `text` | 忽略 | - |
| `number` | `最小值:最大值`，可只写一侧 | `1:3650`、`1:`、`:100` |
| `switch` | `开值:关值`，留空默认 `true:false` | `true:false`、`1:0` |
| `select` | `值:显示名`，英文/中文逗号或换行分隔 | `wangeditor-next:wangEditor Next,ueditor-plus:UEditor Plus` |

没有引入 JSON：这些选项要人手在参数页里填，`值:显示名` 比 JSON 少一半括号，
出错了也容易肉眼定位。代价是值里不能出现逗号与冒号，已在保存时校验。

### 27.4 后端：控件不是安全边界

`SysConfigService#normalizeAndValidateControl` 在 **create / update 都调用**：

| 控件 | 校验 |
|---|---|
| `number` | 必须是整数；配置了范围时不能越界；`min > max` 拒绝 |
| `switch` | 值必须等于开值或关值；开值与关值不能相同 |
| `select` | 至少一个选项；值必须在选项内；选项值不能空、不能重复 |
| `text` | 丢弃 options，避免留下误导性配置 |

内置参数（`config_type='Y'`）除了原来的“禁改 key、禁删除”，
现在 **control_type 与 config_options 也被锁定**：前端置灰，后端强制还原成库里的值。
理由是把开关参数改成文本框、或把下拉选项删掉，会让已有值变得不可解析，
而这种故障只会在功能悄悄失效时暴露。

### 27.5 顺手接上 `sys.file.uploadMaxMb`

之前这个 key 和 `sys.log.keepDays` 一样“种在库里没人读”。本轮新增：

- `lx-common-core`：`ConfigProvider` 只读 SPI（`getValue / getIntValue / getBooleanValue`）；
- `lx-module-system`：`SysConfigService implements ConfigProvider`，只暴露读，不暴露写；
- `lx-module-file`：`ObjectProvider<ConfigProvider>` 注入，读不到（裁剪部署）就回退 YAML。

优先级：**参数设置 > `lx.file.max-size-mb` > 容器上限**。
容器 `spring.servlet.multipart.max-request-size=20MB` 是请求进入 Controller 前的硬限制，
数据库里的值只能调小、不能突破；参数写成 30 时按 20 生效并 warn。
参数值非法（0 / 负数 / 非整数）时回退 YAML，不让一条脏配置把上传全挂掉。

### 27.6 前端：一个页面，四种值控件

`src/utils/config-control.js` 集中放解析函数（与后端同一套约定），
页面只负责：

- 列表：`switch` 显示“开启 / 关闭”标签，`select` 显示显示名而不是原始值；
- 编辑：控件类型下拉切换后，值控件在 `el-input` / `el-input-number` /
  `el-switch` / `el-select` 之间切换；
- 切换类型时把值收敛到新控件能接受的范围（例如切到数字时取最小值）；
- 打开编辑弹窗时不自动改写历史值，避免“看一眼就改数据”，保存时再让校验拦。

搜索区新增“控件”筛选，列表接口对应新增 `controlType` 参数。

### 27.7 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| `.local/verify-config-control.ps1` | **29/29**：4 条种子参数类型、控件筛选、数字越界/非整数、开关非法值、下拉越界/空选项、text 丢弃选项、内置锁定、上传上限读到 1MB 后 1.5MB 文件返回 3003 |
| `.local/uitest/config-control.mjs` | **19/19**：列表按控件渲染、内置开关置灰、新增数字参数、编辑改下拉并保存、删除清理、控制台 0 error |
| `.local/uitest/smoke.mjs` | 22/22（参数页仍 4 行） |
| `npm run build` | 成功 |

### 27.8 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 跑完验证脚本后，验证码 / 上传上限两条参数的**名称和备注变成 `???????`** | PowerShell 5.1 的 `Invoke-RestMethod` 在响应没有 charset 时按 ISO-8859-1 解码 JSON，脚本又把读到的名称原样 PUT 回去 | `Api()` 改用 `Invoke-WebRequest` + `RawContentStream` 按 UTF-8 解码；再用 UTF-8 SQL 文件修复两条数据 |
| 冒烟测试参数页超时，实际库里有数据 | 就是上一条导致的名称损坏，冒烟等待“登录验证码开关”失败 | 修复数据后复跑 22/22 |
| `el-switch` 打开编辑弹窗时不显示为开 | Element Plus 用 `modelValue === activeValue` 严格比较；计算属性返回布尔 `true`，与字符串 `'true'` 不相等 | `switchValue` 改为字符串透传，不再转布尔 |

### 27.9 相关文件

| 文件 | 变更 |
|---|---|
| `lx-module-system/.../db/migration/system/V9__system_config_control.sql` | 新增两列 + 4 条参数类型 / 选项 |
| `lx-module-system/.../entity/SysConfig.java` | 新增 `controlType`、`configOptions` |
| `lx-module-system/.../service/SysConfigService.java` | 控件归一化与校验、`ConfigProvider` 实现、列表按控件筛选 |
| `lx-module-system/.../controller/SysConfigController.java` | 列表新增 `controlType` 参数 |
| `lx-common-core/.../config/ConfigProvider.java` | 新增只读参数 SPI |
| `lx-module-file/.../service/FileService.java` | 上传上限读参数、容器上限截断、YAML 回退 |
| `lx-ui-admin/src/utils/config-control.js` | 新增，选项解析 / 展示文案 |
| `lx-ui-admin/src/views/system/config/index.vue` | 列表与弹窗按控件渲染 |
| `.local/verify-config-control.ps1` | 新增，29 项 |
| `.local/uitest/config-control.mjs` | 新增，19 项 |

### 27.10 遗留

| 项 | 说明 |
|---|---|
| 数字精度 | 目前只支持整数；以后有金额 / 比率参数再补 decimal 与精度配置 |
| 选项来源 | 下拉选项手填在参数上，没有复用字典；需要多语言或集中维护时再改成 `dict_type` 引用 |
| 控件扩展 | 预留了 `textarea`、`password`、`datetime` 的语义位置，但未实现，避免现在做用不上的控件 |
| 参数缓存 | 仍每次查库；加缓存后 `update / delete` 必须同步失效 |
| 上传上限 | 只能调小到容器上限以内；要支持更大文件需改 YAML 并重启，不能只改数据库 |

---

## 28. 第十一轮：W4 系统监控（在线用户 / 服务监控 / 缓存监控）

### 28.1 一句话结论

三个监控页全部接**真实数据**，没有一处返回写死的假值：

- **在线用户**直接枚举 Sa-Token 平台域会话，一个 Token 一行，可筛选、可单条 / 批量强退；
  列表只下发脱敏 `tokenId`，被踢 Token 的下一次请求立即 401；强退动作落操作日志但**不记请求体**；
- **服务监控**用 JDK MXBean + `FileStore` 实时采样 CPU / 物理内存 / JVM 堆 / 线程 / 磁盘，
  取不到的指标返回 `-1`，前端显示“不可用”，不伪装成 0；
- **缓存监控**按 `lx.redis.enabled` 分流：关闭时展示本机 Caffeine 统计并明确提示单机限制，
  开启时解析 Redis `INFO`；连接失败只降级，不让监控页自己变成故障点。

验证：后端 `.local/verify-monitor.ps1` **51/51**、监控 UI `.local/uitest/monitor.mjs` **8/8**、
冒烟 **22/22**、权限 UI **30/30**、日志 **29/29**、参数控件 **29/29**，`npm run build` 通过。

### 28.2 在线用户为什么不建“在线表”

会话的真实状态只有 Sa-Token 知道：过期、续期、并发登录、被踢，全在 Token 存储里。
另建一张 `sys_online_user` 必然出现“表里还在线、实际早已失效”的双写不一致，
还要额外做心跳与清理任务。

因此 `OnlineUserService` 每次请求直接：

1. `StpPlatformUtil.searchTokenValue("", 0, -1)` 枚举平台域全部 Token Key；
2. 切掉 Key 前缀，排除 `Token-Session` / `last-active` 这类派生 Key（去前缀后仍带 `:`）；
3. 逐个取 Token-Session，读不到的直接跳过（残留 Key 不算在线）；
4. 补用户名 / 昵称 / 部门名，过滤、排序、内存分页。

会话数量在几百级别，枚举成本可接受；同时设置 `MAX_TOKENS=5000` 上限，
超过只 warn 并截断，避免异常膨胀时把接口拖死。

### 28.3 一个 Token 一行 + tokenId 脱敏

同一账号允许多端登录（`sa-token.is-concurrent=true`），所以列表以 **Token 为行**，
而不是以用户为行。用户在办公室和家里各登录一次会看到两条来源不同的记录，
强退时可以只踢掉其中一条。

> 2026-11-20 更新：默认改为**单会话**，新登录会踢掉旧 Token；
> 需要多端时在参数设置开启 `sys.account.multiDeviceLogin`。详见第 44 节。
> 下面的字段说明在两种模式下都成立。

| 字段 | 来源 | 说明 |
|---|---|---|
| `tokenId` | 真实 Token 首 4 + `****` + 末 4 | 只用于强退定位，不能拿来冒充登录 |
| `userId / username / nickname` | Session + 用户表兜底 | Session 缺字段时用库里的值补 |
| `deptName` | `sys_dept` | 批量查，避免 N+1 |
| `loginIp / browser` | **Token-Session** | 每个 Token 各记各的 |
| `loginTime` | Session `createTime` | 等价于登录时间 |
| `lastActiveTime` | Sa-Token last-active Key | 未开启 `active-timeout` 时回退登录时间 |
| `timeoutSeconds` | `getTokenTimeout(token)` | 前端格式化成“x 小时 y 分” |
| `current / kickable / kickableReason` | 后端判定 | 前端只置灰，不重复实现策略 |

强退时把请求里的脱敏值再和当前所有会话的脱敏值做匹配：匹配不到 → “会话已失效”；
首 4 + 末 4 撞车 → 拒绝并提示刷新（宁可踢不掉，也不能踢错人）。

两条硬约束在后端：

- **不能强退当前会话**：防止运维把自己踢下线；
- **非超管不能强退 `user_id=1`**：防止低权限账号锁死内置管理员。

批量强退逐条执行、逐条返回失败原因，不做整批回滚——其中一条可能刚好自然过期。

### 28.4 登录 IP / 浏览器为什么写 Token-Session

账号 Session（`getSession()`）是“一个用户一份”，Token-Session 是“一个 Token 一份”。
如果把 IP / UA 写进账号 Session，同一账号第二次登录会覆盖第一次，
在线列表里两条会话会显示同一个来源——正好失去“多端会话”最需要的信息。

所以 `LoginHelper` 拆成两组：

| 方法 | 写入位置 | 内容 |
|---|---|---|
| `setLoginUser` | 账号 Session | userId / username / nickname |
| `setLoginContext` | **Token-Session** | loginIp / userAgent（UA 截断 200 字符） |

`AuthService.login` 在发完 Token 后调用 `setLoginContext(ServletUtils.getClientIp(), ...)`。
读取侧新增 `LoginHelper.readString(session, key)`，因为监控列表读的是“别人的 Session”，
不能复用只读当前上下文的 getter。

### 28.5 强退的权限与审计

`V10__monitor_online_kickout.sql` 新增按钮权限 `menu_id=1301`：

- `menu_type='F'`，挂在菜单 13（在线用户）下，只提供 `platform:monitor:online:kickout`；
- 授权给超管；用 `NOT EXISTS` 保证可重复执行。

接口设计有两点刻意安排：

1. `POST /api/admin/monitor/online/kickout` 用**请求体**传 `tokenIds`，不走 URL 参数，
   避免 Token 标识被访问日志 / 代理日志记录；
2. `@Log(..., saveRequestData = false)`，操作日志只记“谁在什么时候踢了人”，
   `oper_param` 为空；验证脚本直接查库断言了这一点。

权限拆分同样验证过：只授菜单 13（`platform:monitor:online:list`）的角色
可以看列表，但强退、服务监控、缓存监控全部 403 / 1003。

### 28.6 服务监控：只用 JDK 自带能力

不引入 OSHI / Prometheus。v1.0 的监控页只需要“这台机器现在怎么样”，
JDK 8 ~ 17 自带的 MXBean 已经覆盖核心指标：

| 指标 | 来源 |
|---|---|
| CPU 核数 / 负载 / 使用率 | `OperatingSystemMXBean`（含 `com.sun.management` 扩展） |
| 物理内存 / 交换区 | `com.sun.management.OperatingSystemMXBean` |
| JVM 堆 / 非堆 | `MemoryMXBean` |
| 线程数 / 峰值 / 累计 | `ThreadMXBean` |
| 已加载 / 卸载类 | `ClassLoadingMXBean` |
| 启动时间 / 运行时长 / 启动参数 | `RuntimeMXBean` |
| 磁盘分区 | `File.listRoots()` + `Files.getFileStore()` |

比例字段统一返回 **0~100**（保留两位小数），不让每个页面自己记得乘 100。
磁盘 `total <= 0`（空光驱 / 未挂载分区）直接跳过。

**CPU 反射的关键坑**：JDK 14+ 方法名是 `getCpuLoad()`，8~13 是 `getSystemCpuLoad()`。
一开始用 `target.getClass().getMethod(name)` 取方法，JDK 8 下 CPU 永远返回 `-1`——
运行时实现类 `sun.management.OperatingSystemImpl` 不是 public，invoke 抛
`IllegalAccessException`，而异常又被“继续尝试下一个方法名”吞掉了。
正确做法是**从公开接口 `com.sun.management.OperatingSystemMXBean.class` 取 Method**，
再对实例 invoke。修复后本机采样从“-1”变成正常数值（空闲时 0.0 也合理）。

### 28.7 缓存监控：两种部署形态 + 降级

v1.0 真正使用缓存的只有两处：Sa-Token 会话与登录验证码，所以监控对象就是它们。

| 开关 | 页面内容 |
|---|---|
| `lx.redis.enabled=false` | 验证码存储实现、条目数、容量上限、命中 / 未命中、命中率、淘汰数，并提示“单机缓存，多实例请开 Redis” |
| `lx.redis.enabled=true` | Redis 版本 / 模式 / 端口、内存与上限、命中率、客户端连接、运行时长、逻辑库、命令统计 |

Redis 读取失败（超时 / 权限 / 代理）只把原因放进 `warnings`，接口仍返回 200。
运维往往正是在 Redis 出问题时才打开这个页面，监控页再抛 500 只会让排查更难。

`CaptchaStore` 新增 `stats()` **默认方法**：验证码存储的职责本来很窄，统计属于可选运维能力；
以后新增第三种实现不实现也能编译，页面显示空统计即可，不会因为一个监控字段改坏主流程。

两个统计口径细节：

- Caffeine 开启 `recordStats()`；零请求时 `CacheStats.hitRate()` 返回 **1.0**，
  直接展示会误导，`stats()` 里在 `requestCount == 0` 时返回 0；
- Redis 实现用 **SCAN**（`match lx:captcha:*`，上限 10000）统计 Key 数量，
  不用 `KEYS` 阻塞单线程；达到上限时页面标注“仅统计前 10000 个”。

解析 `INFO` 时有个小坑：`commandstats` 行形如
`cmdstat_get:calls=10,usec=20,usec_per_call=2.00`，`Properties` 会在第一个 `=` 处切开，
key 可能变成 `cmdstat_get:calls`，所以不能假设 key 就是命令名，要拼回原始行再解析。

### 28.8 前端三页

| 页面 | 要点 |
|---|---|
| `/monitor/online` | 账号 / IP 筛选；`row.current` 显示“当前会话”标签；`row.kickable` 控制勾选与按钮；单条 / 批量强退都带二次确认；自动刷新默认关闭，开启后 15s 一次，切页自动清理定时器 |
| `/monitor/server` | CPU / 物理内存 / JVM 堆三张进度卡；系统、JVM、磁盘三段 descriptions / table；字节数、运行时长、时间戳全部前端格式化 |
| `/monitor/cache` | 顶部标签显示“Redis 已启用 / 本机缓存”；未启用时展示 Caffeine 统计；启用时展示 INFO 摘要、逻辑库、命令统计前 20 |

强退成功后立即重拉列表，并用后端返回的 `failures` 提示“哪些没踢掉、为什么”；
如果一条都没成功也没有失败原因，提示“没有可强退的会话”。

### 28.9 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| `.local/verify-monitor.ps1` | **51/51**：V10 数据、真实会话、脱敏、当前会话、IP / 浏览器 / 部门、筛选命中与未命中、自踢拒绝、空列表 1001、单条强退、被踢 Token 401、会话消失、操作日志不含请求体、list-only 角色 403、服务 / 缓存指标真实性、fixture 清理 |
| `.local/uitest/monitor.mjs` | **8/8**：三页渲染、账号筛选与重置、页面强退（确认框 + 被踢 Token 401）、强退后页面可用、控制台 0 error |
| `.local/uitest/smoke.mjs` | **22/22**（占位页用例改用仍未实现的 `/tool/job`） |
| `.local/uitest/permission.mjs` | **30/30**（新增 1301 按钮未影响断言） |
| `.local/verify-log.ps1` | **29/29** |
| `.local/verify-config-control.ps1` | **29/29** |
| `npm run build` | 成功 |

### 28.10 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 在线列表按账号筛选 `total=0` | 过滤发生在 `fillUserInfo` 之前，用户名只存在账号 Session，Token-Session 里没有 | 先补用户名 / 昵称 / 部门，再过滤 |
| CPU 使用率永远是 `-1` | JDK 8 的 `OperatingSystemImpl` 不是 public，`target.getClass().getMethod(...).invoke(...)` 抛 `IllegalAccessException` 且被吞 | 从公开接口 `com.sun.management.OperatingSystemMXBean` 取 `Method` |
| 零请求时缓存命中率显示 100% | Caffeine `CacheStats.hitRate()` 在 `requestCount=0` 时返回 1.0 | `requests == 0` 时返回 0 |
| 冒烟测试“占位页”失败 | `/monitor/online` 已经实现，旧用例还在等“还没有实现” | 改用仍未实现的 `/tool/job`，并断言组件路径 |
| 会话枚举多出无效记录 | `searchTokenValue` 返回带前缀的完整 Key，且同前缀下还有 Token-Session / last-active 派生 Key | 切前缀 + 排除去前缀后仍含 `:` 的 Key |
| 强退后 audit 日志可能泄露 Token | 默认切面会把请求体写进 `oper_param` | 强退接口 `@Log(saveRequestData=false)`，验证脚本查库断言为空 |

### 28.11 相关文件

| 文件 | 变更 |
|---|---|
| `lx-module-system/.../db/migration/system/V10__monitor_online_kickout.sql` | 新增强退按钮权限并授超管 |
| `lx-common-security/.../util/StpPlatformUtil.java` | 会话枚举、前缀、Token-Session、最后活动、按 Token 下线 |
| `lx-common-security/.../util/LoginHelper.java` | Token-Session 常量、`setLoginContext`、`readString` |
| `lx-module-system/.../service/AuthService.java` | 登录写 IP / UA |
| `lx-module-system/.../service/OnlineUserService.java` | 在线列表 / 筛选 / 脱敏 / 强退 |
| `lx-module-system/.../service/ServerMonitorService.java` | MXBean + FileStore 采样、CPU 反射 |
| `lx-module-system/.../service/CacheMonitorService.java` | Caffeine 统计 + Redis INFO 解析 + 降级 |
| `lx-module-system/.../service/captcha/*` | `stats()` 默认方法、Caffeine recordStats、Redis SCAN |
| `lx-module-system/.../controller/Monitor*Controller.java` | 三个监控接口 |
| `lx-module-system/.../dto/OnlineUserVO.java` 等 5 个 DTO | 监控响应模型 |
| `lx-ui-admin/src/api/monitor.js` | 三个监控接口封装 |
| `lx-ui-admin/src/views/monitor/{online,server,cache}/index.vue` | 三个监控页 |
| `.local/verify-monitor.ps1` | 新增，51 项 |
| `.local/uitest/monitor.mjs` | 新增，8 项 |

### 28.12 遗留

| 项 | 说明 |
|---|---|
| 会话枚举成本 | 每次列表都枚举全部 Token；几千会话以上应改为分页 / 索引，或维护独立的在线索引 |
| 最后活动时间 | 未开启 Sa-Token `active-timeout` 时只能回退登录时间，看起来“一直没动” |
| 容器指标 | MXBean 拿的是整机 / JVM 视角，cgroup 限额（容器内存 / CPU quota）未换算 |
| 缓存页范围 | 只统计验证码存储与 Redis INFO，没有单独展示 Sa-Token 会话数量 |
| 强退提示 | 被踢用户下一次请求直接 401，没有“你已被管理员下线”的原因提示 |
| 在线用户审计 | 只记“谁踢了人”，没记被踢账号；需要时从 tokenId 反查的上下文已丢失 |

---

## 29. 第十二轮：定时任务（Spring Task + 数据库锁）

### 29.1 一句话结论

`/tool/job` 从占位页变成**真实可用**的定时任务模块：

- 建任务（执行器下拉、6 段 cron 校验）、启停、执行一次、看执行日志（开始 / 结束 / 耗时 / 触发方式 / 失败堆栈）；
- 多实例部署时同一任务**不重复执行**，靠 `sys_job_lock` 单条 `UPDATE` 抢锁；
- 任务日志按 `sys.job.logKeepDays` 自动清理，页面也能人工清空；
- 关闭 `lx.module.modules.job.enabled` 后接口 404、Bean 不装配、菜单落到占位页，**基座照常启动**。

验证：后端 `.local/verify-job.ps1` **51/51**、模块关闭 `.local/verify-job-module.ps1` **5/5**、
任务页 UI `.local/uitest/job.mjs` **9/9**；回归冒烟 **22/22**、权限 **30/30**、监控 **8/8**、
日志 **29/29**、参数控件 **29/29**、监控后端 **51/51**；`mvn package` 与 `npm run build` 均通过。

### 29.2 为什么不用 Quartz

| 方案 | 表 | 多库兼容 | v1.0 需求 |
|---|---|---|---|
| Quartz 集群 | 11~13 张 `QRTZ_*` | 达梦 / 金仓无官方方言 | 全覆盖，但用不上 |
| **Spring Task + 数据库锁** | 1 张锁表，复用 `sys_job` | 一条标准 `UPDATE`，无方言 | 启停 / 立即执行 / 日志 / 集群不重复 |

v1.0 只需要“到点执行、别重复、能看结果”，没有复杂依赖、分片、错失补偿的需求。
选后者少 12 张表、少一套调度元数据，切信创数据库时不用额外适配。

**代价要写清楚**：Spring Task 不持久化触发器，停机期间错过的执行**不会补跑**。
`sys_job.misfire_policy` 字段保留（1 立即 / 2 执行一次 / 3 放弃），v1.0 实际统一按“放弃”处理，
页面上也如实标注“保留，当前按放弃处理”，不假装它有作用。

### 29.3 V11 迁移

`V11__job_schema.sql`（`lx-module-job/.../db/migration/job/`）只做必要的事：

| 变更 | 原因 |
|---|---|
| `sys_job_log` 补 `job_id / trigger_type / start_time / end_time / duration_ms` | 日志要能按任务查、能算耗时、能区分调度 / 手动 |
| 新增 `sys_job_lock`（`job_id` 主键 + `lock_owner` + `lock_time`） | 一行一任务，抢锁即 `UPDATE` |
| 种子任务 `invoke_target`：`logCleanTask.clean` → `systemLogClean` | 旧值是“类.方法”，v1.0 按名称查 `JobHandler` |
| 补 `sys_job_lock` 的种子行 | 历史任务才不会在第一次执行时才补锁行 |
| 新增参数 `sys.job.logKeepDays = 30`（number，1:3650） | 每个参数都必须有人读，由 `jobLogClean` 执行器读取 |
| 新增按钮权限 2101~2107 并授超管 | 新增 / 编辑 / 删除 / 启停 / 执行一次 / 日志 / 清日志分开授权 |

`ALTER` 拆成单列单句，授权用 `INSERT ... SELECT ... WHERE NOT EXISTS`，
保持标准 SQL，后续切 PostgreSQL / 达梦 / 金仓不用改写法。已执行过的 V1 / V2 不动。

### 29.4 后端结构

```
lx-modules/lx-module-job
├─ controller/  SysJobController、SysJobLogController
├─ service/
│  ├─ SysJobService         CRUD / cron 校验 / 执行器校验 / 重名校验 / 启停 / 手动执行
│  ├─ SysJobLogService      日志分页 / 清空
│  ├─ JobScheduler          独立 ThreadPoolTaskScheduler + CronTrigger，动态注册 / 取消
│  ├─ JobExecutor           抢锁 → 调执行器 → 写日志 → 释放锁
│  ├─ JobLockService        sys_job_lock 抢锁 / 释放 / 删除
│  └─ JobHandlerRegistry    收集容器里的 JobHandler，按名称建索引
├─ handler/     JobLogCleanHandler、DbHealthCheckHandler、PathCheckHandler
├─ entity/      SysJob、SysJobLog
├─ mapper/      SysJobMapper、SysJobLogMapper、SysJobLockMapper
└─ dto/         JobHandlerVO、JobLastRunVO
```

**执行器 SPI 放在 `lx-common-core`**（`com.lingxiao.common.core.job.JobHandler`）：

```java
public interface JobHandler {
    String name();                                   // 写进 sys_job.invoke_target
    String description();                            // 任务表单下拉里显示
    String execute(String params) throws Exception;  // 返回值写 job_message
}
```

原因：系统模块要贡献 `systemLogClean`，但不能反向依赖 job 模块。
SPI 放公共层，依赖方向保持 `modules → common / framework`，job 模块只负责发现与调度。

`invoke_target` 只接受注册表里存在的名称，**不做“类名.方法名”反射调用**——
那等于把任意 Bean 方法暴露给一个后台表单，拼错还要等到凌晨才暴露。

### 29.5 执行链路与集群锁

```
CronTrigger 到点
  → JobExecutor
      1. 读任务快照（任务可能刚被暂停 / 删除）
      2. concurrent=1：本机 AtomicBoolean 挡住同节点重叠执行
      3. 抢集群锁
           UPDATE sys_job_lock
           SET lock_owner=?, lock_time=now
           WHERE job_id=? AND (lock_owner IS NULL OR lock_time < now - timeout)
         抢不到 → 本次跳过（另一个节点在跑）
      4. JobHandler.execute(null)
      5. 成功 / 失败都写 sys_job_log（失败带异常堆栈）
      6. 释放锁（带 owner 条件）
```

几个刻意的选择：

- **一行一任务**：抢锁是单条 `UPDATE ... WHERE`，数据库保证同一行只有一个事务成功，
  不需要 `SELECT ... FOR UPDATE`，也不依赖 Redis（Redis 是可选项）；
- **锁按时间失效**：`lock_time` 超过 `lx.job.lock-timeout-seconds`（默认 600 秒）即可被接管，
  节点宕机不用人工解锁；
- **释放带 owner**：长任务超时被别的节点接管后，旧节点执行完不能把新持有者的锁清掉；
- **写日志失败不影响任务结果**：只打 WARN，不能把“任务已成功”改成“执行失败”。

> 约束：单个任务的执行时间必须小于锁超时，否则会出现两个节点同时跑。
> 真遇到长任务，调大 `lx.job.lock-timeout-seconds`，或把任务拆小。

新增任务默认**暂停**：cron 写错、执行器有副作用时，不会因为一次保存就在凌晨跑起来。
想让它跑必须显式点“启动”，这是有意增加的摩擦。

### 29.6 日志保留：三条入口，一份逻辑

引入 job 模块后，日志清理有两个触发方式，很容易写成两套口径（页面删 180 天、兜底删 365 天）。
本轮拆成：

| 类 | 所在模块 | 作用 |
|---|---|---|
| `LogRetentionService` | system | 唯一的清理逻辑，读 `sys.log.keepDays`，返回结果摘要 |
| `LogRetentionTask` | system | `@Scheduled`，**仅当 job 模块关闭时**注册（`matchIfMissing=true`） |
| `SystemLogCleanJobHandler` | system | `systemLogClean` 执行器，**仅当 job 模块开启时**注册 |

开启 job 时由页面任务驱动（能改 cron、能暂停、能看到每次删了多少条）；
关闭 job 时由 `@Scheduled` 兜底（`lx.log.cleanCron`，默认每天 03:30）。
两条路都调 `cleanNow()`，不会出现两套保留天数。

任务日志自己同样会无限增长（每分钟一次的任务一天 1440 行），
所以新增参数 `sys.job.logKeepDays` 并由 `jobLogClean` 执行器读取；
参数非法或小于 1 时**让任务失败**（页面能看到红色日志），而不是悄悄不清理。

删除任务只删任务定义和锁行，**保留历史日志**：出问题时要能查到“删之前跑过什么”。

### 29.7 模块开关

```yaml
lx:
  module:
    modules:
      job:
        enabled: true
lx:
  job:
    pool-size: 4
    lock-timeout-seconds: 600
    shutdown-wait-seconds: 10
```

- `enabled=true`：Controller / Service / Scheduler / Handler 全部装配；
- `enabled=false`：上述 Bean 不装配，`/api/admin/monitor/job/**` 返回 404，菜单落到前端占位页；
- `@MapperScan` 仍会注册 job 的 Mapper，但没有任何 Bean 调用它，不影响启动。

调度器用**独立线程池**，不与系统模块的 `@Scheduled` 共用：一个长任务不会拖住日志清理。
注册时机放在 `ApplicationReadyEvent` 而不是 `@PostConstruct`，确保 Flyway 已迁移完、任务表可读。

### 29.8 前端任务页

`src/views/tool/job/index.vue` + `src/api/job.js`：

| 区域 | 要点 |
|---|---|
| 列表 | 任务名 + 备注、执行器、cron、状态开关、上次 / 下次执行、操作列 |
| 新增 / 编辑 | 执行器下拉（来自 `/handlers`，不能手填）、cron 输入 + 5 个常用预设、状态 / 并发 / 错失策略、备注 |
| 启停 | 开关直接调独立接口只改 `status`，不提交整张表单，避免覆盖别人刚改的 cron |
| 执行一次 | 二次确认后异步提交，提示“结果去日志里看”，不假装同步成功 |
| 日志抽屉 | 按任务分页，成功 / 失败标签、调度 / 手动标签、耗时列；展开行看失败堆栈；清空需二次确认 |

按钮统一 `v-hasPerm`；启停开关额外按 `platform:monitor:job:changeStatus` 禁用，避免“点了没反应”。

### 29.9 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| `npm run build` | 成功 |
| `.local/verify-job.ps1` | **51/51**：V11 字段 / 锁表 / 参数 / 按钮权限、执行器注册与拒绝未知执行器、5 段 cron 拒绝、创建 → 调度执行 → 日志、暂停后不再执行、手动执行写 MANUAL、集群锁占用跳过 / 过期接管、失败日志含堆栈、删除保留历史、权限拆分（list-only 403）、匿名 401、清空日志、fixture 清理 |
| `.local/verify-job-module.ps1` | **5/5**：8081 关闭态实例 job 接口 404、基座监控仍可用、模块注册表 `job.enabled=false` |
| `.local/uitest/job.mjs` | **9/9**：列表渲染、新建（暂停态）、开关启动 + cron 真实执行、日志抽屉、暂停、手动执行、删除、控制台 0 error |
| `.local/uitest/smoke.mjs` | **22/22**（占位页改用仍未实现的 `/tool/gen`） |
| `.local/uitest/permission.mjs` | **30/30** |
| `.local/uitest/monitor.mjs` | **8/8** |
| `.local/verify-log.ps1` / `verify-config-control.ps1` / `verify-monitor.ps1` | **29/29** / **29/29** / **51/51** |

### 29.10 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| `lx-boot` 报 `dependencies.dependency.version ... is missing` | 新模块只加进了 `lx-modules` 与 `lx-boot`，漏了 BOM | 在 `lx-dependencies` 补 `lx-module-job` 版本声明 |
| `找不到符号 ApplicationReadyEvent` | 导成了 `org.springframework.context.event` | 改为 `org.springframework.boot.context.event` |
| `repackage` 报 `Unable to rename lingxiao.jar` | 后端还在跑，jar 被占用 | 先停 java 进程再打包（老问题，再踩一次） |
| PowerShell 断言恒为 FAIL | `[string]$x.y).Length -ge 10` 被当成多个参数解析 | 用括号包住整个表达式：`(([string]$x.y).Length) -ge 10` |
| 同上，`Contains('keepDays')` 找不到 | PowerShell 的 `.Contains()` 区分大小写，异常里是 `KeepDays` | 断言改用 `'KeepDays'` |
| 冒烟测试“占位页”失败 | `/tool/job` 已实现，用例还在等“还没有实现” | 改用仍未实现的 `/tool/gen`，并断言组件路径 |
| 监控 UI 冷启动偶发“磁盘表格没有数据行” | 文案先渲染、异步采样结果后到 | 断言前 `waitForFunction` 等表格行出现 |

### 29.11 相关文件

| 文件 | 变更 |
|---|---|
| `lx-modules/lx-module-job/**` | 新模块：pom、Controller / Service / Mapper / Entity / DTO / 3 个执行器、V11 迁移、README |
| `lx-common/lx-common-core/.../job/JobHandler.java` | 新增执行器 SPI |
| `lx-modules/lx-module-system/.../service/LogRetentionService.java` | 新增，日志清理共用逻辑 |
| `lx-modules/lx-module-system/.../task/LogRetentionTask.java` | 改为调用 Service，且仅在 job 关闭时注册 |
| `lx-modules/lx-module-system/.../task/SystemLogCleanJobHandler.java` | 新增 `systemLogClean` 执行器 |
| `lx-modules/pom.xml`、`lx-boot/pom.xml`、`lx-dependencies/pom.xml` | 注册新模块 |
| `lx-boot/src/main/resources/application.yml` | 模块开关、Flyway `db/migration/job`、`lx.job.*`、Mapper 日志级别 |
| `lx-ui-admin/src/api/job.js` | 任务与日志接口封装 |
| `lx-ui-admin/src/views/tool/job/index.vue` | 定时任务页面 |
| `.local/verify-job.ps1`、`verify-job-module.ps1`、`uitest/job.mjs` | 新增验证脚本 |
| `.local/uitest/smoke.mjs`、`uitest/monitor.mjs` | 占位页改 `/tool/gen`；磁盘表格等待行出现 |

### 29.12 遗留

| 项 | 说明 |
|---|---|
| 错失补偿 | 停机期间错过的执行不补跑；`misfire_policy` 只是兼容字段 |
| 执行器参数 | `execute(String params)` 预留了参数，v1.0 恒为 `null`，页面也没有传参入口 |
| 任务依赖 / 分片 | 不支持；需要时按“一个执行器内部自行拆分”处理 |
| 长任务 | 执行时间超过锁超时会被接管并重复执行，只能靠调大超时或拆任务规避 |
| 执行历史统计 | 只存明细日志，没有“成功率 / 平均耗时”聚合，日志清空后不可追溯 |
| 调度一致性 | 每个节点各自注册全部任务，靠锁去重；节点数很多时锁竞争会上升（v1.0 规模无压力） |


## 30. 第十三轮：接口文档与个人中心（W5 前两项）

### 30.1 一句话结论

本轮收掉两个占位缺口：

- **接口文档**：SpringDoc 从 Controller 生成 OpenAPI 3，Knife4j 展示 `/doc.html`，不再手写 YAML；
- **个人中心**：自助改昵称 / 邮箱 / 手机号 + 校验原密码改密；改密后该账号**所有终端的会话立即失效**；
- 没有新增数据库迁移，Flyway 仍是 **V11**；没有新增参数，改密策略走代码常量 + DTO 校验。

接口文档页从此不是占位页；个人中心也不再是“只读 + 一句以后再做”。

### 30.2 选型：为什么是 SpringDoc 1.7 + Knife4j 4.4

| 方案 | 结论 |
|---|---|
| Springfox 3 | 与 Boot 2.7 的路径匹配策略冲突，启动期常见 `Failed to start bean 'documentationPluginsBootstrapper'`；已停止维护 |
| 纯 Swagger UI | 能出页面，但中文环境、离线资源、分组体验都不如 Knife4j |
| **SpringDoc 1.7.0 + Knife4j 4.4.0** | 1.7.0 是 SpringDoc 兼容 Boot 2.7 的最后一组 1.x；Knife4j 4.4.0 的 Boot 2 starter 正好配它 |

约束：

- 文档依赖**只放在 `lx-boot`**，`common` / `modules` 不引 swagger 注解，业务代码不被文档框架绑架；
- 以后换 UI 只改 `lx-boot` 一个依赖，Controller 不用动；
- 文档内容 = 当前代码，接口改了什么，文档就变什么，不存在两套契约。

### 30.3 依赖与配置

`lx-dependencies`：

```xml
<springdoc.version>1.7.0</springdoc.version>
<knife4j.version>4.4.0</knife4j.version>

<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-ui</artifactId>
  <version>${springdoc.version}</version>
</dependency>
<dependency>
  <groupId>com.github.xiaoymin</groupId>
  <artifactId>knife4j-openapi3-spring-boot-starter</artifactId>
  <version>${knife4j.version}</version>
</dependency>
```

`lx-boot` 只加 `knife4j-openapi3-spring-boot-starter`（它会把 springdoc 带进来，版本由 BOM 锁死）。

`application.yml`：

```yaml
springdoc:
  api-docs: { enabled: true, path: /v3/api-docs }
  swagger-ui: { enabled: true, path: /swagger-ui.html }
knife4j:
  enable: true
  production: false
lx:
  api-doc:
    enabled: true
    title: 凌霄后台管理接口
```

`OpenApiConfig`（`lx-boot/.../config`）只补标题 / 说明 / 版本，并在 `lx.api-doc.enabled=false` 时不装配。
生产要彻底关掉文档需要三处一起改：

```yaml
lx.api-doc.enabled: false
springdoc.api-docs.enabled: false
knife4j.production: true
```

只改 `lx.api-doc.enabled` 只会少一个 OpenAPI Bean，`/v3/api-docs` 仍会由 SpringDoc 生成——这是有意写清楚，
避免有人以为一个开关就关干净了。

### 30.4 鉴权边界：为什么白名单不用改

`SaTokenConfigure` 的拦截器只注册在 `/api/admin/**`：

```java
registry.addInterceptor(new SaInterceptor(handle -> StpPlatformUtil.checkLogin()))
        .addPathPatterns("/api/admin/**")
        .excludePathPatterns("/api/admin/auth/login", "/api/admin/auth/captcha", "/api/admin/demo/**");
```

而文档入口是：

| 路径 | 用途 | 是否被拦 |
|---|---|---|
| `/doc.html` | Knife4j 页面 | 否（不在 `/api/admin/**`） |
| `/v3/api-docs/**` | OpenAPI 描述 | 否 |
| `/webjars/**` | Knife4j 静态资源 | 否 |
| `/swagger-ui/**` | 原生 Swagger UI | 否 |

所以本轮**没有**往白名单里加无意义的 exclude；只在 `SaTokenConfigure` 的注释里写清这条边界：
以后若把文档挪进 `/api/admin`，必须显式放行，否则 UI 会拿着空 Token 收 401。

> 注意：文档接口本身是公开的。v1.0 默认内网部署；公网环境必须用上面的三处配置关掉，或在 Nginx 层限制来源 IP。

### 30.5 前端文档页

`src/views/tool/apidoc/index.vue`：

| 区域 | 做法 |
|---|---|
| 主体 | `iframe` 内嵌 `/doc.html`，不在 Vue 里重写一遍文档 UI |
| 打开方式 | 「新窗口打开」直接开 `/doc.html`，适合双屏对照 |
| 调试 | 顶部显示 Token 名与**脱敏后的 Token 值**（前 8 位），「复制当前 Token」写入剪贴板，粘到 Knife4j 的「全局参数设置」 |
| 刷新 | 改 `iframe` 的 `:key` 强制重建，解决文档页缓存 |
| 说明 | 明确写出“Token 默认 12 小时过期，过期后重新登录再复制” |

开发期 Vite 只代理了 `/api`，而 `/doc.html` 不在其下，所以 `vite.config.js` 额外补了
`/doc.html`、`/v3/api-docs`、`/webjars`、`/swagger-ui`、`/favicon.ico` 五条代理；
生产 Nginx 同样要转发这几条路径，否则 iframe 打开的是前端 404。

### 30.6 个人中心后端

新增两个 DTO（`lx-admin-api`）：

| DTO | 字段 | 约束 |
|---|---|---|
| `ProfileUpdateRequest` | nickname / email / phone | 昵称非空 ≤64；邮箱格式 ≤128；手机号 ≤32 |
| `PasswordChangeRequest` | oldPassword / newPassword | 原密码非空；新密码 6~64 |

新增两个接口（`AuthController`）：

| 方法 | 路径 | 审计 |
|---|---|---|
| PUT | `/api/admin/auth/profile` | `@Log(title="个人中心", saveRequestData=false)` |
| PUT | `/api/admin/auth/password` | 同上 |

**资料更新的边界**：只改 `nickname / email / phone`。
请求体里就算带 `username`、`status`、`userId`，DTO 没有这些字段，Jackson 直接忽略；
Service 也不把它们拼进 UPDATE，所以自助入口无法提权、无法改登录标识。

**改密的顺序**（`AuthService.changePassword`）：

1. 取当前登录用户；
2. `passwordEncoder.matches(oldPassword, user.password)`，失败返回 **2005 原密码不正确**；
3. 新旧密码相同 / 新密码太短直接拒绝；
4. BCrypt 编码新密码，只更新 `password` 与 `update_by`；
5. `StpPlatformUtil.logoutByUserId(userId)` 注销**账号级** Session，所有端 Token 一起失效。

为什么用 `logoutByUserId` 而不是 `logout()`：后者只注销当前 Token，家里 / 办公室的其它会话还能继续用旧密码。

**审计安全**：

- 两个接口都标 `saveRequestData = false`，`LogAspect` 不序列化请求体；
- `LogAspect` 在 `proceed()` 之前捕获 `username`，改密把会话踢掉不影响这条日志的归属；
- 验证脚本直接查库断言：`oper_param` 为空、`newPass` 在参数 / 异常 / 结果三列里都搜不到。

**Session 刷新**：`updateProfile` 成功后调用 `LoginHelper.setLoginUser(...)` 把新昵称写回 Session，
否则顶部头像区要重新登录才会变。

### 30.7 个人中心前端

- `api/auth.js`：新增 `updateProfile` / `changePassword`；
- `store/auth.js`：
  - state 增加 `email / phone`；
  - 抽出 `applyUserInfo(info)`，登录后加载、刷新、改资料后共用；
  - `updateProfile(form)` 拿后端返回值直接覆盖 store；
  - `reset()` 一并清空 email / phone；
- `views/profile/index.vue`：
  - 描述区展示 用户 ID / 账号 / 昵称 / 邮箱 / 手机号 / 角色；
  - 「编辑资料」弹窗：昵称必填、邮箱格式校验；
  - 「修改密码」弹窗：原密码 / 新密码 / 确认新密码，两次不一致在前端拦截；
  - 改密成功后：`ElMessage.success` → `auth.reset()` 清 Token → `router.replace('/login')`。
    不能只弹“成功”让用户继续用已经失效的会话。

### 30.8 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功 |
| `npm run build` | 成功 |
| `.local/verify-apidoc.ps1` | **26/26**：`/doc.html` 200 且公开、OpenAPI 3.0.1、68 条路径、auth profile/password 为 PUT、swagger-config、webjars 资源 200 |
| `.local/verify-profile.ps1` | **31/31**：info 含 email/phone、资料写入、越权字段忽略、邮箱 / 空昵称校验、错误原密码 2005、同密码 / 短密码 1001、改密后旧 Token 401、旧密码 2001、新密码可登录、审计无请求体 / 无明文、资料与密码复原 |
| `.local/uitest/apidoc.mjs` | **10/10**：页面非占位、iframe 指向 `/doc.html`、Knife4j 真实渲染、标题正确、Token 脱敏、刷新重建 iframe、控制台 0 error |
| `.local/uitest/profile.mjs` | **13/13**：展示邮箱 / 手机号、编辑资料后描述区与顶部昵称同步、非法邮箱阻止提交、原密码错误留在弹窗、改密后跳登录、旧密码失败、新密码成功、控制台 0 error |
| `.local/uitest/smoke.mjs` | **22/22**（个人中心仍正常渲染） |
| `.local/uitest/permission.mjs` | **30/30**（受限账号仍能看到个人中心菜单） |

### 30.9 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| `mvn dependency:get` 报 `Plugin not found` | 命令写法把 `org.springdoc:springdoc-openapi-ui` 当成 plugin 前缀解析 | 直接用 alimaven HTTP 探测 POM：springdoc 1.7.0 / knife4j 4.4.0 均 200 |
| 本地仓库没有 knife4j | `E:\maven\repository` 只有 springdoc 1.5.4 | 首次 `mvn compile` 自动下载，版本由 BOM 锁定 |
| iframe 打开 `/doc.html` 是前端 404 | Vite 只代理 `/api` | 补 `/doc.html`、`/v3/api-docs`、`/webjars`、`/swagger-ui`、`/favicon.ico` 五条代理 |
| 验证脚本用中文 `title` 过滤不稳 | PowerShell 5.1 控制台编码 / SQL 中文字面量容易变形 | 审计断言改用 ASCII 的 `oper_url` 过滤 |
| 改密后当前请求仍返回 0 | Sa-Token 在进入 Controller 前已完成认证，注销发生在方法内部 | 前端成功后必须主动清 Token 跳登录；不能依赖“这次请求失败” |
| 文档页描述区中文断言失败风险 | Knife4j 是异步渲染 | UI 用例先等 `innerText.length > 200` 再断言标题 |

### 30.10 相关文件

| 文件 | 变更 |
|---|---|
| `lx-dependencies/pom.xml` | 新增 springdoc 1.7.0 / knife4j 4.4.0 版本与 BOM 条目 |
| `lx-boot/pom.xml` | 引入 knife4j starter |
| `lx-boot/.../config/OpenApiConfig.java` | 新增，OpenAPI 标题 / 说明 / 版本，按 `lx.api-doc.enabled` 装配 |
| `lx-boot/src/main/resources/application.yml` | `springdoc.*`、`knife4j.*`、`lx.api-doc.*` |
| `lx-common-security/.../SaTokenConfigure.java` | 注释补充文档路径鉴权边界 |
| `lx-common-security/.../StpPlatformUtil.java` | 新增 `logoutByUserId` |
| `lx-api/lx-admin-api/.../dto/ProfileUpdateRequest.java` | 新增 |
| `lx-api/lx-admin-api/.../dto/PasswordChangeRequest.java` | 新增 |
| `lx-api/lx-admin-api/.../dto/UserInfoResponse.java` | 增加 email / phone |
| `lx-module-system/.../controller/AuthController.java` | 新增 PUT profile / password，`saveRequestData=false` |
| `lx-module-system/.../service/AuthService.java` | 资料更新、改密、Session 刷新、会话注销 |
| `lx-ui-admin/src/api/auth.js` | 新增两个接口 |
| `lx-ui-admin/src/store/auth.js` | email / phone、`applyUserInfo`、`updateProfile` |
| `lx-ui-admin/src/views/profile/index.vue` | 去只读，新增两个弹窗 |
| `lx-ui-admin/src/views/tool/apidoc/index.vue` | 新增接口文档页 |
| `lx-ui-admin/vite.config.js` | 文档相关代理 |
| `.local/verify-apidoc.ps1`、`verify-profile.ps1` | 新增后端验证 |
| `.local/uitest/apidoc.mjs`、`profile.mjs` | 新增 UI 验证 |

### 30.11 遗留

| 项 | 说明 |
|---|---|
| 文档鉴权 | `/doc.html` 与 `/v3/api-docs` 公开，v1.0 靠内网 / Nginx 限制；公网部署必须关文档 |
| Token 注入 | Knife4j 没有自动带 Token，需要手动粘贴；后续可用 `knife4j.setting` 或前端生成带 `Authorization` 的链接优化 |
| 中文注解 | 未给 Controller 加 `@Tag` / `@Operation`，文档按方法名与字段名展示；代码生成器落地后再统一补 |
| 文档分组 | v1.0 只有一个默认分组；`/api/app`、`/api/portal` 落地后按 `docs/12-api-codegen.md` 拆 `01-admin` / `02-portal` / `03-app` |
| 头像上传 | 个人中心暂不支持换头像；文件中心已具备上传能力，后续接 `PUT /profile/avatar` |
| 手机号 / 邮箱唯一性 | 目前只校验格式，不校验唯一；如果业务要求“一个邮箱只能绑一个账号”，需加唯一索引与冲突提示 |
| 密码策略 | 只有 6~64 长度与“不得与原密码相同”，没有复杂度 / 历史密码 / 过期策略 |
| 资料变更审计 | 审计只记“改过”，不记字段前后值；需要追溯时再引入变更明细表 |

---

## 31. 第十四轮：代码生成器（W5 收口）

### 31.1 一句话结论

菜单 22「代码生成」从占位页变成**真实可用**的生成器：

- 导入数据库表（JDBC `DatabaseMetaData` 读列 / 主键 / 注释），配表信息、配字段、在线预览 7 个文件、下载 ZIP；
- 生成物 **Entity / Mapper / Service / Controller / api.js / index.vue / menu.sql**，
  Java 4 个类用 `javac -source 1.8` 实测能编译；
- 菜单 SQL 用占位符而不是 `LAST_INSERT_ID()`，替换后事务执行通过；
- 导入时逐表反馈：无主键 / 复合主键 / 重复选择只跳过那一张，不影响其它表；
- 关闭 `lx.module.modules.generator.enabled` 后接口 404、菜单落占位页，基座照常启动。

验证：后端 `.local/verify-gen.ps1` **105/105**、生成器页 UI `.local/uitest/gen.mjs` **9/9**；
回归冒烟 **22/22**、权限 **30/30**；`mvn package`、`npm run build` 均通过，Flyway 到 **V12**。

### 31.2 边界先定死：只预览 + 下载，不写盘

写盘要处理包名白名单、覆盖确认、IDE 热加载、Windows 文件占用、目录不存在的回滚。
v1.0 先把「生成结果正确」做扎实：下载后由开发者决定放进哪个模块。

这样也自然没有路径穿越：模板只渲染字符串，不碰文件系统。
`docs/12-api-codegen.md` 里「生成到指定模块」的能力留到后续版本。

生成器同样只支持**单表、单主键**：

| 表形态 | 处理 |
|---|---|
| 无主键 | 导入时拒绝，跳过原因写进响应 |
| 复合主键 | 导入时拒绝 |
| 主键类型不是 `Long / Integer / String` | 导入时拒绝（Controller 路径参数要用） |
| 主子表 / 树表 | 不生成 |

宁可少生成，也不要生成「看起来能跑、实际更新错行」的代码。

### 31.3 V12 迁移

`V12__generator_schema.sql`（`lx-module-generator/.../db/migration/generator/`）：

| 变更 | 原因 |
|---|---|
| 新建 `gen_table`（唯一索引 `table_name`） | 表级配置：类名 / 模块名 / 业务名 / 功能名 / 作者 / 路由 |
| 新建 `gen_table_column`（`table_id` 普通索引） | 字段级配置：类型、表单 / 列表 / 查询标记、控件、字典、排序 |
| 参数 `sys.gen.author = lingxiao`（text） | 导入时写入 `function_author`，`CodeGenService` 读取 |
| 按钮 2201~2205 | import / edit / remove / preview / download 分开授权 |
| 授超管 | `INSERT ... SELECT ... WHERE NOT EXISTS`，可重复执行 |

两个刻意的选择：

- **不建逻辑删除**：`del_flag` 会让唯一索引 `uk_gen_table_name` 顶住——删掉配置后同名表再也导不进来。
  工具表没有审计追溯需求，直接物理删；
- **不建外键**：删配置时先删子表再删主表，与 `sys_user_role` 等关联表一致，切库不用改写法。

### 31.4 后端结构

```
lx-modules/lx-module-generator
├─ controller/  GenController
├─ service/
│  ├─ GenTableService       导入 / 表配置 / 字段配置 / 删除
│  ├─ DatabaseMetaService   JDBC 元数据读取（表、列、主键、注释）
│  └─ CodeGenService        Velocity 上下文、预览、ZIP、生成前校验
├─ support/
│  ├─ GenConstants          查询方式 / HTML 控件 / Java 类型 / 命名正则
│  ├─ JavaTypeResolver      数据库类型 → Java 类型
│  ├─ NamingUtils           下划线 → PascalCase / camelCase，关键字避让
│  └─ TextUtils             按输出语境清洗（Java / HTML / JS / SQL）
├─ entity/      GenTable、GenTableColumn
├─ mapper/      GenTableMapper、GenTableColumnMapper
└─ dto/         DbTableVO、TableMeta、ImportTableRequest、ImportResultVO、GenPreviewVO
```

依赖方向只到 `lx-common-*`，**不依赖 `lx-module-system`**。
生成物作者通过 `common-core` 的 `ConfigProvider` 读 `sys.gen.author`；
系统模块被裁剪时回退当前登录用户，再回退 `lingxiao`。

### 31.5 元数据为什么用 DatabaseMetaData

达梦、金仓、PostgreSQL 的元数据表名和字段都不一样，写四套 `information_schema` 查询
等于写四个生成器。JDBC 规范把这层差异抹平了，代价是个别库的注释 / 自增信息可能缺失。

所以原则是「读不到就给默认值，绝不让导入失败」：

- 表 / 列注释读不到时为空字符串，生成时按列名兜底；
- 只读当前连接对应的库，不接收 catalog / schema 参数，避免被拿来读别的库；
- `gen_table`、`gen_table_column`、`flyway_schema_history`、`qrtz_locks` 不参与生成。

**MySQL 必须带 `useInformationSchema=true`**：否则驱动走 `SHOW TABLE STATUS`，
`REMARKS`（表注释）读出来是空的。已写进 `lx-boot` 的 JDBC URL。

### 31.6 命名与文本清洗

生成物里所有会进入代码的文本都按语境处理，而不是「统一 replace 一下」：

| 场景 | 处理 | 防的是什么 |
|---|---|---|
| Java 字符串 / javadoc | 去引号、反斜杠，`*/` 变成 `* /` | 注释截断、字符串截断 |
| HTML | 转义 `& < > " '` | 列注释里的标签被当 HTML 执行 |
| JS 单引号字符串 | 去引号 / 反斜杠，`< >` 换成 `‹ ›` | `</script>` 提前结束 Vue SFC 的 script 块 |
| SQL 字面量 | 转义单引号、截断长度 | 菜单 SQL 语法错误 |

命名一律过白名单正则（`GenConstants`）：

| 项 | 规则 |
|---|---|
| 表名 / 列名 | `^[A-Za-z][A-Za-z0-9_]{0,63}$` |
| 类名 | `^[A-Z][A-Za-z0-9]{0,63}$` |
| 模块名 | `^[a-z][a-z0-9]{0,31}$` |
| 业务名 / Java 字段 | `^[a-z][A-Za-z0-9]{0,63}$`，且不能是 Java 关键字 |
| 前端路由 | `^/[A-Za-z0-9_\-/]{1,200}$`，额外拒绝 `..` 与 `//` |

**生成前再校验一次**：导入时校验过，但配置可以被接口直接改、DB 也可能被人工改。
生成是所有模板变量的最后一道出口，这里不校验就等于把风险交给模板。

### 31.7 7 个模板与生成规则

| 文件 | 关键点 |
|---|---|
| Entity | 按真实列生成，**不继承 BaseEntity**（不强行要求 `del_flag` / `version`）；自增主键 `@TableId(value="...", type=IdType.AUTO)` |
| Mapper | `extends BaseMapper<T>` 空接口 |
| Service | 具体类（与 `SysJobService` 同风格），不做 interface + impl 双份；按配置的 `queryType` 生成 `eq / like / gt / ge / lt / le` |
| Controller | `/api/admin/{module}/{business}`、`@RequiresPermission("platform:{module}:{business}:xxx")`、`@Log`、日期参数 `@DateTimeFormat` |
| api.js | 走基座 `request.js`，路径 `/admin{apiPath}`（响应已解包，不再判断 code） |
| index.vue | 查询 + 表格 + 新增 / 编辑弹窗 + 删除；有字典时用 `DictSelect` / `DictTag`；按钮 `v-hasPerm` |
| menu.sql | 菜单 + 3 个按钮 + 角色授权，全部占位符 |

生成物 ZIP 的根目录是表名，例如 `biz_order/src/main/java/...`。

菜单 SQL 占位符：

```
{menuId}  {parentMenuId}  {buttonAddId}  {buttonEditId}  {buttonRemoveId}  {roleId}
```

不用 `LAST_INSERT_ID()`：菜单 ID 由应用侧雪花算法生成，SQL 保持「逻辑语句 + 占位符」，
MySQL / PostgreSQL / 达梦 / 金仓都能执行。

Velocity 用 2.3，`RUNTIME_REFERENCES_STRICT=false`：注释、字典这类可选变量为空时输出空串而不是抛异常。
模板从 classpath 加载，改模板要重新打包（v1.0 不做运行时热加载）。

### 31.8 前端生成器页

`src/views/tool/gen/index.vue` + `src/api/gen.js`：

| 区域 | 要点 |
|---|---|
| 列表 | 表名、注释、实体类、模块 / 业务名、创建时间；按表名 / 注释筛选 |
| 导入弹窗 | 列当前库所有表，已导入置灰；勾选后提交，响应里逐条展示跳过原因 |
| 配置抽屉 · 基本信息 | 类名 / 模块名 / 业务名 / 功能名 / 作者 / 路由，保存前前端也做一次正则提示 |
| 配置抽屉 · 字段配置 | 内联编辑 Java 类型 / 字段名 / 查询方式 / 控件 / 字典 / 表单列表查询标记 / 排序 |
| 预览 | 左侧 7 个文件页签，右侧代码块；内容来自后端真实渲染 |
| 下载 | 走真实 ZIP 接口，浏览器保存为 `{businessName}-code.zip` |
| 删除 | 二次确认，只删配置，不删物理表 |

按钮统一 `v-hasPerm`；`/db/list` 用 `list` 权限（导入弹窗要先能看表），写操作各自独立。

### 31.9 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功（jar 52,183,509 字节） |
| `npm run build` | 成功 |
| `.local/verify-gen.ps1` | **105/105** |
| `.local/uitest/gen.mjs` | **9/9** |
| `.local/uitest/smoke.mjs` | **22/22** |
| `.local/uitest/permission.mjs` | **30/30** |

`verify-gen.ps1` 覆盖：

1. V12 迁移（两张表、参数、5 个按钮、超管授权、菜单 22 指向生成器页）；
2. 注释读取（`useInformationSchema` 生效）、工具表隐藏、关键字过滤；
3. 导入跳过（无主键 / 复合主键 / 重复选择 / 已导入）与逐表反馈；
4. 导入默认值（类 / 模块 / 业务 / 功能名、作者、路由、8 列、类型映射、表单列表查询标记）；
5. 表配置校验（小写类名、大写模块名、`..` 路径）与字段校验（大写字段、重复、关键字、
   非法查询方式、非字符串 LIKE、非法控件、非法字典）；
6. 预览 7 个文件 + 各语境转义（javadoc 不能出现 `*/`、Vue 里不能出现原始 `<b>`）；
7. ZIP 条目与预览路径一致、实体内容逐字节相等、菜单 SQL 替换占位符后事务执行并回滚；
8. 生成 Java 用 `javac -encoding UTF-8 -source 1.8 -target 1.8` 编译，产出 4 个 class；
9. 删除配置后 `gen_table` / `gen_table_column` 为 0，物理表仍在；
10. 只读角色可看列表 / 详情 / 库表，但导入 / 编辑 / 删除 / 预览 / 下载全部 403；
11. 匿名请求 401。

UI 用例覆盖页面渲染、导入、配置抽屉保存、字段配置保存、预览 7 个文件、下载提示、删除、控制台 0 error。

### 31.10 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 表注释全是空 | MySQL 驱动默认走 `SHOW TABLE STATUS` | JDBC URL 加 `useInformationSchema=true` |
| 生成的 Service / Controller 编译失败 | 模板只按需 import，日期查询参数用到 `LocalDate` 时没 import | 模板固定 import `LocalDate / LocalDateTime / LocalTime` |
| 预览的 `api.js` 少了 `/admin` | 前端基座 `request.js` 的 `baseURL` 只有 `/api` | 模板改成 `/admin${apiPath}` |
| Vue 页面被截断 | 列注释里的 `</script>` 提前结束 SFC 脚本块 | `jsLabel` 把 `< >` 换成 `‹ ›` |
| `menu.sql` 断言误报 | 注释里写了 `LAST_INSERT_ID()`，断言按子串判断 | 注释改成“不要依赖数据库自增函数” |
| 中文测试表注释落库变 `??????` | PowerShell 管道向 mysql 传中文时按 ANSI 编码 | 验证脚本的 fixture 全部改纯 ASCII |
| 验证脚本 `Contains("v-hasPerm=\"...\"")` 解析失败 | PowerShell 5.1 的双引号嵌套 | 改用单引号三连写法 `'''platform:...'''` |
| `javac` 找不到 | PATH 里只有 `java.exe`（Oracle javapath） | 脚本按 `JAVA_HOME\bin\javac.exe` 回退 |
| `repackage` 报 `Unable to rename lingxiao.jar` | 后端还在跑，jar 被占用 | 先停 java 进程再打包（老问题） |
| 冒烟“占位页”用例失败 | 菜单 22 已实现，v1.0 不再有缺组件的菜单 | 用例临时插菜单 229950 并授权，结束删除 |

### 31.11 相关文件

| 文件 | 变更 |
|---|---|
| `lx-modules/lx-module-generator/**` | 新模块：pom、Controller / Service / Mapper / Entity / DTO / support、V12 迁移、7 个模板、README |
| `lx-modules/pom.xml`、`lx-boot/pom.xml`、`lx-dependencies/pom.xml` | 注册新模块与 Velocity 版本 |
| `lx-boot/src/main/resources/application.yml` | Flyway `db/migration/generator`、`lx.module.modules.generator.enabled`、`useInformationSchema=true`、Mapper 日志 |
| `lx-ui-admin/src/api/gen.js` | 生成器接口封装 |
| `lx-ui-admin/src/views/tool/gen/index.vue` | 代码生成页面 |
| `lx-ui-admin/src/views/placeholder/index.vue` | 注释改为“菜单先加、组件没跟上”的兜底说明 |
| `.local/verify-gen.ps1`、`.local/uitest/gen.mjs` | 新增验证脚本 |
| `.local/uitest/smoke.mjs` | 占位页改为临时菜单 229950，结束清理 |

### 31.12 遗留

| 项 | 说明 |
|---|---|
| 数据库 | 只在 MySQL 5.7 实测；其它库靠 `DatabaseMetaData` 兜底，注释 / 自增可能缺失 |
| 表形态 | 仅单表、单主键，无主子表 / 树表 |
| 生成能力 | 无 DTO / VO、MapStruct、OpenAPI 注解、i18n 模板 |
| 写入工程 | 只下载 ZIP，需人工放进模块并编译；不改 `lx-boot` 依赖，也不注册前端路由 |
| 模板热加载 | 不支持，改模板要重新打包 |
| 字段元数据 | 列名、主键、自增在导入时固定，更新接口只改展示与生成策略 |
| 导入即配置 | 导入只读表结构，字段注释质量取决于数据库；注释缺失时生成物读起来偏机械 |

---

## 32. 第十五轮：图片管线（W3 门禁收口）

### 32.1 一句话结论

文件中心从「只存原图」补成一条完整图片管线：

- 上传 JPEG / PNG / GIF / BMP 时读宽高、剥离 EXIF、按 Orientation 转正；
- 生成 `thumb`（最长边 160px）与 `medium`（最长边 800px）两档变体；
- 公开接口 `/api/file/{fileId}?size=thumb|medium`，变体缺失自动回退原图；
- 文件网格不再加载原图，详情预览走中图，详情显示真实宽高；
- 任何一步图片处理失败都回退原图并记日志，**不影响上传成功**。

验证：后端 `.local/imgtest/verify-image.mjs` **27/27**、文件中心 UI `.local/uitest/image.mjs` **10/10**；
回归冒烟 **22/22**、权限 **30/30**；`mvn package`、`npm run build` 通过，Flyway 到 **V13**。

### 32.2 边界先定死：只做基础处理

v1.0 不内嵌在线图片编辑器，只解决后台最痛的三个问题：列表慢、EXIF 泄露、手机竖拍横着显示。

| 能力 | v1.0 | 后续 |
|---|---|---|
| 缩略图 / 中图 | ✅ 上传时生成 | 按场景多尺寸 |
| EXIF 剥离 | ✅ JPEG / PNG 重新编码 | 可选保留版权信息 |
| 方向纠正 | ✅ 3 / 6 / 8 | 镜像方向 2 / 4 / 5 / 7 |
| 裁剪 / 水印 | ❌ | 独立图片编辑页 |
| 引用计数回收 | ❌ | 按 `ref_count` 延迟删除 |
| 远程存储 | ❌ 仅本地磁盘 | MinIO / OSS / COS / S3 |

**衍生图不写 `sys_file`**：它们不是独立业务文件，不参与秒传、改名、引用计数，
只把相对路径记在原图行的 `thumb_path` / `medium_path` 上。这样文件库永远只有一条业务记录，
不会出现“删了缩略图把原图引用也删了”的问题。

### 32.3 V13 迁移

`V13__file_image_variants.sql`（`lx-module-file/.../db/migration/file/`）：

```sql
ALTER TABLE sys_file
  ADD COLUMN medium_path varchar(500) DEFAULT NULL
  COMMENT '中图相对路径，最长边约 800px' AFTER thumb_path;
```

V3 已经有 `thumb_path`，这次只补中图。两列都存**相对存储路径**，不存 URL、不存绝对路径：
换域名、换存储实现时不用刷数据。

### 32.4 后端结构

新增 `com.lingxiao.modules.file.image`：

| 类 | 职责 |
|---|---|
| `ImageProcessor` | 读尺寸、解析 EXIF Orientation、剥离元数据、生成 thumb / medium |
| `ProcessedImage` | 一次处理的结果：主图字节、宽高、变体字节与格式 |

处理顺序：

```
读文件头拿宽高（不解码整图）
  → 像素数 > max-pixels：只入库宽高，跳过变体
  → JPEG / PNG 重新编码（剥离 EXIF + 按 Orientation 旋转）
  → 重新读宽高（旋转后宽高可能互换）
  → 生成 thumb / medium（小图不放大、不生成无意义变体）
```

几个关键取舍：

1. **EXIF 方向自己解析**：Thumbnailator 0.4.8 的 `useExifOrientation(true)` 在
   `InputStream` 源上实测不生效，改为自己读 APP1 的 TIFF IFD0（tag `0x0112`）。
   只处理最常见的 3 / 6 / 8，镜像方向保持原样，避免引入翻转矩阵。
2. **GIF / BMP 不重编码**：GIF 重编码会丢动画，BMP 本来没有 EXIF，保持原字节更安全；
   BMP 的变体输出 PNG。
3. **输出格式跟随原图**：JPEG 变体输出 JPG（体积小），PNG / GIF / BMP 输出 PNG（保留透明通道）。
4. **失败不阻断上传**：剥离失败保留原图；变体保存失败只记 warn，`thumb_path` 为空，
   前端按 `thumbUrl || url` 回退。

`FileService.upload` 只改三段：主图改存 `ProcessedImage.mainBytes`、落库宽高与两个路径、
`toVO` 返回 `thumbUrl` / `mediumUrl`（不再把磁盘路径当 URL 返回）。

`PublicFileController` 增加可选 `size` 参数：

```
/api/file/{fileId}                原图，Cache-Control max-age=3600
/api/file/{fileId}?size=thumb     缩略图，max-age=86400
/api/file/{fileId}?size=medium    中图，max-age=86400
```

未知 `size`、变体未生成、老文件都回退原图，不返回 404。
MediaType 按**实际读取的路径**推断，PNG 变体不会因为原图叫 `.png` 但变体是 `.jpg` 而错标。

### 32.5 前端

| 位置 | 变化 |
|---|---|
| `FileLibrary.vue` 网格 | 已用 `file.thumbUrl || file.url`，后端返回真实 URL 后自动生效 |
| `FileLibrary.vue` 详情 | 预览图改用 `mediumUrl`，点击预览仍打开原图；新增“图片变体”状态行 |
| `FileLibrary.vue` 详情尺寸 | 显示入库宽高（已按方向转正） |
| `tool/file/index.vue` | 标识改为“单文件 ≤ 10MB · 图片自动生成缩略图/中图” |

### 32.6 验证结果

| 项 | 结果 |
|---|---|
| `mvn -q -DskipTests package` | 成功（jar 52,303,673 字节） |
| `npm run build` | 成功 |
| `.local/imgtest/verify-image.mjs` | **27/27** |
| `.local/uitest/image.mjs` | **10/10** |
| `.local/uitest/smoke.mjs` | **22/22** |
| `.local/uitest/permission.mjs` | **30/30** |
| Flyway | V13 成功，`medium_path` 存在 |

`verify-image.mjs` 用真实文件覆盖：

1. 1600×1000 JPEG 手工注入 EXIF Orientation=6，上传后宽高变 1000×1600；
2. 存储原图里不再包含 `Exif` 字节，且与原上传字节不同（证明重新编码）；
3. thumb 最长边 160、medium 最长边 800，匿名可访问且带缓存头；
4. PNG 变体仍为 `image/png`，尺寸 160×107；
5. 100×80 小图不生成变体；
6. TXT 不生成宽高 / 变体，`?size=thumb` 回退原图；
7. 升级前的老图片没有变体，`?size=thumb` 返回 200 而不是 404。

UI 用例覆盖：网格 `<img>` 指向 `?size=thumb` 且 naturalWidth/Height 已加载、
详情预览指向 `?size=medium`、详情显示 `1000 × 1600`、控制台 0 error。

### 32.7 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| EXIF 方向没生效，宽高仍是 1600×1000 | Thumbnailator 0.4.8 对 `InputStream` 不读 EXIF | 自己解析 APP1 / TIFF，再用 `.rotate()` |
| 测试里新文件全部 404 | 测试脚本把 `/api` 拼了两遍 | `get()` 对 `/api/` 开头直接用站点根 |
| 上传的 MIME 是 `application/octet-stream` | Node `Blob` 没带 type；浏览器上传会带 | 管线按扩展名判断，不依赖客户端 MIME |
| 重传同一张图拿不到新变体 | 秒传按上传字节的 hash 命中老行 | 测试先删旧行；老数据回填列入遗留 |
| 原图重编码后变大 | JPEG 质量 0.95 + 图形边缘 | 降到 0.90，仍以“去元数据”为优先 |

### 32.8 相关文件

| 文件 | 变更 |
|---|---|
| `lx-dependencies/pom.xml` | Thumbnailator 0.4.8 版本 |
| `lx-modules/lx-module-file/pom.xml` | 引入 Thumbnailator |
| `lx-modules/lx-module-file/.../image/ImageProcessor.java` | 新增：尺寸 / EXIF / 变体 |
| `lx-modules/lx-module-file/.../image/ProcessedImage.java` | 新增：处理结果 |
| `lx-modules/lx-module-file/.../service/FileService.java` | 主图替换、变体落盘、宽高 / 路径入库、URL 回填 |
| `lx-modules/lx-module-file/.../controller/PublicFileController.java` | `size` 参数、缓存头、MediaType 按实际路径 |
| `lx-modules/lx-module-file/.../entity/SysFile.java` | `mediumPath` |
| `lx-modules/lx-module-file/.../dto/FileVO.java` | `mediumUrl` |
| `lx-modules/lx-module-file/.../config/FileProperties.java` | `thumb-size` / `medium-size` / `max-pixels` |
| `lx-modules/lx-module-file/.../db/migration/file/V13__file_image_variants.sql` | 新增中图列 |
| `lx-boot/src/main/resources/application.yml` | 图片参数 |
| `lx-ui-admin/src/components/FileLibrary.vue` | 中图预览、变体状态 |
| `lx-ui-admin/src/views/tool/file/index.vue` | 页面说明 |
| `.local/imgtest/make-images.ps1`、`verify-image.mjs` | 新增后端验证 |
| `.local/uitest/image.mjs` | 新增 UI 验证 |

### 32.9 遗留

| 项 | 说明 |
|---|---|
| 历史图片 | 升级前已存在的图片没有变体；重新上传命中秒传也不会补生成，需要人工回填或后续加“重建变体”按钮 |
| 格式覆盖 | WebP 在 JDK 8 无内置解码器，保持原样入库；如需变体要加 `webp-imageio` |
| 方向覆盖 | 只处理 3 / 6 / 8；镜像方向 2 / 4 / 5 / 7 仍按原方向展示 |
| MIME 校验 | 仍以扩展名白名单为准，没有做“文件头 vs 扩展名”一致性校验（安全加固轮补） |
| 裁剪 / 水印 | 不在 v1.0，图片编辑页与引用计数回收一起留到后续 |
| 变体清理 | 删除原图时没有级联删除 `_thumb` / `_medium` 文件（当前没有删除接口） |

---

## 33. 第十六轮：部署交付（发布包闭环）

### 33.1 一句话结论

从「源码能跑」推进到「发布包能直接上服务器」：

- `deploy/` 汇齐生产配置、建库脚本、Linux / Windows 启停脚本、systemd 单元、Nginx 示例与部署手册；
- `deploy/package.ps1` / `package.sh` 只复制构建产物，组装出 `release/lingxiao-1.0.0/` 与压缩包；
- 生产 profile 关闭代码生成器、OpenAPI / Knife4j、SQL 日志，日志按 50MB 滚动；
- 发布包在 **8082** 以 `prod` profile 真实启停通过，`/doc.html`、`/v3/api-docs`、
  `/api/admin/tool/gen/list` 全部 404，登录与文件列表正常，登录日志落库。

### 33.2 发布包结构

```
lingxiao-1.0.0/
├─ lingxiao.jar
├─ config/application-prod.yml
├─ web/                     # 前端 dist
├─ bin/                     # start / stop / restart / status（sh + ps1）
├─ lingxiao.service         # systemd 单元
├─ nginx/lingxiao.conf
├─ sql/init-db.sql
├─ data/upload/  logs/  run/
```

打包脚本 **不执行** `mvn` / `npm`，只复制已有产物：
构建失败或忘记 `npm run build` 时脚本直接报错，而不是把半成品打进发布包。

### 33.3 生产 profile 关掉了什么

| 项 | 配置 | 为什么 |
|---|---|---|
| 代码生成器 | `lx.module.modules.generator.enabled=false` | 整库表结构与生成代码不该暴露给线上账号 |
| OpenAPI | `springdoc.api-docs.enabled=false` | `/v3/api-docs` 直接 404 |
| Knife4j | `knife4j.enable=false`、`production=true` | `doc.html` 不再可用 |
| 文档静态资源 | `ApiDocDisabledFilter` | 见 33.4 |
| SQL 日志 | `NoLoggingImpl` | 避免日志膨胀与参数泄露 |
| 验证码 | `lx.captcha.enabled=true` | 防暴力破解；运行期仍受 `sys.account.captchaEnabled` 控制 |
| 日志 | `logs/lingxiao.log`，50MB × 30 份，总量 2GB | 单机排障足够，不会打满磁盘 |

### 33.4 为什么还要一个 ApiDocDisabledFilter

只关 SpringDoc 时出现一个反直觉现象：

```
/v3/api-docs  -> 404
/doc.html     -> 200   ← Knife4j 的静态页面仍在
```

`doc.html`、`/webjars/**` 是 Knife4j 打进 jar 的静态资源，不归 SpringDoc 管。
页面打开后虽然拿不到数据，但对生产来说仍是多余的信息暴露面。

处理：`lx.api-doc.enabled=false` 时注册一个 `OncePerRequestFilter`，
把 `/doc.html`、`/v3/api-docs/**`、`/swagger-ui/**`、`/webjars/**` 统一回 404。
放在 `lx-boot` 而不是 common：这是启动模块的部署策略，业务与安全模块不需要知道文档开关。

### 33.5 脚本设计

Linux（`bin/*.sh`，systemd 为主，pid 文件为辅）：

- `start.sh` 工作目录固定在应用根目录，`nohup` 启动并把控制台输出写 `logs/console.log`；
- `stop.sh` 先 `kill` 再等 30 秒，超时才 `kill -9`，给 Spring 优雅停机的机会；
- `status.sh` 用退出码区分 running / dead / stopped，便于脚本编排；
- `lingxiao.service` 设置了 `SuccessExitStatus=143`（SIGTERM 正常退出）与 `Restart=on-failure`。

Windows（`bin/*.ps1`，纯 ASCII）：

- `start.ps1` 优先用 `%JAVA_HOME%\bin\java.exe`，pid 写 `run\lingxiao.pid`，3 秒后检查进程是否还活着；
- `stop.ps1` 同样先软停、30 秒后强杀；
- 需要开机自启时用 NSSM / WinSW 包一层，脚本本身不做服务注册。

### 33.6 Nginx 关键点

- `try_files $uri $uri/ /index.html`：Vue Router history 模式刷新深链不 404；
- `location /api/` 反代到 `127.0.0.1:8080`，保留 `/api` 前缀与 `X-Forwarded-*`；
- `client_max_body_size 20m` 对齐后端 `max-request-size`；
- 带 hash 的静态资源 `immutable` 缓存 30 天，`index.html` 不缓存；
- 安全头：`nosniff`、`SAMEORIGIN`、`Referrer-Policy`、`Permissions-Policy`。

生产只开放 80/443，8080 不暴露公网；文档反代块只留作排障，正式上线可删。

### 33.7 实测结果（8082 / prod）

| 检查 | 结果 |
|---|---|
| `bin/start.ps1` 启动 | 成功，pid 写入 `run/lingxiao.pid` |
| Flyway | `Schema lingxiao is up to date`（V13） |
| `GET /doc.html` | **404** |
| `GET /v3/api-docs` | **404** |
| `GET /webjars/...` | **404** |
| `GET /api/admin/tool/gen/list`（带 Token） | **404** |
| `POST /api/admin/auth/login` | `code=0` |
| `GET /api/admin/file/list` | `code=0` |
| `sys_login_log` | 新增 `admin / 登录成功` 记录 |
| `logs/lingxiao.log` | 正常写入 |
| `bin/stop.ps1` + `status.ps1` | 正常停止，pid 文件清理 |

### 33.8 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 生产 `/doc.html`、`/v3/api-docs` 仍 200 | `springdoc` 被缩进到 `spring:` 下，整段被忽略 | 改成根级 `springdoc.*` |
| 改完配置 `/v3/api-docs` 404 了，`/doc.html` 还是 200 | Knife4j 页面是静态资源，不受 SpringDoc 开关控制 | 增加 `ApiDocDisabledFilter` |
| `package.ps1` 里 `lingxiao.service` 拷贝路径写错 | 目标写成 `nginx\..\lingxiao.service` | 直接复制到应用根目录 |
| `mvn` 没重跑就打包，发布包里是旧 jar | 打包脚本只复制产物 | 打包前先 `mvn -DskipTests package`；脚本校验 jar 存在 |

### 33.9 相关文件

| 文件 | 说明 |
|---|---|
| `deploy/config/application-prod.yml` | 生产配置（环境变量注入敏感项） |
| `deploy/sql/init-db.sql` | 建库 / 建账号 / 授权 |
| `deploy/linux/start.sh` / `stop.sh` / `restart.sh` / `status.sh` | Linux 启停 |
| `deploy/linux/lingxiao.service` | systemd 单元 |
| `deploy/windows/*.ps1` | Windows 启停（纯 ASCII） |
| `deploy/nginx/lingxiao.conf` | Nginx 站点 + HTTPS 示例 |
| `deploy/package.ps1` / `package.sh` | 发布包组装 |
| `deploy/README.md` | 部署手册（环境变量、上线清单、备份、FAQ） |
| `lx-boot/src/main/java/com/lingxiao/boot/config/ApiDocDisabledFilter.java` | 生产关闭文档入口 |
| `lx-boot/src/main/resources/application.yml` | `springdoc` 改根级 |
| `release/lingxiao-1.0.0/`、`release/lingxiao-1.0.0.zip` | 当前发布包 |

### 33.10 遗留

| 项 | 说明 |
|---|---|
| HTTPS | 手册给了 certbot 结构与 443 示例，但没有真实证书；上线前必须补 |
| 升级脚本 | 目前是手工替换 jar / web；后续可加 `upgrade.sh` 做备份 + 替换 + 健康检查 |
| 多实例 | 会话在内存时只能单实例；多实例需开 Redis，并验证定时任务集群锁 |
| Windows 服务 | 未内置服务注册，依赖 NSSM / WinSW |
| 健康检查 | 未接 actuator；Nginx / 负载均衡暂时只能探 `/api/admin/auth/captcha` |
| 容器化 | v1.0 不做 Docker，后续按需补 Dockerfile 与 compose |

---

## 34. 第十七轮：登录失败锁定与密码策略

### 34.1 一句话结论

把「登录」从「只验密码」补成一套可运维的账号安全：

- 连续失败达到阈值后锁定账号，到期自动解锁；管理员可以手动解锁；
- 失败次数与锁定时间**落库**（`sys_user.login_fail_count` / `lock_time`），不依赖缓存，重启与后续多实例读同一份状态；
- 密码复杂度参数化（`off / basic / strong`），新增用户、管理员重置密码、个人中心改密三处统一校验；
- 参数阈值、锁定时长改动**即时生效**，不需要重启。

### 34.2 为什么不用缓存计数

| 方案 | 问题 |
|---|---|
| Caffeine（默认单机缓存） | 重启即清零，攻击者只要撞上重启窗口就能继续试错 |
| Redis | v1.0 里是可选依赖，单机不装时登录也要能工作 |
| **数据库两列** | 单机、重启、多实例都读同一份；`UPDATE` 幂等，不需要分布式锁 |

代价是每次登录多一次 `UPDATE`，相对 BCrypt 的耗时可以忽略。

### 34.3 数据与迁移（V14）

`V14__login_security.sql`：

```sql
ALTER TABLE sys_user
  ADD COLUMN login_fail_count int NOT NULL DEFAULT 0 AFTER login_time,
  ADD COLUMN lock_time datetime DEFAULT NULL AFTER login_fail_count;
```

| 参数 key | 默认 | 控件 | 读取方 |
|---|---|---|---|
| `sys.account.maxRetryCount` | 5（3~10） | number | `AuthService` |
| `sys.account.lockMinutes` | 10（1~1440） | number | `AuthService` |
| `sys.account.passwordPolicy` | basic | select | `PasswordPolicy` |

新增按钮权限 `platform:system:user:unlock`（menu_id=105，父菜单 5），并授权给超级管理员角色。

### 34.4 登录流程

```
验证码
  → 查用户
  → lock_time 未到期？直接 2004（不比对密码，避免锁定期继续撞库）
  → 密码错误？失败次数 +1；达到阈值则写 lock_time、次数清零、返回 2004
  → 密码正确但账号停用 → 2002
  → 成功：写登录 IP / 时间，并清零失败次数与 lock_time
  → 发 Token、写登录日志
```

几个刻意的取舍：

1. **锁定期不比对密码**：比对只会给攻击者额外信息（“密码对了但被锁”），也浪费 BCrypt 算力；
2. **锁定到期从 0 重新计数**：避免“上次锁定前剩 1 次”被继承，导致刚解锁又立刻锁；
3. **未知用户名不计数**：没有用户行就没有锁定状态，避免给用户名枚举制造副作用；
4. **参数越界兜底**：数据库里被手工改成 0 / 9999 时，代码侧夹到 1~100 次、1~1440 分钟。

### 34.5 密码策略

| 取值 | 规则 |
|---|---|
| `off` | 不校验复杂度（长度仍受 DTO 的 6~64 约束） |
| `basic` | ≥ 8 位，同时含英文字母与数字（默认） |
| `strong` | ≥ 10 位，同时含大写、小写、数字、符号 |

- **只在“设置新密码”时校验**：登录不校验，否则策略一改，所有老密码用户都被挡在门外；
- 参数值非法（手工改库、导入旧数据）回退 `basic` 并记 warn，不让一个参数写错导致所有改密请求 500；
- 前端只做体验层提示（8~64 位），真正的规则以后端为准，参数改成 `strong` 时由后端返回具体原因。

### 34.6 接口与权限

| 接口 | 权限 | 说明 |
|---|---|---|
| `PUT /api/admin/system/user/{userId}/unlock` | `platform:system:user:unlock` | 清零失败次数与锁定时间，未锁定时调用也幂等 |
| `PUT /api/admin/system/user/{userId}/password` | `platform:system:user:resetPwd` | 重置密码同时清零失败次数与锁定（重置通常就是为被锁用户恢复） |

登录日志会记录“账号锁定中，剩余 N 分钟”“密码错误次数过多，账号锁定 N 分钟”，
操作日志记录解锁与重置密码；日志切面继续把 `password` 字段替换成 `***`。

### 34.7 前端

- 用户列表新增「锁定」列：锁定期内显示红色「已锁定」，否则 `-`；
- 操作列在锁定时出现「解锁」按钮（受 `platform:system:user:unlock` 控制），确认后调接口并刷新列表；
- 新增用户 / 重置密码 / 个人中心改密的密码提示统一为「至少 8 位，含字母和数字」，
  并注明实际规则以「参数设置 → 密码复杂度策略」为准；
- 判断是否锁定用 `lock_time` 与当前时间比较，而不是看 `login_fail_count`。

### 34.8 验证

| 用例 | 结果 |
|---|---|
| `.local/verify-login-security.ps1` | **59/59** |
| `.local/uitest/login-security.mjs` | **8/8** |
| 回归 `smoke.mjs` | 22/22 |
| 回归 `permission.mjs` | 30/30 |
| 回归 `verify-image.mjs` | 27/27 |
| 全新库跑满 V1~V14 | 14 条迁移全部成功，登录 `code=0` |

后端用例覆盖：阈值 5 次锁定、锁定期正确密码仍 2004、到期自动解锁、管理员解锁（幂等）、
重置密码清除锁定、阈值改 3 后第 3 次锁定、阈值/时长越界拒绝（1001）、
策略 off/basic/strong（创建与自助改密）、未知用户名不锁定、
登录日志与操作日志落库、密码不出现在操作日志、匿名 401、非管理员 1003。

### 34.9 本轮踩坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 启动报 `Duplicate entry '5' for key 'PRIMARY'` | `sys_config.config_id` 不是自增列，V14 写死的 5/6/7 与 V11/V12 冲突 | 改成 7/8/9，并在迁移注释里记下各版本占用区间 |
| 迁移失败后库停在半截 | MySQL DDL 不在 Flyway 事务里，`ALTER TABLE` 已执行 | 删掉 `flyway_schema_history` 里失败记录、手工回退两列后重跑；同时在**全新库**上验证一遍完整迁移 |
| 脚本报 `maxRetryCount 更新 401` | 验证脚本的 6 处 `Call ... $adminToken` 漏传了 Token | 补上参数；这类“缺参”不会报语法错，只能看服务端 `未提供 token` |

### 34.10 相关文件

| 文件 | 说明 |
|---|---|
| `lx-modules/lx-module-system/.../db/migration/system/V14__login_security.sql` | 锁字段、3 个参数、解锁权限 |
| `lx-modules/lx-module-system/.../service/PasswordPolicy.java` | 密码复杂度策略 |
| `lx-modules/lx-module-system/.../service/AuthService.java` | 锁定判断 / 失败计数 / 成功清零 / 自助改密校验 |
| `lx-modules/lx-module-system/.../service/SysUserService.java` | 新增用户校验、重置密码、`unlock` |
| `lx-modules/lx-module-system/.../controller/SysUserController.java` | 解锁接口 |
| `lx-modules/lx-module-system/.../entity/SysUser.java` | `loginFailCount` / `lockTime` |
| `lx-ui-admin/src/views/system/user/index.vue` | 锁定列、解锁按钮、密码提示 |
| `lx-ui-admin/src/views/profile/index.vue` | 改密提示对齐策略 |
| `lx-ui-admin/src/api/user.js` | `unlockUser` |
| `lx-common/lx-common-security/.../SaTokenConfigure.java` | 拦截器只校验真实 Controller 方法（修 500 → 404） |
| `.local/verify-login-security.ps1` | 后端 59 项验证 |
| `.local/verify-prod-release.ps1` | 发布包 prod 抽测 20 项 |
| `.local/uitest/login-security.mjs` | 用户页 / 改密 UI 8 项 |

### 34.11 遗留

| 项 | 说明 |
|---|---|
| 解锁通知 | 解锁后只写操作日志，没有站内信 / 邮件通知被解锁用户 |
| 密码历史 | 不记录历史密码，无法禁止复用最近 N 次密码 |
| 强制改密 | 没有“首次登录 / 重置后必须改密”的标记 |
| IP 维度限流 | 只按账号锁定；同一 IP 撞多个账号仍靠网关 / WAF 限流 |
| 验证码策略 | 验证码只有全局开关，没有“连续失败后才要求验证码”的降级策略 |

### 34.12 发布包抽测（prod profile）

重新打包 `release/lingxiao-1.0.0` 后以 `prod` 启动 8082，新增
`.local/verify-prod-release.ps1` 做验收，**20/20 通过**：

- Flyway 停在 V14、无失败记录；`sys_user.lock_time` 存在；
- 3 个参数默认 5 / 10 / basic；解锁权限已授权给角色 1；
- 管理员登录、`/auth/info` 正常；
- 关闭的代码生成器接口 `/api/admin/tool/gen/list`、`/api/admin/tool/gen/db/list` 返回 404；
- `/doc.html`、`/v3/api-docs` 返回 404；
- 真实锁定链路：创建用户 → 连续 5 次失败锁定 → 正确密码仍 2004 →
  `PUT /system/user/{id}/unlock` 解锁 → 重新登录成功；登录日志与计数落库。

抽测中发现并修掉一个生产行为缺陷：**匿名访问“模块已关闭”的接口返回 500 而不是 404**。
根因是 `SaTokenConfigure` 给 `/api/admin/**` 挂了 `SaInterceptor`，
模块关闭后路径没有 Controller，DispatcherServlet 交进来的是
`ResourceHttpRequestHandler` / `BasicErrorController`，此时抛出的 `NotLoginException`
没有 `@ExceptionHandler` 承接，最后变成 500 错误页。
修法是拦截器里先判断 `handler instanceof HandlerMethod`，只对真实 Controller 方法做登录校验；
没有 Controller 的请求直接放行，交给 Spring 走正常 404。
真实接口的匿名访问仍是 401（回归 `verify-login-security`、`smoke`、`permission` 均通过）。



---

## 35. 第十八轮：主题切换 + 依赖台账 + 多语言 / 接口文档方案

### 35.1 背景

本轮不是加业务 CRUD，而是回答四个“基座成熟度”问题：

1. 后台要做多语言，怎么落地？
2. 后台要加主题切换（暗色 / 品牌色 / 侧栏风格），怎么做？
3. 项目到底用了哪些“轮子”，全部记录下来。
4. 现在接口文档是这些，以后写新功能怎么更新？

处理方式：**主题直接实现并验收；多语言、依赖、接口文档先出可执行方案/台账**。
原则仍然是“v1.0 不引新运行时依赖、不动数据库”。

### 35.2 主题切换（已实现）

范围只在 `lx-ui-admin`，后端零改动、无 Flyway 迁移。

实现结构：

| 层 | 文件 | 职责 |
|---|---|---|
| 工具 | `src/utils/theme.js` | 主题归一化、颜色混合、写 `<html>` class / CSS 变量、监听系统明暗 |
| 状态 | `src/store/app.js` | `theme` 状态、`localStorage` 持久化、`initTheme` / `setTheme` / `toggleDark` / `resetTheme` |
| 交互 | `src/components/ThemeDrawer.vue` | 外观模式、6 个主题色 + 取色器、侧栏深浅、恢复默认 |
| 入口 | `src/layout/Navbar.vue` | 顶栏月亮/太阳一键切换 + 齿轮打开抽屉 |
| 挂载 | `src/layout/index.vue` | 仅在后台布局挂 `ThemeDrawer` |
| 样式 | `src/assets/styles/index.css` | 亮/暗两套 `--lx-*`；侧栏浅色覆盖；菜单浮层补色 |
| 入口 | `src/main.js` | 引入 `element-plus/theme-chalk/dark/css-vars.css`，挂载前 `initTheme()` |
| 侧栏 | `src/layout/Sidebar.vue` | 去掉硬编码 `background-color/text-color`，改 Element Plus 菜单变量 |

能力：

- 亮色 / 暗色 / 跟随系统（`prefers-color-scheme` 实时响应）；
- 主色同时写 `--lx-primary*` 与 `--el-color-primary-light-3/5/7/8/9` / `dark-2`；
- 侧栏深色 / 浅色；浅色侧栏在暗色主题下自动用深灰，不会突然变白；
- 偏好存 `localStorage['lx-admin-theme']`，脏数据自动回退默认；
- 登录页也读取主题，避免“后台是暗色、退出后闪白”；
- 顺手把 30 处硬编码颜色收口到变量（导航、卡片、表格空态、日志块、编辑器外壳、错误页等）。

### 35.3 多语言方案（文档）

新增 `docs/23-i18n-plan.md`，基于既有 `docs/15-i18n.md` 做“落地裁剪”：

- 先做 L1 后台界面双语，不建表：后端 `MessageSource` + `Accept-Language`，
  错误码增加 `messageKey`，校验消息 key 化；前端 `vue-i18n@9` + `ElConfigProvider`；
- 菜单 / 字典 / 参数直接用现有稳定键（`menu_key` / `dict_type + dict_value` / `config_key`），
  **不新增数据库列**；
- L2 才启用 `i18n_language / i18n_key / i18n_value`，加载顺序“数据库 > 语言包 > 原始字段”；
- 明确不翻译操作日志 / SQL / 异常堆栈；不做运行时机器翻译；
- 估算 4~5 人天，拆成“后端链路 + 登录/布局/首页”和“18 个业务页”两轮。

本轮只写方案，不改代码：v1.0 保持中文单语，避免发布前引入大范围文案改动。

### 35.4 依赖台账（文档）

新增 `docs/22-dependencies.md`，口径是**实际构建产物**：

- 解包 `lx-boot/target/lingxiao.jar` 的 `BOOT-INF/lib/*.jar`，实际 **102 个运行时 jar**
  （此前口头说的 103 把 `BOOT-INF/lib/` 目录条目也算进去了；本轮已纠正）；
- 按“自研 / Spring / Web 校验 / JSON / 持久层 / 认证 / Redis 缓存 / 接口文档 / 业务工具 / 日志 / 编译注解”分组，
  每个 jar 记录版本与用途；
- 前端记录 12 个直接依赖（10 运行时 + 2 开发）的声明范围与实际锁定版本，
  以及 dayjs / lodash-es / @vueuse/core / esbuild / rollup 等关键传递依赖；
- 记录“刻意不引”的轮子：Quartz、Spring Security 全家桶、Redisson、vue-i18n（当前）、ECharts；
- 给出升级手册：后端只改 `lx-dependencies/pom.xml`，前端改 `package.json` 后更新 lock，
  两边都要跑回归并同步本文档。

另外写了 `.local/check-deps-doc.mjs` 做自检：把 jar 名与文档里的 artifactId 对齐，
本轮结果 `jars=102 missing=0`，防止以后加了依赖忘记录。

### 35.5 接口文档规范（文档）

新增 `docs/25-api-doc.md`，先核实事实：

- 当前 `/v3/api-docs` 是 **76 个 path**（此前 README 写 68，已按实测更新）；
- 文档由 SpringDoc 扫 Controller 自动生成，**v1.0 没有 `@Tag/@Operation/@Schema`**，
  所以分组是类名、操作是方法名，但路径/参数/响应/调试都准确；
- 写清“新增接口 10 项检查清单”：路径归属、权限码、`@Log`、`@Valid`、`R<T>`、
  前端 api.js、菜单 SQL、文档核对、权限回归、施工记录；
- 给出可选增强：`swagger-annotations` 以 `provided` 引入 + `@Tag/@Operation/@Schema` 约定；
  生成器 `controller.java.vm` 要同步加，否则生成模块和手写模块不一致；
- 发版核对：`.local/verify-apidoc.ps1` + path 数写进发布记录；
- 生产关闭：`lx.api-doc.enabled=false` 时 `/doc.html`、`/v3/api-docs/**`、`/swagger-ui/**`、`/webjars/**` 均 404。

### 35.6 验证

| 项 | 结果 |
|---|---|
| `npm run build` | 成功（5.55s） |
| `.local/uitest/theme.mjs` | **12/12** |
| 回归 `.local/uitest/smoke.mjs` | **22/22** |
| `.local/verify-apidoc.ps1` | **26/26**，76 paths |
| 后端编译 | 未改后端，沿用现有 jar |

主题用例覆盖：默认亮色、一键暗色、刷新保持、抽屉、主色同步（`--lx-primary` 与
`--el-color-primary` 都为 `#0e9f6e`）、浅色侧栏（选中白字 / 普通 `#6b7891`）、
跟随系统（模拟暗→亮）、恢复默认清 localStorage、暗色下导航/卡片/表格/输入框无白块、控制台 0 error。

### 35.7 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 暗色下侧栏正常，但折叠子菜单浮层还是白的 | `el-menu` 浮层 teleport 到 `body`，拿不到 `.sidebar` 作用域 | 在全局 `index.css` 补 `.el-menu--popup` 的 `--el-menu-*` 变量 |
| 浅色侧栏选中项“绿底绿字”看不见 | 选中文字用了 `--lx-sidebar-text-active`，浅色模式下它等于主色 | 拆出 `--lx-sidebar-active-text: #fff`，选中态永远白字 |
| 只引 `element-plus/dist/index.css`，暗色背景变但组件不变 | Element Plus 暗色变量在单独的 `dark/css-vars.css` | `main.js` 显式引入，且放在 index.css 之前 |
| 刷新时先闪一下亮色 | 主题在 `app.mount` 之后才应用 | `useAppStore(pinia).initTheme()` 放到 `mount` 之前 |
| 依赖数一会儿 103 一会儿 102 | `BOOT-INF/lib/` 目录条目也被 `-like 'BOOT-INF/lib/*'` 匹配 | 统计时用 `*.jar`；文档与 README 统一为 102 |

### 35.8 文件清单

| 文件 | 说明 |
|---|---|
| `lx-ui-admin/src/utils/theme.js` | 新增：主题工具 |
| `lx-ui-admin/src/store/app.js` | 扩展：主题状态与动作 |
| `lx-ui-admin/src/components/ThemeDrawer.vue` | 新增：主题抽屉 |
| `lx-ui-admin/src/layout/Navbar.vue` | 主题快捷按钮 / 抽屉入口 / 颜色变量 |
| `lx-ui-admin/src/layout/index.vue` | 挂载 `ThemeDrawer` |
| `lx-ui-admin/src/layout/Sidebar.vue` | 菜单颜色变量化 + 浅色侧栏 |
| `lx-ui-admin/src/main.js` | 暗色样式表 + 初始化主题 |
| `lx-ui-admin/src/assets/styles/index.css` | 亮/暗变量、填充色变量、菜单浮层 |
| `lx-ui-admin/src/views/login/index.vue` | 登录页跟随主题 |
| `lx-ui-admin/src/components/RichEditor.vue` / `FileLibrary.vue` | 硬编码色改变量 |
| `lx-ui-admin/src/views/dashboard/index.vue` 等 10 个页面 | 卡片 / 日志块 / 面板色改变量 |
| `docs/22-dependencies.md` | 新增：依赖台账 |
| `docs/23-i18n-plan.md` | 新增：多语言落地方案 |
| `docs/24-theme-switch.md` | 新增：主题实现说明 |
| `docs/25-api-doc.md` | 新增：接口文档维护规范 |
| `.local/uitest/theme.mjs` | 新增：主题 UI 12 项 |
| `.local/check-deps-doc.mjs` | 新增：依赖文档与 jar 对账 |
| `README.md` / `lx-ui-admin/README.md` | 更新：主题、依赖、文档索引、路径数 76 |

### 35.9 后续

- 主题：如客户需要“换设备同步”，再加 `sys_user.theme_config` + 个人中心接口；
- 多语言：按 `docs/23-i18n-plan.md` 的 L1 排期，先做后端链路；
- 接口文档：如要求中文接口名，按 `docs/25-api-doc.md` 第 4 节加注解，并同步生成器模板；
- 依赖：每次升级先跑 `.local/check-deps-doc.mjs`，再更新台账。

---

## 36. 第十九轮：JUnit 单测 + 最小 CI

### 36.1 背景

v1.0 之前所有验证都靠 `.local/verify-*.ps1` 与 Puppeteer 脚本，问题有两个：

1. 必须有 MySQL / Redis / 前端 dev server 才能跑，换台机器或别人接手时成本高；
2. 反馈慢，改一个命名工具或密码策略要等整套接口回归才知道有没有坏。

本轮目标：**补一层不依赖外部服务、15 秒内跑完的纯逻辑单测**，再用 GitHub Actions
在 JDK 8/11/17 上自动跑一遍。数据库 / 页面级回归仍保留，不重复建设。

### 36.2 依赖与构建配置

- `lx-common/pom.xml`、`lx-modules/pom.xml` 统一继承 `spring-boot-starter-test:2.7.18`（test scope），
  子模块不再逐个写；
- 根 `pom.xml` 的 `maven-surefire-plugin` 固定 `-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai`，
  避免 Windows 控制台中文乱码、时间断言受时区影响；
- test scope 不进 `BOOT-INF/lib`，**运行时 jar 仍是 102 个**，发布包结构不变。

实际版本：JUnit Jupiter 5.8.2、Mockito 4.5.1、AssertJ 3.22.0、Spring Test 5.3.31。

### 36.3 测试清单（66 项 / 13 个类）

| 模块 | 测试类 | 项数 | 锁住什么 |
|---|---|---|---|
| `lx-common-core` | `RTest` | 6 | code=0 / 1000、data 透传、`isSuccess` 被 `@JsonIgnore` |
| `lx-common-core` | `PageResultTest` | 2 | 分页字段契约、空分页返回空列表 |
| `lx-common-core` | `ErrorCodeTest` | 4 | 号码唯一、文案非空、核心号码稳定、码段分布 |
| `lx-common-core` | `ServiceExceptionTest` | 4 | 错误码与自定义消息优先级、RuntimeException |
| `lx-common-core` | `ConfigProviderTest` | 3 | int / boolean 脏值回退默认值 |
| `lx-module-system` | `PasswordPolicyTest` | 5 | off / basic / strong、非法值回退 basic、1001 |
| `lx-module-system` | `HtmlSanitizerTest` | 9 | script / 事件属性 / 伪协议 / iframe / 内联样式 |
| `lx-module-system` | `SysMenuServiceTest` | 4 | 两层树、孤儿节点作根、空列表、详情 1004 |
| `lx-module-file` | `LocalStorageServiceTest` | 4 | 读写删闭环、`../` 穿越拒绝、文件不存在 |
| `lx-module-job` | `SysJobServiceTest` | 7 | 空 cron、5 段 cron、执行器不存在、重名、枚举非法 |
| `lx-module-generator` | `NamingUtilsTest` | 5 | 下划线 / 连字符、关键字避让、空值 |
| `lx-module-generator` | `JavaTypeResolverTest` | 5 | MySQL / PG / 达梦类型、未知回退 String |
| `lx-module-generator` | `TextUtilsTest` | 8 | 注释折叠、Javadoc 截断、JS / HTML / Java / SQL 转义 |

合计 **66 项**，全部通过，0 skipped。文件系统测试用 JUnit `@TempDir`，
Mapper / 参数读取用 Mockito，**没有任何用例连接 MySQL 或 Redis**。

### 36.4 最小 CI

新增 `.github/workflows/ci.yml`：

| Job | 内容 |
|---|---|
| `backend` | 矩阵 JDK 8 / 11 / 17；`mvn -B -ntp test`；JDK 8 额外 `package` |
| `frontend` | Node 20；`npm ci` + `npm run build` |

- 触发：push main/master、任意 PR、手动 `workflow_dispatch`；
- 上传各 JDK 的 surefire 报告与前端 `dist`，保留 7 天；
- 不启数据库、不跑 Puppeteer、不发布镜像，避免把“最小 CI”做成运维负担。

> 当前目录尚未 `git init`，流水线要等工程入库并推到 GitHub 后才会真正执行；
> 启用步骤写在 `docs/26-testing-ci.md` 6.1。`.gitignore` 早已就绪（`target/`、`node_modules/`、
> `release/`、`data/`、`.local/`、`*.log` 均不入库），首次提交不会带进几十 MB 产物。

### 36.5 验证

| 项 | 结果 |
|---|---|
| `mvn -B -ntp test`（JDK 8） | **BUILD SUCCESS**，66/66 |
| `mvn -B -ntp test`（JDK 11） | **BUILD SUCCESS**，66/66 |
| `mvn -B -ntp test`（JDK 17） | 本机无 17，交由 CI 矩阵验证 |
| 运行时 jar 数量 | 102，未变化 |
| 重新打包后接口回归 | `verify-crud.ps1` **pass=74 / fail=0** |
| 后端业务代码 | 未修改（只加了测试与 POM 配置） |

### 36.6 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `assertThat(Resource).exists()` 编译不过 | AssertJ 的 `ObjectAssert` 没有 `exists()`，那是 `FileAssert` | 改成 `assertThat(resource.exists()).isTrue()` |
| Surefire 警告 `Parameter 'useModulePath' is unknown` | Boot 2.7 管理的 Surefire 是 2.22.2，该参数 3.x 才支持 | 删除 `useModulePath`，不升级插件 |
| 单元测试会不会拖慢发布打包 | 测试只编译 `src/test`，`-DskipTests` 仍是发布默认 | 发布脚本不变；CI 不允许跳过 |
| 本机 `package` 报 `Unable to rename ... lingxiao.jar`，jar 被截成 10KB | Windows 下后端进程正从该 jar 运行，文件句柄被占用；Boot repackage 改名失败 | **先停后端再 `package`**，然后重启；CI 无此问题。已验证重建后 49.9MB / 102 个运行时 jar |
| 按文档 `-pl lx-modules/lx-module-generator test` 报依赖 `1.0.0-SNAPSHOT (absent)` | 内部模块没有 `install` 到本地仓库，`-pl` 只构建一个模块 | 加 `-am` 带上游模块；同时加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则上游模块报“没有指定测试”失败 |

### 36.7 文件清单

| 文件 | 说明 |
|---|---|
| `pom.xml` | Surefire 编码 / 时区配置 |
| `lx-common/pom.xml`、`lx-modules/pom.xml` | 统一 test 依赖，子模块不重复声明 |
| `lx-common-core/src/test/.../RTest.java` 等 5 个 | 统一响应、分页、错误码、异常、参数 SPI |
| `lx-module-system/src/test/.../PasswordPolicyTest.java` 等 3 个 | 密码策略、HTML 清洗、菜单树 |
| `lx-module-file/src/test/.../LocalStorageServiceTest.java` | 本地存储与路径穿越 |
| `lx-module-job/src/test/.../SysJobServiceTest.java` | 定时任务保存校验 |
| `lx-module-generator/src/test/.../NamingUtilsTest.java` 等 3 个 | 命名、类型映射、文本转义 |
| `.github/workflows/ci.yml` | 最小 CI（后端矩阵 + 前端构建） |
| `docs/26-testing-ci.md` | 测试与 CI 说明 |
| `docs/11-jdk-engineering.md` | 第 5 节改写为真实 GitHub Actions 配置；第 9 节测试策略标注落地状态 |
| `docs/22-dependencies.md` | 新增 2.12 测试依赖与“不引 H2 / Testcontainers / JaCoCo”说明 |
| `README.md`、`lx-ui-admin/README.md` | 更新测试 / CI 段落与文档索引 |

### 36.8 后续

- Controller 层：按模块补 `@SpringBootTest` + MockMvc 冒烟，不追求全量；
- Mapper / Flyway：需要时再引 Testcontainers（MySQL 8），不要用 H2 代替；
- 前端纯函数（`theme.js` 的 `normalizeTheme` / `mixColor`）可补 Vitest，先把浏览器用例留着；
- 覆盖率暂不引入 JaCoCo，避免为数字写无意义断言。

---

## 37. 第二十轮：顶栏按钮可用性 + GitHub 交接文档

### 37.1 背景

三个问题一起处理：

1. 右上角「全屏 / 主题 / 设置」三个图标点击区域偏小，实际只有 18×18；
2. 想知道现有依赖（“轮子”）有没有完整清单；
3. 项目上传 GitHub、换一台电脑 clone 后怎么接着做。

第 2、3 个问题本质是“交接资料是否完整”，所以本轮同时补一份换机器文档。

### 37.2 顶栏按钮

**根因**：Element Plus 的 `.el-icon` 自带 `width: 1em; height: 1em`，
只加 `padding` 不会扩大盒子（box-sizing 为 border-box 时反而挤压图标）。
上一版 `font-size: 18px`，所以点击区域就是 18×18。

**改法**（`lx-ui-admin/src/layout/Navbar.vue`）：

- 显式 `width: 38px; height: 38px`，`display: inline-flex` 居中；
- 图标 `font-size: 22px`，视觉明显变大；
- `padding: 0`、`border-radius: 8px`，hover 背景不变；
- 折叠按钮（汉堡）共用同一套尺寸，保持左对齐视觉一致。

**验证**（Puppeteer 实测）：

| 项 | 结果 |
|---|---|
| 三枚图标盒子 | `38 × 38`，`font-size: 22px` |
| 控制台 error | 0 |
| 主题用例 | **12/12 PASS**（含点击三枚图标的用例） |
| 冒烟用例 | **22/22 PASS** |
| `npm run build` | 成功（5.32s） |

1440×900 截图见 `.local/uitest/shots/navbar-1440.png`（本地临时产物，不入库）。

### 37.3 交接文档

新增 `docs/27-github-onboarding.md`：

- 轮子清单入口（指向 `docs/22`，含自查命令）；
- 上传前检查：会入库 / 不会入库的文件、首次 `git init` + push 命令、误加产物怎么撤回；
- 换机器启动：环境要求、建库、环境变量（特别标注 `DB_PORT` 默认 3307）、后端 / 前端启动；
- 首次登录与自检清单；
- 继续任务：阅读顺序、每轮固定动作、当前进度快照、下一轮候选；
- 本地专属资产（验证脚本、MySQL 数据、上传文件、发布包）怎么备份。

同时新增 `.gitattributes`：仓库统一 LF，`.ps1` 保持 CRLF，`.sh` 强制 LF，
避免 Windows 开发、Linux 部署时出现 `bad interpreter: /bin/bash^M`。

`README.md` 同步：

- 启动示例 `npm install` → `npm ci`（与 CI 一致）；
- 增加「换电脑 / 从 GitHub clone 请看 docs/27」提示；
- 文档索引加 27，范围描述由 `22~26` 改为 `22~27`。

### 37.4 文件清单

| 文件 | 改动 |
|---|---|
| `lx-ui-admin/src/layout/Navbar.vue` | 图标 38×38 点击区、22px 字号 |
| `docs/27-github-onboarding.md` | 新增：上传 GitHub 与换机器继续开发 |
| `.gitattributes` | 新增：换行符与二进制文件约定 |
| `.gitignore` | 补充 `shots/`、`.workbuddy/`（本地截图与工具产物） |
| `README.md` | `npm ci`、docs/27 入口、索引与 22~27 描述 |
| `docs/22-dependencies.md` | 顶部增加 docs/27 链接 |
| `docs/21-build-log.md` | 本节 |

后端代码、数据库、Flyway 脚本本轮**未改动**。

### 37.5 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 只改 `font-size` 后点击区域仍是 20×20 | `.el-icon` 固定 `width/height: 1em` | 显式设置 `width/height: 38px` |
| `npm run build` 报 `EPERM ... dist\ueditor-plus` | Windows 有进程/索引占用 `dist`，Vite 清空目录失败 | 关掉占用后手动 `Remove-Item dist -Recurse -Force`，再构建即成功 |
| 文档里写“19 张表” | 实际迁移建了 22 张（含 5 张 `gen_*` / 文件关联表） | 以 `CREATE TABLE` 统计为准，docs/27 写 22 张 |

### 37.6 后续

- 如果希望验证脚本跟仓库走，下一轮把 `.local/verify-*.ps1` 与 `.local/uitest/*.mjs`
  筛一遍移到 `tools/`，只保留正式用例，并替换文档里的 `.local/` 路径；
- 上传 GitHub 后确认 CI 首次运行结果，必要时把矩阵缓存/超时再调；
- 顶栏如还需要更明显，可考虑加文字标签或把三枚图标合并成带分隔线的按钮组（当前先按“变大”处理）。

---

## 38. 第二十一轮：数据库快照随仓库走

### 38.1 背景

上一轮答了“数据库文件在哪”，但只解决了“物理文件在 `.local/mysql/data`”。
用户真正的诉求是：**把一份 SQL 放进项目文件夹，clone 下来直接导入就能用**，
而不是每次靠 Flyway 从空库迁 14 条，也不是把 200 MB 的 InnoDB 目录塞进仓库。

### 38.2 做法

新增 `deploy/sql/lingxiao-1.0.0.sql`（**48 KB**）：

| 项 | 值 |
|---|---|
| 来源 | `mysqldump 5.7.26` + `--single-transaction --quick --triggers --no-create-db --databases lingxiao` |
| 表 | 23 张（22 张业务表 + `flyway_schema_history`） |
| Flyway 记录 | 14 条（V1~V14） |
| 初始数据 | 64 菜单、64 角色菜单、9 参数、3 字典类型 + 7 字典数据、1 定时任务、1 公告、admin 账号 |
| 编码 | UTF-8 无 BOM，LF 换行 |
| 危险语句 | 只有 `DROP TABLE IF EXISTS` + `CREATE TABLE`，没有 `CREATE DATABASE`（不需要建库权限） |

**为什么不直接 dump 现网库**：开发库里有验证脚本留下的脏数据——
126 条登录日志、396 条操作日志、9 条 `sys_file`（文件本身在 `data/upload`，不入库，
导过去只会是坏缩略图）、1 条 `gen_table`（指向测试导入的 `sys_config`）、
8 个 `verify-job-*` / `ui-job-*` 测试任务、8 条测试公告。

所以流程是：**先导入临时库 → 清理 → 再 dump 临时库**，正式库一个字节都没动。

清理清单（`sys_login_log` / `sys_oper_log` / `sys_job_log` / `sys_job_lock` /
`sys_file` / `sys_file_name_log` / `gen_table` / `gen_table_column` 清空，
`sys_job` 只留种子任务，`sys_notice` 只留 `notice_id = 1`）。

快照头部写了一段中文说明：用途、内容、已清理项、导入命令、四条注意事项、重新生成命令。

### 38.3 验证

**A. 导入 → 启动 → 点接口（端到端）**

1. 建空库 `lingxiao_snapshot2`；
2. 把快照的 `USE` 临时改指向它并导入（`mysql ... < file`，退出码 0）；
3. 用**现有 `lingxiao.jar`** 起在 8081（`DB_NAME=lingxiao_snapshot2`）；
4. Flyway 日志：`Successfully validated 14 migrations` / `Schema is up to date. No migration necessary.`；
5. 接口实测 **15/15**：

| 用例 | 结果 |
|---|---|
| 登录 `admin/admin123` | PASS |
| 当前用户信息 | PASS（权限 1 条） |
| 登录菜单树 | PASS（4 个顶级） |
| 用户列表 | PASS（total=1） |
| 菜单列表 | PASS（64 行） |
| 参数列表 | PASS（total=9） |
| 公告列表 | PASS（total=1） |
| 字典类型 | PASS（total=3） |
| 定时任务 | PASS（total=1） |
| 文件中心 | PASS（total=0，符合预期） |
| 代码生成 | PASS（total=0，符合预期） |
| 服务器监控 / 在线用户 / 登录日志 / 操作日志 | PASS |

**B. 数据一致性**：导入后 `information_schema.tables = 23`、`sys_menu = 64`、
`sys_user = 1`、`flyway_schema_history = 14`，中文（`超级管理员`、`系统管理`）无乱码。

**C. 收尾**：停掉 8081 实例，`REVOKE` 临时授权，`DROP` 两个临时库；
正式库 `lingxiao` 与 8080 上的原实例未受影响。

### 38.4 文件清单

| 文件 | 改动 |
|---|---|
| `deploy/sql/lingxiao-1.0.0.sql` | 新增：v1.0.0 数据库快照（48 KB） |
| `deploy/README.md` | 交付物树加该文件；第 4 节加「4.1 方式二：导入现成数据」 |
| `docs/27-github-onboarding.md` | 3.3 改成「先 init-db、再 A/B 二选一」；2.2 入库表加该文件；FAQ 加两条 |
| `README.md` | 快速开始加一句“想要 clone 下来就有数据” |
| `docs/21-build-log.md` | 本节 |

后端代码、Flyway 脚本、前端**未改动**；`.gitignore` 也无需改（`deploy/sql/` 本来就不被忽略）。

### 38.5 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| PowerShell `mysqldump ... > file.sql` 产物是 UTF-16 | PS 5.1 的 `>` 会转成 UTF-16LE，mysql 客户端读不了 | 改用 `mysqldump --result-file=`，按字节写入 |
| 在 `node -e` 里用反引号正则报 `Unterminated regexp literal` | PowerShell 把反引号当转义符吞了 | 正则写进 `.mjs` 文件再跑 |
| 快照里 `USE \`lingxiao\`` 没法导进临时库 | dump 里带 `USE` 语句，会覆盖命令行指定的库 | 验证时用脚本把 `USE` 临时改写，不改发布的文件 |
| 头部中文写入后乱码 | 用 `latin1` 读写整个文件时把中文也降成单字节 | 正文按 latin1 字节保真、文件头单独用 UTF-8 编码后 `Buffer.concat` |
| 接口自测出现 `NumberFormatException: "page"` / `"routes"` | 用了不存在的 `/xxx/page`、`/menu/routes`，被 `/{id}` 吃掉 | 实际路径是 `/list`、`/auth/menus`，见各 Controller 的 `@GetMapping` |

### 38.6 后续

- 版本升级（新增 `V15__*.sql`）后，快照**不需要**跟着改：老快照 + 启动时自动补迁移，
  依然能起来；只有希望“新版本也开箱即用”时才重新生成快照并改名（如 `lingxiao-1.1.0.sql`）；
- 重新生成前记得先清理（照 38.2 的清单），别把日志和测试数据再带进去；
- 如果以后要交付给客户，建议把快照里的 `admin` 密码改成随机值并在部署文档里提示首次改密。

### 38.7 顺手修的文档漂移

做快照时顺手核对了发布包里的 jar 与 `docs/11` 的依赖矩阵，发现四处对不上（`docs/22` 是对的，
错的是 `11` 这张规划期的表）：

| 依赖 | `11` 原来写 | 发布包实际 |
|---|---|---|
| Flyway | 8.5.13 | **7.15.0**（8.5.13 不支持 MySQL 5.7，见 `21` 第 5.5 节） |
| MyBatis-Plus | 3.5.7 | **3.5.3.2** |
| Hutool | 5.8.32 | **5.8.22** |
| Thumbnailator | 0.4.20 | **0.4.8** |

另外 Druid / Redisson / PG / 达梦 / 金仓 / Flowable / EasyExcel / MapStruct / MinIO / Quartz /
Testcontainers 都还没进包（源码里 0 引用），原表全标 ✅ 会误导后来人。已改成：

- 表头加一句「✅ = v1.0 已在用，— = 还没进包」；
- 未引入的行标 **规划** 或 **不引入**，并写清 v1.0 实际用什么替代（HikariCP / Lettuce / Spring Task）；
- 补上 v1.0 真正在用的 Jsoup / Velocity / Lombok 三行。

---

## 39. 第二十二轮：上传 GitHub

### 39.1 背景

代码一直只在本机，`docs/27` 写好了流程但没执行。用户要求直接推上去。

### 39.2 做了什么

| 步骤 | 结果 |
|---|---|
| `git init -b main` | `E:\java\lingxiao\.git` |
| `git add .` | 371 个文件（无 `target/` `node_modules/` `dist/` `.local/` `release/`） |
| 首次提交 | `ee26d5e chore: 凌霄 v1.0 后台基座` |
| 远端 | `git@github.com:hunterlr/lingxiao.git`（SSH） |
| 推送 | `main -> main`，`git ls-remote` 与本地 HEAD 完全一致 |
| 仓库可见性 | public（要改私有：Settings → Danger Zone → Change visibility） |

提交前做了三件事：

1. `git diff --cached --name-only` 按顶层目录统计，确认没有构建产物；
2. 扫敏感串（`ghp_*` / `sk-*` / `BEGIN * PRIVATE KEY` / `AKIA*`）——0 命中；
3. 查大文件——无 >5 MB 文件，不会撞 GitHub 的 100 MB 限制。

### 39.3 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `git ls-remote` 报 `Failed to connect to 127.0.0.1 port 10808` | `~/.gitconfig` 里 `[http "https://github.com"] proxy = socks5h://127.0.0.1:10808`，代理没开 | 临时用 `-c "http.https://github.com.proxy="` 绕过；`curl --noproxy "*"` 实测直连 200 |
| `git push` 弹不出认证，直接 `failed to execute prompt script` | `credential.helper=manager` 但 Git Credential Manager 二进制缺失 | 改用 SSH，绕开凭据助手 |
| `ssh -T git@github.com` 报 `Permission denied (publickey)` | `~/.ssh/id_rsa` 存在但没注册到 GitHub | 用 `https://github.com/<user>.keys` 验证（返回空即未注册），加公钥后重试 |
| `api.github.com` 任何路径都 301 | 本机网络对 API 域做了重定向 | 不影响 `git push`；只是没法用 API 查 Actions，需要 PAT 或人工看网页 |
| 剪贴板里的公钥丢了 | 用户侧剪贴板被覆盖 | `Set-Clipboard` 重新写入并用 `Get-Clipboard` 回读校验长度一致 |

### 39.4 验证

```text
local  HEAD : ee26d5e853af318c7a49fb0ac3b76cd16fac35e3
remote main : ee26d5e853af318c7a49fb0ac3b76cd16fac35e3
tracked     : 371
status      : ## main...origin/main（工作区干净）
```

### 39.5 后续

- 去 Actions 看 `CI` 首次运行（我这边没有 PAT，查不了 API）；
- 决定仓库是否要转私有；公开的话建议补 `LICENSE`；
- `~/.gitconfig` 里那段死代理建议删掉，否则以后 `git clone` 还会踩；
- 下一轮按 `docs/27` 4.3 的候选继续（多语言 L1 / 中文接口名 / Excel）。

---

## 40. 第二十三轮：README 双语化 + 多语言 L1（后端）

### 40.1 README 重写

原来的 `README.md` 是施工式文档（“第 N 轮做了什么、验证了多少项”），不适合给外部看。这一轮改成公开项目介绍：

| 动作 | 结果 |
|---|---|
| 中文 `README.md` 重写 | 徽章 → 一句话定位 → 功能特性 → 截图 → 技术栈 → 快速开始 → 目录 → 接口 → 文档 → 路线图 → 贡献 / 致谢 |
| 新增 `README.en.md` | 与中文同结构，顶部互相加语言切换链接 |
| 截图 | 12 张 PNG 放到 `docs/images/`（登录、首页、用户、角色、参数、字典、生成器、监控、任务、文件、暗色、主题抽屉），表格里用 `<img width="430">` 控制宽度 |
| 删掉的内容 | 施工记录、验证项计数、“本轮做了什么”全部移出 README，只留在 `docs/21` |

截图由 `.local/uitest/shots-readme.mjs` 用 Puppeteer 生成（1440×900，控制台 0 error），脚本不入库。
提交 `a4b6d4e`，只带 `README.md` / `README.en.md` / `docs/images/`，不夹带 i18n 改动。

### 40.2 多语言 L1：只做后端

按 `docs/23` 的分轮建议，第一轮先把**接口契约**稳定下来，前端第二轮再切 UI。

新增文件：

| 文件 | 作用 |
|---|---|
| `lx-common-core/.../i18n/MessageResolver.java` | 最小解析接口，core 不依赖 Spring |
| `lx-common-core/.../i18n/MessageUtils.java` | 静态入口；永不抛异常，失败退回默认文案 |
| `lx-common-web/.../i18n/I18nConfig.java` | MessageSource + LocaleResolver 装配 |
| `lx-common-web/.../i18n/LingxiaoLocaleResolver.java` | `X-Lang > ?lang= > Accept-Language > zh-CN` |
| `lx-common-web/.../i18n/I18nMessageInitializer.java` | 启动时把 MessageSource 插到静态入口上 |
| `i18n/messages.properties` / `messages_en_US.properties` | 94 个键（18 个错误码 + 5 个带参错误 + 70 个校验 + 1 个分隔符） |

改动文件：

- `R.java`：`messageKey` 字段（`NON_NULL` 不输出）+ `ok/fail(ErrorCode)` 本地化；
- `ServiceException.java`：`messageKey` / `args` / 三参构造 / `keyed(...)` 工厂，`getMessage()` 保留中文兜底；
- `GlobalExceptionHandler.java`：构造注入 MessageSource，带 key 的异常翻译后再返回；
- `AuthService.java`：锁定文案换成 `error.2004.locked` / `error.2004.failures`（登录日志仍写中文）；
- `PasswordPolicy.java`：密码策略文案 key 化；
- 17 个 DTO / 实体、87 处 `@NotBlank/@Size/...`：中文 message 换成 `{valid.*}`。

### 40.3 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 想给配置类构造注入 MessageSource 报循环依赖 | 配置类 → Bean → 配置类 | 拆出 `I18nMessageInitializer` 单独组件 |
| `ServiceException(ErrorCode, "key")` 走不到带参构造 | 两参构造是“字面量”语义，重载更具体 | 无参 key 用 `ServiceException.keyed(...)` 工厂 |
| 三参构造 `super(key)` 导致翻译失败时把 key 弹给用户 | 缺省消息用了 key | 改成 `super(errorCode.getMsg())`，兜底是中文枚举 |
| 校验消息不跟语言走 | Validator 需要容器里的 MessageSource | 不自己定义 Validator，让 Boot 自动装配接上（配合 `LocaleContextHolder`） |
| `MockHttpServletRequest` 默认 Accept-Language 是 en | Spring 测试桩的默认值 | 测试里显式给 `fr-FR` 验证“不支持 → 回退中文” |
| 中英 properties 容易只改一边 | 两份文件手工维护 | 新增 `I18nMessageSourceTest` 断言键集合完全一致 |

### 40.4 验证

```text
mvn -B -ntp test            BUILD SUCCESS，84 项（原 66 + 新增 18）
mvn -B -ntp -DskipTests package   BUILD SUCCESS
```

接口实测（8080，关闭验证码）：

```text
X-Lang: zh-CN  {"code":2001,"msg":"用户名或密码错误","messageKey":"error.2001"}
X-Lang: en-US  {"code":2001,"msg":"Incorrect username or password","messageKey":"error.2001"}
X-Lang: en-US  {"code":1001,"msg":"password: Password must not be empty; username: Username must not be empty"}
X-Lang: en-US  {"code":1002,"msg":"Not signed in or session expired","messageKey":"error.1002"}
X-Lang: en-US  {"code":0,"msg":"Success","data":{"tokenName":"lx-platform-token",...}}
```

### 40.5 下一轮

- 前端：`vue-i18n@9`、`store/locale.js`、`ElConfigProvider`、顶栏语言切换、`X-Lang` 请求头；
- `MenuVO` 补 `menuKey`，侧边栏 `t('menu.'+key, name)`；
- 18 个业务页文案替换，按 `docs/23` 3.4 清单验收。

---

## 41. 第二十四轮：前端界面双语（L1 第二轮）

后端契约在第一轮已经稳定（`R.messageKey` + `error.<code>`），本轮把 18 个业务页、
登录页、错误页和公共组件全部接上 `vue-i18n`，并按 `docs/23` 3.4 逐项验收。

### 41.1 基础设施

| 文件 | 内容 |
|---|---|
| `package.json` / `package-lock.json` | 新增 `vue-i18n@^9.14.4`（锁定 9.14.4），不引 `@intlify/unplugin-vue-i18n`——语言包是普通 JS 对象，不需要编译期提取 |
| `src/i18n/index.js` | `createI18n({ legacy: false, globalInjection: true })`；`fallbackLocale = zh-CN`；`missingWarn/fallbackWarn = false`；导出 `LOCALE_STORAGE_KEY` / `DEFAULT_LOCALE` / `SUPPORTED_LOCALES` / `resolveInitialLocale()` |
| `src/i18n/zh-CN.js` / `en-US.js` | 当前各 **897 键**，键集合必须完全一致（`scripts/check-i18n-keys.mjs` 断言） |
| `src/store/locale.js` | Pinia 存 `locale`；`init()` 回写 i18n 与 `html.lang`；`setLocale()` 立即生效 + 落 `localStorage['lx-admin-locale']` |
| `src/main.js` | `app.use(i18n)` + `localeStore.init()` |
| `src/App.vue` | `ElConfigProvider :locale` 绑定响应式值；`watch(locale)` 同步 `document.title` |
| `src/api/request.js` | 每个请求带 `X-Lang`；拦截器兜底文案改 `t('common.*')` |

初始语言顺序与 `docs/23` 一致：`localStorage` → `navigator.language`（`en*` 归到 `en-US`）
→ `zh-CN`。日文、法文等一律回退中文，只维护两套。

### 41.2 菜单、面包屑、页面标题

- 后端 `MenuVO` 增加 `menuKey`，`AuthService.toVO` 从 `sys_menu.menu_key` 填充并下发；
- `Sidebar.vue` / `SidebarItem.vue` / `Breadcrumb.vue` 统一 `te('menu.'+menuKey) ? t(...) : menu.name`
  ——新加菜单即使前端没译文也不会显示键名；
- `router/dynamic.js` 生成路由时写 `meta.titleKey = 'menu.'+menuKey`；
- `router/index.js` 静态路由（登录 / 403 / 404 / 占位页）写死 `titleKey`；
- `router/guard.js` 的 `afterEach` 与 `App.vue` 的 `watch` 都用 `titleKey` 取标题。

### 41.3 页面与公共组件

| 范围 | 做法 |
|---|---|
| 18 个业务页 + 登录 / 403 / 404 / 占位 / 个人中心 | 文案统一走 `t('page.<页面>.*')`、`common.*`；表格列、表单 label、按钮、确认框、toast 全量替换 |
| `FileLibrary.vue` | `fileLib.*`（搜索、上传、改名、详情、改名历史、复制链接） |
| `DictTag.vue` / `DictSelect.vue` | 运行时 `dict.<type>.<value>`，缺项回退数据库 `label`/`dictLabel` |
| `utils/config-control.js` | `config.<config_key>` 与 `configControl.*`，缺项回退数据库 `config_name`/`remark` |
| `ThemeDrawer.vue` + `utils/theme.js` | `THEME_PRESETS` 的 `name` 改 `key`，运行时 `t('theme.color.'+key)` |
| `RichEditor.vue` / `useEditorEngine.js` | 引擎名、切换提示走 `editor.*`，`hintKey` 字段替代写死文案 |
| `UEditorPlus.vue` / `WangEditorNext.vue` | 占位符默认值、上传失败提示、文件中心 `remark` 按当前语言写 |
| `store/auth.js` | `displayName` / `roleLabel` getter 里 `void i18n.global.locale.value`，保证切语言时重算 |

字典 / 参数文案是**运行时**取 key 的，不在模块加载时固化 label，
所以切语言后表格里已渲染的字典标签也会跟着变（这是 L1 唯一“翻译数据库文本”的地方，
且只翻译“标签”，不翻译业务数据本身）。

### 41.4 顶栏语言切换

`Navbar.vue` 在主题按钮旁加语言胶囊（`中` / `EN`），点击弹 `简体中文 / English`，
选中项禁用。原实现用 `el-tooltip` 包 `el-dropdown` 触发器，Element Plus 2.14 会报
`Invalid prop: validation failed for prop "role"`（tooltip 给触发器塞 `role="button"`，
而 dropdown 只接受 `menu/navigation/...`），改为原生 `title` 提示。

### 41.5 后端顺带修正

`CacheMonitorService` 的 `note` / `warnings` / Redis uptime 原来硬编码中文，
英文界面下这一页会残留中文。改为 `MessageUtils.message(...)`，新增
`monitor.cache.note.*`、`monitor.cache.warning.*`、`monitor.cache.duration.*` 共 11 个键，
中英 properties 同步。业务日志（`log.info`）仍保持中文，符合“日志内容不翻译”的约定。

### 41.6 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `el-tooltip` 包 `el-dropdown` 触发器时控制台报 `role` 校验失败 | tooltip 给子节点注入 `role="button"`，dropdown 只接受 menu 系列 | 语言胶囊改用原生 `title`，不再套 tooltip |
| 中文下 `auth.displayName` 切英文后不更新 | Pinia getter 依赖的是 i18n 全局单例，不是响应式 state | getter 内 `void i18n.global.locale.value` 建立依赖 |
| 主题预设名写死中文，切英文仍显示“凌霄蓝” | `THEME_PRESETS.name` 是数据不是文案 | 改成 `key`，模板里 `t('theme.color.'+key)` |
| 生成器 / 任务页的“动态拼接键”切语言不更新 | 模板里 `t('page.job.'+preset.key)` 依赖响应式 preset | preset 内置 `key`，模板运行时再 `t`，不缓存结果 |
| 语言包两边容易只改一边 | 两份 JS 手工维护 | `scripts/check-i18n-keys.mjs` 断言键集合一致 + 列出 missing/unused |
| dev server 偶发 chokidar `EBUSY` 崩溃 | `dist/` 被外部进程（预览 / 缩略图缓存）占用，热更新仍去读 | `vite.config.js` 加 `server.watch.ignored: ['**/dist/**', '**/dist-*/**']` |
| UI 冒烟脚本“退出登录”步骤等 `.el-dropdown-menu` 可见超时 | 顶栏现在有两个下拉，Element Plus 把未打开的那个也渲染成 `display:block` 的 0×0 节点，`waitForSelector({visible:true})` 命中的是它 | 回归脚本改成等“任意 `.el-dropdown-menu__item` 有实际高度” |

### 41.7 验证

```text
node scripts/check-i18n-keys.mjs      zh=897 en=897，键集合一致，missing=none
npx vite build --outDir <临时目录>     built in 5.61s，exit=0
mvn -B -ntp test                      BUILD SUCCESS，84 项（16 个测试类）
.local/uitest/smoke.mjs               pass=22 fail=0，浏览器控制台 0 error
.local/uitest/theme.mjs               12 项全 PASS
```

浏览器逐页（Chrome，admin/admin123，`en-US`）：

- 登录页、侧边栏、面包屑、`document.title` 全英文；刷新后仍保持 `en-US`；
- 18 个业务页静态 UI 全英文，剩余中文只有数据库内容（菜单名、字典 label、参数名 /
  说明、部门 / 岗位 / 公告 / 任务名、文件备注、日志内容），符合 L1 范围；
- Element Plus：分页 `Total 1 | 10/page | 1 | Go to`、日期区间 `Start time / End time / to`、
  确认框 `Notice / Sign out now? / Cancel / Sign out` 均为英文；
- 表单校验：任务新增空提交提示 `Please enter the job name / Please select an executor`；
- 后端错误：`error.2001` 英文 `Incorrect username or password`、中文 `用户名或密码错误`；
  空参数校验英文 `password: Password must not be empty; username: Username must not be empty`；
- 控制台无 `[intlify]` missing key 警告、无 Vue prop 警告。

### 41.8 下一轮

- L1 收尾：`docs/23` 3.4 清单已全部勾选，可直接进入 L2 设计评审；
- L2（v1.2）才引入 `i18n_*` 表翻译业务数据；L1 不新增数据库列，语言只存浏览器；
- 若后续要“个人语言偏好跨设备”，在 `sys_user` 加 `language` 列并在登录后回填即可，
  不影响本轮前端结构。

---

## 42. 第二十五轮：按钮权限复核与补齐

### 42.1 起因

记录待办时曾判断“`v-hasPerm` 写了但 0 处使用”，复核发现是**搜索词写错**：
指令实际叫 `v-hasPerm`（`main.js` 里 `app.directive('hasPerm', permission)`），
按 `v-permission` 搜索自然是 0。真实情况是 13 个页面已用 46 处。

复核同时发现两个真空档：

| 缺口 | 现状 | 处理 |
|---|---|---|
| 文件中心 | 上传 / 改名按钮没有权限控制，只读账号也能上传 | 加 `platform:file:upload` / `platform:file:rename` |
| 用户启停用 | 行内 `el-switch` 未判断 `user:edit`，只读账号可切换状态（后端会 403） | 加 `canEditUser` 计算属性，无权限时禁用，与任务页 `canChangeStatus` 一致 |

同时修掉 `lx-module-system/README.md` 里“前端指令待做”的过期描述，
写明覆盖范围与“前端隐藏不是安全边界”。

### 42.2 验证方法

本地脚本 `.local/uitest/perm-check.mjs`（不入库）：

1. admin 登录，取全部 `M` / `C` 菜单（21 个）；
2. 建只读角色 `readonly_test`：`dataScope=5`（仅本人），只授 M/C，不授任何 F 按钮；
3. 建同账号名的测试用户并登录，拉 `/auth/info`；
4. 断言权限集合里没有写权限，直连写接口必须 403；
5. 用完删除测试用户与角色（幂等，可重复跑）。

### 42.3 实测结果

```text
M/C menus=21  all menus=64
readonly permissions=18   has write perm? false
POST   /admin/system/user          → 403 {"code":1003,"msg":"没有权限执行该操作"}
DELETE /admin/system/oper-log/clean → 403 {"code":1003}
GET    /admin/system/user/list      → 200 code=0，dataScope=5 只返回本人 1 行
```

浏览器逐页核对（只读账号 14 页）：

| 页面 | 剩余按钮 |
|---|---|
| 用户 / 角色 / 菜单 / 部门 / 岗位 / 参数 / 在线用户 / 操作日志 / 登录日志 | 搜索、重置、刷新（+ 详情） |
| 字典 | 搜索 |
| 公告 | 搜索、重置、刷新、预览 |
| 文件中心 | 搜索、重置、详情、复制链接（**无** 上传 / 改名） |
| 定时任务 / 生成器 | 搜索、重置、刷新（**无** 新增 / 执行 / 日志 / 导入 / 配置 / 下载） |

状态开关：用户页 `switchDisabled=1/1`，任务页 `1/1`，均为只读态。

admin 复查：用户页有新增 / 编辑 / 重置密码 / 删除，文件中心有上传 / 改名，
任务页有新增 / 执行一次 / 日志 / 编辑 / 删除，开关可用。

回归：`smoke.mjs` 22/22，控制台 0 error；`npx vite build` 5.67s 通过。

### 42.4 踩坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 第一次统计 `v-hasPerm` 为 0 | 按 `v-permission` 搜索，大小写与命名都不对 | 以 `app.directive` 注册名为准再搜 |
| 清理脚本“执行成功”但数据还在 | `PageResult` 字段是 `records`，脚本取了 `list` | 改 `records`；也提醒接口返回不是 MyBatis-Plus `Page` |
| 本地 MySQL 隔一会儿就消失 | `mysqld` 用 `Start-Process` 从短命令里拉起，会被随命令回收 | 用后台长驻方式启动（或系统服务），跑脚本前先探 3307 |

### 42.5 下一项

`docs/13` §12.2：数据范围「2 自定义」——`sys_role_dept` 表 + 自定义部门树 + 列表过滤。

---

## 43. 第二十三轮：数据范围「2 自定义」

### 43.1 目标与现状

`sys_role.data_scope='2'`（自定义部门）在 v1.0 早期只有枚举与降级逻辑：
没有 `sys_role_dept` 表，保存时直接拒绝，历史数据按“仅本人”处理；
部门 / 角色列表也没有按数据范围过滤。本轮把这条链路补齐，并顺手堵住
“知道 ID 直接调接口”的越权口子。

### 43.2 数据库

新增 `V15__system_role_dept.sql`：

```sql
CREATE TABLE sys_role_dept (
  role_id bigint NOT NULL,
  dept_id bigint NOT NULL,
  PRIMARY KEY (role_id, dept_id),
  KEY idx_role_dept_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 不加外键，与 V1 的约定一致（跨表一致性由应用层保证）；
- 联合主键天然去重；`dept_id` 索引用于删除部门时清理关联；
- 语义是**精确集合**：勾父部门不自动包含下级，需要下级显式勾选或改用 4。

### 43.3 后端

| 文件 | 改动 |
|---|---|
| `SysRoleDept` / `SysRoleDeptMapper` | 新增；角色维度查部门、用户维度查并集、按角色 / 部门清理 |
| `SysRole` | 新增 `@TableField(exist=false) List<Long> deptIds` |
| `SysRoleService` | 保存 / 详情带 `deptIds`；先清后插；`null` = 不修改；空集合、越权部门、切到 2 却不带部门均拒绝；删除角色清理关联 |
| `DataScopeService` | `2` 返回 `sys_role_dept` 部门集合；多角色 1 > 4 > 3 > 2 > 5、自定义取并集；空集合收紧为仅本人；新增 `applyDeptScope` / `applyRoleScope` / `visibleDeptIds` / `assertDeptManageable` / `assertRootDeptAllowed` |
| `SysDeptService` | 列表 / 树按范围过滤；详情 / 新增 / 修改 / 删除断言部门范围；非全部范围不能建根部门；删除部门清理 `sys_role_dept` |

角色列表的过滤口径：非全部范围账号只看到“可授权”的角色，
即隐藏内置超管与 `data_scope=1` 的角色，与 `assertAssignableRoles` 一致；
同时保存自定义范围时不能勾选自己范围之外的部门，避免横向扩权。

> `resolveFor(userId)` 是包内可见的测试入口，生产仍走 `resolve()`；
> 这样单测不用模拟 Sa-Token 的静态登录上下文。

### 43.4 前端

`system/role/index.vue`：

- 数据范围选「自定义数据」时才渲染部门树；`check-strictly`，与后端的精确集合语义一致；
- 打开编辑时把 `detail.deptIds` 回显到树上；`openAdd` / 关闭弹窗时清空；
- 保存前前端先拦截“选自定义但一个部门都没勾”，请求体带 `deptIds`；
- 非自定义范围提交 `deptIds: null`，避免误清历史数据（后端切回其他范围时仍会主动清理）；
- 中英文各补 4 个键：`customDept`、`customDeptTip`、`customDeptRequired`、`emptyDept`，
  `scopeCustom` 去掉“未启用”。

### 43.5 验证

后端：

```text
mvn test → 92 项全通过（新增 DataScopeServiceTest 6 + SysRoleServiceTest 2）
```

覆盖：自定义命中、空集合收紧、超管放行、4 > 2、3 > 2、仅本人带 userId。

接口脚本 `.local/uitest/role-dept-check.mjs`（13/13）：

```text
创建 3 个部门 + 自定义角色（勾 2 个部门）+ 4 个用户
受限账号登录后：
  用户列表只包含自定义部门的 3 个用户
  部门列表 / 树只剩 2 个部门（父节点缺失时子节点提升为根）
  角色列表不含超管角色、保留可授权角色
  范围外用户详情 / 部门详情 → 403
空部门保存 → 拒绝（至少一个部门）
```

UI：角色页新增 / 编辑弹窗中，选「自定义数据」出现部门树，`凌霄科技` 回显勾选，
保存后库中 `sys_role_dept` 正确落一行；截图核对无布局问题。

回归：

```text
smoke.mjs 22/22    theme.mjs 12/12    check-i18n-keys.mjs missing=none
vite build 5.60s   控制台 0 error
```

测试部门 / 用户 / 角色已清理，`sys_user`、`sys_role`、`sys_dept` 各剩 1 条，
`sys_role_dept` 0 条。

### 43.6 取舍与踩坑

| 现象 / 选择 | 说明 |
|---|---|
| 自定义范围为什么不含下级 | 与 `docs/04` / `docs/05` 的既有语义一致；需要下级用 4，避免“勾一个父节点悄悄放大整棵树” |
| 角色列表怎么过滤 | 角色没有部门归属，按“可授权”口径隐藏超管与全部数据角色；比按部门交集更可解释，也和新用户授权校验一致 |
| 部门写操作为什么也加断言 | 只过滤列表挡不住直接按 ID 调接口；`docs/04` 要求列表 / 详情 / 修改 / 删除同一口径 |
| 自定义范围内新建子部门 | 新部门不会自动进范围，建完可能看不见；README 已注明部门树维护建议交给全部范围管理员 |
| 保存空部门为什么拒绝而不是存空 | 空集合会被收紧成仅本人，属于静默失败；直接报错更早暴露配置问题 |
| `wrapper` 条件加在 `orderBy` 之后 | MyBatis-Plus 按调用顺序拼 SQL，会生成 `ORDER BY ... AND ...`；本轮把数据范围条件统一放在排序之前 |

### 43.7 下一项

原计划是 `docs/13` §12.3（L2 多语言设计）；中途先处理了在线会话的
“同账号多行 + 失效会话残留”，见第 44 节。L2 方案仍待评审。

---

## 44. 第二十四轮：会话策略（默认单会话 + 失效清理）

### 44.1 现象与定位

在线用户页出现 5 条 `admin`，但 `current=true` 只有 1 条。逐条核对
`login_time` / `browser` 后确认：不是接口重复计数，而是**5 次真实登录**
（联调脚本 `role-dept-check`、两个无头浏览器回归、OpenCode 浏览器、排查用的 node 探测）。

根因是当时的设计：

```yaml
sa-token:
  is-concurrent: true   # 存储层允许并发
  is-share: false       # 每次登录发新 Token
```

`docs/21` §28.3 也明确写过“列表以 Token 为行”。所以一个账号登录 N 次就有 N 行，
旧 Token 要等 12 小时自然过期。

排查时还发现两个真问题：

1. 被删除的测试用户仍有一条会话挂在列表里，`username / nickname` 为空——
   `SysUserService.delete()` 没有注销该用户的 Token；
2. 停用账号、管理员重置密码同样没有注销会话，旧 Token 能继续用到过期。

### 44.2 方案

| 决策 | 做法 |
|---|---|
| 默认口径 | 单会话：新登录先踢掉该账号的全部旧 Token |
| 可配置 | 参数 `sys.account.multiDeviceLogin`（switch，默认 `false`），开启后恢复多端 |
| 不改 `is-concurrent` | 那是启动期配置；改成登录前按参数主动踢，改完下一次登录立即生效，不用重启 |
| 参数非法 / 缺失 | 按单会话处理（失败收紧，不意外放开多端） |
| 失效时机 | 新登录、自助改密、管理员重置密码、停用、删除 |

### 44.3 改动

| 文件 | 改动 |
|---|---|
| `SessionPolicy` | 新增；读取 `sys.account.multiDeviceLogin`，默认单会话 |
| `AuthService.login` | 校验通过后、发 Token 前调用 `logoutByUserId`（单会话模式） |
| `SysUserService.resetPassword` | 落新密码后注销该账号全部会话 |
| `SysUserService.changeStatus` | 停用时立即注销会话 |
| `SysUserService.delete` | 物理删除后注销会话，避免“空白账号”残留 |
| `V16__system_session_policy.sql` | 新增参数 `config_id=10`，`switch`，默认 `false` |
| `zh-CN.js` / `en-US.js` | 参数名翻译 `config.sys.account.multiDeviceLogin` |

> 顺序固定为“先写库、再踢会话”。写库失败时不能先把人登出。

### 44.4 验证

```text
mvn -B -ntp test                95 项全通过（新增 SessionPolicyTest 3 项）
session-policy-check.mjs        18/18
smoke.mjs                       22/22，控制台 0 error
theme.mjs                       12/12
check-i18n-keys.mjs             zh=902 en=902，missing=none
```

接口脚本覆盖：

```text
默认参数 = false
单会话：连登两次 → 旧 Token 401，在线列表 admin 只有 1 行
多端：参数改 true → 两个 Token 都可用，列表至少 2 行
切回 false：下一次登录后旧多端会话被清理
停用账号 → 旧 Token 立即失效；重新启用可登录
管理员重置密码 → 旧 Token 立即失效，新密码可登录
删除用户 → 旧 Token 立即失效
```

清理后 `sys_user` / `sys_role` / `sys_dept` 各 1 条，`sys_role_dept` 0 条，
参数恢复 `false`，Flyway 当前 V16。

### 44.5 取舍

| 问题 | 说明 |
|---|---|
| 为什么不直接 `is-concurrent: false` | 那是启动配置，且会把“允许多端”做成不可运行期切换；现在的实现参数改完即可生效 |
| 为什么默认踢旧会话 | 后台管理以安全为先，一人一个有效会话；确实需要手机 + 电脑同时用再开参数 |
| 多端模式下列表还按 Token 吗 | 是。多端时每个会话来源不同，按 Token 才能单独强退；单会话时自然只有 1 行 |
| 删除 / 停用为什么不靠 Token 过期 | 12 小时窗口太长，且删人后列表出现空白账号，运维无法判断来源 |

### 44.6 下一项

`docs/13` §12.3：L2 多语言设计（只写方案，v1.2 再实现）——
`i18n_language` / `i18n_key` / `i18n_text` 三张表与回退链，先评审再排期。

---

## 45. 第二十五轮：参数名乱码修复（i18n 键解析）

### 45.1 现象

参数设置页 `sys.account.maxRetryCount` 的参数名显示成 `????????`；
切到英文后，其他参数名也仍是中文。

### 45.2 两个根因

1. **数据被写脏**：V14 的种子数据本身是正确的 UTF-8 中文，但迁移应用后 28 秒，
   一个验证脚本通过 `PUT /api/admin/system/config/7` 改值，请求体不是 UTF-8，
   把 `config_name` / `remark` 覆盖成了问号（`update_by=1` 与 `update_time` 可佐证）。
   同一条 INSERT 里的另外两行正常，所以不是迁移文件编码问题。
2. **i18n 键写法错**：语言包写成了 `config: { 'sys.account.maxRetryCount': '…' }`，
   而 vue-i18n 把 `t()` 的 key 按点号拆成路径。`config.sys.account.maxRetryCount`
   找不到，`te()` 返回 `false`，`configLabel()` 静默回退数据库原文。
   也就是说 10 个参数名其实**一直没有走翻译**，英文界面下也是中文。

### 45.3 修复

| 文件 | 改动 |
|---|---|
| `V17__fix_config_encoding.sql` | 按 `config_key` 恢复 `config_name` / `remark`，可重复执行 |
| `zh-CN.js` / `en-US.js` | `config` 改为嵌套结构，10 个参数名中英都可解析 |
| `scripts/check-i18n-keys.mjs` | 新增 `dotted-leaf` 检测：叶子键自身带点直接判失败 |
| `deploy/sql/lingxiao-1.0.0.sql` | 按 V17 重新生成：24 张表、V1~V17 迁移记录、参数名已修正 |

浏览器回归 `.local/uitest/config-label-check.mjs` 3/3：

```text
中文：参数名 = 登录失败锁定阈值，无问号
英文：参数名 = Failed-login lock threshold
```

### 45.4 取舍

| 问题 | 说明 |
|---|---|
| 为什么不给 `createI18n` 加 `flatJson: true` | 一行能修，但会改变全局键解析行为；改成嵌套是 vue-i18n 的标准写法，影响面最小 |
| 为什么 V17 不改 `update_by` | 迁移只修数据，不伪造操作人；真实改动人已由 `update_by=1` 保留 |
| 参数名到底以哪边为准 | 页面显示以 i18n 为准，数据库文本仅作缺项回退；L2 再统一迁到 `i18n_*` 表 |

---

## 46. 第二十六轮：品牌名与版本号收口到后端配置

### 46.1 背景

“凌霄后台”“v1.0”“1.0.0”原来散落在侧边栏、登录页、版权、浏览器标题、
Swagger 元信息里。私有化部署想换个名字，要同时改前端语言包和后端配置，
前端还得重新 `vite build`，很容易漏改、也容易前后端版本对不上。

### 46.2 为什么选后端配置

| 方案 | 问题 |
|---|---|
| 前端 `.env` | 构建期常量，改完必须重新打包；Swagger / 启动日志仍是后端版本，两边会漂移 |
| `sys_config` 参数表 | 换库 / 清库 / 导快照会丢；登录页未登录就要显示品牌，不适合依赖业务参数 |
| **后端 `application.yml`** | 版本天然属于制品；Swagger / 接口 / 前端共用一个值；部署时可用环境变量覆盖，换品牌不用重新构建前端 |

### 46.3 落地

| 文件 | 改动 |
|---|---|
| `application.yml` | 新增 `lx.app.name` / `lx.app.name-en` / `lx.app.version: '@project.version@'`；Swagger 标题、版本、Knife4j 页脚改为引用同一配置 |
| `AppProperties` / `AppProfile` | 配置绑定与名片 DTO；中英文名互相兜底，空白值 trim |
| `AppProfileController` | `GET /api/admin/app/profile`，返回名称 + 版本，不含环境信息 |
| `SaTokenConfigure` | 放行 `/api/admin/app/profile`，登录页未登录也能读 |
| `api/app.js` + `store/app.js` | 启动时拉一次名片（`silent`，失败静默）；Pinia 缓存 |
| `utils/brand.js` | `appName()` 按当前语言取中/英文名，`appVersion()` 取版本；组件不再写死品牌 |
| `Sidebar.vue` / `login/index.vue` / `App.vue` / `router/guard.js` | 品牌、页脚版本、版权、浏览器标题统一走 `brand.js` |
| `zh-CN.js` / `en-US.js` | `brandSub` 增加 `{version}` 占位，新增 `brandSubNoVersion`；版权改 `{year}` / `{name}` 占位；首页欢迎语改 `{app}` 占位，登录/公告/任务文案去掉写死的 `v1.0` |
| `deploy/config/application-prod.yml` | 增加 `lx.app.*` 覆盖示例 |

版本仍只有一个入口：根 `pom.xml` 的 `<version>`。构建时
`spring-boot-starter-parent` 把 `@project.version@` 替换进打好的 jar；
发布新版本不需要动前端，也不需要改 `application.yml`。

### 46.4 验证

```text
GET /api/admin/app/profile（不带 Token）
  {"name":"凌霄后台","nameEn":"LingXiao Admin","version":"1.0.0-SNAPSHOT"}
GET /api/admin/system/config/list（不带 Token）  仍为 1002，未放大放行范围
/v3/api-docs  info.title=凌霄后台管理接口  info.version=1.0.0-SNAPSHOT
命令行覆盖 --lx.app.name / --lx.app.name-en / --lx.app.version   名片立即变化
brand-check.mjs   11/11（中英文名称、版本、版权、侧栏、欢迎语、浏览器标题）
smoke.mjs         22/22      theme.mjs 12/12      config-label-check.mjs 3/3
mvn test          99 项 / 20 个类（新增 AppProfileTest 4 项）
check-i18n-keys   zh=903 en=903  dotted-leaf=none
vite build        通过
```

### 46.5 取舍

| 问题 | 说明 |
|---|---|
| 前端为什么还要读接口 | 不读就只能写死；多一次轻量 GET（每会话一次），失败时静默回退语言包，不阻塞登录 |
| 为什么开发环境显示 `v1.0.0-SNAPSHOT` | 版本直接来自 pom，不手工截断；正式构建把版本改成 `1.0.0` 即显示 `v1.0.0` |
| 中英文名算不算两处 | 同一个 `lx.app` 配置块里的两个字段，属于一份配置；英文缺省时自动用中文名兜底 |
| 为什么不做成参数设置项 | 品牌 / 版本是制品与部署属性，不是运行期业务参数；参数表清空不应影响登录页 |

---

## 47. 第二十七轮：发布包演练 + 英文界面截图

### 47.1 做了什么

把“本地能跑”推进到“发出去的包能跑”：

1. 重新构建 jar 与前端 dist，用 `deploy/package.ps1` 组装发布包；
2. 解压到临时目录，用 `application-prod.yml` + 空白数据库启动；
3. 逐项验证登录、参数、在线用户、上传、生产禁用项；
4. 用最小静态服务器 + `/api` 反向代理模拟 Nginx，跑浏览器端关键路径；
5. 补 12 张英文界面截图，`README.en.md` 全部换用英文图。

### 47.2 打包

```powershell
mvn -q "-DskipTests" package          # lx-boot/target/lingxiao.jar ≈ 52 MB
cd lx-ui-admin; npm run build         # dist/index.html
powershell -ExecutionPolicy Bypass -File deploy\package.ps1 -Version 1.0.0-SNAPSHOT
```

产物：`release/lingxiao-1.0.0-SNAPSHOT/` 与同名 `.zip`。目录内容：

```
bin/  config/  data/  logs/  nginx/  run/  sql/  web/
lingxiao.jar  lingxiao.service  README.md
```

> 打包时 PowerShell 会打印本机 `fnm` 初始化失败（`fnm_multishells` 权限），
> 那是终端配置噪音，脚本本身退出码为 0，包内容完整。

### 47.3 干净目录 + prod profile 启动

```sql
-- 空白库（不影响开发库 lingxiao）
CREATE DATABASE lingxiao_drill CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON lingxiao_drill.* TO 'lingxiao'@'%';
```

```powershell
# 解压后用环境变量覆盖，不把密码写进 yml
$env:DB_HOST='127.0.0.1'; $env:DB_PORT='3307'; $env:DB_NAME='lingxiao_drill'
$env:DB_USER='lingxiao';  $env:DB_PASSWORD='lingxiao123'; $env:SERVER_PORT='8082'
java -jar lingxiao.jar --spring.profiles.active=prod --lx.captcha.enabled=false
```

结果：Flyway 在空白库依次应用 **V1 ~ V17**，无校验失败；应用正常启动，
日志落在包内 `logs/lingxiao.log`，上传目录落在包内 `data/upload/`。

### 47.4 接口验证

| 检查项 | 结果 |
|---|---|
| `GET /api/admin/app/profile`（匿名） | 200，`凌霄后台 / LingXiao Admin / 1.0.0-SNAPSHOT` |
| `POST /api/admin/auth/login` | 200，返回平台 Token |
| 参数列表 | 200，10 条，参数名正常（无乱码） |
| 在线用户 | 200，1 个会话 |
| `POST /api/admin/file/upload` | 200，写入 `data/upload/common/2026/11/...` |
| 文件列表 | 200，能查到刚上传的文件 |
| `GET /v3/api-docs` | **404**（prod 按设计关闭） |
| `GET /api/admin/tool/gen/list` | **404**（prod 按设计关闭生成器） |

### 47.5 模拟 Nginx 的浏览器验收

本机没有安装 Nginx，用约 90 行 Node 脚本承担同样职责：静态文件 + 回退 `index.html`
+ `/api/*` 反向代理到 8082。`drill-check.mjs` 结果 **4/4**：

```
PASS 登录页：release 包读取版本 :: 后台管理基座 · v1.0.0-SNAPSHOT
PASS 首页：prod 后端 + release 前端联调 :: 欢迎使用凌霄后台。当前登录账号 admin
PASS 用户管理：列表渲染 :: 1
PASS 控制台无 error
```

### 47.6 遇到的问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 静态服务器返回 `forbidden` | `path.join(ROOT, '/login')` 里的前导 `/` 被当成根路径 | 改为 `path.resolve(ROOT, '.' + urlPath)` 并保留前缀校验 |
| 代码生成页截图为空 | 开发库还没导入过表 | 通过 `POST /api/admin/tool/gen/import` 导入 `sys_notice` 后重拍 |
| 登录页英文默认账号文案挤压 | 英文比中文长，“记住我 / 默认账号”挤在一行 | `.login-extra` 允许换行，右侧文案 `margin-left:auto` 右对齐 |

### 47.7 英文截图

`13-login-en.png` ~ `24-theme-drawer-en.png` 共 12 张，全部在 `en-US` 下真实登录后截取：

登录 / 首页 / 用户 / 角色 / 参数 / 字典 / 生成器 / 服务监控 / 定时任务 / 文件中心 / 暗色主题 / 主题抽屉。

`README.en.md` 主表和折叠区已全部换成英文图，并说明：
界面文案是英文，**业务数据（任务名、备注等）仍是录入时的语言**，内容级翻译属于 L2。

### 47.8 验收

```
deploy/package.ps1                退出码 0，zip 内容完整
空库 Flyway                        V1~V17 全部 applied
prod 接口                         8/8 符合预期（含 2 个按设计 404）
drill-check.mjs                   4/4
英文截图                          12 张，console error = 0
```

### 47.9 取舍与遗留

| 问题 | 说明 |
|---|---|
| 为什么用 Node 模拟 Nginx | 本机没有 Nginx；演练目标是“前后端分离后的真实路径”，静态 + 代理足够覆盖 |
| 发布包里的 `application-prod.yml` 是否可直接上线 | 是示例配置，敏感项全部走环境变量；上线前按 `deploy/README.md` 核对 |
| 回滚怎么做 | 保留上一版目录与数据库备份；Flyway 只前进，回滚用备份库 + 旧包，不写逆向迁移 |
| 真实 Nginx 验证 | 仍建议在一台有 Nginx 的 Linux 机器上用 `deploy/nginx/lingxiao.conf` 再走一遍 |

---

## 48. 第二十八轮：模块插拔最后一公里（v1.1 CMS 前置）

### 48.1 为什么先做这一轮

`docs/20-cms-embed-plan.md` 定的是“加依赖 + 开开关 + 重启 = 可用”。但 v1.0 还有三个洞：

1. `sys_menu.module_code` 列从 V1 就有，定时任务 / 代码生成却一直挂在 `system` 下，
   关闭模块后菜单仍然下发；
2. Flyway `locations` 写死四个目录，模块关闭仍会扫描它的迁移；
3. `/api/admin/modules` 接口早就存在，前端从未调用，路由只认菜单。

不补这三处，CMS 一关闭就会露馅。本轮只动基座，不写任何 `cms_*` 代码。

### 48.2 改动清单

| 文件 | 改动 |
|---|---|
| `lx-common-core/.../module/ModuleProvider.java` | 新增：模块启用状态 SPI，避免系统模块反向依赖 `lx-framework` |
| `lx-framework/.../module/ModuleRegistry.java` | 实现 SPI；`system` / `file` 永远启用，未配置模块按关闭 |
| `lx-framework/.../module/ModuleMigrationLocations.java` | 新增：按开关拼装 Flyway 目录，目录不存在自动跳过 |
| `lx-framework/.../config/LxFrameworkAutoConfiguration.java` | 注册 `FlywayConfigurationCustomizer` |
| `lx-framework/pom.xml` | 加 `flyway-core`（optional）+ `spring-boot-starter-test`（test） |
| `lx-module-system/.../service/AuthService.java` | 菜单按模块过滤；空目录剪枝；权限码同样过滤 |
| `lx-module-system/.../security/StpInterfaceImpl.java` | 权限码按模块过滤 |
| `lx-module-system/.../mapper/SysMenuMapper.java` | `selectPermsByUserId` 多返回 `module_code` |
| `lx-module-system/.../dto/MenuPermRow.java` | 新增：权限码 + 模块的最小投影 |
| `lx-api/.../dto/MenuVO.java` | 新增 `moduleCode`，前端可做二次校验 |
| `lx-module-system/.../db/migration/system/V18__menu_module_code.sql` | 定时任务 → `job`，代码生成 → `generator` |
| `lx-boot/src/main/resources/application.yml` | 去掉写死 `locations`；加 `out-of-order` 与 `ignore-missing-migrations` |
| `lx-ui-admin/src/api/module.js` | 新增：`GET /api/admin/modules` 封装 |
| `lx-ui-admin/src/store/app.js` | 新增 `modules` / `moduleCodes` 与 `loadModules()` |
| `lx-ui-admin/src/router/guard.js` | 登录后并行拉用户信息与模块开关 |
| `lx-ui-admin/src/router/dynamic.js` | 按启用模块二次过滤路由，`meta.moduleCode` 落库 |

### 48.3 判定规则

| `module_code` | 结果 |
|---|---|
| `system` / `file` | 永远启用，即使配置里漏写或写 `false` |
| 配置了 `lx.module.modules.<code>.enabled=true` | 启用 |
| 配置了但 `false` | 关闭 |
| 没有配置（如移除依赖后的 `cms`） | 关闭，库里残留菜单不再下发 |
| 空 / null | 启用，兼容没有模块归属的旧数据 |

菜单过滤后还要**剪掉空目录**：`系统工具` 属于 `system`，子节点全被过滤时
目录本身也不能留，否则侧边栏会出现一个点不开的空白分组。

### 48.4 Flyway 的两个开关

```yaml
spring:
  flyway:
    out-of-order: true
    ignore-missing-migrations: true
```

- `out-of-order`：模块可能在基座升级后才启用，它的迁移版本号会小于当前最大版本
  （例如 V11 在 V18 之后启用）；不打开会被 Flyway 拒绝。
- `ignore-missing-migrations`：模块关闭后，它已执行的迁移在 classpath 里“消失”，
  默认校验会失败；打开后保留历史记录、跳过缺失校验。

**踩坑**：一开始写的是 `ignore-migration-patterns: "*:missing"`，启动直接报
`FlywayTeamsUpgradeRequiredException: ignoreMigrationPatterns is not supported by
Flyway Community Edition`。`ignore-migration-patterns` 是 Teams 能力，
Community 只能用旧的 `ignoreMissingMigrations`。

### 48.5 验证

**A. 单测**：`mvn test` → **112 项 / 23 个类**（JDK 8 与 JDK 11 各跑一遍，均通过），新增：

- `ModuleRegistryTest` 5 项：基座永远启用、关闭 / 未配置、空 `module_code`、列表排序、列表与 `isEnabled` 一致；
- `ModuleMigrationLocationsTest` 3 项：固定目录、启用排序、目录不存在跳过；
- `StpInterfaceImplTest` 5 项：域隔离、超管、关闭模块过滤、无注册表、空权限码。

**B. 迁移**：启动日志 `Migrating schema lingxiao to version "18 - menu module code"`，
`flyway_schema_history` 最新为 V18；菜单归属变为 `system 47 / file 3 / job 8 / generator 6`。

**C. 关闭 job 模块**（`--lx.module.modules.job.enabled=false`）：

| 检查项 | 结果 |
|---|---|
| Flyway | 只扫描 system / file / generator；`Successfully validated 18 migrations` |
| `/api/admin/modules` | `job=false`，其余为 true |
| 超管菜单 | 无 `tool:job` / `monitor:job:*`；“系统工具”仍有文件 / 生成器 / 接口文档 |
| 普通角色 | 临时用户只授权 `system:user` + `tool:job` + `tool:gen`，`/auth/info` 只返回前两者的权限，job 被过滤 |
| 接口 | `/api/admin/monitor/job/list` → 404；`/api/admin/tool/gen/list` → 200 |
| 前端 | 侧边栏无“定时任务”；直接访问 `/tool/job` 落 404；`/tool/gen` 正常 |

**D. 重新开启**：菜单与接口恢复，V18 不重复执行，数据完整。

**E. 回归**：`smoke.mjs` 22/22、console error 0；`npm run build` 通过。

### 48.6 取舍与遗留

| 问题 | 结论 |
|---|---|
| 运行期热插拔 | 不做；模块开关仍是启动期生效，改完必须重启 |
| 菜单管理页是否过滤 | 不过滤；管理员需要看到关闭模块的菜单并预配置，侧边栏 / 权限接口才过滤 |
| `module.yaml` 依赖解析 | 未做；`requires` / `optional` 仍靠人工保证，v1.1 先约定 CMS 依赖 system + file |
| 前端构建裁剪 | `VITE_MODULES` 后置；v1.1 先保证运行期路由正确 |
| 在线用户 `KEYS` | 与本轮无关，仍是切 Redis 前的小优化（见 `docs/13` §12.6 的缓存计划） |

---

## 49. 第二十九轮：v1.1 CMS 第一刀（2026-11-22）

### 49.1 本轮目标

按 `docs/20` §7 把 CMS 从“文档”变成“能用的后台模块”，同时收掉上一轮的
头像自助编辑与第三方登录 schema 预留。

范围只到**后台维护链路**：站点、栏目（树）、文章（草稿 / 发布 / 下线）、标签。
门户 API、内容版本、回收站、素材选择器、内容级多语言明确后置。

### 49.2 改动清单

| 文件 / 目录 | 改动 |
|---|---|
| `lx-modules/lx-module-cms/` | 新模块：4 Controller / 4 Service / 4 实体 / 6 Mapper |
| `lx-modules/pom.xml` | 加入 `<module>lx-module-cms</module>` |
| `lx-dependencies/pom.xml` | `dependencyManagement` 增加 CMS 模块 |
| `lx-boot/pom.xml` | 增加 CMS 模块依赖 |
| `lx-boot/.../application.yml` | 新增 `lx.module.modules.cms`（`enabled=true`） |
| `V20__cms_schema.sql` | `cms_site` / `cms_category` / `cms_category_closure` / `cms_article` / `cms_tag` / `cms_article_tag` |
| `V21__cms_menu.sql` | 菜单 `3000/3010/3020/3030/3040` + 15 个按钮权限，`module_code='cms'` |
| `lx-common-core/.../PageResult.java` | 补 `of(...)`，避免 CMS 自己拼分页结构 |
| `lx-common-web/.../HtmlSanitizer.java` | 从 `lx-module-system` 上移，公告与 CMS 共用 |
| `lx-module-system/pom.xml` | 移除 `jsoup`（改由 `lx-common-web` 携带） |
| `lx-ui-admin/src/api/cms.js` | CMS 接口封装 |
| `lx-ui-admin/src/views/cms/` | 站点 / 栏目 / 文章 / 标签四个页面 |
| `lx-ui-admin/src/i18n/{zh-CN,en-US}.js` | `menu.cms:*` + `page.cms.*` 中英 |
| `docs/28-third-party-login.md` | 第三方登录设计边界（本轮只预留 schema） |
| `V19__system_user_social.sql` | `sys_user_social`（provider / open_id / union_id / 昵称 / 头像 / 原始数据） |
| `lx-api/.../ProfileUpdateRequest.java`、`AuthService` | 个人中心支持改头像，`normalizeAvatar` 拒绝非 `http(s)` 协议 |
| `FileController` / `api/file.js` / `profile/index.vue` | 头像上传 / 预览 / 移除 |

### 49.3 关键实现

**A. 栏目闭包**
`parent_id` 是唯一事实来源；`cms_category_closure` 每次按站点全量重建。
文章按父栏目筛选时用闭包子查询拿整棵子树，避免递归 SQL。
写入前校验父栏目属于同一站点、且不能把自己挂到自己的子孙下。

**B. 文章状态**
新建一律草稿（请求里的 `status` 不采纳）；编辑不改状态；
发布 / 下线是独立接口与独立权限码。发布写 `publish_time`，下线保留历史值。

**C. 清洗与读取**
正文写入与读取两侧都过 `HtmlSanitizer`。列表 SQL 不查 `content`，只有详情返回，
避免列表页把大字段全部拖出来；预览抽屉展示的是详情接口清洗后的内容。

**D. 标签**
`(site_id, tag_key)` 唯一；文章只允许挂同站点标签；
删除标签前查 `cms_article_tag`，有引用就拒绝。

**E. 删除保护**
站点（仍有栏目 / 文章）、栏目（仍有子栏目 / 文章）、标签（仍被引用）全部在服务层拦截，
不依赖跨模块外键。文章删除先删关联再删正文。

**F. 权限与模块开关**
四个 Controller 都带 `@ConditionalOnProperty(lx.module.modules.cms.enabled)`；
权限码走 `platform:cms:*`，菜单 `module_code='cms'`，关闭后菜单 / 权限 / 接口一起下线。

### 49.4 验证

**A. 编译与单测**

- `mvn -q -DskipTests package` 通过；
- 新增 CMS 单测 14 项（闭包深度、成环、跨站点、删除保护、读取侧清洗、删除顺序）；
- 全仓 **126 项 / 26 个测试类**，JDK 8 与 JDK 11 各跑一遍，全部通过。

**B. 启动与迁移**

- 启动日志出现 `Migrating schema lingxiao to version "19 - system user social"`、
  `"20 - cms schema"`、`"21 - cms menu"`；
- 超管 `/auth/menus` 出现“内容管理”及四个子菜单；`/auth/info` 返回 `*:*:*`。

**C. 真实接口 34/34**

覆盖：站点 CRUD 与标识重复、栏目树、深层栏目、成环拒绝、跨站点父级拒绝、
标签 CRUD、文章强制草稿、`<script>` 与 `javascript:` 清洗、标签回填、
父栏目筛子栏目、标签筛选、发布写时间、下线、被引用不可删、逐级清空后删站点。

**D. 浏览器 13/13**

登录 → 侧边栏出现“内容管理” → 站点列表 / 新增 → 栏目树 / 新增弹窗 →
标签列表 / 新增 → 文章列表 → 预览（服务端清洗后的正文）→ 发布 → 删除 → 清理。
截图留在 `.local/uitest/cms-shots/`（不提交）。

**E. 关闭 / 开启**

- 关闭：`/api/admin/cms/site/list` 404；菜单不含 `cms:*`；直接访问 `/cms/site` 落 404；
- 重新开启：关闭前创建的站点仍在，菜单与接口恢复。

**F. 前端**

`node scripts/check-i18n-keys.mjs`：中英各 1013 键，无 missing / dotted-leaf；
`npm run build` 通过。

### 49.5 踩坑

| 问题 | 处理 |
|---|---|
| 权限验收误判 | 超管返回的是 `*:*:*`，不是展开后的权限码；测试要按通配符判断 |
| 分页 `total` 是字符串 | `PageResult` 里 `total` 为 `long`，JSON 序列化后仍可能是字符串，断言先 `Number()` |
| 单端登录踢掉脚本 Token | 浏览器登录会按单会话策略踢掉接口脚本的 Token，清理数据前需重新登录 |
| 弹窗内输入框定位 | 页面可能有隐藏的同 placeholder 输入框，测试脚本把查找范围收敛到 `.el-dialog` |
| 栏目页 i18n 键写错 | 页面误用 `page.cms.category.selectSiteFirst`，检查脚本报 missing，统一改为 `page.cms.common.selectSiteFirst` |
| 删除顺序 | 文章 → 标签 → 孙栏目 → 子栏目 → 根栏目 → 站点，任一上层还有引用都会拒绝 |

### 49.6 遗留

| 项 | 说明 |
|---|---|
| 素材选择器 | Logo / 封面仍是 `fileId 或 URL` 文本输入；上传权限与 CMS 编辑权限耦合，方案待定 |
| 门户 API | `/api/portal/cms/**` 未实现，发布后的文章暂无对外读取入口 |
| 内容版本 / 回收站 | 未做，删除是物理删除（有引用保护） |
| 默认授权 | CMS 菜单只授权给超管角色，其它角色需手工分配 |
| 前端构建裁剪 | `VITE_MODULES` 仍后置，运行期按后端模块开关控制 |

---

## 50. 第三十轮：CMS 门户 API（2026-11-22）

### 50.1 本轮目标

给 v1.1 CMS 补上“发布之后有人读”的一侧：`/api/portal/cms/**` 公开只读接口。
不做门户工程、不做缓存、不做模板；先把接口契约、可见性与字段边界定死。

范围：站点、栏目导航树、标签、已发布文章分页 / 详情 / slug / 搜索。

### 50.2 改动清单

| 文件 | 改动 |
|---|---|
| `lx-module-cms/.../controller/CmsPortalController.java` | 新增 8 个门户只读接口，`@ConditionalOnProperty(cms.enabled)` |
| `lx-module-cms/.../service/CmsPortalService.java` | 可见性判断、字段白名单、阅读量、子树 / 标签筛选 |
| `lx-module-cms/.../dto/portal/*.java` | 5 个门户 VO，不复用后台实体 |
| `lx-module-cms/.../dto/ArticleTagRow.java` | 标签关联查询行，供列表批量回填 |
| `CmsArticleMapper` | 新增 `increaseViewCount` |
| `CmsArticleTagMapper` | 新增 `selectRowsByArticleIds`，避免一页文章逐篇查标签 |
| `CmsPortalServiceTest` | 新增 8 项单测 |
| `docs/29-portal-api.md` | 新增门户接口契约（路径、参数、可见性、字段、缓存建议） |
| `docs/20` §7 / §11 / §14.4 / §15 | 状态、验收清单、门户落地记录 |
| `lx-modules/lx-module-cms/README.md` | 补门户接口与开关说明 |

没有新增依赖、没有新增表、没有新增迁移；门户接口完全复用 `cms_*` 现有结构。

### 50.3 关键实现

**A. 三重可见性自己兜**
`/api/portal/**` 不在 Sa-Token 拦截路径里，公开访问。Service 必须自己保证：

- 站点启用（`status=0`）；
- 文章已发布（`status=1`）；
- 文章所属栏目启用，且**所有祖先栏目都启用**。

第三条最初只判了直接栏目，接口验收立刻抓出“停用父栏目下的文章仍可读”；
补成闭包判断后列表与详情行为一致。

**B. 列表 / 详情分工**
列表 `select` 排除 `content`，只回摘要、栏目名与标签；详情才回正文，
并在返回前再过一次 `HtmlSanitizer`。这样既不拖大字段，也不信任库里历史数据。

**C. 字段白名单**
5 个 `Portal*VO` 手写映射，不下发 `status / delFlag / createBy / remark / version`。
后续后台实体加字段不会意外从公开接口漏出去。

**D. 筛选条件的站点校验**
`categoryKey / tagKey` 都先按 `siteKey` 解析站点，再校验栏目 / 标签属于该站点且启用；
A 站的 key 不能拿来筛 B 站。slug 同样只在站点内唯一。

**E. 隐藏与停用的区别**
停用栏目：整棵子树从导航与文章列表里消失。
隐藏栏目：本身不进导航，可见子栏目上提；它的文章仍可通过列表 / 详情访问。

**F. 分页与关键字上限**
`pageSize` 上限 50；`keyword` 超 50 字符截断；避免公开接口被一页拉空或超长 LIKE。

**G. 阅读量**
`UPDATE cms_article SET view_count = view_count + 1`，单条语句自增，
不先查后写。详情返回 `当前值 + 1`，与数据库保持一致的近似值。

### 50.4 验证

**A. 编译与单测**

- `mvn -q -DskipTests package` 通过；
- 新增 `CmsPortalServiceTest` 8 项：草稿 / 跨站点 / 停用栏目 / 停用祖先 / 正文清洗 + 阅读量 /
  导航树剔除与提升 / 空 slug / 站点字段白名单；
- 全仓 **134 项 / 28 个测试类**，JDK 8 与 JDK 11 各跑一遍全部通过（clean test）。

**B. 真实接口 38/38**

`.local/portal-api-check.mjs` 覆盖：

- 站点信息与启用站点列表；
- 导航树：可见栏目、隐藏子栏目上提、隐藏栏目本身不出现、停用栏目整棵子树剔除；
- 标签：只出启用；
- 文章列表：已发布可见、草稿 / 下线 / 跨站点 / 停用栏目（含父栏目停用）不可见；
- 列表不返回 `content`，带栏目名与标签；
- 栏目子树筛选、`categoryKey`、`tagKey`、标题搜索；
- 详情按 ID / slug，正文清洗，阅读量递增；
- 停用站点 / 未知站点 / 草稿 / 下线 / 跨站点 / 停用栏目详情统一 1004；
- `pageSize=1000` 被压到 50；
- 验收后逐级清理，`cms_*` 无残留。

**C. 关闭态 5/5**

`--lx.module.modules.cms.enabled=false` 启动后：

- `/admin/modules` 里 `cms.enabled=false`（模块仍列出，只是未启用）；
- `/api/portal/cms/sites` 与 `/api/portal/cms/sites/{key}/articles` 均 404；
- `/api/admin/cms/site/page` 404；
- 重新开启后门户接口恢复，关闭态没有产生额外迁移。

**D. 前端**

门户接口无前端改动；后台四个 CMS 页面与 i18n 未变，不需要重跑构建。

### 50.5 踩坑

| 问题 | 处理 |
|---|---|
| 只判文章直接栏目 | 停用父栏目下的文章仍出现在列表 / 详情；列表补闭包 `NOT EXISTS`，详情与筛选补 `isCategoryVisible` |
| 单测状态位写反 | “停用栏目”用例把 `status/visible` 传反，断言拿到 null；修正为 `status=1` |
| 关闭态模块列表 | 关闭后 `/admin/modules` 仍返回 `cms`，只是 `enabled=false`；验收要判字段，不要判“列表里没有” |
| 后台进程被 shell 回收 | `Start-Process` 启动的 java 偶尔随父 shell 结束；改用工具的后台任务方式，稳定拿到 PID 与日志 |
| 旧 surefire 报告 | 上移 `HtmlSanitizerTest` 后旧模块 `target` 仍有报告，统计会多 9 项；用 `mvn clean test` 复核 |

### 50.6 遗留

| 项 | 说明 |
|---|---|
| 缓存 / 限流 | 未做；详情阅读量可被脚本刷高，上线前补限流或异步统计 |
| 闭包一致性 | 手工改库改栏目状态不会自动重建闭包；需要走一次后台栏目编辑 |
| 素材选择器 | 已在 §51 补齐；Logo / 封面仍兼容 `fileId` 与 URL，门户两种都要处理 |
| 内容生命周期 | 定时发布、版本、回收站仍未做 |
| 门户工程 | Nuxt3、SEO、sitemap、ES、CDN 属 v1.3 |

---

## 51. 第三十二刀：CMS 素材选择器（2026-11-22）

### 51.1 背景与目标

上一刀（§49）里站点 Logo、栏目封面、文章封面还是“`fileId` 或 `https://` 地址”
纯文本输入。用户要么自己去文件中心翻 ID，要么把绑定当前域名的绝对地址写进业务数据；
换域名、换环境后图片全部失效。这一刀把三处字段统一接到文件中心图片库。

**目标**：

- 选图即可用，落库只存 `fileId`，不存当前环境域名；
- 存量 `http(s)` 外链继续可用，不强制迁移；
- 上传 / 浏览按文件中心权限走，没有文件权限的 CMS 编辑仍能手工填外链；
- 不改变门户契约：门户继续原样返回字段值，由消费端解析。

### 51.2 代码落点

| 层 | 文件 | 改动 |
|---|---|---|
| 组件 | `lx-ui-admin/src/components/MediaPicker.vue` | 新增：预览 / 输入 / 选择 / 上传 / 清除 |
| 工具 | `lx-ui-admin/src/utils/asset.js` | 新增 `resolveAssetUrl`，统一 fileId 与外链解析 |
| 后端 | `FileController.list` | 新增可选参数 `imageOnly` |
| 后端 | `FileService.list` / `IMAGE_EXTENSIONS` | `imageOnly=true` 时按图片扩展名过滤 |
| 页面 | `views/cms/site/index.vue` | Logo 换成 `MediaPicker` |
| 页面 | `views/cms/category/index.vue` | 封面换成 `MediaPicker` |
| 页面 | `views/cms/article/index.vue` | 封面换成 `MediaPicker` |
| 会话 / 头像 | `layout/Navbar.vue`、`views/profile/index.vue`、`views/system/user/index.vue` | 头像统一走 `resolveAssetUrl` |
| 多语言 | `i18n/zh-CN.js` / `en-US.js` | 新增 `mediaPicker.*` |

### 51.3 关键设计

**A. 只写 `fileId`，不写绝对地址**

选择器 `choose()` 与上传成功都只 `emit(String(file.fileId))`。
字段里因此不会出现 `http://127.0.0.1:8080/api/file/...` 这种环境相关地址；
门户 / 前台拿到 `fileId` 后自行拼公开访问前缀。

**B. 兼容存量外链，并在失焦时归一**

输入框允许手工粘贴。`handleBlur()` 用
`/\/api\/file\/(\d+)(?:[?#].*)?$/` 识别“页面内文件地址”，归一成纯 `fileId`；
`https://cdn.example.com/a.png` 这类外链保持原样，不会被误改。

**C. 地址解析抽公共函数**

`resolveAssetUrl(value, { size })`：

- 空白 → `''`；
- `http://` / `https://` / `data:` / `blob:` → 原样返回；
- 纯数字 → `/api/file/{id}`，传 `size` 时追加 `?size=thumb|medium`；
- 其它相对地址 → 原样返回。

顺手修掉系统用户列表的老问题：外链头像以前会被拼成 `/api/file/http...`，
现在三个页面共用同一套判断。

**D. 权限边界**

- “选择素材”按钮 `v-hasPerm="'platform:file:list'"`；
- “上传”按钮 `v-hasPerm="'platform:file:upload'"`；
- 两个按钮都只是隐藏，输入框始终存在；没有文件权限的角色仍可手填外链，
  不会因为 CMS 权限与文件权限耦合而完全无法编辑。

**E. 服务端 `imageOnly` 过滤，而不是前端过滤当前页**

只在 `files` 上做前端 `filter` 会让非图片文件占满分页，出现“第 1 页 0 张图、
第 2 页才有图”的假空状态。因此列表参数下沉到后端：

```sql
-- MyBatis-Plus LambdaQueryWrapper，实际为 IN 条件
ext IN ('jpg','jpeg','png','gif','bmp','webp','svg')
```

- 只影响列表筛选，不改上传白名单；
- 不传 / `false` 时行为与 v1.0 完全一致，文件中心页面无需改动；
- 前端保留一次轻量兜底过滤（ext 或 `image/*`），防止历史数据 ext 为空。

**F. 上传后自动选中**

`doUpload()` 走既有 `uploadFile(file, { bizType })`，成功后直接写回 `fileId`
并关闭弹窗，省掉“上传完还要再找一遍”的步骤。`beforeUpload` 先挡非图片，
减少一次无意义请求。

### 51.4 验证

**A. 前端**

- `node scripts/check-i18n-keys.mjs`：zh=1022、en=1022，无 missing / 单边；
- `npm run build` 通过；
- 后端 `mvn -q -DskipTests -pl lx-modules/lx-module-file -am compile` 通过。

**B. 接口 16/16**

`.local/media-picker-check.mjs`：

- 登录、文件列表、`imageOnly` 列表可用；
- `imageOnly` 结果全部是图片扩展名，且总数不大于全量；
- 上传 1×1 PNG 后能按文件名在 `imageOnly` 下查到，`fileId` 不变；
- 不带 `imageOnly` 也能查到同一张图（证明过滤没动数据）；
- 上传 `.txt` 后，`imageOnly` 下查不到，不带过滤能查到；
- 用 `fileId` 建站点 → 后台详情回读一致 → 门户站点接口返回同一 `fileId` → 清理。

> 文件中心按 SHA-256 秒传：同一张 1×1 PNG 重复上传会返回**旧文件与旧文件名**，
> 脚本因此改用返回的 `originalName` 查询，而不是自己拼 remark。

**C. 浏览器 6/6**

`.local/uitest/media-picker.mjs`：

- 管理员登录，进入站点列表并打开编辑弹窗；
- 弹窗渲染出素材选择器；
- 打开选择弹窗，列出图片卡片（6 张）；
- 点击卡片后输入框写入纯 `fileId`；
- 预览 `src` 解析为 `/api/file/{id}?size=thumb`；
- 保存后后台详情回读到同一 `fileId`。

截图：`media-picker-dialog.png`（图片库弹窗）、`media-picker-selected.png`（选中后的预览）。

**D. 回归**

`mvn clean test`：JDK 8 / JDK 11 各跑一遍，134 项 / 28 类，0 失败 0 错误；
后台 CMS 34/34、门户 38/38、CMS 浏览器 13/13 不受影响。

### 51.5 踩坑

| 问题 | 处理 |
|---|---|
| 秒传导致“查不到新图” | 重复上传返回旧文件名；验收改用接口返回的 `originalName` 查询 |
| 分页总数是字符串 | 后端 `PageResult.total` 序列化为字符串，脚本统一 `Number(...)` 再比较 |
| 浏览器登录踢掉 API 会话 | 单会话模式下浏览器登录会让脚本 Token 失效；保存后重新登录再回读 |
| 弹窗动画期间点击落空 | 等 `.media-card` 出现后再留 700ms，避免点到遮罩 |
| 后端占用 jar 导致 clean 失败 | `mvn clean` 前先停掉 8080 进程，否则删不掉 `lx-boot/target/lingxiao.jar` |

### 51.6 遗留

| 项 | 说明 |
|---|---|
| 文件引用计数 | 选择器只写 `fileId`，不做引用登记；删除文件仍可能留下悬空引用 |
| 上传权限耦合 | 无 `platform:file:upload` 的编辑只能手填外链，属预期取舍 |
| 图片裁剪 / 尺寸建议 | 未做；站点 Logo 与文章封面的推荐尺寸只能靠文档约定 |

---

## 52. CMS 文章回收站（2026-11-22）

### 52.1 背景与范围

素材选择器收口后，进入内容生命周期：**回收站 → 定时发布 → 内容版本**。
第一刀做回收站，范围限定为**文章**：

- 站点 / 栏目 / 标签已有一层引用保护，误删会先被拦下；
- 文章是高频内容，删掉后需要能找回标签、发布时间与归属；
- 复用 `BaseEntity.delFlag`（已经是 `@TableLogic`），不引入第二套删除标记。

交付目标：后台删除文章进回收站、可恢复为草稿、可彻底删除；回收中的文章后台列表与门户
都不可见；站点 / 栏目 / slug 的各种唯一性与删除保护把回收站文章也算进去。

### 52.2 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `V22__cms_article_recycle.sql` | 3 个审计列、`(del_flag, delete_time)` 索引、菜单 3050 + 按钮 3051 / 3052 |
| 实体 | `CmsArticle` | `deleteBy` / `deleteByName` / `deleteTime`，`select=false` 不污染普通查询 |
| VO | `dto/CmsArticleRecycleVO` | 回收站行：标题、slug、站点 / 栏目名、删除前状态、删除人、删除时间 |
| Mapper | `CmsArticleMapper` | `softDeleteById` / `restoreById` / `hardDeleteDeletedById` / `selectDeletedById` / `selectRecyclePage` / 三个含回收站的计数 |
| Service | `CmsArticleService` | `delete` 改软删除；新增 `recyclePage` / `restore` / `purge`；slug 唯一性换口径 |
| Service | `CmsSiteService` / `CmsCategoryService` | 删除保护改用含回收站的计数；站点先查文章再查栏目 |
| Controller | `CmsArticleController` | `GET /recycle/list`、`PUT /{id}/restore`、`DELETE /{id}/purge` |
| 前端 | `api/cms.js` | 三个新方法 |
| 前端 | `views/cms/recycle/index.vue` | 回收站页面；站点筛选、关键字、恢复 / 彻底删除 |
| 前端 | `views/cms/article/index.vue` | 工具栏「回收站」入口；删除确认文案说明会进回收站 |
| 单测 | `CmsArticleServiceTest` 等 | 12 / 4 / 3 项，覆盖软删除、恢复、彻底删除与计数口径 |

迁移里的菜单插入统一写成 `INSERT ... SELECT ... WHERE NOT EXISTS`，不用
`INSERT IGNORE` / `ON DUPLICATE KEY`，MySQL / PostgreSQL / 达梦 / 金仓都能执行。

### 52.3 关键决策

1. **软删除保留标签关联。**
   `delete` 只写 `del_flag='1'` 与删除审计；恢复后标签不用重打。彻底删除时才
   `deleteByArticleId` + 物理删正文，顺序固定。
2. **删除人快照落库。**
   `delete_by` / `delete_by_name` / `delete_time` 一次写完；昵称取 Session，
   取不到回退用户名，再回退 `system`。回收站不跨模块 JOIN `sys_user`。
3. **恢复一律回草稿。**
   保留 `publish_time`（首次发布时间），但不恢复发布状态；避免回收站一恢复就对外曝光。
4. **唯一性 / 删除保护包含回收站。**
   slug 在回收期间仍被占用；站点 / 栏目下存在回收站文章时拒绝删除。否则恢复时
   可能撞 slug 或找不到归属。
5. **恢复前二次校验。**
   站点必须存在、栏目必须仍属于该站点、slug 不能撞其它文章；栏目已删则提示直接彻底删除。
6. **回收站列表不查正文。**
   站点 / 栏目 `LEFT JOIN` 取名字，栏目被手工删掉时仍能显示行；关键字在 Service 里
   转 `%kw%`，SQL 不拼 `CONCAT`。
7. **权限三档。**
   进入回收站 `recycle`、恢复 `restore`、彻底删除 `purge`；角色可以只给恢复不给物理删除。

### 52.4 施工步骤

1. **数据库**
   - `cms_article` 增列 `delete_by` / `delete_by_name` / `delete_time`；
   - 建 `idx_cms_article_del (del_flag, delete_time)`；
   - 插菜单 3050（`/cms/recycle`，组件 `cms/recycle/index`）与按钮 3051 / 3052，
     `module_code='cms'`，并给 `role_id=1` 补齐授权。
2. **后端**
   - `CmsArticle` 增加三个字段并标记 `select=false`；
   - Mapper 写显式 SQL：软删除、恢复、仅回收站可物理删除、按 ID 读回收站记录、
     回收站分页、含回收站的 slug / 站点 / 栏目计数；
   - Service：`delete` 改软删；`restore` 校验站点 / 栏目 / slug 后回草稿；
     `purge` 先清标签再物理删；`recyclePage` 做分页上限与关键字截断；
   - `ensureSlugUnique` 改用 `countBySlugIncludingDeleted`；
   - 站点 / 栏目删除保护改用含回收站计数，站点删除先提示文章、再提示栏目。
3. **前端**
   - `api/cms.js` 增加回收站列表、恢复、彻底删除；
   - 新页面展示站点 / 栏目 / 标题 / 删除前状态 / 删除人 / 删除时间，恢复与彻底删除分开权限；
   - 文章页工具栏加入口，删除确认文案改为“进入回收站，可恢复”；
   - 中英文 `page.cms.recycle.*` 与 `menu.cms:recycle` / `menu.cms:article:restore` /
     `menu.cms:article:purge` 补齐。

### 52.5 验证

**A. 后端**

- `mvn -q -pl lx-modules/lx-module-cms -am test` 通过；
- 全仓 `mvn clean test`：JDK 8 / JDK 11 各一遍，**141 项 / 28 类**，0 失败 0 错误；
- `mvn -q -DskipTests package` 通过，jar 重新打包后后台启动正常。

**B. 接口 33/33**

`.local/cms-recycle-check.mjs`：

- 发布后门户可见 → 删除 → 后台列表 total=0、门户 by-slug 404；
- 回收站列表返回文章，含站点 / 栏目名、删除人、删除时间、删除前状态；
- 关键字命中 / 未命中；
- 站点、栏目删除都被“含回收站文章”拦下；
- 回收期间新文章不能用同一 slug；
- 恢复后为草稿、保留 `publish_time` 与标签、门户仍不可见；重新发布后恢复可见；
- 彻底删除后回收站清空、详情 404、标签可删、slug 可复用；
- 清理站点 / 栏目。

**C. 浏览器 12/12**

`.local/uitest/cms-recycle.mjs`：

- 管理员登录，侧边栏出现「回收站」；
- 回收站列表展示站点 / 栏目 / 删除人 / 删除前状态；
- 关键字搜索保留行，未命中显示空态；
- 恢复后行消失；文章列表里该文章为草稿；
- 文章页删除 → 行从列表消失，确认框提示进入回收站；
- 工具栏「回收站」跳转正确；
- 彻底删除后行消失，后台详情与门户都 404。

截图：`.local/uitest/cms-recycle.png`（回收站列表与操作列）。

**D. 前端**

- `node scripts/check-i18n-keys.mjs`：zh=1039、en=1039，无 missing / 单边 / dotted-leaf；
- `npm run build` 通过。

### 52.6 踩坑

| 问题 | 处理 |
|---|---|
| `@TableLogic` 自动过滤，`selectById` 读不到回收站记录 | 回收站读 / 分页 / 恢复 / 计数全部写自定义 SQL，显式 `del_flag='1'` |
| 站点删除总先报“还有栏目” | 删除校验改为文章优先，验收能直接看到回收站提示；栏目校验保留在后 |
| 浏览器脚本中途重新登录导致页面 401 | 单会话模式会踢掉浏览器 Token；恢复后的状态先在页面断言，API 回读放到流程末尾 |
| Element Plus 2.8 下拉结构变化 | 不再找 `.el-input__inner`，改点 `.el-select__wrapper` 再选可见 option |
| 确认框默认按钮不是“确定” | 先按文案找按钮，找不到再点 `.el-button--primary / --danger` |
| 截图时状态标签像空白 | 实际是截图落在重绘瞬间；单独截行元素确认 `草稿` 标签存在，非缺陷 |

### 52.7 遗留

| 项 | 说明 |
|---|---|
| 回收站覆盖范围 | 仅文章；站点 / 栏目 / 标签仍物理删除 + 引用保护 |
| 保留期 / 自动清理 | 未做；回收站内容需手动彻底删除，后续可加定时清理 |
| 数据范围 | 回收站列表未接数据范围，与当前 CMS 其它列表一致，权限细化时统一处理 |
| 内容生命周期后续 | 定时发布（`publish_time` 扫描）、内容版本仍待做 |
| 门户解析 | 门户仍原样返回 `fileId` / URL，消费端按 `docs/29` 约定拼接 |

---

## 53. CMS 文章定时发布（2026-11-22）

### 53.1 背景与范围

回收站收口后，内容生命周期继续：**定时发布 → 内容版本**。本批做一次性排期：

- 运营可以提前选时间，到点由后台任务自动把文章从“定时待发布”改成“已发布”；
- 不做周期发布、批量排期、定时下线，也不接审核流；
- 不引入 Quartz / 消息队列，沿用 Spring Task + 数据库条件更新。

### 53.2 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `V23__cms_article_schedule.sql` | `schedule_time datetime(3)`、`(status, schedule_time)` 索引、按钮 3053 |
| 实体 | `CmsArticle` | `scheduleTime`；状态注释补 `3 定时待发布` |
| DTO | `CmsArticleScheduleDTO` | 只接 `scheduleTime`，`@NotNull` 使用 `valid.cms.article.schedule.required` |
| Mapper | `CmsArticleMapper` | `scheduleById`（条件 0/2/3）、`cancelScheduleById`（条件 3）、`selectDueScheduledIds`、`publishScheduledById`（条件抢占） |
| Service | `CmsArticleService` | `STATUS_SCHEDULED`、`schedule` / `cancelSchedule` / `publishDueScheduled`；发布 / 下线清定时；创建 / 恢复清定时 |
| 任务 | `task/CmsScheduleTask` | `@Scheduled` 固定间隔扫描，CMS 开启才注册；异常兜底 |
| Controller | `CmsArticleController` | `PUT /{id}/schedule`、`PUT /{id}/schedule/cancel`，权限码 `platform:cms:article:schedule` |
| 配置 | `application.yml` | `lx.cms.schedule-scan-interval-ms=30000`、`schedule-scan-initial-delay-ms=15000`、`schedule-batch-size=200` |
| 前端接口 | `api/cms.js` | `scheduleArticle` / `cancelScheduleArticle` |
| 前端页面 | `views/cms/article/index.vue` | 状态筛选加“定时待发布”、计划时间列、定时弹窗、取消定时 |
| 单测 | `CmsArticleServiceTest` | 19 项（新增 7 项排期相关） |

### 53.3 关键决策

1. **状态值 `3` 而不是“草稿 + 时间”。**
   列表筛选、按钮显隐、门户可见性都只判断状态；门户本来就只查 `status='1'`，
   定时文章不需要额外过滤时间。
2. **数据库条件更新当锁。**
   `UPDATE ... WHERE status='3' AND schedule_time <= now`，多实例同时扫描只有一台成功；
   不建锁表、不引分布式锁，和项目“Spring Task + 数据库锁”的主线一致。
3. **首次发布时间取计划时间。**
   `publish_time = COALESCE(publish_time, schedule_time)`，重新发布保留历史首次时间；
   发布后 `schedule_time=NULL`，避免列表残留“已发布但还显示计划时间”。
4. **已发布必须先下线。**
   直接排期等于悄悄替换线上内容；重复排期视为改时间，允许。
5. **人接管状态时清定时。**
   手动发布、下线、回收站恢复都会清 `schedule_time`；恢复仍是草稿，需要重新排期。
6. **扫描不依赖 job 模块。**
   CMS 关闭时任务不注册；job 模块关闭不影响内容发布。单轮最多 200 篇，失败下一轮重试。
7. **时间按服务器本地时区。**
   当前不做站点级时区；私有化单时区部署够用，跨时区运营后续再补。

### 53.4 施工步骤

1. **数据库**
   - `cms_article` 增 `schedule_time`，建 `(status, schedule_time)` 索引；
   - 插按钮 3053 `cms:article:schedule`，`module_code='cms'`，补 `role_id=1` 授权；
   - SQL 继续用 `INSERT ... SELECT ... WHERE NOT EXISTS`，不用 MySQL 方言。
2. **后端**
   - 实体 / DTO / 校验消息中英文各一份；
   - Mapper：排期、取消、到点候选、条件发布四条显式 SQL；
   - Service：状态校验、时间必须晚于当前、已发布拒绝、扫描批量处理；
   - Task：固定间隔扫描，异常兜住并记录本轮发布篇数；
   - Controller：两个接口共用 `schedule` 权限码。
3. **前端**
   - 文章列表状态筛选与标签补“定时待发布”，新增计划时间列；
   - 行内新增“定时发布”（非已发布）与“取消定时”（仅状态 3）；
   - 弹窗用 `el-date-picker` 选到秒，提交前再校验必须晚于当前；
   - 中英文文案补齐，i18n 键数量 1039 → 1051。

### 53.5 验证

**A. 后端**

- `mvn -q -pl lx-modules/lx-module-cms -am test` 通过；
- 全仓 `mvn clean test`：JDK 8 / JDK 11 各一遍，**148 项 / 28 类**，0 失败 0 错误；
- `mvn -q -DskipTests package` 通过；Flyway 执行到 V23，应用正常启动；
- 菜单 3053 与 `role_id=1` 授权已落库。

**B. 接口 39/39**

`.local/cms-schedule-check.mjs`：

- 过去时间、空时间、已发布文章排期都被拒绝；
- 草稿排期后 `status=3`、`scheduleTime` 有值、门户不可见、列表可按状态 3 筛选；
- 重复排期成功改时间；
- 取消排期回草稿并清空时间，再次取消被拒绝；
- 手动发布 / 下线 / 删除后恢复都清空 `scheduleTime`；
- 到点后扫描自动发布：`status=1`、`publishTime` 等于计划时间、`scheduleTime` 为空、门户可见；
- 清理站点 / 栏目。

**C. 浏览器 10/10**

`.local/uitest/cms-schedule.mjs`：

- 草稿行出现“定时发布”，弹窗含标题与“到点后由后台任务自动发布”提示；
- 输入未来时间提交后，列表显示“定时待发布”和计划时间；
- 取消定时确认后回草稿；
- 第二篇排期到 5 秒后，刷新列表等到“已发布”，发布时间列显示计划时间；
- 门户接口能看到该文章；已发布行不再出现“定时发布 / 取消定时”。

截图：`.local/uitest/cms-schedule.png`（定时待发布列表）。

**D. 前端**

- `node scripts/check-i18n-keys.mjs`：zh=1051、en=1051，无 missing / 单边 / dotted-leaf；
- `npm run build` 通过。

### 53.6 踩坑

| 问题 | 处理 |
|---|---|
| 单测里 `LambdaUpdateWrapper` 读不到 TableInfo 缓存 | 排期 / 取消改成 Mapper 显式条件 SQL，顺带获得并发安全，不再依赖实体缓存 |
| 排期和“改状态”并发时可能互相覆盖 | 条件 UPDATE 限定可排期状态，影响行数为 0 时提示刷新重试 |
| 手动发布后计划时间残留 | `publish` / `offline` / `restore` 全部显式 `schedule_time=NULL` |
| 浏览器脚本中途调登录会踢掉页面会话 | 所有前置数据用启动时 Token 建好，浏览器登录后不再刷新 Token，末尾再 `refreshToken` 清理 |
| Element Plus 日期时间选择器 | 直接点输入框输入 `YYYY-MM-DD HH:mm:ss` 后回车，再点“确定定时” |
| 扫描任务异常会让后续轮次停摆 | 任务方法整体 try/catch，记录日志后等下一轮 |

### 53.7 遗留

| 项 | 说明 |
|---|---|
| 精度 | 默认 30 秒扫描一次，到点文章最多晚 30 秒可见 |
| 排期能力 | 仅一次性；周期发布、批量排期、定时下线未做 |
| 通知 | 发布成功 / 失败只进日志，无站内信；失败下一轮重试 |
| 时区 | 按服务器本地时区解释，站点级时区未做 |
| 内容版本 | 排期发布的是“到点时最新内容”；版本快照仍待做 |

---

## 54. CMS 文章内容版本（2026-11-22）

### 54.1 背景与范围

回收站、定时发布之后，内容生命周期补上最后一环：**文章内容版本**。
目标是编辑误删、改错、误发布时能找回旧稿，而不是做完整的 CMS 审计平台。

- 只做文章：标题 / slug / 摘要 / 封面 / 正文 / 来源 / 栏目 / 标签 / 状态；
- 不做站点、栏目、标签版本；
- 不做版本对比（diff）、不做人工命名版本、不做版本保留期界面；
- 不引入外部依赖，版本号与去重都在应用层完成。

### 54.2 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `V24__cms_article_version.sql` | `cms_article_version` 表、`(article_id, version_no)` 唯一键、按钮 3054 / 3055 |
| 实体 | `CmsArticleVersion` | 不可变快照；`tagIds` 逗号分隔，`tagIdList` 为详情回填字段 |
| DTO | `CmsArticleVersionVO` | 列表行，不返回正文 |
| Mapper | `CmsArticleVersionMapper` | `selectVersionPage` / `selectMaxVersionNo` / `selectLatestHash` / `deleteUpToVersion` / `deleteByArticleId` |
| Service | `CmsArticleService` | `versionPage` / `versionDetail` / `rollback`；创建 / 编辑 / 发布 / 回滚 / 恢复后 `snapshot` |
| Controller | `CmsArticleController` | 版本列表 / 详情 / 回滚三个接口，权限码 `version` / `rollback` |
| 配置 | `application.yml` | `lx.cms.version-keep=30` |
| 前端接口 | `api/cms.js` | `listArticleVersions` / `getArticleVersion` / `rollbackArticleVersion` |
| 前端页面 | `views/cms/article/index.vue` | 行内「历史版本」抽屉、详情弹窗、回滚确认、最新版本禁用回滚 |
| 单测 | `CmsArticleServiceTest` | 27 项（新增 8 项版本相关） |

### 54.3 关键决策

1. **快照不可变。**
   版本行只增不改；回滚本身也写一条 `rollback` 版本，历史链条不会断。
2. **版本只覆盖内容字段。**
   排序、备注、作者、浏览量不进快照；回滚不会覆盖运营后来调整的元数据。
3. **SHA-256 相邻去重。**
   哈希包含内容、标签和状态：连续保存相同内容不产生版本；发布、恢复这类
   状态变化仍会留痕。
4. **回滚统一回草稿。**
   不自动重新发布；`publish_time` 保留，`schedule_time` 清空。
5. **回滚前校验。**
   版本必须属于该文章；栏目必须存在且同站点；slug 不能被其它文章（含回收站）占用。
6. **标签宽松恢复。**
   已删除 / 跨站点的标签跳过并记日志，正文优先恢复。
7. **按文章限量保留。**
   默认最近 30 个版本，超出删最旧；文章彻底删除时清空全部版本。
8. **列表不查正文。**
   版本列表只出摘要；正文详情读取时再清洗一次。
9. **最新版本禁止回滚。**
   前端对第一行禁用回滚按钮，避免“回滚到当前内容”这种无意义操作。

### 54.4 施工步骤

1. **数据库**
   - 建 `cms_article_version`，唯一键 `(article_id, version_no)`；
   - 插按钮 3054 `cms:article:version`、3055 `cms:article:rollback`，补超管授权；
   - SQL 继续用 `INSERT ... SELECT ... WHERE NOT EXISTS`，不用 MySQL 方言。
2. **后端**
   - 实体 / VO / Mapper 显式 SQL；
   - `snapshot` 在创建、编辑、发布、回滚、回收站恢复后调用；
   - `rollback` 覆盖内容、清定时、回草稿，写 `rollback` 快照；
   - `purge` 先清标签关联与版本，再物理删文章；
   - `version-keep` 超限后删最旧版本。
3. **前端**
   - 行内新增「历史版本」按钮，打开抽屉；
   - 抽屉展示版本号、变更类型、标题、状态、操作人、时间；
   - 版本详情弹窗展示元信息与清洗后的正文；
   - 回滚二次确认，成功后刷新版本列表与文章列表；
   - 中英文文案补齐，i18n 键 1051 → 1068。

### 54.5 验证

**A. 后端**

- `mvn -q -pl lx-modules/lx-module-cms -am test` 通过；
- 全仓 `mvn clean test`：JDK 8 / JDK 11 各一遍，**156 项 / 28 类**，0 失败 0 错误；
- `mvn -q -DskipTests package` 通过；Flyway 执行到 V24，应用正常启动；
- 菜单 3054 / 3055 与 `role_id=1` 授权已落库。

**B. 接口 42/42**

`.local/cms-version-check.mjs`：

- 创建自动写 `create`；编辑写 `edit`；相同内容重复保存不新增；
- 版本分页 `pageSize=1` 返回 1 条、total 正确；
- 版本详情返回旧标题 / 旧正文，正文中的 `<script>` 被清洗；
- 回滚恢复标题、正文与草稿状态，写 `rollback`；重复回滚同一版本被去重；
- 发布写 `publish`；回收站恢复写 `restore`；已发布文章回滚回草稿；
- 其它文章的版本号读取 / 回滚都 404；
- slug 被占用时回滚拒绝；
- 彻底删除后版本行全部清零。

**C. 浏览器 9/9**

`.local/uitest/cms-version.mjs`：

- 文章行出现「历史版本」；抽屉提示与版本顺序正确（发布 / 编辑 / 创建）；
- 最新版本回滚按钮禁用；
- 版本详情展示旧正文；
- 回滚确认后抽屉第一行变「回滚」，主列表变为「草稿」；
- 页面 Token 复核后端内容与状态已回滚。

截图：`.local/uitest/cms-version.png`（版本抽屉）。

**D. 前端**

- `node scripts/check-i18n-keys.mjs`：zh=1068、en=1068，无 missing / 单边 / dotted-leaf；
- `npm run build` 通过。

### 54.6 踩坑

| 问题 | 处理 |
|---|---|
| 编辑接口 `@Valid` 要求 `siteId`，但服务层不允许改站点 | 版本验收脚本编辑时仍传 `siteId`；真实前端表单本来就带 |
| `PageResult.total` JSON 里是字符串 | 验收脚本统一 `Number(total)` 比较，不能直接 `=== 1` |
| 浏览器登录会踢掉 API Token（单会话策略） | 页面内的最终复核用 `localStorage` 的 Token 发请求，清理阶段再刷新 API Token |
| 恢复快照可能被哈希去重 | 恢复前若最新版本已是草稿且内容相同，不重复记版本；验收按“发布 → 删除 → 恢复”构造状态差 |
| 用 PowerShell `Set-Content` 改含中文的 `.mjs` 会乱码 | 中文脚本一律用 UTF-8 写入工具或 Node 生成，不用 PS 的默认编码读改写 |

### 54.7 遗留

| 项 | 说明 |
|---|---|
| 版本范围 | 仅文章内容；站点 / 栏目 / 标签无版本 |
| 对比能力 | 无 diff、无并排对比、无人工作版本备注 |
| 回滚语义 | 统一回草稿，不自动发布、不恢复定时 |
| 保留策略 | 只按数量截断（默认 30），没有按时间 / 存储量的清理界面 |
| 数据范围 | 版本列表与其它 CMS 列表一致，暂未接数据范围 |
| 后续方向 | v1.2 会员体系；内容级多语言与门户工程按 v1.3 计划推进 |

---

## 55. v1.2 会员第一刀（2026-11-22）

### 55.1 背景与范围

v1.0 基座与 v1.1 CMS 之后进入会员体系。第一刀只打穿“账号 + 登录 + 个人中心 +
后台可管”这条最短闭环，等级升级、积分、评论、第三方登录都后置。

- 会员与平台后台**物理隔离**：独立表、独立 Token、独立 StpLogic；
- 注册 / 登录 / 找回密码 / 自助改密 / 后台会员管理；
- 头像复用文件中心，只存 `fileId` 或外链；
- 不做会员角色与权限码、不做真实短信 / 邮件、不做注销入口。

### 55.2 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 依赖 | `lx-dependencies/pom.xml`、`lx-modules/pom.xml`、`lx-boot/pom.xml` | 新增 `lx-module-member` |
| 开关 | `application.yml` | `lx.module.modules.member.enabled=true` |
| 迁移 | `db/migration/member/V25__member_schema.sql` | `mem_level` / `mem_user` / `mem_password_reset`、参数 11~18、菜单 4000 段 |
| 安全域 | `StpMemberUtil` | `member` 域独立 `StpLogic` |
| 配置 | `MemberSaTokenConfigure` | Token 名 `lx-member-token`、`/api/app/**` 拦截与白名单 |
| 实体 | `MemUser` / `MemLevel` / `MemPasswordReset` | 密码 / 验证码哈希 `WRITE_ONLY` |
| 服务 | `MemberUserService` | 会员端 + 后台全部账号逻辑 |
| 服务 | `MemberLevelService` / `MemberCaptchaService` / `MemberPasswordPolicy` / `MemberTokenIssuer` | 等级、验证码、密码策略、Token 签发 |
| 下发 | `LogMemberResetCodeSender` | 开发环境验证码只写日志 |
| 接口 | `MemberAuthController` / `MemberProfileController` / `MemberAdminController` | `/api/app/auth/**`、`/api/app/profile/**`、`/api/admin/member/**` |
| 前端接口 | `lx-ui-admin/src/api/member.js` | 后台会员接口封装 |
| 前端页面 | `views/member/user/index.vue` | 列表、筛选、新增编辑、启停、重置密码、删除 |
| 单测 | `MemberUserServiceTest` / `MemberPasswordPolicyTest` / `MemberCaptchaServiceTest` | 15 + 5 + 6 = 26 项 |
| 文档 | `docs/30-member-embed-plan.md` | 会员模块边界与切片计划 |

### 55.3 关键决策

1. **物理隔离不是“逻辑分表”。**
   会员表前缀 `mem_*`，平台表 `sys_*`；会员 Token 名为 `lx-member-token`，
   与 `lx-platform-token` 并存。两套安全域只共享底层存储。
2. **会员模块不依赖 system。**
   后台账号、参数、菜单仍由基座提供；会员模块只依赖 common / framework，
   将来单独售卖时不需要搬系统管理。
3. **唯一性由应用层校验。**
   不建带 `del_flag` 的唯一键，否则两条逻辑删除记录会互相冲突；
   查询统一带 `del_flag='0'`，删除后允许同名重新注册。
4. **登录失败计数落库。**
   `mem_user.login_fail_count` / `lock_time`，阈值和锁定时长由 `mem.account.*` 控制；
   锁定期内不再比对密码，避免计时攻击和无效哈希计算。
5. **验证码复用 SaTokenDao。**
   单机走内存、Redis 走 Redis，按 `login / register / reset` 场景隔离；
   总闸 `lx.captcha.enabled` 与参数 `mem.security.captchaEnabled` 双重控制。
6. **找回密码不暴露账号是否存在。**
   账号不存在时静默成功；验证码只存 BCrypt 哈希，一次性使用，
   过期或使用后立即失效；重置成功踢掉全部会话。
7. **后台删除即释放账号。**
   逻辑删除后列表、登录、唯一性校验都忽略该行，用户名 / 手机号 / 邮箱可重新注册；
   历史业务数据仍指向原 `member_id`。
8. **菜单段独立。**
   会员占 4000 段，CMS 占 3000 段，后续会员子菜单不需要重排 CMS 菜单。
9. **前端文案一次补齐。**
   菜单、页面、校验提示中英同步，不把半成品文案留给下一刀。

### 55.4 施工步骤

1. **数据库**
   - 建 `mem_level`、`mem_user`、`mem_password_reset`；
   - 插入 3 条等级种子、参数 11~18、菜单 4000 段与超管授权；
   - SQL 不使用 MySQL 方言，保持 MySQL / PG / 达梦 / 金仓可移植。
2. **后端**
   - 新建模块与依赖，注册模块开关；
   - 独立 `StpMemberUtil` + `MemberSaTokenConfigure`；
   - 实现注册 / 登录 / 找回 / 自助改密 / 后台 CRUD；
   - 密码策略、验证码、Token 签发拆成独立服务，便于单测；
   - 会员参数全部从 `ConfigProvider` 读取，缺失回退默认值。
3. **前端**
   - `api/member.js` 封装后台接口；
   - `views/member/user/index.vue` 接 `MediaPicker` 上传头像；
   - 菜单与文案中英同步，构建通过。
4. **测试**
   - 三个单测类锁住注册规则、失败锁定、验证码隔离、密码策略；
   - 真实接口脚本覆盖域隔离与完整生命周期。

### 55.5 验证

**A. 后端**

- `mvn -q -pl lx-modules/lx-module-member -am test` 通过；
- 全仓 `mvn clean test`：JDK 8 / 11 **182 项 / 31 类**，0 失败 0 错误；
- `mvn -q -DskipTests package` 通过；Flyway 执行到 V25，应用正常启动；
- `mem_level` 3 条、参数 11~18、菜单 4000 段与 8 条超管授权已落库。

**B. 接口 46/46**

`.local/member-check.mjs`：

- 后台登录、菜单下发、会员菜单组件路径正确；
- 平台 Token 访问 `/api/app/profile` 401，会员 Token 访问后台 401，匿名访问 401；
- 注册成功直接发 Token，Token 名 `lx-member-token`；资料不返回密码；
- 手机号、邮箱登录；会员自助改密后旧 Token 失效；
- 后台重置密码后旧 Token 失效；后台设置的密码可登录；
- 找回密码验证码从日志读取，重置成功且同一验证码不能复用；
- 前 4 次密码错误 2001，第 5 次 2004；锁定期内正确密码也拒绝；
  后台重置解锁后可登录；
- 停用后登录 2002，启用后恢复；
- 删除后不能登录，原会员不在列表，同名可重新注册且 `memberId` 不同；
- 验证码总闸关闭时会员验证码返回 `enabled=false`；
- 平台后台会话全程不受影响。

**C. 前端**

- `npm run build` 通过；
- i18n zh=1116、en=1116，无 missing / dotted-leaf；
- 浏览器 `.local/uitest/member.mjs` **12/12**：入口、隔离提示、关键词筛选、
  编辑时用户名锁定、改昵称、停用 / 启用、后台重置密码、删除后不能登录、无前端报错。

**D. 关闭态 7/7**

`.local/member-off-check.mjs`（`--lx.module.modules.member.enabled=false` 重启）：

- 平台后台仍可登录，CMS 不受影响；
- 会员菜单不下发；
- `/api/admin/member/**`、`/api/app/auth/**`、`/api/app/profile` 均 404；
- `mem_user` / `mem_level` / `mem_password_reset` 表与数据保留。

### 55.6 踩坑

| 问题 | 处理 |
|---|---|
| `lx-boot` 引用新模块时缺少版本 | 先在 `lx-dependencies` 的 `dependencyManagement` 加 `lx-module-member` |
| 单独 `mvn -pl lx-module-member test` 找不到 common 依赖 | 内部模块未 install，必须加 `-am` |
| 纯单测使用 Lambda 条件构造器报 `can not find lambda cache` | `@BeforeAll` 里 `TableInfoHelper.initTableInfo` 初始化实体元数据 |
| `verify(mapper).update(eq(null), any())` 匹配不到 | 改用 `isNull()` 匹配 |
| 验收脚本用中文正则匹配日志，PowerShell 输出乱码 | 改为匹配纯 ASCII 的 `memberId=..., code=..., expireMinutes=` |
| 会员删除后同名注册，验证脚本最初按“用户名继续占用”写 | 按实际逻辑删除语义修正：删除即释放账号，可重新注册 |
| 后台重置密码同时解锁 | 重置会清零失败计数与锁定时间，验收脚本先重置再测锁定 |

### 55.7 遗留

| 项 | 说明 |
|---|---|
| 短信 / 邮件 | `LogMemberResetCodeSender` 只写日志，生产上线前必须替换 |
| 等级与积分 | 等级表与字段已就绪，升级任务、积分规则、权益未做 |
| 互动 | 评论、收藏、点赞、消息通知未做 |
| 第三方登录 | `mem_oauth` 表未建，绑定流程未做 |
| 会员权限 | 只有登录态，没有会员角色 / 权限码 |
| 注销 | 只有后台逻辑删除，没有会员自助注销 |
| 后续方向 | 会员等级升级与积分规则、互动能力、第三方登录绑定 |

---

## 56. v1.2 会员第二刀：安全补口（2026-11-22）

### 56.1 背景与范围

第一刀打通“账号 + 登录 + 个人中心 + 后台可管”后，第二刀只补安全缺口：
注册 / 登录 / 找回密码 / 重置密码限流、验证码错误次数、短信 / 邮件通道接口化。
不扩等级、积分、评论、第三方登录等业务面。

### 56.2 代码落点

| 层 | 文件 | 说明 |
|---|---|---|
| 迁移 | `db/migration/member/V26__member_security_hardening.sql` | 参数 19~25：验证码尝试次数、注册 / 登录 / 找回限流 |
| 限流 | `MemberRateLimitService` | 单机内存固定窗口，按动作 + 账号 + IP 计数，超限 1006 |
| 验证码 | `MemberCaptchaService` | 同一 key 允许输错 N 次，达到上限立即作废 |
| 通道接口 | `MemberSmsSender` / `MemberEmailSender` | 短信、邮件真实通道 SPI |
| 开发实现 | `DevMemberSmsSender` / `DevMemberEmailSender` | 只写日志，保留可解析验证码行 |
| 装配 | `MemberChannelConfiguration` | `lx.member.channel.mode=dev` 默认装配开发实现 |
| 路由 | `RoutingMemberResetCodeSender` | 手机号走短信、邮箱走邮件、用户名优先手机号 |
| 业务 | `MemberUserService` | 注册 / 登录 / 找回 / 重置入口统一先限流 |
| 单测 | `MemberRateLimitServiceTest` / `RoutingMemberResetCodeSenderTest` | 窗口边界、Key 隔离、通道路由 |
| 配置 | `application.yml` | `lx.member.channel.mode: dev` |

### 56.3 关键决策

1. **限流与锁定分层。** 限流挡短时间脚本重放，内存窗口、重启清零；
   账号锁定仍落 `mem_user.login_fail_count / lock_time`，重启不丢。
2. **账号 + IP 双维度。** 每个动作两个 Key；IP 阈值自动放宽为账号阈值的 10 倍。
3. **动作独立计数。** 注册 / 登录 / 找回 / 重置互不占用额度。
4. **验证码有限重试。** 默认 3 次，达到上限立即作废；成功也立即作废。
5. **通道接口解耦。** 业务层只依赖 `MemberResetCodeSender`；生产实现
   `MemberSmsSender` / `MemberEmailSender` 即可，不改业务逻辑。
6. **开发通道可验收。** 日志保留 `memberId / username / code / expireMinutes`，
   本地脚本直接读取；生产必须切 `mode=prod`。

### 56.4 验证

```text
mvn -q test                       196 项 / 33 类，失败 0 错误 0
mvn -q -DskipTests package        通过
member-check.mjs                  57/57
Flyway                            V26 - member security hardening 成功
参数 19~25                        已落库
```

接口脚本新增覆盖：

```text
注册前 5 次正常进入业务校验，第 6 次 1006
找回密码前 5 次静默成功，第 6 次 1006
重置密码前 5 次正常进入业务校验，第 6 次 1006
登录前 30 次 2001，第 31 次 1006
手机号申请 → DevMemberSmsSender 日志；邮箱申请 → DevMemberEmailSender 日志
```

验证码错误次数由单测覆盖：前两次错误可重试，第 3 次作废，之后正确码也不能用。

### 56.5 踩坑

| 问题 | 处理 |
|---|---|
| 验收脚本用同一账号反复登录，登录限流会先于业务错误触发 | 默认登录阈值设 30，脚本现有登录次数约 15；限流验收用独立不存在账号 |
| 注册限流若只按 IP 计数，连续跑验收会互相影响 | 账号维度优先，IP 阈值放宽为 10 倍 |
| 找回密码账号不存在也要计数 | 先限流再查账号，避免用限流差异探测账号是否存在 |
| 开发通道日志格式变化导致脚本取不到验证码 | 保持 `memberId=..., username=..., code=..., expireMinutes=...` 顺序 |

### 56.6 遗留

| 项 | 说明 |
|---|---|
| 真实服务商 | 当前默认 `lx.member.channel.mode=dev` 只写日志，生产需实现短信 / 邮件接口 |
| 多实例限流 | 内存窗口各节点独立计数，后续可换 Redis / 网关限流 |
| 等级与积分 | 等级表与字段已就绪，升级任务、积分规则、权益未做 |
| 互动 / 第三方 / 权限 | 与第一刀遗留一致 |
