-- =====================================================================
-- 迁移 — CMS 菜单与权限（V21）
-- =====================================================================
-- 约定
--   - menu_id 从 3000 起，避开 system / file / job / generator 已用区间。
--   - module_code = 'cms'，CMS 关闭时 AuthService 按模块过滤，菜单不下发。
--   - 用 INSERT IGNORE：本脚本由 Flyway 只执行一次；手工重复导入也不会
--     产生重复菜单或重复授权（menu_key / 主键都会挡住）。
--   - 只给超级管理员（role_id=1）授权；普通角色由管理员在角色页勾选。
--
-- 权限码
--   platform:cms:site:list|add|edit|remove
--   platform:cms:category:list|add|edit|remove
--   platform:cms:article:list|add|edit|remove|publish|offline
--   platform:cms:tag:list|add|edit|remove
-- =====================================================================

INSERT IGNORE INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
  (3000, 'cms',          0, '内容管理', 'M', '/cms', NULL, NULL, 'documentation', 0, '0', '0', 'cms', NOW(3), '0', 0),
  (3010, 'cms:site',     3000, '站点管理', 'C', '/cms/site',     'cms/site/index',     'platform:cms:site:list',     'monitor', 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3020, 'cms:category', 3000, '栏目管理', 'C', '/cms/category', 'cms/category/index', 'platform:cms:category:list', 'tree-table', 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3030, 'cms:article',  3000, '文章管理', 'C', '/cms/article',  'cms/article/index',  'platform:cms:article:list',  'form', 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3040, 'cms:tag',      3000, '标签管理', 'C', '/cms/tag',      'cms/tag/index',      'platform:cms:tag:list',      'dict', 4, '0', '0', 'cms', NOW(3), '0', 0);

INSERT IGNORE INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
  (3011, 'cms:site:add',       3010, '新增站点', 'F', NULL, NULL, 'platform:cms:site:add',       NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3012, 'cms:site:edit',      3010, '编辑站点', 'F', NULL, NULL, 'platform:cms:site:edit',      NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3013, 'cms:site:remove',    3010, '删除站点', 'F', NULL, NULL, 'platform:cms:site:remove',    NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3021, 'cms:category:add',   3020, '新增栏目', 'F', NULL, NULL, 'platform:cms:category:add',    NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3022, 'cms:category:edit',  3020, '编辑栏目', 'F', NULL, NULL, 'platform:cms:category:edit',   NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3023, 'cms:category:remove',3020, '删除栏目', 'F', NULL, NULL, 'platform:cms:category:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3031, 'cms:article:add',    3030, '新增文章', 'F', NULL, NULL, 'platform:cms:article:add',     NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3032, 'cms:article:edit',   3030, '编辑文章', 'F', NULL, NULL, 'platform:cms:article:edit',    NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3033, 'cms:article:remove', 3030, '删除文章', 'F', NULL, NULL, 'platform:cms:article:remove',  NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3034, 'cms:article:publish',3030, '发布文章', 'F', NULL, NULL, 'platform:cms:article:publish', NULL, 4, '0', '0', 'cms', NOW(3), '0', 0),
  (3035, 'cms:article:offline',3030, '下线文章', 'F', NULL, NULL, 'platform:cms:article:offline', NULL, 5, '0', '0', 'cms', NOW(3), '0', 0),
  (3041, 'cms:tag:add',        3040, '新增标签', 'F', NULL, NULL, 'platform:cms:tag:add',         NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3042, 'cms:tag:edit',       3040, '编辑标签', 'F', NULL, NULL, 'platform:cms:tag:edit',        NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3043, 'cms:tag:remove',     3040, '删除标签', 'F', NULL, NULL, 'platform:cms:tag:remove',      NULL, 3, '0', '0', 'cms', NOW(3), '0', 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE module_code = 'cms';
