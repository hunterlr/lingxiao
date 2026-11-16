# lx-ui-admin · 凌霄后台管理前端

Vue 3 + Vite + Element Plus 的后台管理界面，对接 `lx-admin-api` 的 `/api/admin/**`。
只做后台管理，不含门户 / H5（属于后续模块）。

---

## 1. 快速启动

```bash
cd lx-ui-admin
npm install
npm run dev        # http://127.0.0.1:5173
```

前置条件：后端已在 `http://127.0.0.1:8080` 启动，数据库可用。

开发代理在 `vite.config.js` 里：`/api` → `http://127.0.0.1:8080`，
接口文档另代理 `/doc.html`、`/v3/api-docs`、`/webjars`、`/swagger-ui`、`/favicon.ico`，
所以前端代码里所有请求都写成 `/api/admin/...`，与后端路径完全一致，
生产交给 Nginx 转发，不需要改代码。

默认账号：`admin / admin123`。

### npm 11 的安装脚本审批

npm 11.19 默认拦截依赖的 postinstall 脚本。本项目需要 `esbuild`（Vite 的打包器）
和 `vue-demi`（Pinia 的 Vue 版本适配）的安装脚本，已在 `package.json` 的
`allowScripts` 中放行：

```json
"allowScripts": {
  "esbuild@0.21.5": true,
  "vue-demi@0.14.10": true,
  "es5-ext": false
}
```

如果换机器 / 升级依赖后 `vite` 启动报 `esbuild: Failed to install correctly`，
执行：

```bash
npm install-scripts approve esbuild
npm install-scripts approve vue-demi
```

### 验证码开关

验证码有**两级开关**：

1. `lx.captcha.enabled`（启动参数，默认 true）：总闸，仅供本地联调 / 自动化测试；
2. `sys.account.captchaEnabled`（参数设置，默认 true）：运行期开关，运营可改。

本地联调不想手动输验证码：

```bash
java -jar lx-boot/target/lingxiao.jar --lx.captcha.enabled=false
```

关闭后 `GET /auth/captcha` 返回 `enabled=false`，登录页自动隐藏验证码输入框，
`captchaKey / captchaCode` 不再必填，前端也不会发送这两个字段。**生产环境必须保持开启。**

在参数设置里把 `sys.account.captchaEnabled` 改成 `false` 也能达到同样效果（无需重启），
改完刷新登录页即可看到输入框消失。读取参数失败时按“开启”处理，避免参数库异常导致登录裸露。

---

## 2. 目录结构

```text
lx-ui-admin/
├─ index.html
├─ vite.config.js           # 端口、/api 代理、@ 别名
├─ .env.development         # 代理目标、前端端口
├─ .env.production          # 生产 API 前缀
└─ src/
   ├─ main.js               # 应用入口：Pinia / Router / Element Plus / 权限指令
   ├─ App.vue
   ├─ api/                  # 按后端模块拆分的接口封装
   │  ├─ request.js         # axios 实例：Token、统一 code、401、错误提示
   │  ├─ auth.js            # 登录 / 登出 / 当前用户 / 菜单 / 验证码
   │  ├─ user.js role.js menu.js dept.js post.js dict.js config.js notice.js file.js log.js monitor.js job.js gen.js
   ├─ assets/styles/index.css   # 设计变量 + 管理页通用类
   ├─ components/
   │  ├─ AppIcon.vue        # 后端菜单图标名 → Element Plus 图标
   │  ├─ DictTag.vue        # 字典值 → 标签
   │  ├─ DictSelect.vue     # 字典下拉
   │  ├─ FileLibrary.vue    # 文件网格 / 上传 / 改名 / 详情（文件中心唯一实现）
   │  ├─ RichEditor.vue     # 富文本外壳：双引擎 + 右上角切换下拉（v-model HTML）
   │  └─ editor/
   │     ├─ WangEditorNext.vue  # 默认引擎
   │     └─ UEditorPlus.vue     # 备用引擎（iframe + uploadServiceUpload）
   ├─ composables/
   │  ├─ useDict.js         # 字典缓存（按 dictType 复用，避免重复请求）
   │  └─ useEditorEngine.js # 编辑器三层优先级：本机偏好 > 平台默认 > 构建兜底
   ├─ directives/permission.js  # v-hasPerm
   ├─ layout/               # 侧边栏 / 顶栏 / 面包屑 / 主布局
   ├─ router/
   │  ├─ index.js           # 静态路由 + 守卫注册
   │  ├─ guard.js           # 登录态、动态路由装载、标题
   │  └─ dynamic.js         # 后端菜单 → 路由（找不到组件落占位页）
   ├─ store/
   │  ├─ auth.js            # Token、用户信息、权限、菜单
   │  └─ app.js             # 侧边栏折叠
   ├─ utils/
   │  ├─ auth.js            # Token 读写（localStorage）
   │  ├─ tree.js            # 平铺转树、路径反查、子树收集
   │  └─ index.js           # 空值、大小、时间格式化
   └─ views/
      ├─ login/             # 登录页
      ├─ dashboard/         # 首页
      ├─ profile/           # 个人中心（自助改资料 / 改密）
      ├─ error/             # 403 / 404
      ├─ placeholder/       # 未实现菜单的占位页
      ├─ system/            # 用户 / 角色 / 菜单 / 部门 / 岗位 / 字典 / 参数 / 公告
      ├─ monitor/           # 在线用户 / 服务监控 / 缓存监控 / 操作日志 / 登录日志
      ├─ tool/file/         # 文件中心（上传、搜索、改名、改名历史）
      ├─ tool/job/          # 定时任务（CRUD、启停、执行一次、执行日志）
      ├─ tool/gen/          # 代码生成（导入、配置、预览、下载、删除）
      └─ tool/apidoc/       # 接口文档（iframe 内嵌 Knife4j /doc.html）
```

---

## 3. 认证与请求约定

### 3.1 Token

- 登录响应返回 `tokenName`（固定 `lx-platform-token`）与 `tokenValue`。
- 前端把两者都存 localStorage；请求拦截器按 `tokenName` 设置请求头。
- 不写死请求头名，为后续租户域 / 会员域留口子。

### 3.2 统一响应

后端所有接口返回：

```json
{ "code": 0, "msg": "操作成功", "data": {} }
```

`api/request.js` 的处理：

| 情况 | 行为 |
|---|---|
| `code === 0` | 直接返回 `data`，页面不用再解一层 |
| `code !== 0` | `ElMessage.error(msg)`，抛出带 `code` 的 `BizError` |
| HTTP 401 / `code === 1002` | 清 Token，整页跳 `/login?redirect=...` |
| HTTP 403 | 提示“没有权限执行该操作” |
| 请求配置 `silent: true` | 失败不弹提示（用于探测型请求） |

### 3.3 长整型 ID

后端 Jackson 全局把 `Long` 序列化成字符串，所以前端拿到的
`userId / roleId / menuId / fileId` 都是字符串。**不要 `parseInt`**，
19 位雪花 ID 会丢精度；比较一律用 `String(a) === String(b)`。

---

## 4. 权限与动态路由

### 4.1 登录后加载顺序

1. `POST /api/admin/auth/login` 拿 Token；
2. 路由守卫发现 `loaded === false`，并行请求 `GET /auth/info` 与 `GET /auth/menus`；
3. `router/dynamic.js` 把菜单（type=C）注册成 Layout 的子路由；
4. 重新进入目标地址（`replace`），避免第一次导航命中 404。

刷新页面走同一条路径，所以深链可以直接打开。

### 4.2 菜单 → 路由映射

- 菜单的 `path` 直接作为路由地址（如 `/system/user`）；
- 菜单的 `component` 映射到 `src/views/{component}.vue`；
- 组件文件不存在时落到 `views/placeholder/index.vue`，页面会显示
  “缺哪个文件、计划版本”，比直接 404 更好排查；
- 目录（M）不生成路由，只参与侧边栏与面包屑；
- 按钮（F）不生成路由，只作为权限码。

### 4.3 按钮权限

```vue
<el-button v-hasPerm="'platform:system:user:add'">新增用户</el-button>
<el-button v-hasPerm="['platform:system:user:edit', 'platform:system:user:add']">保存</el-button>
```

数组表示任一命中；超管权限 `*:*:*` 直接放行。

前端隐藏按钮只是体验优化，**真正的权限边界在后端 `@RequiresPermission`**。

### 4.4 文件接口权限（容易漏）

文件中心是文件管理的唯一入口（菜单里不再有重复的「文件库」），后端按动作分开校验：

| 动作 | 需要的权限码 |
|---|---|
| 上传（含富文本插图） | `platform:file:upload` |
| 列表 / 详情 / 改名历史 | `platform:file:list` |
| 改名 | `platform:file:rename` |

因此：

- 授权「文件中心」时，`platform:file:list` + `upload` + `rename` 三个都要给；
- 只让某人写公告、不让他进文件中心时，单独勾「文件上传」即可 —— 富文本插图走同一个接口；
- 以后如果做“素材库 / 引用管理”，会作为独立功能（选择器 + 引用统计）重新建菜单，
  不再复用「文件库」这个名字。

未授权深链（例如只有公告权限却直接访问 `/system/user`）落到 **404 兜底页**，
因为动态路由根本没注册该页面；`403` 留给“页面存在但接口拒绝”的情况。

### 4.5 日志接口权限（看与清空分开）

| 动作 | 权限码 |
|---|---|
| 操作日志列表 / 详情 | `platform:monitor:operlog:list` |
| 清空操作日志 | `platform:monitor:operlog:clean` |
| 登录日志列表 | `platform:monitor:loginlog:list` |
| 清空登录日志 | `platform:monitor:loginlog:clean` |

清空是不可逆操作，故意不与 `list` 共用一个权限码：能看审计数据的人不应该自动拥有删除审计数据的权力。
两个清空按钮都用 `v-hasPerm` 隐藏，后端 `@RequiresPermission` 仍是最终边界。

### 4.6 监控接口权限

| 动作 | 权限码 |
|---|---|
| 在线用户列表 | `platform:monitor:online:list` |
| 强制下线（单条 / 批量） | `platform:monitor:online:kickout` |
| 服务监控 | `platform:monitor:server:list` |
| 缓存监控 | `platform:monitor:cache:list` |

强退与列表分开授权（Flyway `V10`）：能看在线列表不等于能把别人踢下线。
列表返回的 `tokenId` 是脱敏值（首 4 + `****` + 末 4），不是可直接冒充登录的 Token。

### 4.7 定时任务接口权限

| 动作 | 权限码 |
|---|---|
| 任务列表 / 详情 / 执行器下拉 | `platform:monitor:job:list` |
| 新增 / 编辑 / 删除 | `platform:monitor:job:add` / `edit` / `remove` |
| 启停开关 | `platform:monitor:job:changeStatus` |
| 执行一次 | `platform:monitor:job:run` |
| 执行日志 | `platform:monitor:job:log` |
| 清空日志 | `platform:monitor:job:log:clean` |

“执行一次”与“清空日志”都会实际改变系统状态，单独授权（Flyway `V11`）：
能看任务列表的人不应该自动拥有触发任务或删除审计日志的权力。
列表里的启停开关按 `changeStatus` 置灰，按钮统一 `v-hasPerm`，后端 `@RequiresPermission` 仍是最终边界。

### 4.8 代码生成接口权限

| 动作 | 权限码 |
|---|---|
| 列表 / 详情 / 数据库表清单 | `platform:tool:gen:list` |
| 导入表 | `platform:tool:gen:import` |
| 配置表 / 字段 | `platform:tool:gen:edit` |
| 删除配置 | `platform:tool:gen:remove` |
| 预览代码 | `platform:tool:gen:preview` |
| 下载 ZIP | `platform:tool:gen:download` |

预览和下载都会把整张表的字段与生成代码暴露出来，必须单独授权（Flyway `V12`）。
只给 `list` 的角色能看列表、看库表、看配置，但导入 / 改 / 删 / 预览 / 下载都会被后端 403。
生成器默认开启，生产环境建议关掉 `lx.module.modules.generator.enabled`。

---

## 5. 页面清单

| 页面 | 路径 | 状态 | 对应接口 |
|---|---|---|---|
| 登录 | `/login` | 完成 | `GET /auth/captcha`、`POST /auth/login` |
| 首页 | `/dashboard` | 完成 | 复用 `/auth/info` |
| 用户管理 | `/system/user` | 完成 | `/system/user/**`（锁定列 / 解锁按钮、重置密码同时清锁定） |
| 角色管理 | `/system/role` | 完成 | `/system/role/**`（含菜单授权树） |
| 菜单管理 | `/system/menu` | 完成 | `/system/menu/**` |
| 部门管理 | `/system/dept` | 完成 | `/system/dept/**` |
| 岗位管理 | `/system/post` | 完成 | `/system/post/**` |
| 字典管理 | `/system/dict` | 完成 | `/system/dict/**`（左类型右数据） |
| 参数设置 | `/system/config` | 完成 | `/system/config/**`（文本 / 数字 / 开关 / 下拉控件，选项可维护） |
| 公告管理 | `/system/notice` | 完成 | `/system/notice/**`（wangEditor Next / UEditor Plus 可切换） |
| 文件中心 | `/tool/file` | 完成 | `/file/upload`（`platform:file:upload`）、`/file/list`、改名（`platform:file:rename`）、历史；网格走 `?size=thumb`、详情走 `?size=medium` |
| 操作日志 | `/monitor/oper-log` | 完成 | `/system/oper-log/list`、`/{operId}`、`DELETE /clean` |
| 登录日志 | `/monitor/login-log` | 完成 | `/system/login-log/list`、`DELETE /clean` |
| 个人中心 | `/profile` | 完成（自助改资料 / 改密，改密提示跟随密码策略） | `/auth/info`、`PUT /auth/profile`、`PUT /auth/password` |
| 在线用户 | `/monitor/online` | 完成 | `/monitor/online/list`、`POST /monitor/online/kickout`（一个 Token 一行、脱敏标识、禁止自踢） |
| 服务监控 | `/monitor/server` | 完成 | `/monitor/server`（CPU / 物理内存 / JVM / 系统 / 磁盘） |
| 缓存监控 | `/monitor/cache` | 完成 | `/monitor/cache`（Caffeine 统计 / Redis INFO，异常降级） |
| 定时任务 | `/tool/job` | 完成 | `/monitor/job/**`（执行器下拉、6 段 cron 校验、启停、执行一次、执行日志 / 清空） |
| 代码生成 | `/tool/gen` | 完成 | `/tool/gen/**`（导入表、表 / 字段配置、预览 7 个文件、下载 ZIP、删除配置） |
| 接口文档 | `/tool/api-doc` | 完成 | iframe 内嵌 `/doc.html`（SpringDoc + Knife4j），顶部可复制 Token 用于在线调试 |

---

## 6. 几个实现决策

1. **Element Plus 全量注册**：后台对体积不敏感，换取“少一类忘记注册组件的坑”。
   后续要压缩首屏再换按需引入。
2. **动态路由扁平注册**：所有业务页面都是 Layout 的直接子路由（绝对路径），
   侧边栏层级由菜单树渲染。这样移动菜单不需要改前端路由结构。
3. **`route.path` 作为页面 key**：切页销毁旧组件，避免搜索条件 / 分页状态串页。
4. **字典前端缓存**：`useDict` 模块级缓存，按 `dictType` 复用；改字典后刷新页面生效。
5. **文件管理只有一个入口**：文件中心承载上传 / 搜索 / 改名 / 改名历史，
   不再拆「文件库」这种同数据源、同操作的重复页面。图片裁剪 / 水印属于
   “上传时处理”，按 `docs/09-media.md` 在 P5 做（参数 + 上传管线），不做独立栏目。
6. **不做假仪表盘**：没有统计接口就不编数字，首页只展示真实登录信息与菜单入口。

7. **富文本双引擎**：公告正文的编辑器可切换 —— 本机偏好（`localStorage.lx-editor-engine`）
   > 平台默认（系统参数 `sys.editor.type`，登录即可读 `/system/config/public/value`）> 构建兜底
   （`VITE_RICH_EDITOR`）。切换只影响当前浏览器，不打断别人正在编辑的草稿。

8. **锁定状态以 `lock_time` 为准**：列表判断是否锁定用「锁定截止时间是否晚于当前时间」，
   而不是看失败次数；解锁按钮只在锁定时出现，解锁后刷新列表，不本地猜测状态。

9. **主题只覆盖 CSS 变量**：亮/暗、主色、侧栏深浅全部通过 `--lx-*` 与
   `--el-color-primary-*` 变量表达，页面不写 `dark` 判断、不维护两套样式。
   状态在 `store/app.js`，落 `localStorage.lx-admin-theme`；`utils/theme.js`
   负责颜色混合与写 DOM。详见 [docs/24-theme-switch.md](../docs/24-theme-switch.md)。

---

## 7. 已知限制（与后端 W3/W4/W5 对应）

- 文件缩略图：新上传的 JPEG / PNG / GIF / BMP 会自动生成 thumb（≤160px）与 medium（≤800px）；
  升级前已存在的图片没有历史变体，仍回退原图。WebP 保持原样、不生成变体。
- 公告正文：服务端已接 Jsoup 白名单清洗（写入 + 读取两侧），内联样式会被剥离。
- 个人中心：只开放昵称 / 邮箱 / 手机号与改密；头像、手机号唯一性、历史密码暂未做。
  密码复杂度由后端 `sys.account.passwordPolicy`（off / basic / strong）校验，前端只做长度与一致性提示。
  改密成功后该账号所有端会话立即失效，前端会清 Token 回登录页。
- 接口文档：`/doc.html` 不自动带 Token，需在 Knife4j「全局参数设置」里手动粘贴（页面顶部可复制）；
  生产环境应同时关 `lx.api-doc.enabled`、`springdoc.api-docs.enabled`，并置 `knife4j.production=true`。
- 数据范围：用户列表与用户写操作已生效；部门 / 角色列表暂未过滤，角色页的“自定义数据”为禁用项。
- 系统监控：在线用户 / 服务监控 / 缓存监控已接真实数据。
- 代码生成：只做单表、单主键；下载 ZIP 后需人工放进模块并编译，不自动写工程目录、不注册前端路由。
  模板不支持运行时热加载，改模板要重新打包。生产环境建议关闭 `lx.module.modules.generator.enabled`。
- 定时任务：Spring Task 不持久化触发器，停机期间错过的执行不补跑；`misfirePolicy` 只是兼容字段，页面已注明。
  单个任务执行时间必须小于锁超时（默认 600 秒），否则可能被其它节点接管并重复执行。
- 在线用户“最后活动”：未开启 Sa-Token `active-timeout` 时后端回退成登录时间，页面会显示与登录时间相同。
- 缓存监控：只展示验证码存储与 Redis `INFO` 摘要，不单独统计 Sa-Token 会话数量。
- 主题：偏好只存当前浏览器（localStorage），不跨设备；登录页没有主题入口，但会读取已保存主题；
  Knife4j `/doc.html` 是独立页面，不跟随后台暗色。

---

## 8. 构建与联调

```bash
npm run build     # 产物在 dist/
npm run preview   # 本地预览构建产物（4173）
```

`npm install` / `npm run dev` / `npm run build` 会自动执行 `scripts/sync-ueditor.mjs`，
把 `ueditor-plus-main/dist-min` 复制到 `public/ueditor-plus/`（该目录已 gitignore）；
需要单独重同步时用 `npm run sync:ueditor`。

富文本默认引擎由 `VITE_RICH_EDITOR` 兜底（`.env.*`，默认 `wangeditor-next`）；
用户可在编辑器右上角「切换编辑器」临时换引擎，平台默认值在「系统管理 → 参数设置」的
`sys.editor.type` 里改。

生产部署由 Nginx 提供静态文件，并把 `/api` 反代到 `lx-boot`；
`src/router/index.js` 使用 history 模式，Nginx 需要 `try_files $uri $uri/ /index.html`。

接口回归脚本在 `.local/verify-crud.ps1`（74 项，不进仓库）；
权限接口断言在 `.local/verify-permission.ps1`（34 项，纯 ASCII）；
日志闭环断言在 `.local/verify-log.ps1`（29 项：写操作落库、详情、密码不外泄、清空、登录日志、匿名 401）；
系统监控后端断言在 `.local/verify-monitor.ps1`（51 项：会话枚举 / 脱敏 / 筛选 / 强退 / 401 / 审计 / 指标真实性）；
定时任务后端断言在 `.local/verify-job.ps1`（51 项：V11 迁移 / cron 校验 / 调度 / 暂停 / 手动执行 / 集群锁 / 失败堆栈 / 删除保留历史 / 权限拆分），
模块关闭断言在 `.local/verify-job-module.ps1`（5 项：job 接口 404、基座仍可用）；
代码生成后端断言在 `.local/verify-gen.ps1`（105 项：V12 迁移 / 注释读取 / 导入跳过 / 默认值 / 配置校验 / 预览转义 / ZIP 与 javac / 删除保留物理表 / 权限拆分 / 匿名 401），
图片管线断言在 `.local/imgtest/verify-image.mjs`（27 项：EXIF 方向 / 元数据剥离 / thumb 与 medium 尺寸 / PNG 类型 / 小图与 TXT 不变 / 匿名访问 / 老文件回退），
接口文档断言在 `.local/verify-apidoc.ps1`（26 项：`/doc.html` 公开、OpenAPI 3、路径数 ≥50、PUT 接口、webjars；当前 76 条路径）；
个人中心断言在 `.local/verify-profile.ps1`（31 项：资料更新、越权字段忽略、2005 原密码、改密踢会话、审计无明文）；
前端冒烟脚本在 `.local/uitest/smoke.mjs`（登录、主要业务页面、深链、占位页、
岗位新增/删除、操作 / 登录日志、401、退出），权限 UI 断言在 `.local/uitest/permission.mjs`（30 项：
受限侧边栏、按钮显隐、未授权深链 404、绕过 UI 直打接口 403，含两个日志接口与清空），验证码开关在
`.local/uitest/captcha-toggle.mjs`（12 项：关闭态隐藏 + 直接登录、开启态出图、错误码被拒），
监控三页在 `.local/uitest/monitor.mjs`（8 项：渲染、筛选、页面强退、控制台 0 error），
定时任务页在 `.local/uitest/job.mjs`（9 项：列表、新建、开关启动 + cron 执行、日志抽屉、暂停、手动执行、删除），
代码生成页在 `.local/uitest/gen.mjs`（9 项：导入、配置抽屉、字段保存、预览 7 文件、下载、删除、控制台 0 error），
文件中心在 `.local/uitest/image.mjs`（10 项：网格 `?size=thumb` 真实加载、详情 `?size=medium`、方向纠正后的尺寸、控制台 0 error），
接口文档页在 `.local/uitest/apidoc.mjs`（10 项：非占位、iframe 渲染、Token 脱敏、刷新重建），
个人中心在 `.local/uitest/profile.mjs`（13 项：资料保存、邮箱校验、原密码错误、改密跳登录、新密码登录），
主题在 `.local/uitest/theme.mjs`（12 项：默认亮色、一键暗色、刷新保持、抽屉、主色同步、侧栏深浅、跟随系统、恢复默认、暗色无白块、控制台 0 error）。
GitHub Actions 的 `frontend` job 会执行 `npm ci` + `npm run build`（详见 `docs/26-testing-ci.md`）；
页面级 UI 回归仍由本目录下的 Puppeteer 脚本在发布前人工执行。
详细结果记录在 `docs/21-build-log.md` 第 20~36 节。
