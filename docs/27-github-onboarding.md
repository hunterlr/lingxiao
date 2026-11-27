# 27 · 上传 GitHub 与换机器继续开发

> 这份文档回答三个问题：
>
> 1. 项目里用了哪些“轮子”，清单在哪？
> 2. 把整个项目传到 GitHub，换一台电脑 clone 下来，怎么跑起来？
> 3. 新机器上怎么接着做下一轮任务，哪些东西不在仓库里？
>
> 适用版本：v1.0 后台基座（2026-11-15）。

---

## 1. 轮子清单在哪

**唯一台账：[`docs/22-dependencies.md`](22-dependencies.md)。**

它记录的是“实际进包”的依赖，不是凭 `pom.xml` 猜的：

| 范围 | 内容 | 数量 |
|---|---|---|
| 后端运行时 | `BOOT-INF/lib/*.jar`（含 13 个项目自研 jar） | **102 个** |
| 后端测试 | JUnit / Mockito / AssertJ（test scope，不进包） | 见 22 第 2.12 节 |
| 前端直接依赖 | `package.json` 声明的 10 个运行时 + 2 个开发依赖 | **12 个** |
| 前端关键传递依赖 | dayjs / lodash-es / esbuild / rollup 等 | 见 22 第 3.2 节 |
| 刻意没引的 | Quartz、ECharts、H2、Testcontainers、JaCoCo、vue-i18n… | 见 22 第 4 节 |

自查命令（换机器后也能复现）：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead('lx-boot\target\lingxiao.jar')
($z.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' }).Count
$z.Dispose()
```

> 新增依赖的规矩：版本只改 `lx-dependencies/pom.xml`（前端改 `package.json`），
> 同时更新 `docs/22-dependencies.md` 与 `docs/11-jdk-engineering.md` 的依赖矩阵。
> 只改 POM 不改文档，视为施工未完成。

---

## 2. 上传 GitHub 之前

### 2.1 当前状态

- **已上传**：<https://github.com/hunterlr/lingxiao>，分支 `main`，首次提交 `ee26d5e`（2026-11-16，371 个文件）。
- `.gitignore` 已就绪，`target/`、`node_modules/`、`dist/`、`release/`、`data/`、`.local/`、`*.log` 都不会入库。
- `.gitattributes` 已就绪：仓库内统一 LF，`.ps1` 保持 CRLF，`.sh` 保持 LF（避免 Linux 上 `bad interpreter`）。
- 认证方式：**SSH**（`~/.ssh/id_rsa`，指纹 `SHA256:AkJzU7/fpGxdxMepA+hWsrCBHqA5P7pnRQ0x9haQYIA`），无密码短语。
- 仓库当前是 **public**；要改私有：`Settings → General → Danger Zone → Change visibility`。
- 没有 `LICENSE`：如果要公开给别人用，建议补一个（MIT / Apache-2.0）；内部仓库可以不加。

### 2.2 会入库 / 不会入库

| 内容 | 是否入库 | 说明 |
|---|---|---|
| `lx-common/` `lx-framework/` `lx-modules/` `lx-api/` `lx-boot/` | ✅ | 后端源码、Flyway 脚本、`application.yml` |
| `lx-ui-admin/`（不含 `node_modules/` `dist/`） | ✅ | 前端源码、`package-lock.json`、`.env.development` |
| `docs/` `deploy/` `README.md` `.github/` | ✅ | 文档、部署脚本、CI |
| 各模块 `src/test/` | ✅ | 112 项单测 |
| `deploy/sql/lingxiao-1.0.0.sql` | ✅ | 50 KB 数据库快照，clone 后可直接导入（见 3.3 方式 B） |
| `lx-ui-admin/public/ueditor-plus/` | ❌ | `prebuild` 从 npm 包生成，不入库 |
| `target/` `dist/` `release/` | ❌ | 构建产物，重新生成 |
| `data/upload/` | ❌ | 本地上传文件，不随代码走 |
| `.local/` | ❌ | MySQL 数据目录、验证脚本、UI 脚本、截图、日志 |
| `*.log` | ❌ | 运行日志 |

> **`.local/` 是重点**：里面的 `verify-*.ps1` 与 `uitest/*.mjs` 是每轮发版前的人工回归脚本，
> 默认不进仓库。换机器后如果要继续跑接口 / 页面回归，见本文 4.4。

### 2.3 首次上传命令（已于 2026-11-16 执行完毕，留作参考）

```powershell
cd E:\java\lingxiao

git init -b main
git config user.name "你的名字"
git config user.email "you@example.com"

git add .
git status --short          # 仔细看一眼，不应该出现 target/、node_modules/、.local/、release/

git commit -m "chore: 凌霄 v1.0 后台基座"
git remote add origin git@github.com:<你的账号>/lingxiao.git
git push -u origin main
```

> 用 SSH 而不是 HTTPS，是因为这台机器上 `api.github.com` 被重定向劫持、Git Credential Manager
> 二进制也缺失；SSH 只需要一次性把公钥加到 GitHub，之后不再需要任何交互。

如果误把产物加进了暂存区（还没提交）：

```powershell
git reset                  # 只撤销暂存，不删本地文件
git status --short | Select-String 'target/|node_modules/|dist/|\.local/|release/'
```

如果已经提交了，想让 Git 重新按 `.gitignore` 收录：

```powershell
git rm -r --cached .       # 只从索引移除，磁盘文件不动
git add .
git commit -m "chore: 移除误提交的构建产物"
```

GitHub 单文件上限 100 MB；本项目最大的入库文件是前端 `package-lock.json` 与文档，远低于限制。
发布包 `release/*.zip` 约 50 MB，但已被忽略，不会上传。

> 在 Windows 上 `git add` 的 `.sh` 默认是 644（没有执行位）；Linux 服务器首次使用前执行
> `chmod +x deploy/linux/*.sh`（`deploy/README.md` 也有同样提醒）。

### 2.4 推送后打开 CI

1. 仓库 `Settings → Actions → General`，允许 Actions 运行；
2. 打开 `Actions` 页，能看到 `CI` 工作流；首次 `push` 会自动跑；
3. 后端 job 会在 **JDK 8 / 11 / 17** 上各跑一次 `mvn -B -ntp test`，JDK 8 额外 `package`；
4. 前端 job 跑 `npm ci` + `npm run build`。

失败时先看 `docs/26-testing-ci.md`；本地复现用 `mvn -B -ntp test`。

---

## 3. 换一台电脑跑起来

### 3.1 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 8 / 11 / 17 | 产物是 JDK 8 字节码，三者都能跑；推荐 8 或 11 |
| Maven | 3.9.x | 需要能访问 Maven Central（首次构建要下依赖） |
| Node.js | 18 / 20 LTS | `package-lock.json` 是 lockfileVersion 3 |
| MySQL | 5.7 / 8.0 | v1.0 只验证 MySQL；字符集 `utf8mb4` |
| Redis | 可选 | 默认 `lx.redis.enabled=false`，不装也能跑 |
| Git | 任意较新版本 | clone 仓库 |

### 3.2 拉代码

```bash
git clone https://github.com/<你的账号>/lingxiao.git
cd lingxiao
```

### 3.3 初始化数据库

`deploy/sql/` 下有两个文件，**先执行 `init-db.sql`，再二选一**：

| 文件 | 作用 |
|---|---|
| `init-db.sql` | 建库、建应用账号、授权（必须执行） |
| `lingxiao-1.0.0.sql` | v1.0.0 数据库快照：23 张业务表 + `flyway_schema_history` + 初始数据（可选） |

第 1 步，建库建号：

```bash
mysql -uroot -p < deploy/sql/init-db.sql
```

`init-db.sql` 里的 `CHANGE_ME` 要换成真实密码，并与后端 `DB_PASSWORD` 一致。

第 2 步，二选一：

**方式 A（推荐给正式环境）：什么都不用导，交给 Flyway。**

> 应用启动时 Flyway 按模块开关自动执行 `db/migration/*` 下的迁移（当前 V1~V42）。
> 基座 `system` / `file` 永远参与；`job` / `generator` 等模块只有开启时才扫描自己的目录。
> 全新库第一次启动会看到一串 `Migrating schema ...` 日志，属于正常现象。

**方式 B（想要“开箱即用”的现成数据）：导入快照。**

```bash
mysql -uroot -p < deploy/sql/lingxiao-1.0.0.sql
# 或者用应用账号（库已存在即可，不需要 CREATE DATABASE 权限）
mysql -h127.0.0.1 -P3306 -ulingxiao -p lingxiao < deploy/sql/lingxiao-1.0.0.sql
```

快照里已经包含：

| 内容 | 数量 / 说明 |
|---|---|
| 表 | 24 张（23 张业务表 + `flyway_schema_history`） |
| Flyway 记录 | 17 条（V1~V17）。快照停在 v1.0 发布时的状态，**尚未刷新**：首次启动会补执行 V18 起的全部迁移，CMS / 会员 / 多语言的表在这一步创建（刷新计划见 `docs/13` §12.29 第 1 项） |
| 菜单 / 角色授权 | 64 条菜单 + 64 条角色菜单 |
| 参数 / 字典 / 任务 / 公告 | 10 / 3+7 / 1 / 1 |
| 账号 | `admin` / `admin123` |

导入时注意三点：

1. 文件里是 `DROP TABLE IF EXISTS` + `CREATE TABLE`，**重复导入会把库重置成快照状态**，已有数据会没；
2. 快照带 `flyway_schema_history`，所以导入后**不要改已执行过的 `V*.sql`**，否则启动报 checksum mismatch；
3. 登录日志 / 操作日志 / 文件记录 / 代码生成临时表已清空，所以首页统计是 0、文件中心是空列表，这是正常的。

> 两种方式不能叠加：已经用 A 跑过一次、库里已有表之后，再导 B 也没关系（B 是覆盖），
> 但反过来先导 B 再启动，Flyway 会补执行 V18 及之后尚未执行的迁移，不会重建已有的表。

### 3.4 启动后端

关键环境变量（都有默认值，生产必须显式设置）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | |
| `DB_PORT` | **`3307`** | 默认值不是 3306，新机器一般要改成 3306 |
| `DB_NAME` | `lingxiao` | |
| `DB_USER` | `lingxiao` | |
| `DB_PASSWORD` | `lingxiao123` | 生产必须改 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | 仅在 `lx.redis.enabled=true` 时使用 |

Windows PowerShell：

```powershell
$env:DB_PORT = '3306'
$env:DB_PASSWORD = '你的密码'

mvn -B -ntp -DskipTests package
java -jar lx-boot\target\lingxiao.jar
```

Linux / macOS：

```bash
DB_PORT=3306 DB_PASSWORD='你的密码' \
  mvn -B -ntp -DskipTests package
DB_PORT=3306 DB_PASSWORD='你的密码' \
  java -jar lx-boot/target/lingxiao.jar
```

启动成功后：

- 后端：`http://127.0.0.1:8080`
- 接口文档：`http://127.0.0.1:8080/doc.html`
- 健康检查（未登录会返回 401/1002，说明服务活着）：`http://127.0.0.1:8080/api/admin/auth/captcha`

> 开发期不想每次打包，也可以 `mvn -B -ntp -DskipTests install` 之后
> `mvn -pl lx-boot spring-boot:run`；直接 `-pl` 不带 `-am` 会因为找不到内部 SNAPSHOT 失败。

### 3.5 启动前端

```bash
cd lx-ui-admin
npm ci                 # 严格按 package-lock 安装，不要用 npm install
npm run dev            # http://127.0.0.1:5173
```

代理目标在 `.env.development`：

```ini
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_PORT=5173
```

后端换了端口时只改这里，前端代码不用动。

### 3.6 首次登录

- 账号：`admin`
- 密码：`admin123`
- 来源：`V2__system_seed.sql` 里的 BCrypt 哈希（明文就是 `admin123`）

**登录后第一件事：到「个人中心 → 修改密码」。** 新库默认密码是公开的，
无论是本地还是服务器，都不应该长期保留。

### 3.7 自检清单

| 检查 | 命令 / 地址 | 期望 |
|---|---|---|
| 后端单测 | `mvn -B -ntp test` | 66 tests / 0 failures |
| 前端构建 | `cd lx-ui-admin && npm run build` | 生成 `dist/` |
| 登录 | `http://127.0.0.1:5173` | 能进首页，侧边栏 18 个菜单 |
| 接口文档 | `http://127.0.0.1:8080/doc.html` | 76 条路径 |
| 文件上传 | 文件中心上传一张图 | 网格出现缩略图 |
| 数据库 | `SHOW TABLES;` | 22 张表（`sys_*` / `gen_*`） |

---

## 4. 怎么“继续任务”

### 4.1 先读这 5 份（顺序别乱）

| 顺序 | 文档 | 读它是为了知道 |
|---|---|---|
| 1 | `README.md` | 项目是什么、当前做到哪、怎么启动 |
| 2 | `docs/17-v1-plan.md` | v1.0 的范围与验收口径（冲突以它为准） |
| 3 | `docs/21-build-log.md` **最后一节** | 上一轮具体改了什么、踩了什么坑、下一步 |
| 4 | `docs/26-testing-ci.md` | 测试怎么跑、哪些逻辑被锁住了 |
| 5 | `docs/22-dependencies.md` | 用了哪些轮子、版本改哪里 |

需要细节时再查：`11`（JDK / CI）、`23`（多语言方案）、`24`（主题）、`25`（接口文档）、
`20`（CMS 嵌入计划）、`13`（长期路线）。

### 4.2 每轮固定动作

1. **选任务**：从 `docs/13-roadmap.md` 或上一轮 5.4 的候选里挑一个，先写清验收标准；
2. **看现状**：先读相关模块的 `pom.xml` / 包说明注释 / 现有测试，不要凭记忆改；
3. **写代码**：遵守模块边界（`modules` 之间不直接依赖 Mapper，走 api 或事件）；
4. **补单测**：改到错误码、安全规则、命名映射、cron、树算法，必须加/改测试；
5. **同步文档**（这是本项目的硬要求）：
   - `docs/21-build-log.md` 追加一节：背景、改动、验证、踩坑、文件清单；
   - `docs/22-dependencies.md`：加了依赖就更新，没加就写“无新增”；
   - `docs/11-jdk-engineering.md`：依赖矩阵 / CI 有变化才改；
   - `docs/26-testing-ci.md`：测试数量与覆盖变化才改；
   - `README.md` 与 `lx-ui-admin/README.md`：能力或页面有变化才改。
6. **验证**：`mvn -B -ntp test` + `npm run build`；涉及接口 / 页面再跑对应脚本；
7. **打包**：`deploy/package.ps1`（Windows）或 `deploy/package.sh`（Linux），产物在 `release/`；
8. **提交**：一个任务一个 commit，message 写“做了什么”，不要写“update”。

### 4.3 当前进度快照（2026-11-15）

已完成：

- 后端 v1.0 基座：登录鉴权、8 组系统管理、文件中心、监控、定时任务、代码生成器、模块开关；
- 前端 18 个业务页面 + 动态路由 + 主题切换 + 个人中心；
- 登录安全（失败锁定、密码策略）、发布包与部署脚本；
- 112 项 JUnit 单测 + GitHub Actions 最小 CI；
- 文档 01~27。

下一轮候选（按优先级）：

1. 多语言 L1（`docs/23-i18n-plan.md`）：后端 MessageSource + 错误码 key 化，前端 vue-i18n；
2. 中文接口名注解（`docs/25-api-doc.md` 第 4 节）：`@Tag` / `@Operation` / `@Schema` + 生成器模板；
3. Excel 导入导出；
4. 管理员使用手册；
5. 清理 `.local` 临时产物、把验证脚本整理进 `tools/`。

### 4.4 不在仓库里的东西，怎么带走

以下内容被 `.gitignore` 排除，clone 后不会存在：

| 内容 | 为什么不在仓库 | 新机器怎么办 |
|---|---|---|
| `.local/verify-*.ps1` | 本机接口回归脚本，路径含本机环境 | 从旧机器复制，或按需重写；只影响发版前回归 |
| `.local/uitest/*.mjs` | Puppeteer 页面脚本 + Chrome 路径 | 同上；需要先 `npm i puppeteer-core`，并把脚本顶部的 `CHROME` 常量改成新机器路径 |
| `.local/mysql/` | 隔离 MySQL 数据目录，体积大 | 新机器建新库，Flyway 自动迁移 |
| `data/upload/` | 历史上传文件 | 需要历史附件时单独拷贝 |
| `release/*.zip` | 发布包 | 新机器重新 `deploy/package.*` 生成 |
| `*.log` / 截图 | 运行产物 | 不需要 |

如果希望这些脚本以后“跟着仓库走”，下一轮建议把**筛选后的**脚本移到 `tools/verify/`
与 `tools/uitest/`（只保留 `smoke` / `permission` / `theme` / `monitor` / `job` / `gen` /
`image` / `apidoc` / `profile` / `login-security` 等正式用例，去掉 `debug-*`），
并把文档里的 `.local/` 路径一起替换。当前轮没有做，避免一次改太多影响验证。

临时备份命令（Windows）：

```powershell
$dst = "E:\backup\lingxiao-local"
New-Item -ItemType Directory -Force $dst | Out-Null
Copy-Item .local\verify-*.ps1 $dst
robocopy .local\uitest $dst\uitest *.mjs package.json package-lock.json fixtures /S
```

### 4.5 安全与配置约定

- `application.yml` 里的数据库密码是**开发默认值**，生产用环境变量或 `deploy/config/application-prod.yml` 覆盖；
- 不把真实密钥、Token、客户数据写进仓库；`deploy/sql/init-db.sql` 的 `CHANGE_ME` 就是提醒；
- 生产启动参数关闭生成器 / 接口文档 / SQL 日志（见 `deploy/README.md` 与 `docs/25-api-doc.md`）；
- 升级依赖只改 `lx-dependencies/pom.xml` 与 `package.json`，并同步 `docs/22`。

---

## 5. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 启动报 `Access denied for user 'lingxiao'` | `DB_PASSWORD` 与建库时不一致；或没执行 `init-db.sql` |
| 启动报 `Unknown database 'lingxiao'` | 没建库；执行 `deploy/sql/init-db.sql` |
| 连不上数据库 | 默认端口是 **3307**，本机 MySQL 是 3306 时设 `DB_PORT=3306` |
| Flyway 报 checksum mismatch | 改过已经执行过的 `V*.sql`；不要改历史脚本，新增 `V15__xxx.sql` |
| 前端 401 / 接口 404 | 后端没起或端口不是 8080；检查 `.env.development` 的 `VITE_PROXY_TARGET` |
| 上传图片 404 | `lx.file.local-path` 相对启动目录；确认 `data/upload` 可写 |
| 导完 `lingxiao-1.0.0.sql` 后首页统计是 0、文件中心空 | 正常：快照里日志与文件记录已清空，快照只保证菜单 / 角色 / 参数等基础数据 |
| 导入快照报 `Table 'lingxiao.xxx' already exists` | 快照里是 `DROP TABLE IF EXISTS`，正常不会报；若手工改过文件，改用方式 A 重建空库 |
| `npm ci` 失败 | Node 版本过低（需 18+）；或 `package-lock.json` 与 `package.json` 不同步 |
| `mvn package` 报 `Unable to rename ... lingxiao.jar` | Windows 下后端正从该 jar 运行；先停服务再打包 |
| CI 第一次跑失败 | 看 Actions 日志；先在本地跑 `mvn -B -ntp test` 与 `npm run build` 复现 |
| `git push` 报 `Failed to connect to 127.0.0.1 port 10808` | `~/.gitconfig` 给 `https://github.com` 配了 `socks5h` 代理但代理没开。临时绕过：`git -c "http.https://github.com.proxy=" push`；或删掉 `[http "https://github.com"] proxy` 那段 |
| `ssh -T git@github.com` 报 `Permission denied (publickey)` | 公钥没加到 GitHub。用 `curl https://github.com/<你的账号>.keys` 核对（返回空就是没加），指纹用 `ssh-keygen -lf ~/.ssh/id_rsa.pub` 对 |
| 加完公钥立刻 push 仍失败 | 大概率是加到了 `SSH signing keys` 而不是 `SSH keys`；两者不在同一页 |
| `api.github.com` 请求全是 301 | 本机网络对 API 域做了重定向，`github.com` 正常；不影响 `git push`，只是没法用 API 查 Actions |

---

## 6. 一句话

**代码、文档、测试、CI 都在仓库里；数据库、上传文件、发布包、验证脚本是“环境产物”，换机器要重新生成或单独备份。**
新机器按「建库 → 打包 → 起后端 → `npm ci` 起前端 → 改 admin 密码 → `mvn test`」走一遍，
再从 `docs/21-build-log.md` 最后一节接着做，就不会丢上下文。
