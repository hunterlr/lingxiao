-- =====================================================================
-- 迁移 — CMS 基础结构（V20）
-- =====================================================================
-- 范围（v1.1 第一刀）
--   cms_site            站点：多站点 / 域名 / SEO 默认值
--   cms_category        栏目：parent_id 树 + 闭包表，便于取子树
--   cms_category_closure 栏目闭包：ancestor_id / category_id / depth
--   cms_article         文章：草稿 / 发布 / 下线 + 正文 HTML
--   cms_tag             标签
--   cms_article_tag     文章-标签关联
--
-- 约束
--   1. 只建 cms_*，不碰 sys_* 业务表；菜单在 V21 单独幂等导入。
--   2. 文件只存 fileId / URL（cover），不建 cms_file。
--   3. 作者只存 author_id（sys_user.user_id）+ author_name 快照，
--      不建跨模块外键；用户删除后文章仍可显示历史署名。
--   4. 不用数据库外键：与项目其它模块一致，删除走应用层校验。
--   5. 富文本正文由服务端 HtmlSanitizer 清洗后入库，DB 不承担 XSS 防线。
--
-- 版本号：Flyway 版本全局递增，模块目录按开关裁剪；
--         关闭 CMS 时本迁移不执行，重新开启后补跑。
-- =====================================================================

CREATE TABLE cms_site (
  site_id          bigint       NOT NULL,
  site_key         varchar(64)  NOT NULL COMMENT '稳定键，菜单/门户/脚本引用用',
  site_name        varchar(100) NOT NULL,
  domain           varchar(255) DEFAULT NULL COMMENT '绑定域名，可空；多站点时用于门户识别',
  logo             varchar(255) DEFAULT NULL COMMENT 'fileId 或 http(s) URL',
  seo_title        varchar(200) DEFAULT NULL,
  seo_keywords     varchar(255) DEFAULT NULL,
  seo_description  varchar(500) DEFAULT NULL,
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (site_id),
  UNIQUE KEY uk_cms_site_key (site_key),
  KEY idx_cms_site_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 站点';

CREATE TABLE cms_category (
  category_id      bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  parent_id        bigint       NOT NULL DEFAULT 0,
  category_key     varchar(64)  NOT NULL COMMENT '站点内稳定键，栏目迁移/门户路径用',
  category_name    varchar(100) NOT NULL,
  slug             varchar(100) DEFAULT NULL COMMENT '门户 URL 片段',
  path             varchar(255) DEFAULT NULL COMMENT '门户完整路径，可空；由应用层拼',
  template         varchar(100) DEFAULT NULL COMMENT '列表模板标识，v1.3 门户使用',
  icon             varchar(64)  DEFAULT NULL,
  cover            varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  seo_title        varchar(200) DEFAULT NULL,
  seo_keywords     varchar(255) DEFAULT NULL,
  seo_description  varchar(500) DEFAULT NULL,
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
  PRIMARY KEY (category_id),
  UNIQUE KEY uk_cms_category_key (site_id, category_key),
  KEY idx_cms_category_parent (site_id, parent_id),
  KEY idx_cms_category_status (site_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 栏目';

CREATE TABLE cms_category_closure (
  category_id      bigint NOT NULL,
  ancestor_id      bigint NOT NULL,
  depth            int    NOT NULL COMMENT '0=自身，1=父级，依此类推',
  PRIMARY KEY (category_id, ancestor_id),
  KEY idx_cms_closure_ancestor (ancestor_id, depth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 栏目闭包';

CREATE TABLE cms_article (
  article_id       bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  category_id      bigint       NOT NULL,
  title            varchar(200) NOT NULL,
  slug             varchar(200) DEFAULT NULL COMMENT '门户 URL 片段，站点内建议唯一',
  summary          varchar(500) DEFAULT NULL,
  cover            varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  content          longtext     COMMENT '已清洗的 HTML',
  author_id        bigint       DEFAULT NULL COMMENT 'sys_user.user_id；第三方作者可空',
  author_name      varchar(64)  DEFAULT NULL COMMENT '署名快照，用户改名不回溯历史文章',
  source           varchar(100) DEFAULT NULL,
  source_url       varchar(255) DEFAULT NULL,
  status           char(1)      NOT NULL DEFAULT '0' COMMENT '0草稿 1已发布 2已下线',
  publish_time     datetime(3)  DEFAULT NULL,
  view_count       bigint       NOT NULL DEFAULT 0,
  sort             int          NOT NULL DEFAULT 0,
  create_by        bigint       DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  update_by        bigint       DEFAULT NULL,
  update_time      datetime(3)  DEFAULT NULL,
  del_flag         char(1)      NOT NULL DEFAULT '0',
  version          int          NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (article_id),
  KEY idx_cms_article_list (site_id, status, publish_time),
  KEY idx_cms_article_category (category_id, status),
  KEY idx_cms_article_slug (site_id, slug),
  KEY idx_cms_article_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章';

CREATE TABLE cms_tag (
  tag_id           bigint      NOT NULL,
  site_id          bigint      NOT NULL,
  tag_name         varchar(64) NOT NULL,
  tag_key          varchar(64) NOT NULL COMMENT '站点内稳定键 / 门户 URL 片段',
  status           char(1)     NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  create_by        bigint      DEFAULT NULL,
  create_time      datetime(3) NOT NULL,
  update_by        bigint      DEFAULT NULL,
  update_time      datetime(3) DEFAULT NULL,
  del_flag         char(1)     NOT NULL DEFAULT '0',
  version          int         NOT NULL DEFAULT 0,
  remark           varchar(500) DEFAULT NULL,
  PRIMARY KEY (tag_id),
  UNIQUE KEY uk_cms_tag_key (site_id, tag_key),
  KEY idx_cms_tag_name (site_id, tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 标签';

CREATE TABLE cms_article_tag (
  article_id       bigint NOT NULL,
  tag_id           bigint NOT NULL,
  PRIMARY KEY (article_id, tag_id),
  KEY idx_cms_article_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章-标签';
