-- =====================================================================
-- 迁移 — CMS 文章回收站（V22）
-- =====================================================================
-- 范围
--   1. cms_article 增加软删除审计列：delete_by / delete_by_name / delete_time
--   2. 回收站列表索引 (del_flag, delete_time)
--   3. 新增“回收站”菜单与恢复 / 彻底删除按钮权限
--
-- 约定
--   - 删除仍是逻辑删除（del_flag='1'），标签关联保留，恢复后标签不丢；
--     彻底删除时才清理 cms_article_tag。
--   - 操作人存 ID + 昵称快照，不跨模块 JOIN sys_user，符合 CMS 模块边界。
--   - 新 SQL 不用 INSERT IGNORE / ON DUPLICATE KEY 等 MySQL 方言；
--     menu_id 已存在时跳过，保证手工重复执行也安全。
-- =====================================================================

ALTER TABLE cms_article ADD COLUMN delete_by bigint DEFAULT NULL;
ALTER TABLE cms_article ADD COLUMN delete_by_name varchar(64) DEFAULT NULL;
ALTER TABLE cms_article ADD COLUMN delete_time datetime(3) DEFAULT NULL;

CREATE INDEX idx_cms_article_del ON cms_article (del_flag, delete_time);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3050, 'cms:recycle', 3000, '回收站', 'C', '/cms/recycle', 'cms/recycle/index', 'platform:cms:article:recycle', 'delete', 5, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3050);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3051, 'cms:article:restore', 3050, '恢复文章', 'F', NULL, NULL, 'platform:cms:article:restore', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3051);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3052, 'cms:article:purge', 3050, '彻底删除', 'F', NULL, NULL, 'platform:cms:article:purge', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3052);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.module_code = 'cms'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
