# AGENTS.md · 仓库约定（给 AI 助手与接手者）

> 本文件只放**稳定约定与硬约束**。进度、待办、遗留看 `docs/13-roadmap.md` §12；
> 每一轮的决策、验证与踩坑看 `docs/21-build-log.md`。
> 数字类基线（单测数、迁移版本、菜单 / 权限码上限）以代码与脚本为准，本文只写“去哪里查”。

## 1. 先读什么

| 要做的事 | 先读 |
|---|---|
| 了解进度与下一步 | `docs/13-roadmap.md` §12（待办清单，按时间追加，最后几节是最新状态） |
| 动 CMS | `docs/20-cms-embed-plan.md`、`docs/29-portal-api.md`、`lx-modules/lx-module-cms/README.md` |
| 动会员 | `docs/30-member-embed-plan.md`、`lx-modules/lx-module-member/README.md` |
| 动门户 | `docs/31-portal-frontend.md`、`lx-ui-portal/README.md` |
| 动多语言 | `docs/15-i18n.md`、`docs/23-i18n-plan.md` §10 |
| 动模块开关 / 新增模块 | `docs/06-modularity.md`、`lx-framework/README.md` |
| 动表结构 | `docs/05-database.md`、对应模块 `db/migration/<module>/` 下最近的脚本 |
| 安全域边界 | `docs/17-v1-plan.md` §3.4.1、`docs/04-permission.md` |

## 2. 开工前自检（每个窗口都做）

1. `git log -1`、`git status --porcelain`：确认基线、工作区干净；有未提交改动先说明，不要覆盖。
2. `mvn -o -B -ntp test`：确认基线全绿再动手（JDK 8 本机约 30 秒）。
3. **先方案后代码**：迁移编号、表、接口、权限码、菜单 ID、页面、验收脚本逐项列出，
   说明不与现有编号撞号，等确认后再写。有疑问先问，不要自行跳步。
4. 一次只做一个切片；切片做完再领下一个。

## 3. 常用命令

```bash
# 后端：全量单测（不依赖 MySQL / Redis）
mvn -o -B -ntp test

# 后端：只跑一个模块（内部模块没 install 到本地仓库，必须带 -am）
mvn -o -B -ntp -pl lx-modules/lx-module-cms -am test

# 后端：打包
mvn -o -B -ntp -DskipTests package

# 后台前端：构建 + 语言包自检（在 lx-ui-admin 下）
npm run build
node scripts/check-i18n-keys.mjs

# 门户：构建（在 lx-ui-portal 下）
npm run build
node scripts/check-portal-i18n.mjs   # 中英文案键一致 + 模板无硬编码中文

# 打包发布包（需要先有 jar / 后台 dist / 门户 .output）
bash deploy/package.sh 1.4.0

# 本地联调：临时关闭验证码（只用于自动化脚本，生产禁止）
java -jar lx-boot/target/lingxiao.jar --lx.captcha.enabled=false
```

本地隔离 MySQL 在 `127.0.0.1:3307`（`lingxiao / lingxiao123`），见 `docs/21` §2。
`.local/` 不入库，里面的 `*-check.mjs` 验收脚本是本机回归用，换机器需要重建。

**本机两个环境坑（先处理，否则第一步就失败）**

- **编译用的 JDK**：默认 `java` 是 JRE（`jre1.8.0_351`），没有 `javac`。类文件过期或改了依赖版本时，
  `mvn` 会报 `No compiler is provided in this environment`。先设置：
  `JAVA_HOME=D:\Program Files\Java\jdk1.8.0_351`，并把它的 `bin` 放到 `PATH` 最前面。
- **git**：换个用户执行 `git` 会报 `dubious ownership`。用
  `git config --global --add safe.directory E:/java/lingxiao` 处理；只加这一个目录，不要配置 `safe.directory=*`。
- **沙箱里的 `bash` 和 `npm` 都是坏的**：`bash` 指向 Windows 的 WSL 存根，
  `npm` 是 fnm 的包装脚本（`uname` 返回 Linux 会去调 `wslpath`）。
  跑 shell 脚本用 `/usr/bin/bash deploy/package.sh`；
  构建前端改用 `node lx-ui-portal/node_modules/nuxt/bin/nuxt.mjs build`
  或 `node <pkg 目录>/node_modules/npm/bin/npm-cli.js run build`。

## 4. 硬约束（违反即返工）

**安全域**

- 三套域互不认 Token：后台 `lx-platform-token`（`/api/admin/**`）、会员
  `lx-member-token`（`/api/app/**`）、租户域未实装。后端鉴权固定用
  `@RequiresPermission`（平台域）或 `StpMemberUtil`（会员域），不用裸 `@SaCheckPermission`。
- 门户只调 `/api/portal/**`、`/api/app/**`、`/api/file/**`，**禁止调 `/api/admin/**`**。
- 平台后台表不带 `tenant_id`，不建 `TenantContext`；`docs/13` 早期“必须保留 tenant_id”的说法已作废。
- 密钥只写不读；密码、验证码、找回归只存哈希；账号不存在时静默成功，不枚举用户。
- 富文本写入与读取各清洗一次（`lx-common-web` 的 `HtmlSanitizer`）。

**模块边界**

- 模块间不互相 import 实体 / Service，不建跨模块外键；跨模块只通过 `lx-api` 契约或只存 ID。
  CMS 不 import 会员类，会员不 import CMS 类。
- 文件只存 `fileId`（或 http(s) 外链），不建 `cms_file` / `mem_file`。
- 可选模块用启动期开关控制（`lx.module.modules.<code>.enabled` 或 `lx.module.<code>.enabled`）：
  关闭 = Bean 不装配、接口 404、迁移目录跳过、菜单与前端路由不下发。新模块必须过关闭态验证。
- 基座（`lx-framework` / `lx-common`）不 import 任何业务模块。

**数据库**

- Flyway 脚本放各模块 `src/main/resources/db/migration/<module>/`，**只新增、不改历史脚本**。
- 版本号全模块共用一个序列，当前最大 **V42**，下一个 **V43**；开工前仍以脚本目录为准。
- 多库兼容：不用 `find_in_set`、`ON DUPLICATE KEY`、`INSERT IGNORE`、`LIMIT`；
  幂等插入用 `INSERT … SELECT … WHERE NOT EXISTS`（V36 起个别历史脚本用了 `INSERT IGNORE`，不改，但新脚本回到标准 SQL）。
- 部门 / 栏目树用闭包表；主键雪花 ID，前端与脚本一律按字符串比较。
- 审计四字段 + `del_flag` + `version`；时间 `datetime(3)`；唯一键与状态字典沿用同模块现有写法。

**编号段（新增前先查脚本中的最大值）**

| 对象 | 现状 |
|---|---|
| Flyway | V1~V43 |
| 菜单 ID | 系统管理 1xxx 起、监控 / 工具 2xxx、CMS 3000~3103、会员 4000~4062、多语言 5000~5032 |
| 权限码 | `platform:system|monitor|tool|file|cms|member|i18n:*`，按钮级 `:add|edit|remove…` |
| `sys_config.config_id` | 系统参数 1~10，会员参数 11~34（`mem.*`）；新增前查迁移脚本 |
| 表前缀 | `sys_`（基座）/ `cms_` / `mem_` / `i18n_` |

**代码与文案**

- 后端 JDK 8 语法（不用 `var`、`record`、`List.of`、文本块）；Java 4 空格缩进，类与公开方法写中文 Javadoc。
- 前端 2 空格、组合式 API + `<script setup>`；后台新增用户可见文案必须中英双份
  （`lx-ui-admin/src/i18n/{zh-CN,en-US}.js`），提交前跑 `node scripts/check-i18n-keys.mjs`。
- 后端错误与校验文案走 `i18n/messages*.properties`；不把中文硬编码进异常。
- **不要用 PowerShell 管道改源码**（默认 ANSI 编码会把中文写坏），一律用编辑工具。
- 不引入新依赖前先看 `docs/22-dependencies.md`；版本统一在 `lx-dependencies/pom.xml`。

## 5. 交付前检查

- [ ] `mvn -o -B -ntp test` 全绿
- [ ] 后台 `npm run build` + `node scripts/check-i18n-keys.mjs`（`missing=none`、无 dotted-leaf）
- [ ] 动了门户：门户 `npm run build` + `node scripts/check-portal-i18n.mjs`
- [ ] 新迁移：真实库启动一次，`flyway_schema_history` 到新版本
- [ ] 可选模块：关闭后启动成功、接口 404、菜单不下发；重开后数据还在
- [ ] 接口脚本 `.local/*-check.mjs` 覆盖正常路径、越权、关闭态
- [ ] 文档同步：
  - `docs/21-build-log.md` 追加一小节（做了什么 / 关键决策 / 验证 / 踩坑 / 遗留）
  - `docs/13-roadmap.md` §12 追加小节并写明“下一步”
  - 对应模块 plan 文档（`docs/20` / `30` / `31` / `15` / `23`）与表、接口文档（`docs/05` / `25` / `29`）
  - 影响对外能力时同步 `README.md` 与 `README.en.md`
- [ ] 被后续切片完成的“遗留”项，回填到原处（划掉并指向新章节），不要留下互相矛盾的描述

提交信息沿用现有风格：`feat(cms): 站点主题、模板映射与门户 SEO 收口`、`fix(admin): …`、`docs: …`。
一个提交只做一件事。
