# 凌霄 · 部署手册（v1.0）

> 目标：一台 2C/4G 服务器 + JDK 8/11/17 + MySQL 5.7/8.0，`java -jar` 起后端，
> Nginx 提供前端静态文件并反代 `/api`。Redis 可选，单机不装也能跑。

---

## 1. 交付物长什么样

`deploy/` 是部署脚本与配置的**源目录**；执行打包脚本后会生成可直接上传的发布包：

```text
lingxiao-1.0.0/
├─ lingxiao.jar                 # 后端可执行 jar
├─ config/
│  └─ application-prod.yml      # 生产配置（敏感项走环境变量）
├─ web/                         # 前端 dist（Nginx root）
├─ bin/
│  ├─ start.sh / stop.sh / restart.sh / status.sh   # Linux
│  └─ start.ps1 / stop.ps1 / restart.ps1 / status.ps1  # Windows
├─ lingxiao.service             # systemd 单元示例
├─ nginx/
│  └─ lingxiao.conf             # Nginx 站点示例
├─ sql/
│  ├─ init-db.sql               # 建库 / 建账号 / 授权
│  └─ lingxiao-1.0.0.sql        # 数据库快照（23 张业务表 + 初始数据，可选导入）
├─ data/upload/                 # 本地文件存储（运行时写入）
├─ logs/                        # 日志
└─ run/                         # pid 文件
```

---

## 2. 前置条件

| 项 | 要求 |
|---|---|
| JDK | 8 / 11 / 17（项目按 JDK 8 编译，向上兼容） |
| MySQL | 5.7 / 8.0，字符集 `utf8mb4` |
| Redis | 可选；多实例部署时建议开启 |
| Nginx | 生产提供静态文件与 TLS |
| 内存 | 2C/4G 起步；`-Xmx512m` 足够后台基座 |

服务器时间建议设为 `Asia/Shanghai`；JVM 启动参数已固定 `-Duser.timezone=Asia/Shanghai`。

---

## 3. 从源码构建与打包

```bash
# 1) 后端
mvn -DskipTests package
# 产物：lx-boot/target/lingxiao.jar

# 2) 前端
cd lx-ui-admin
npm ci
npm run build
# 产物：lx-ui-admin/dist

# 3) 组装发布包
cd ..
bash deploy/package.sh 1.0.0        # Linux
# 或
powershell -ExecutionPolicy Bypass -File deploy\package.ps1 -Version 1.0.0   # Windows
```

产物在 `release/`：目录 + `.tar.gz`（Linux）或 `.zip`（Windows）。

> 打包脚本只复制构建产物，不替你执行 `mvn` / `npm`。这样发布包内容可控，
> 不会因为脚本里偷偷跳过测试而把未验证的代码带上线。

---

## 4. 初始化数据库

用 MySQL 管理员执行：

```bash
mysql -uroot -p < deploy/sql/init-db.sql
```

脚本只做三件事：

1. `CREATE DATABASE lingxiao`（`utf8mb4`）；
2. 建应用账号 `lingxiao`；
3. 授权 `SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/DROP/REFERENCES`。

**业务表不用手工导入**：应用启动时 Flyway 自动执行 `V1` 起的全部迁移（当前到 V42）。
应用账号必须有 `CREATE / ALTER / INDEX / DROP`，否则首次启动迁移会失败。
迁移完成后也不必回收这些权限：后续版本仍会加新脚本。

执行前记得把 `init-db.sql` 里的 `CHANGE_ME` 换成真实密码，并同步到
`DB_PASSWORD` 环境变量。

### 4.1 方式二：导入现成数据（可选）

如果不想等 Flyway 逐条迁移，或者想直接拿到一套配好的后台数据（菜单、角色、
参数、字典、定时任务都在），导入同目录的版本快照：

```bash
mysql -uroot -p < deploy/sql/lingxiao-1.0.0.sql
```

- 文件约 50 KB，包含 24 张表（23 张业务表 + `flyway_schema_history`）和 V1~V17 迁移记录；
- 因为带了迁移记录，导入后启动不会重复建表；V18 及之后的迁移会正常补执行；
- 里面有 `DROP TABLE IF EXISTS`，重复导入会把库重置成快照状态；
- 默认账号 `admin` / `admin123`，导入后请立刻改密码；
- 登录日志、操作日志、文件记录、代码生成临时表是空的，首页统计为 0 属于正常。
- 快照停在 v1.0 发布时的 V17，**没有刷新**：CMS、会员、多语言的表不在里面，导入后首次启动由 Flyway 补建。刷新计划见 `docs/13-roadmap.md` §12.29。

> 本目录目前只覆盖**后台**（`lx-ui-admin` + 后端 jar）。内容门户 `lx-ui-portal` 还没有
> 打包、进程脚本与 Nginx 示例，CI 也不构建它——这是已知缺口，见 `docs/13-roadmap.md` §12.28。

两种方式选一种即可；正式环境建议用方式一（Flyway），快照主要用于本地开发、
演示环境和“clone 下来马上能点”的场景。

---

## 5. 生产配置

发布包自带 `config/application-prod.yml`，启动时加 `--spring.profiles.active=prod`
即可被 Spring Boot 自动加载（`config/` 与 jar 同级）。

### 5.1 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端端口；只监听内网，由 Nginx 反代 |
| `DB_HOST` | `127.0.0.1` | MySQL 地址 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `lingxiao` | 数据库名 |
| `DB_USER` | `lingxiao` | 应用账号 |
| `DB_PASSWORD` | 空 | **必须设置** |
| `DB_POOL_SIZE` | `20` | Hikari 最大连接数 |
| `DB_MIN_IDLE` | `5` | Hikari 最小空闲 |
| `LX_REDIS_ENABLED` | `false` | `true` 时会话与验证码落 Redis |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | 本机 / 6379 / 空 / 0 | Redis 连接 |
| `LX_FILE_PATH` | `./data/upload` | 上传文件目录（相对应用根目录） |
| `LX_LOG_FILE` | `./logs/lingxiao.log` | 后端文件日志 |
| `LX_APP_NAME` | jar 内 `lx.app.name` | 全站中文品牌名（登录页 / 侧边栏 / 浏览器标题） |
| `LX_APP_NAME_EN` | jar 内 `lx.app.name-en` | 英文品牌名 |
| `LX_APP_VERSION` | 构建时的 pom 版本 | 展示版本；一般不用设，发版只改根 `pom.xml` |
| `SPRING_PROFILE` | `prod` | 启动脚本使用的 profile |
| `JAVA_OPTS` | `-Xms256m -Xmx512m -XX:+UseG1GC` | JVM 参数 |

Linux systemd 示例：

```ini
Environment="DB_PASSWORD=replace-me"
Environment="LX_REDIS_ENABLED=false"
```

Windows PowerShell：

```powershell
$env:DB_PASSWORD = 'replace-me'
.\bin\start.ps1
```

> 名称与版本的唯一真源是 `config/application-prod.yml` 的 `lx.app.*`
> （版本默认来自构建时的 pom）。前端启动时读取 `/api/admin/app/profile`，
> 换品牌不需要重新构建前端；没有前端包的情况下也能用环境变量覆盖。

### 5.2 生产配置已经关掉了什么

| 项 | 生产值 | 原因 |
|---|---|---|
| 代码生成器 | `lx.module.modules.generator.enabled=false` | 会暴露整库表结构与生成代码 |
| OpenAPI / Knife4j | `lx.api-doc.enabled=false`、`springdoc.*=false`、`knife4j.production=true` | 不对外暴露接口清单 |
| 文档静态入口 | `ApiDocDisabledFilter` 把 `/doc.html`、`/webjars/**` 等直接回 404 | Knife4j 页面是静态资源，只关 SpringDoc 关不掉 |
| SQL 日志 | `NoLoggingImpl` | 避免日志膨胀与参数泄露 |
| 演示模块 | `demo.enabled=false` | 基座必须能在关闭态启动 |
| 验证码 | `lx.captcha.enabled=true` | 防暴力破解；运行期仍受 `sys.account.captchaEnabled` 控制 |
| 文件目录 | `./data/upload` | 与工作目录绑定，迁移时整目录复制 |

---

## 6. 启动与停止

### 6.1 Linux（systemd，推荐）

```bash
sudo useradd -r -s /sbin/nologin lingxiao
sudo mkdir -p /opt/lingxiao
sudo tar -xzf lingxiao-1.0.0.tar.gz -C /tmp
sudo cp -R /tmp/lingxiao-1.0.0/. /opt/lingxiao/
sudo chown -R lingxiao:lingxiao /opt/lingxiao
sudo chmod +x /opt/lingxiao/bin/*.sh

sudo cp /opt/lingxiao/lingxiao.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lingxiao
sudo systemctl status lingxiao
```

看日志：

```bash
sudo journalctl -u lingxiao -f
tail -f /opt/lingxiao/logs/lingxiao.log
```

### 6.2 Linux（无 systemd，快速验证）

```bash
cd /opt/lingxiao
export DB_PASSWORD='replace-me'
./bin/start.sh
./bin/status.sh
./bin/stop.sh
```

`start.sh` 会把 pid 写到 `run/lingxiao.pid`，控制台输出写到 `logs/console.log`。

### 6.3 Windows

```powershell
cd C:\lingxiao
$env:DB_PASSWORD = 'replace-me'
powershell -ExecutionPolicy Bypass -File .\bin\start.ps1
powershell -ExecutionPolicy Bypass -File .\bin\status.ps1
powershell -ExecutionPolicy Bypass -File .\bin\stop.ps1
```

要注册成 Windows 服务，可用 [NSSM](https://nssm.cc/) 或 WinSW，把
`java.exe -jar C:\lingxiao\lingxiao.jar --spring.profiles.active=prod`
设为启动命令，工作目录设为 `C:\lingxiao`。

---

## 7. Nginx

```bash
sudo cp /opt/lingxiao/nginx/lingxiao.conf /etc/nginx/conf.d/lingxiao.conf
sudo vim /etc/nginx/conf.d/lingxiao.conf   # server_name / root 按实际改
sudo nginx -t
sudo systemctl reload nginx
```

关键点：

- `root` 指向 `/opt/lingxiao/web`（前端 dist）；
- `location /` 使用 `try_files ... /index.html` 支持 Vue Router history 模式；
- `location /api/` 反代到 `127.0.0.1:8080`，保留 `/api` 前缀；
- `client_max_body_size 20m` 与后端 `spring.servlet.multipart.max-request-size` 对齐；
- 生产只开放 80/443，8080 不要暴露到公网；
- 上线前加 TLS（certbot / 云厂商证书），并把 HTTP 301 到 HTTPS。

---

## 8. 上线检查清单

- [ ] `DB_PASSWORD` 已设置，不是空值，不是 `lingxiao123`。
- [ ] 启动日志出现 `Successfully applied ... migration` 或 `Schema ... is up to date`。
- [ ] 登录页能打开，`admin` 能登录。
- [ ] **立即修改 `admin` 默认密码**：个人中心 → 修改密码。
- [ ] `sys.account.captchaEnabled` 为开启；退出后验证码输入框出现。
- [ ] 新建一个测试角色 / 用户，确认未授权菜单不可见、直打接口返回 403。
- [ ] 上传一张图片，文件中心能看到缩略图，详情能预览中图。
- [ ] 关闭代码生成器后，`/api/admin/tool/gen/list` 返回 404，基座仍可用。
- [ ] `/doc.html`、`/v3/api-docs` 返回 404（生产不应暴露接口文档）。
- [ ] `curl -fsS http://127.0.0.1:8080/api/admin/auth/captcha` 返回 200。
- [ ] Nginx 反代后 `/api` 正常，前端刷新深链不 404。
- [ ] 日志目录、上传目录有写权限，磁盘剩余空间足够。
- [ ] MySQL 已配置定期备份；`data/upload` 与数据库一起备份。
- [ ] 防火墙只开放 22 / 80 / 443。

---

## 9. 升级与回滚

升级：

```bash
sudo systemctl stop lingxiao
sudo cp lingxiao-1.0.1.jar /opt/lingxiao/lingxiao.jar
sudo cp -R lingxiao-1.0.1/web/. /opt/lingxiao/web/
sudo systemctl start lingxiao
```

Flyway 在启动时自动执行新增迁移。**不要手工改已执行过的迁移文件**，
校验失败会阻止启动（这是有意设计）。

> **从 V13 升级到 V14（登录失败锁定与密码策略）的额外动作**
>
> 1. `V14__login_security.sql` 会新增 `sys_user.login_fail_count` / `lock_time` 两列，
>    并向 `sys_config` 写入 3 个参数（`sys.account.maxRetryCount` / `lockMinutes` / `passwordPolicy`），
>    同时新增按钮权限 `platform:system:user:unlock`（menu_id=105）并授权给超级管理员角色；
> 2. 迁移后所有账号的失败计数为 0、未锁定，不需要数据清洗；
> 3. 已在线的用户需要**重新登录**一次，前端 `/auth/info` 才会拿到新权限，
>    用户页的「解锁」按钮才会出现；
> 4. 回滚到旧版本时，旧代码不读这两列，但 Flyway 校验会因多出 V14 而失败：
>    回滚前需从备份恢复，或手工删除 `flyway_schema_history` 中 `version='14'` 的记录。

回滚：

1. 换回旧 jar 与旧 `web/`；
2. 如果新版本已经执行了不可逆迁移（例如删列），需要从备份恢复数据库；
3. 因此升级前必须备份数据库与 `data/upload`。

---

## 10. 备份

```bash
# 数据库
mysqldump -h127.0.0.1 -ulingxiao -p --single-transaction --routines \
  --default-character-set=utf8mb4 lingxiao > lingxiao-$(date +%F).sql

# 上传文件
tar -czf upload-$(date +%F).tar.gz -C /opt/lingxiao data/upload
```

建议每天备份、保留 7~30 天，并定期做恢复演练。

---

## 11. 常见问题

| 现象 | 排查 |
|---|---|
| 启动报 `Access denied` | `DB_USER` / `DB_PASSWORD` 不对；账号来源 IP 是否被限制 |
| 启动报 Flyway `CREATE` 权限不足 | 应用账号缺少 `CREATE/ALTER/INDEX/DROP` |
| 上传返回 413 | Nginx `client_max_body_size` 小于后端上限 |
| 上传返回“文件超过 10MB 限制” | 参数设置 `sys.file.uploadMaxMb` 或 `lx.file.max-size-mb` |
| 图片没有缩略图 | 原图小于 160px、WebP、或超过 `max-pixels`；看日志有无 warn |
| 重启后所有用户 401 | `lx.redis.enabled=false` 时会话在内存；多实例或无状态部署要开 Redis |
| 页面能开但接口 401 | Nginx 没转发 `Authorization`/自定义 Token 头；确认 `location /api/` 配置 |
| 中文乱码 | 数据库 / 表 / 连接串统一 `utf8mb4`；JVM 已有 `-Dfile.encoding=UTF-8` |
| 时间差 8 小时 | JVM 时区、MySQL `serverTimezone=Asia/Shanghai`、Nginx 不影响时间 |
| 日志太大 | 生产 `application-prod.yml` 已按 50MB 滚动、保留 30 份、总量 2GB |

---

## 12. 安全边界

- 平台后台域与会员域物理隔离；v1.0 不建 `tenant_id`，不要提前加。
- `/api/file/{fileId}` 是**公开访问**，只适合公告配图等公开素材；
  合同 / 证件等私有附件后续必须走签名 URL 或鉴权下载。
- `admin` 默认密码只用于首次登录，上线后立即修改。
- 生产关闭代码生成器与接口文档；需要临时排障时再打开，用完关掉。
- 数据库账号只授权 `lingxiao` 库，不要用 root 跑应用。
- 上传目录不要配置成 Nginx 静态目录，所有文件统一走 `/api/file/{fileId}`，
  避免绕过业务层直接枚举磁盘文件。
