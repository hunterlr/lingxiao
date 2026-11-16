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

#### （7）路由标题

`router` 的 `meta.title` 改成 key（如 `menu.system.user`），
`document.title` 与面包屑统一走 `t()`。

### 3.4 验收清单（L1）

- [ ] 默认 `zh-CN`，与 v1.0 视觉/文案完全一致；
- [ ] 切到 `en-US`：登录页、侧边栏、面包屑、8 个系统页、日志页、监控页、任务页、生成器页无中文残留（允许日志内容中文）；
- [ ] 刷新后语言保持；
- [ ] Element Plus 分页、日期、空状态、确认框变英文；
- [ ] 后端错误：密码错误、账号锁定、无权限、参数校验，四种提示都跟着语言变；
- [ ] 未知语言 / 缺翻译 key：回退中文，不白屏、不显示 `menu.xxx`；
- [ ] 浏览器控制台无 i18n missing key 警告；
- [ ] 定时任务日志、操作日志仍为中文（明确不翻译）。

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
