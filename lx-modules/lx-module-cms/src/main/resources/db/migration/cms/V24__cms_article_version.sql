-- =====================================================================
-- 迁移 — CMS 文章内容版本（V24）
-- =====================================================================
-- 范围
--   1. cms_article_version：文章内容快照（标题 / 正文 / 栏目 / 标签等）
--   2. 每篇文章版本号自增，唯一约束 (article_id, version_no)
--   3. 新增“历史版本 / 回滚”按钮权限
--
-- 约定
--   - 版本只覆盖内容字段：标题、slug、摘要、封面、正文、来源、栏目、标签、状态；
--     排序 / 备注 / 作者等元数据不进入快照。
--   - content_hash 用于相邻快照去重，避免“保存了但内容没变”也产生版本。
--   - 版本保留数量由应用层配置 lx.cms.version-keep 控制，超出后删最旧的。
--   - 快照是审计数据，不做逻辑删除；文章彻底删除时一起清理。
--   - 菜单插入继续用 INSERT ... SELECT ... WHERE NOT EXISTS，不用 MySQL 方言。
-- =====================================================================

CREATE TABLE cms_article_version (
  version_id       bigint       NOT NULL,
  article_id       bigint       NOT NULL,
  site_id          bigint       NOT NULL,
  version_no       int          NOT NULL,
  change_type      varchar(20)  NOT NULL COMMENT 'create / edit / publish / rollback / restore',
  category_id      bigint       DEFAULT NULL,
  title            varchar(200) NOT NULL,
  slug             varchar(200) DEFAULT NULL,
  summary          varchar(500) DEFAULT NULL,
  cover            varchar(255) DEFAULT NULL,
  content          longtext,
  source           varchar(100) DEFAULT NULL,
  source_url       varchar(255) DEFAULT NULL,
  tag_ids          varchar(1000) DEFAULT NULL COMMENT '逗号分隔的标签 ID 快照',
  status           char(1)      DEFAULT NULL COMMENT '快照时的文章状态',
  content_hash     varchar(64)  NOT NULL COMMENT 'SHA-256，用于相邻版本去重',
  operator_id      bigint       DEFAULT NULL,
  operator_name    varchar(64)  DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_cms_article_version_no (article_id, version_no),
  KEY idx_cms_article_version_article (article_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章内容版本';

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3054, 'cms:article:version', 3030, '历史版本', 'F', NULL, NULL, 'platform:cms:article:version', NULL, 7, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3054);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3055, 'cms:article:rollback', 3030, '回滚版本', 'F', NULL, NULL, 'platform:cms:article:rollback', NULL, 8, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3055);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (3054, 3055)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
