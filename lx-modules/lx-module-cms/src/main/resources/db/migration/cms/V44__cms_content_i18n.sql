-- =====================================================================
-- 迁移 — CMS 内容多语言（V44）
-- =====================================================================
-- 范围
--   1. cms_content_i18n   文章 / 单页的各语言版本（人工填写，不接机翻）
--
-- 约定
--   - 默认语言仍存主表（cms_article / cms_page）；本表只存“其它语言”。
--   - 一个内容一个语言一行：唯一键 (owner_type, owner_id, lang)。
--   - status：0 草稿 / 1 已发布；门户只读已发布版本，未发布回退默认语言。
--   - 正文写入前由服务端 HtmlSanitizer 清洗，与主表同一套白名单。
--   - 不做级联删除：文章彻底删除时由业务层清理（与标签关联一致）。
-- =====================================================================

CREATE TABLE cms_content_i18n (
  i18n_id      bigint        NOT NULL,
  tenant_id    bigint        NOT NULL DEFAULT 0,
  owner_type   varchar(16)   NOT NULL COMMENT 'article / page',
  owner_id     bigint        NOT NULL,
  lang         varchar(16)   NOT NULL COMMENT '语言代码，如 en-US',
  title        varchar(200)  NOT NULL,
  summary      varchar(500)  DEFAULT NULL,
  content      mediumtext    DEFAULT NULL,
  seo_title    varchar(200)  DEFAULT NULL,
  seo_keywords varchar(255)  DEFAULT NULL,
  seo_description varchar(500) DEFAULT NULL,
  status       char(1)       NOT NULL DEFAULT '0' COMMENT '0 草稿 / 1 已发布',
  create_by    bigint        DEFAULT NULL,
  create_time  datetime(3)   NOT NULL,
  update_by    bigint        DEFAULT NULL,
  update_time  datetime(3)   DEFAULT NULL,
  del_flag     char(1)       NOT NULL DEFAULT '0',
  version      int           NOT NULL DEFAULT 0,
  PRIMARY KEY (i18n_id),
  UNIQUE KEY uk_cms_i18n_owner_lang (owner_type, owner_id, lang),
  KEY idx_cms_i18n_lang (lang, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 内容多语言版本';
