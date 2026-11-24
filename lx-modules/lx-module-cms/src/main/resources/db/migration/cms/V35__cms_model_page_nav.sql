-- =====================================================================
-- 迁移 — CMS 第三刀：内容模型、单页、导航、区块 / 轮播与草稿预览（V35）
-- =====================================================================
-- 范围（v1.3）
--   cms_model             站点级内容模型（内置 article / gallery / page）
--   cms_model_field       模型字段定义（文本 / 富文本 / 图片 / 文件 / 单选 / 多选 / 日期 / 数字）
--   cms_content_ext       扩展字段 JSON（owner_type = article / page）
--   cms_page              单页（关于我们 / 联系方式）
--   cms_nav / cms_nav_item 顶部 / 页脚 / 移动端导航与树形导航项
--   cms_block             可复用内容块 / 首页推荐位
--   cms_banner            轮播图（起止时间 + 排序）
--   cms_preview_ticket    一次性草稿预览票据
--   cms_category.model_id 栏目绑定内容模型；空值兼容旧数据，服务层回退文章模型
--
-- 约束
--   1. 只建 cms_*，不碰 sys_* 业务表；菜单在 V36 单独幂等导入。
--   2. 文件只存 fileId / URL，不建 cms_file。
--   3. 不建跨模块外键；导航目标、模型引用都由服务层校验。
--   4. 正文 HTML 由服务端 HtmlSanitizer 清洗后入库，DB 不承担 XSS 防线。
--   5. 版本号全局递增；关闭 CMS 时本迁移不执行，重新开启后补跑。
-- =====================================================================

ALTER TABLE cms_category
  ADD COLUMN model_id bigint DEFAULT NULL COMMENT '内容模型；空=内置文章模型';

CREATE INDEX idx_cms_category_model ON cms_category (site_id, model_id);

CREATE TABLE cms_model (
  model_id         bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  model_key        varchar(64)  NOT NULL,
  model_name       varchar(100) NOT NULL,
  model_type       varchar(16)  NOT NULL DEFAULT 'article' COMMENT 'article / gallery / page',
  built_in         char(1)      NOT NULL DEFAULT '0' COMMENT '1=内置模型，不可删除 / 改 key',
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  sort             int          NOT NULL DEFAULT 0,
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (model_id),
  UNIQUE KEY uk_cms_model_key (site_id, model_key),
  KEY idx_cms_model_site (site_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 内容模型';

CREATE TABLE cms_model_field (
  field_id         bigint       NOT NULL,
  model_id         bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  field_key        varchar(64)  NOT NULL,
  field_name       varchar(100) NOT NULL,
  field_type       varchar(32)  NOT NULL COMMENT 'text/textarea/richtext/number/date/select/checkbox/image/images/file',
  is_required      char(1)      NOT NULL DEFAULT '0' COMMENT '1=必填',
  default_value    varchar(500) DEFAULT NULL,
  options_json     text         COMMENT '选项：[{"label":"A","value":"a"}]',
  validation_json  varchar(1000) DEFAULT NULL COMMENT '长度 / 正则 / 范围等附加校验',
  is_list          char(1)      NOT NULL DEFAULT '0',
  is_search        char(1)      NOT NULL DEFAULT '0',
  sort             int          NOT NULL DEFAULT 0,
  status           char(1)      NOT NULL DEFAULT '0',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (field_id),
  UNIQUE KEY uk_cms_model_field_key (model_id, field_key),
  KEY idx_cms_model_field_site (site_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 模型字段';

CREATE TABLE cms_content_ext (
  ext_id           bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  owner_type       varchar(16)  NOT NULL COMMENT 'article / page',
  owner_id         bigint       NOT NULL,
  model_id         bigint       DEFAULT NULL,
  ext_json         longtext     COMMENT '扩展字段 JSON',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (ext_id),
  UNIQUE KEY uk_cms_content_ext_owner (owner_type, owner_id),
  KEY idx_cms_content_ext_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 内容扩展字段';

CREATE TABLE cms_page (
  page_id          bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  model_id         bigint       DEFAULT NULL,
  page_key         varchar(100) NOT NULL COMMENT '站点内稳定键 / 门户 URL',
  page_name        varchar(100) NOT NULL,
  title            varchar(200) DEFAULT NULL,
  summary          varchar(500) DEFAULT NULL,
  cover            varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  content          longtext     COMMENT '已清洗的 HTML',
  template         varchar(100) DEFAULT NULL,
  seo_title        varchar(200) DEFAULT NULL,
  seo_keywords     varchar(255) DEFAULT NULL,
  seo_description  varchar(500) DEFAULT NULL,
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0草稿 1已发布 2已下线',
  publish_time     datetime(3)  DEFAULT NULL,
  sort             int          NOT NULL DEFAULT 0,
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (page_id),
  UNIQUE KEY uk_cms_page_key (site_id, page_key),
  KEY idx_cms_page_site (site_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 单页';

CREATE TABLE cms_nav (
  nav_id           bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  nav_key          varchar(64)  NOT NULL,
  nav_name         varchar(100) NOT NULL,
  position         varchar(16)  NOT NULL DEFAULT 'top' COMMENT 'top / footer / mobile',
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (nav_id),
  UNIQUE KEY uk_cms_nav_key (site_id, nav_key),
  KEY idx_cms_nav_site (site_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 导航';

CREATE TABLE cms_nav_item (
  item_id          bigint       NOT NULL,
  nav_id           bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  parent_id        bigint       NOT NULL DEFAULT 0,
  item_name        varchar(100) NOT NULL,
  target_type      varchar(16)  NOT NULL DEFAULT 'link' COMMENT 'category / page / link',
  target_id        bigint       DEFAULT NULL,
  url              varchar(500) DEFAULT NULL,
  icon             varchar(64)  DEFAULT NULL,
  sort             int          NOT NULL DEFAULT 0,
  visible          char(1)      NOT NULL DEFAULT '0' COMMENT '0显示 1隐藏',
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (item_id),
  KEY idx_cms_nav_item_nav (nav_id, parent_id),
  KEY idx_cms_nav_item_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 导航项';

CREATE TABLE cms_block (
  block_id         bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  block_key        varchar(64)  NOT NULL,
  block_name       varchar(100) NOT NULL,
  block_type       varchar(16)  NOT NULL DEFAULT 'html' COMMENT 'html / image',
  content          longtext     COMMENT '已清洗的 HTML',
  cover            varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  link_url         varchar(500) DEFAULT NULL,
  sort             int          NOT NULL DEFAULT 0,
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (block_id),
  UNIQUE KEY uk_cms_block_key (site_id, block_key),
  KEY idx_cms_block_site (site_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 区块 / 碎片';

CREATE TABLE cms_banner (
  banner_id        bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  banner_group     varchar(32)  NOT NULL DEFAULT 'home',
  title            varchar(200) DEFAULT NULL,
  image            varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  link_url         varchar(500) DEFAULT NULL,
  start_time       datetime(3)  DEFAULT NULL,
  end_time         datetime(3)  DEFAULT NULL,
  sort             int          NOT NULL DEFAULT 0,
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (banner_id),
  KEY idx_cms_banner_group (site_id, banner_group, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 轮播 / 广告位';

CREATE TABLE cms_preview_ticket (
  ticket_id        bigint       NOT NULL,
  ticket           varchar(64)  NOT NULL,
  site_id          bigint       NOT NULL,
  target_type      varchar(16)  NOT NULL COMMENT 'article / page',
  target_id        bigint       NOT NULL,
  expire_time      datetime(3)  NOT NULL,
  used             char(1)      NOT NULL DEFAULT '0' COMMENT '0未使用 1已使用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  PRIMARY KEY (ticket_id),
  UNIQUE KEY uk_cms_preview_ticket (ticket),
  KEY idx_cms_preview_target (site_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 草稿预览票据';

-- =====================================================================
-- 内置模型播种：为已有站点补齐 article / gallery / page 三个模型。
-- 新站点由 CmsModelService.ensureBuiltIn(siteId) 懒初始化。
-- UUID_SHORT() 只在迁移里生成本地主键，不参与业务规则。
-- =====================================================================

INSERT INTO cms_model
  (model_id, site_id, model_key, model_name, model_type, built_in, status, sort, create_time, del_flag, version, remark)
SELECT UUID_SHORT(), s.site_id, 'article', '文章', 'article', '1', '0', 1, NOW(3), '0', 0, '内置模型：文章'
FROM cms_site s
WHERE NOT EXISTS (
  SELECT 1 FROM cms_model m WHERE m.site_id = s.site_id AND m.model_key = 'article'
);

INSERT INTO cms_model
  (model_id, site_id, model_key, model_name, model_type, built_in, status, sort, create_time, del_flag, version, remark)
SELECT UUID_SHORT(), s.site_id, 'gallery', '图集', 'gallery', '1', '0', 2, NOW(3), '0', 0, '内置模型：图集'
FROM cms_site s
WHERE NOT EXISTS (
  SELECT 1 FROM cms_model m WHERE m.site_id = s.site_id AND m.model_key = 'gallery'
);

INSERT INTO cms_model
  (model_id, site_id, model_key, model_name, model_type, built_in, status, sort, create_time, del_flag, version, remark)
SELECT UUID_SHORT(), s.site_id, 'page', '单页', 'page', '1', '0', 3, NOW(3), '0', 0, '内置模型：单页'
FROM cms_site s
WHERE NOT EXISTS (
  SELECT 1 FROM cms_model m WHERE m.site_id = s.site_id AND m.model_key = 'page'
);

-- 文章模型：副标题、SEO 关键词 / 描述
INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'subtitle', '副标题', 'text', '0', NULL, NULL, NULL, '0', '0', 1, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'article'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'subtitle');

INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'seoKeywords', 'SEO 关键词', 'text', '0', NULL, NULL, NULL, '0', '0', 2, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'article'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'seoKeywords');

INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'seoDescription', 'SEO 描述', 'textarea', '0', NULL, NULL, NULL, '0', '0', 3, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'article'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'seoDescription');

-- 图集模型：图片列表、摄影师、拍摄日期
INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'images', '图集图片', 'images', '1', NULL, NULL, NULL, '1', '0', 1, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'gallery'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'images');

INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'photographer', '摄影师', 'text', '0', NULL, NULL, NULL, '0', '0', 2, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'gallery'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'photographer');

INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'shotAt', '拍摄日期', 'date', '0', NULL, NULL, NULL, '0', '0', 3, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'gallery'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'shotAt');

-- 单页模型：副标题、联系方式
INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'subtitle', '副标题', 'text', '0', NULL, NULL, NULL, '0', '0', 1, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'page'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'subtitle');

INSERT INTO cms_model_field
  (field_id, model_id, site_id, field_key, field_name, field_type, is_required, default_value, options_json, validation_json, is_list, is_search, sort, status, create_time, del_flag, version)
SELECT UUID_SHORT(), m.model_id, m.site_id, 'contact', '联系方式', 'textarea', '0', NULL, NULL, NULL, '0', '0', 2, '0', NOW(3), '0', 0
FROM cms_model m
WHERE m.model_key = 'page'
  AND NOT EXISTS (SELECT 1 FROM cms_model_field f WHERE f.model_id = m.model_id AND f.field_key = 'contact');

-- 旧栏目回填文章模型；新栏目由后台选择文章 / 图集模型。
UPDATE cms_category c
JOIN cms_model m ON m.site_id = c.site_id AND m.model_key = 'article'
SET c.model_id = m.model_id
WHERE c.model_id IS NULL;
