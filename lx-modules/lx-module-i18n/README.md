# lx-module-i18n

数据库语言包与后台翻译工作台（v1.4 / P2.5 第二刀）。

## 开关

```yaml
lx:
  module:
    i18n:
      enabled: true          # 关闭时 Bean 不装配
      cache-seconds: 5       # 已发布译文本地缓存 TTL
```

`lx.module.i18n.enabled=false` 时：

- `/api/admin/i18n/**`、`/api/portal/i18n/**` 全部 404；
- 多语言菜单不下发（`AuthService` 按 `moduleCode` 过滤）；
- `db/migration/i18n/V39~V42` 不参与 Flyway，`i18n_*` 表不创建；
- 后端文案回退资源包 `i18n/messages*.properties`，系统按默认语言运行。

## 数据与迁移

| 迁移 | 内容 |
|---|---|
| `V39__i18n_schema.sql` | `i18n_language` / `i18n_key` / `i18n_value` / `i18n_pack` / `i18n_missing_log` + zh-CN / en-US 种子 |
| `V40__i18n_menu.sql` | 5000 段菜单与 18 个权限，仅授权 `role_id=1` |
| `V41__i18n_seed_messages.sql` | 从 `messages*.properties` 导入 348 键 / 696 值 |
| `V42__i18n_value_published.sql` | `i18n_value.published_text`：工作副本与线上发布值分离 |

## 关键语义

- **草稿隔离**：保存草稿只改 `value_text`、`review_status=0`；`MessageSource` 和门户
  只读 `published_text`，所以编辑不会提前影响线上。
- **发布**：按 `languageCode + namespace` 生成新 `i18n_pack` 版本，写 checksum 与
  `snapshot_json`，再执行 `published_text = value_text`、`review_status=1`。
- **回滚**：按快照恢复 `value_text` / `published_text`，并把目标版本标为当前；
  不是只改版本指针。
- **优先级**：数据库已发布值 > `i18n/messages*.properties` > 调用方默认文案 / key。
- **语言解析**：复用 `X-Lang > ?lang= > Accept-Language > zh-CN`，本模块不新增解析器。

## 接口

后台（权限码 `platform:i18n:*`）：

- `/api/admin/i18n/language/**`：语言增删改查、启停
- `/api/admin/i18n/key/**`、`/value/batch`：翻译键 / 批量草稿
- `/api/admin/i18n/import|export|coverage|missing`：CSV、覆盖率、缺失记录
- `/api/admin/i18n/pack/list|current|publish|rollback`：语言包

门户（公开，只读已发布）：

- `GET /api/portal/i18n/languages`
- `GET /api/portal/i18n/version?lang=&namespace=`
- `GET /api/portal/i18n/{lang}/{namespace}`（ETag + `max-age=5`）

## 验证

```bash
# 模块单测
mvn -q -pl lx-modules/lx-module-i18n -am test

# 启用态：8080 启动（--lx.captcha.enabled=false）
node lx-modules/lx-module-i18n/scripts/verify-i18n.mjs

# 关闭态：8081 + 全新库 lingxiao_verify
#   java -jar lx-boot/target/lingxiao.jar --server.port=8081 \
#        --lx.captcha.enabled=false --lx.module.i18n.enabled=false
node lx-modules/lx-module-i18n/scripts/verify-i18n.mjs --off
```

## 不在本模块

CMS 内容翻译、`/{lang}/` URL 前缀与 hreflang、租户语言包、门户前端语言包接入。
