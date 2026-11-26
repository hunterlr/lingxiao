-- =====================================================================
-- 迁移 — 多语言语言包（V39）
-- =====================================================================
-- 范围
--   1. i18n_language     平台语言定义
--   2. i18n_key          翻译键 / 默认值
--   3. i18n_value        译文（平台 tenant_id=0，租户覆盖留位）
--   4. i18n_pack         语言包版本 / 校验和 / 发布快照
--   5. i18n_missing_log  缺失翻译记录
--
-- 约定
--   - 表前缀统一 i18n_；模块关闭时本目录不参与 Flyway，表不创建。
--   - 主键由应用侧雪花 ID 生成；种子数据使用固定小 ID，避免重复导入。
--   - 时间由应用填充，不依赖 current_timestamp / on update。
--   - 不建跨模块外键；key_id 由应用保证存在。
--   - i18n_pack.snapshot_json 是 v1.4 为实现“可回滚”新增的字段：
--     发布时保存译文快照，回滚时按快照恢复 i18n_value。
-- =====================================================================

CREATE TABLE i18n_language (
  language_id   bigint       NOT NULL,
  language_code varchar(16)  NOT NULL COMMENT 'BCP 47，例如 zh-CN',
  language_name varchar(64)  NOT NULL,
  native_name   varchar(64)  NOT NULL,
  locale        varchar(32)  NOT NULL,
  direction     varchar(8)   NOT NULL DEFAULT 'ltr' COMMENT 'ltr / rtl',
  is_default    smallint     NOT NULL DEFAULT 0 COMMENT '1=平台默认语言',
  status        char(1)      NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  sort          int          NOT NULL DEFAULT 0,
  create_time   datetime(3)  NOT NULL,
  update_time   datetime(3)  DEFAULT NULL,
  PRIMARY KEY (language_id),
  UNIQUE KEY uk_i18n_language_code (language_code),
  KEY idx_i18n_language_sort (status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台语言定义';

CREATE TABLE i18n_key (
  key_id        bigint        NOT NULL,
  namespace     varchar(64)   NOT NULL COMMENT 'common / error / valid / cms ...',
  key_code      varchar(255)  NOT NULL COMMENT '完整语言包键，例如 error.2001',
  default_value varchar(2000) DEFAULT NULL COMMENT '默认语言文案，缺译文时兜底',
  module_code   varchar(64)   DEFAULT NULL,
  status        char(1)       NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用（废弃键保留）',
  remark        varchar(500)  DEFAULT NULL,
  create_time   datetime(3)   NOT NULL,
  update_time   datetime(3)   DEFAULT NULL,
  PRIMARY KEY (key_id),
  UNIQUE KEY uk_i18n_key (namespace, key_code),
  KEY idx_i18n_key_code (key_code),
  KEY idx_i18n_key_module (module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译键';

CREATE TABLE i18n_value (
  value_id       bigint       NOT NULL,
  key_id         bigint       NOT NULL,
  tenant_id      bigint       NOT NULL DEFAULT 0 COMMENT '0=平台；租户覆盖留位',
  language_code  varchar(16)  NOT NULL,
  value_text     text,
  review_status  char(1)      NOT NULL DEFAULT '0' COMMENT '0 草稿 / 1 已发布',
  version        int          NOT NULL DEFAULT 1,
  translated_by  bigint       DEFAULT NULL,
  translated_at  datetime(3)  DEFAULT NULL,
  create_time    datetime(3)  NOT NULL,
  update_time    datetime(3)  DEFAULT NULL,
  PRIMARY KEY (value_id),
  UNIQUE KEY uk_i18n_value (key_id, tenant_id, language_code),
  KEY idx_i18n_value_lang (tenant_id, language_code, review_status),
  KEY idx_i18n_value_key (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译值';

CREATE TABLE i18n_pack (
  pack_id       bigint       NOT NULL,
  tenant_id     bigint       NOT NULL DEFAULT 0,
  language_code varchar(16)  NOT NULL,
  namespace     varchar(64)  NOT NULL,
  version       int          NOT NULL,
  checksum      varchar(64)  DEFAULT NULL,
  status        char(1)      NOT NULL DEFAULT '0' COMMENT '0 草稿/历史 / 1 当前发布 / 2 已废弃',
  snapshot_json longtext     COMMENT '发布时译文快照 {keyId:valueText}，用于回滚',
  published_by  bigint       DEFAULT NULL,
  published_at  datetime(3)  DEFAULT NULL,
  PRIMARY KEY (pack_id),
  UNIQUE KEY uk_i18n_pack (tenant_id, language_code, namespace, version),
  KEY idx_i18n_pack_current (tenant_id, language_code, namespace, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语言包版本';

CREATE TABLE i18n_missing_log (
  log_id        bigint       NOT NULL,
  tenant_id     bigint       NOT NULL DEFAULT 0,
  language_code varchar(16)  NOT NULL,
  namespace     varchar(64)  NOT NULL,
  key_code      varchar(255) NOT NULL,
  hit_count     bigint       NOT NULL DEFAULT 1,
  first_seen    datetime(3)  NOT NULL,
  last_seen     datetime(3)  NOT NULL,
  sample_locale varchar(64)  DEFAULT NULL,
  PRIMARY KEY (log_id),
  UNIQUE KEY uk_i18n_missing (tenant_id, language_code, namespace, key_code),
  KEY idx_i18n_missing_last (tenant_id, language_code, last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺失翻译记录';

-- 平台语言种子：中文默认，英文启用；后续由后台语言管理维护
INSERT IGNORE INTO i18n_language
  (language_id, language_code, language_name, native_name, locale, direction,
   is_default, status, sort, create_time)
VALUES
  (1, 'zh-CN', '简体中文', '简体中文', 'zh_CN', 'ltr', 1, '0', 1, NOW(3)),
  (2, 'en-US', 'English', 'English', 'en_US', 'ltr', 0, '0', 2, NOW(3));
