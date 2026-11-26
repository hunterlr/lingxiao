# 23 · 多语言落地方案（v1.0 → v1.1）

> 关联：完整目标态设计见 [15-i18n.md](15-i18n.md)；本文只回答一件事：
> **现在这套 v1.0 基座，怎么用最小改动把“多语言”落地，而不是一上来就建 9 张 i18n 表。**
>
> 结论先说：**后台界面双语先做（1~2 天），业务数据翻译后做（按模块推进），CMS/会员内容最后做。**
> v1.0 保持中文单语，不在发布前动 i18n。

---

## 1. 现状盘点

| 层 | 现状 | 改造难度 |
|---|---|---|
| 后端返回消息 | `R.fail("用户不存在")`、异常消息多为中文字面量 | 中：要统一收口到 messageKey |
| 后端校验 | `@NotBlank(message = "用户名不能为空")` | 中：改成 `{user.username.required}` |
| 前端界面 | 文案全部硬编码中文 | 大：页面多，但机械 |
| 菜单 | `sys_menu.menu_name` 中文，已有 `menu_key` | 小：`menu_key` 天然可当翻译键 |
| 字典 | `sys_dict_data.dict_label` 中文 | 小：用 `dict.<type>.<value>` 约定 |
| 参数设置 | 页面 label 中文，`config_key` 稳定 | 小：按 key 翻译 |
| 日志 / 审计 | 中文，给运维看 | 不翻译 |
| CMS / 会员 | v1.0 未做 | 后续按 docs/15 |

**关键判断：v1.0 的 `sys_menu.menu_key` 和 `sys_config.config_key` 已经是稳定标识符，不需要为了多语言改表。**

> 本表是 2026-11-15 的**改造前**盘点，保留作决策依据。L1 已于 2026-11-17 完成，
> 现状与验收见 §10；上面的“全部硬编码中文”已不再成立。

---

## 2. 分层目标

| 层级 | 范围 | 何时做 | 是否建表 |
|---|---|---|---|
| **L1 后台界面** | 菜单、按钮、表单 label、校验提示、错误提示、Element Plus 组件文案 | v1.1 | 不建，语言包文件 |
| **L2 业务数据** | 字典项、参数说明、公告标题/正文 | v1.2 | 可选 `i18n_*` 表 |
| **L3 内容与前台** | CMS 文章、栏目、会员中心、门户 UI | v1.3+ | 按 docs/15 全量设计 |
| **L4 运维文本** | 操作日志、登录日志、异常堆栈、SQL | 永不翻译 | 不建 |

先做 L1 的原因：

1. 不改数据库，不碰业务数据，风险最低；
2. 能验证“语言协商 + 前端切换 + Element Plus 联动”整条链路；
3. 后面 L2/L3 只是把“文件里的值”换成“表里的值”，架构不用推倒重来。

---

## 3. L1 最小闭环（建议 v1.1 第一件事）

### 3.1 语言编码与协商

沿用 docs/15 的 BCP 47：

| 语言 | 编码 | 默认 |
|---|---|---|
| 简体中文 | `zh-CN` | ✅ |
| 英语 | `en-US` | 可选 |
| 繁体中文 | `zh-TW` | 后续 |

协商优先级（后端）：

```
X-Lang 请求头  >  ?lang= 查询参数  >  Accept-Language  >  平台默认 zh-CN  >  fallback
```

规则：

- 只认平台已启用的语言，未知语言直接回退 `zh-CN`；
- 大小写不敏感，统一存成 `zh-CN` 形式；
- 语言只影响“展示文案”，不影响数据、排序、时区；日期格式由 `Locale` 派生。

### 3.2 后端改造

#### （1）语言包文件

```
lx-modules/lx-module-system/src/main/resources/i18n/
  messages.properties          # 兜底（建议写英文或中文，二选一，团队定）
  messages_zh_CN.properties
  messages_en_US.properties
  ValidationMessages.properties          # Bean Validation 默认
  ValidationMessages_zh_CN.properties
  ValidationMessages_en_US.properties
```

键格式（与 docs/15 对齐）：

```
error.1001=参数校验失败
error.1002=未登录或登录已过期
error.1003=没有权限
error.1004=资源不存在
error.2001=用户名或密码错误
error.2004=账号已锁定，请稍后再试或联系管理员

user.username.required=用户名不能为空
user.password.required=密码不能为空
user.password.policy=密码至少 8 位，且同时包含字母和数字
```

#### （2）MessageSource 配置

```java
@Bean
public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasenames("i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false); // 不要跟着服务器系统语言跑
    source.setUseCodeAsDefaultMessage(false);
    return source;
}
```

#### （3）语言解析

```java
public class LingxiaoLocaleResolver implements LocaleResolver {
    // X-Lang > ?lang= > Accept-Language > zh-CN
}
```

- 注册为 `LocaleResolver` Bean，Spring MVC 的 `LocaleContextHolder` 自动可用；
- **不要**把语言塞进 ThreadLocal 自己管，容易在线程池里串味；
- 定时任务、异步线程没有请求上下文，固定用平台默认语言。

#### （4）错误消息收口

当前 `R` 结构是 `{code, msg, data}`，保持兼容，新增可选字段：

```json
{
  "code": 2001,
  "msg": "用户名或密码错误",
  "messageKey": "error.2001",
  "data": null
}
```

约定：

- `code` 是稳定契约，前端逻辑判断只看 `code`；
- `msg` 是本地化后的展示文案，前端可直接弹；
- `messageKey` 给前端做二次翻译/埋点用，可空。

`BusinessException` 增加构造：

```java
throw new BusinessException(2001, "error.2001");
```

全局异常处理器用 `MessageSource.getMessage(key, args, locale)` 填 `msg`。

#### （5）Bean Validation

```java
@NotBlank(message = "{user.username.required}")
private String username;
```

`ValidationMessages*.properties` 放同样的键；这样 `@Valid` 的失败信息也会跟着语言走。

> 注意：校验消息在参数绑定阶段产生，必须保证 `LocaleResolver` 已经生效；
> 用 `WebMvcConfigurer#getValidator` 把 `LocalValidatorFactoryBean` 的
> `ValidationMessageSource` 指向同一个 `MessageSource`，否则会出现“接口是英文、校验是中文”。

#### （6）菜单 / 字典 / 参数

| 数据 | 翻译键 | 前端取值顺序 |
|---|---|---|
| 菜单 | `menu.<menu_key>`（如 `menu.system.user`） | i18n → `menu_name` |
| 字典项 | `dict.<dict_type>.<dict_value>` | i18n → `dict_label` |
| 参数 | `config.<config_key>` | i18n → 页面兜底 label |
| 按钮 | `button.<perms 最后两段>` | i18n → 中文文案 |

**不新增数据库列**。`menu_key` / `dict_type` + `dict_value` / `config_key` 已经是稳定键；
将来 L2 需要“运营可维护译文”时，再加 `i18n_*` 表并让加载顺序变成：
`数据库译文 > 语言包文件 > 原始字段`。

### 3.3 前端改造

#### （1）依赖

```bash
npm i vue-i18n@9
```

`vue-i18n@9` 是 Vue3 对应版本，JDK 无关，体积约 20KB gzip。

#### （2）目录

```
src/i18n/
  index.js          # createI18n、setLocale、getElementLocale
  zh-CN.js          # 登录 / 通用 / 菜单 / 校验 / 错误码
  en-US.js
```

`zh-CN.js` 结构示例：

```js
export default {
  common: {
    confirm: '确定',
    cancel: '取消',
    search: '搜索',
    reset: '重置',
    add: '新增',
    edit: '编辑',
    delete: '删除'
  },
  login: {
    title: '登录后台',
    username: '登录账号',
    password: '密码',
    submit: '登 录'
  },
  menu: {
    'system.user': '用户管理',
    'system.role': '角色管理'
  },
  error: {
    1001: '参数校验失败',
    1002: '未登录或登录已过期',
    1003: '没有权限',
    2001: '用户名或密码错误',
    2004: '账号已锁定，请稍后再试'
  }
}
```

#### （3）Element Plus 语言联动

当前 `main.js` 是静态 `app.use(ElementPlus, { locale: zhCn })`，切换语言不会变。
改成 `ElConfigProvider`：

```vue
<!-- App.vue -->
<el-config-provider :locale="elementLocale">
  <router-view />
</el-config-provider>
```

```js
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const elementLocale = computed(() => (locale.value === 'en-US' ? en : zhCn))
```

这样分页“共 3 条”、日期选择器、空状态等组件文案会一起切换。

#### （4）语言状态

新增 `store/locale.js`：

- `locale`：`zh-CN` / `en-US`；
- 初始化顺序：`localStorage('lx-admin-locale')` → 浏览器 `navigator.language` → `zh-CN`；
- 切换时：
  1. `i18n.global.locale.value = next`；
  2. `localStorage` 落盘；
  3. 同步 `document.documentElement.lang = next`；
  4. 通知后端（可选）：后续个人中心保存 `sys_user.language`。

顶栏加一个语言下拉（中文 / English），与主题按钮并排。

#### （5）请求头

`src/api/request.js` 拦截器加：

```js
config.headers['X-Lang'] = getLocale()
```

后端据此返回对应语言的 `msg`。

#### （6）菜单与字典

- 登录后拿到的菜单树里有 `menu_key`，侧边栏渲染时：

```js
const label = menu.menu_key ? t(`menu.${menu.menu_key}`, menu.menu_name) : menu.menu_name
```

`t(key, fallback)` 的第二个参数是默认值，缺翻译时自动退回后端中文，不会出现空白菜单。

- `DictTag` / `DictSelect` 用 `t('dict.' + type + '.' + value, label)`；
- 参数设置页用 `t('config.' + key, 原 label)`。

> **点号即路径**：vue-i18n 会把 `t()` 的 key 按 `.` 拆成层级路径。
> `config.sys.account.maxRetryCount` 在语言包里必须写成
> `config: { sys: { account: { maxRetryCount: '…' } } }`，
> **不能**写成 `config: { 'sys.account.maxRetryCount': '…' }`——后者运行时永远取不到，
> `te()` 返回 `false` 后静默回退数据库原文。2026-11-20 的参数名乱码就是这个坑
> （叠加了数据库被脚本写脏），`scripts/check-i18n-keys.mjs` 已增加
> “叶子键自身带点”检测并直接判失败。

#### （7）路由标题

`router` 的 `meta.title` 改成 key（如 `menu.system.user`），
`document.title` 与面包屑统一走 `t()`。

### 3.4 验收清单（L1）

> 2026-11-17 全部实测通过，证据见 §10.4。

- [x] 默认 `zh-CN`，与 v1.0 视觉/文案完全一致；
- [x] 切到 `en-US`：登录页、侧边栏、面包屑、8 个系统页、日志页、监控页、任务页、生成器页无中文残留（允许日志内容中文）；
- [x] 刷新后语言保持；
- [x] Element Plus 分页、日期、空状态、确认框变英文；
- [x] 后端错误：密码错误、账号锁定、无权限、参数校验，四种提示都跟着语言变；
- [x] 未知语言 / 缺翻译 key：回退中文，不白屏、不显示 `menu.xxx`；
- [x] 浏览器控制台无 i18n missing key 警告；
- [x] 定时任务日志、操作日志仍为中文（明确不翻译）。

> 说明：菜单名、字典 `label`、参数名 / 说明、部门 / 岗位 / 公告 / 任务名、文件备注
> 属于**业务数据**，L1 不翻译，英文界面下允许中文残留；字典与参数只有“标签”在
> 前端有译文（`dict.<type>.<value>`、`config.<config_key>`），缺项回退数据库文本。

---

## 4. L2：业务数据翻译（v1.2）

当出现“同一字典项要给英文站点看”或“公告要双语”时，再引入表：

```sql
-- 只列新增，完整设计见 docs/15-i18n.md
CREATE TABLE i18n_language (
  lang_code    VARCHAR(16) PRIMARY KEY,
  lang_name    VARCHAR(64) NOT NULL,
  native_name  VARCHAR(64) NOT NULL,
  enabled      TINYINT NOT NULL DEFAULT 1,
  is_default   TINYINT NOT NULL DEFAULT 0,
  fallback     VARCHAR(16),
  sort_num     INT NOT NULL DEFAULT 0
);

CREATE TABLE i18n_key (
  i18n_key     VARCHAR(128) PRIMARY KEY,
  namespace    VARCHAR(32) NOT NULL,
  default_value VARCHAR(512) NOT NULL,
  module_code  VARCHAR(32),
  remark       VARCHAR(255)
);

CREATE TABLE i18n_value (
  id           BIGINT PRIMARY KEY,
  i18n_key     VARCHAR(128) NOT NULL,
  lang_code    VARCHAR(16) NOT NULL,
  value        TEXT,
  version      INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_key_lang (i18n_key, lang_code)
);
```

关键点：

1. **键不变**：L1 用 `menu.system.user`，L2 仍用同一个键，只是值从文件搬到表；
2. **加载顺序**：数据库 > 语言包文件 > 原始字段；
3. **缓存**：按 `lang_code + namespace` 缓存，后台改译文后发版本号失效（单机 Caffeine / 多机 Redis pub/sub）；
4. **导入导出**：语言包 = 一个 CSV（key, zh-CN, en-US），运营用 Excel 维护；
5. **权限**：翻译管理是独立权限 `platform:system:i18n:*`，不给普通管理员。

---

## 5. L3：CMS / 会员内容（v1.3+）

按 docs/15 的 `cms_*_i18n` 设计：

- 内容表拆“主表（语言无关）+ 翻译表（按语言）”；
- 文章、栏目、标签、SEO 字段都要翻译；
- 会员语言偏好存 `mem_user.language`；
- 门户按 URL 前缀（`/en/`）或域名决定语言，SEO 需要 `hreflang`。

这部分等 CMS 真正立项再做，不在 v1.0/v1.1 范围。

---

## 6. 明确不做的事

| 不做 | 原因 |
|---|---|
| 现在就建 9 张 i18n 表 | 没有第二种语言的内容，表是空的，增加迁移和缓存负担 |
| 用 Google/DeepL 运行时翻译 | 不可控、有网络依赖、术语不一致 |
| 翻译操作日志 / SQL / 异常堆栈 | 给运维看，翻译反而降低排错效率 |
| 把语言编码写进每个业务表 | 数据膨胀，查询复杂；语言是“展示维度”，不是“数据维度” |
| 在代码里拼字符串（`t('hello') + name`） | 无法处理语序，英文会变成中式英语；用带参数的消息 |
| 为 RTL（阿拉伯语）现在做适配 | 等确有中东项目再专项做，CSS 逻辑属性改造量大 |

---

## 7. 工作量与排期建议

| 任务 | 预估 | 说明 |
|---|---|---|
| 后端 MessageSource + LocaleResolver + 异常收口 | 0.5 天 | 影响所有接口，必须先做 |
| 后端校验消息 key 化 | 0.5 天 | 8 个模块的 DTO |
| 前端 vue-i18n + 语言包骨架 + 顶栏切换 | 0.5 天 | 含 Element Plus ConfigProvider |
| 前端页面文案替换（18 页） | 1.5~2 天 | 机械工作，按页面验收 |
| 菜单 / 字典 / 参数 key 化 | 0.5 天 | 不改表 |
| 联调 + 回归 + 文档 | 1 天 | 含本文更新和截图 |
| **合计** | **4~5 人天** | 单人一周内可完成 |

建议拆成两轮施工：

1. **第一轮**：后端链路 + 登录页 + 布局 + 首页（能演示双语闭环）；
2. **第二轮**：18 个业务页 + 校验提示 + 验收清单。

---

## 8. 与现有代码的衔接点

| 现有文件 | 改动 |
|---|---|
| `lx-common-core/.../R.java` | 增加 `messageKey` 字段（可空，不影响旧前端） |
| `lx-common-core/.../BusinessException.java` | 增加 key + args 构造 |
| `lx-common-web/.../GlobalExceptionHandler.java` | 用 MessageSource 填 msg |
| `lx-modules/lx-module-system/.../AuthService.java` | 中文异常改 messageKey |
| `lx-ui-admin/src/main.js` | 静态 locale → `ElConfigProvider` |
| `lx-ui-admin/src/App.vue` | 包一层 ConfigProvider |
| `lx-ui-admin/src/layout/Navbar.vue` | 加语言切换 |
| `lx-ui-admin/src/api/request.js` | 加 `X-Lang` |
| `lx-ui-admin/src/layout/Sidebar.vue` | 菜单 label 走 `t()` |
| `lx-ui-admin/src/components/DictTag.vue` / `DictSelect.vue` | 字典 label 走 `t()` |

---

## 9. 一句话回答

**多语言不是“装个 vue-i18n”，而是“先定错误码契约，再收口文案，最后才是翻译文件”。**
v1.0 先把中文写死没关系；要做的时候按本文 L1 走，4~5 天能出双语后台，
数据库一行不用改；等 CMS 真要多语言内容，再启用 docs/15 的完整表设计。

---

## 10. 施工状态（2026-11-16 后端链路 / 2026-11-17 前端界面）

> 两轮均已完成，L1 收口。下面 §10.1~10.3 是第一轮记录，§10.4 是第二轮记录。

### 10.1 已完成

| 内容 | 落点 | 说明 |
|---|---|---|
| 文案解析接口 | `lx-common-core/.../i18n/MessageResolver` | core 不依赖 Spring，只定义 `resolve(key, args)` |
| 静态入口 | `lx-common-core/.../i18n/MessageUtils` | 永不抛异常；未初始化 / 缺 key / 解析器异常都退回默认文案 |
| 语言包装配 | `lx-common-web/.../i18n/I18nConfig` | `ResourceBundleMessageSource`，basename `i18n/messages`，UTF-8，`fallbackToSystemLocale=false` |
| 语言解析 | `lx-common-web/.../i18n/LingxiaoLocaleResolver` | `X-Lang > ?lang= > Accept-Language > zh-CN`；`zh/en/zh_CN/EN-us` 归一化 |
| 静态入口初始化 | `lx-common-web/.../i18n/I18nMessageInitializer` | 单独组件，避免配置类与 MessageSource 循环依赖 |
| 语言包 | `lx-common-web/src/main/resources/i18n/messages*.properties` | 默认中文 + `_en_US`；**94 个键**，中英键集合完全一致 |
| 统一响应 | `R.java` | 新增 `messageKey`（`@JsonInclude(NON_NULL)`）；`ok/fail(ErrorCode)` 自动本地化 |
| 业务异常 | `ServiceException.java` | 新增 `messageKey` / `args`、三参构造、`keyed(...)` 工厂；`getMessage()` 保留中文兜底 |
| 全局异常 | `GlobalExceptionHandler.java` | 带 key 的走 MessageSource 翻译；校验消息由 Validator 直接解析 |
| 鉴权异常 | `SaTokenExceptionHandler`（未改动） | 走 `R.fail(ErrorCode)`，自动跟着语言变 |
| 错误码文案 | `messages*.properties` | `error.<code>` 覆盖 `ErrorCode` 全部 18 个枚举 |
| 参数校验 | 17 个 DTO / 实体、87 处注解 | 中文 `message` 全部换成 `{valid.*}`，**70 个键** |
| 登录锁定 | `AuthService` | `error.2004.locked` / `error.2004.failures` 带分钟参数；登录日志仍写中文 |
| 密码策略 | `PasswordPolicy` | `valid.password.policy.basic/strong` |

### 10.2 实测（8080，`--lx.captcha.enabled=false`）

| 场景 | `X-Lang` | 返回 |
|---|---|---|
| 密码错误 | `zh-CN` | `{"code":2001,"msg":"用户名或密码错误","messageKey":"error.2001"}` |
| 密码错误 | `en-US` | `{"code":2001,"msg":"Incorrect username or password","messageKey":"error.2001"}` |
| 空用户名密码 | `zh-CN` | `password: 密码不能为空；username: 用户名不能为空` |
| 空用户名密码 | `en-US` | `password: Password must not be empty; username: Username must not be empty` |
| 未登录 | `en-US` | `{"code":1002,"msg":"Not signed in or session expired","messageKey":"error.1002"}` |
| 登录成功 | `en-US` | `{"code":0,"msg":"Success",...}`（`messageKey` 为 null 时不输出） |

### 10.3 单测

| 新增测试类 | 数量 | 锁什么 |
|---|---|---|
| `MessageUtilsTest` | 6 | 未初始化 / 缺 key / 解析器抛异常都退回默认文案 |
| `LingxiaoLocaleResolverTest` | 7 | 四级优先级、标签归一化、`setLocale` 不落 Cookie |
| `I18nMessageSourceTest` | 5 | 中英键集合一致、每个 `ErrorCode` 都有文案、占位符替换、缺 key 返回 null |

合计 **84 项**（原 66 + 新增 18），`mvn test` 不依赖 MySQL / Redis。
2026-11-19 数据范围「2 自定义」再新增 8 项（`DataScopeServiceTest` 6 + `SysRoleServiceTest` 2）；
2026-11-22 模块插拔收口新增 `ModuleRegistryTest` 5 项、`ModuleMigrationLocationsTest` 3 项、`StpInterfaceImplTest` 5 项，当前为 **112 项 / 23 个类**（2026-11-21 为 99 项 / 20 个类）。

### 10.4 第二轮（前端）：已完成（2026-11-17）

| 任务 | 落地 |
|---|---|
| 安装 `vue-i18n@9` | `^9.14.4`（锁定 9.14.4），未引 `@intlify/unplugin-vue-i18n` |
| `src/i18n/{index,zh-CN,en-US}.js` | 各 **897 键**，`fallbackLocale = zh-CN`，`missingWarn/fallbackWarn = false` |
| `src/store/locale.js` | `localStorage['lx-admin-locale']` → 浏览器语言 → `zh-CN`；切语言同步 `html.lang` |
| `App.vue` + `main.js` | 静态 `ElementPlus({locale})` 改 `ElConfigProvider`，分页 / 日期 / 确认框跟随 |
| `Navbar.vue` | 顶栏语言胶囊（`中` / `EN`），原生 `title` 提示（避开 tooltip + dropdown 的 `role` 校验） |
| `api/request.js` | 请求头加 `X-Lang`，拦截器兜底文案走语言包 |
| 菜单 / 字典 / 参数 | `MenuVO` 补 `menuKey` 下发；`t('menu.'+key, name)`、`dict.*`、`config.*` 缺项回退数据库文本 |
| 18 个业务页 + 登录 / 403 / 404 / 占位 / 个人中心 | 全量替换；`FileLibrary`、`ThemeDrawer`、`RichEditor`、UEditor / wangEditor 一并接上 |

实测（2026-11-17）：

```text
node scripts/check-i18n-keys.mjs   zh=897 en=897，键集合一致，missing=none
npx vite build                     built in 5.61s
mvn -B -ntp test                   84 项全通过（16 个测试类）
.local/uitest/smoke.mjs            pass=22 fail=0，控制台 0 error
.local/uitest/theme.mjs            12 项全 PASS
```

- 默认 `zh-CN` 与 v1.0 一致；切 `en-US` 后刷新保持；
- 18 个页面静态文案全英文，剩余中文仅为业务数据（菜单名、字典 label、参数说明、
  部门 / 岗位 / 公告 / 任务名、文件备注、日志内容）；
- 后端四类错误双语实测通过：`error.2001` 密码错误、空参数校验、`error.1002` 未登录、
  `error.2004.locked` 账号锁定；
- Element Plus：`Total 1 | 10/page | 1 | Go to`、`Start time / End time / to`、
  `Notice / Sign out now? / Cancel / Sign out` 均英文；
- 控制台无 `[intlify]` missing key 警告、无 Vue prop 警告。

> 第一轮刻意只做后端：接口契约先稳定，前端才有稳定的 `messageKey` 可用；
> 也避免“前端切了英文、后端还弹中文”这种半成品状态。
>
> 下一步是 L2（v1.2）：`i18n_*` 表翻译业务数据；L1 不新增数据库列。

### 10.5 第三轮（v1.4 / P2.5 第二刀）：数据库语言包（已完成，2026-11-26）

**边界**：只做“后端文案 + 后台翻译工作台 + 门户公开语言包接口”。CMS 内容翻译、
`/{lang}/` URL 前缀、hreflang、租户语言包、门户前端接入均不在本刀。

**落地**：

| 内容 | 落点 |
|---|---|
| 独立模块 | `lx-modules/lx-module-i18n`，由 `lx.module.i18n.enabled` 控制；关闭时 Bean 不装配 |
| 迁移 | `db/migration/i18n/V39` 建 5 张表 + 语言种子；`V40` 菜单 / 权限（5000 段）；`V41` 从 `messages*.properties` 导入 348 键 / 696 译文；`V42` 增加 `i18n_value.published_text` |
| MessageSource | `I18nModuleConfig` 提供名为 `messageSource` 的 `@Primary` Bean；DB 已发布值 > 资源包 > 调用方默认值 |
| 缓存 | `DatabaseTranslationProvider`，Caffeine 5 秒；发布 / 回滚主动清空 |
| 语言解析 | 复用 `X-Lang > ?lang= > Accept-Language > zh-CN`，未新增解析器 |
| 后台接口 | 语言 CRUD / 启停、键 CRUD、批量保存草稿、CSV 导入导出、覆盖率、缺失记录、发布 / 回滚 |
| 后台页面 | `views/i18n/{language,translation,pack}` + `api/i18n.js`，菜单 / 权限 `platform:i18n:*` |
| 门户接口 | `/api/portal/i18n/languages`、`/version`、`/{lang}/{namespace}`，ETag + 5 秒缓存 |

**两个关键语义**：

1. **草稿隔离**：`i18n_value.value_text` 是工作副本，`published_text` 是线上发布值。
   保存草稿只改前者并置 `review_status=0`，所以编辑中的新键不会出现在门户，
   已发布键也继续返回上一版。发布时才 `published_text = value_text`。
2. **真回滚**：`i18n_pack.snapshot_json` 保存 `{keyId: valueText}`。回滚不仅切换
   当前版本指针，还按快照恢复 `value_text` / `published_text`，避免“指针回去了、内容没回去”。

**验收**：

```text
mvn -q -pl lx-modules/lx-module-i18n -am test          通过
mvn -q -pl lx-boot -am test                            通过
Flyway（全新库）                                        V1~V42 一次跑通
verify-i18n.mjs                                         73/73
verify-i18n.mjs --off（8081 + 全新库）                   12/12
lx-ui-admin npm run build                              通过
```

**已知取舍**：首版不建单独的审核表，`review_status` 只表示工作副本是否已发布；
语言包缓存是单机 Caffeine，多实例靠 5 秒 TTL；命名空间取键前缀，暂不建
`i18n_namespace`；管理端前端语言包仍是文件形式，本刀只接管后端文案。

