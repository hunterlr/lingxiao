-- =====================================================================
-- 迁移 — CMS 第三刀菜单与权限（V36）
-- =====================================================================
-- 约定
--   - 复用 CMS 3000 根菜单；新增 3056、3060~3103，避开已有 3000~3055。
--   - module_code='cms'，CMS 关闭时 AuthService 按模块过滤，菜单不下发。
--   - INSERT IGNORE：Flyway 只执行一次；手工重复导入也不会产生重复。
--   - 只给超级管理员（role_id=1）授权；普通角色由管理员在角色页勾选。
--
-- 权限码
--   platform:cms:article:preview
--   platform:cms:model:list|add|edit|remove
--   platform:cms:field:add|edit|remove
--   platform:cms:page:list|add|edit|remove|publish|offline|preview
--   platform:cms:nav:list|add|edit|remove
--   platform:cms:nav:item:add|edit|remove
--   platform:cms:block:list|add|edit|remove
--   platform:cms:banner:list|add|edit|remove
-- =====================================================================

INSERT IGNORE INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
  (3056, 'cms:article:preview', 3030, '预览文章', 'F', NULL, NULL, 'platform:cms:article:preview', NULL, 9, '0', '0', 'cms', NOW(3), '0', 0),
  (3060, 'cms:model',           3000, '模型管理', 'C', '/cms/model', 'cms/model/index', 'platform:cms:model:list', 'component', 6, '0', '0', 'cms', NOW(3), '0', 0),
  (3061, 'cms:model:add',       3060, '新增模型', 'F', NULL, NULL, 'platform:cms:model:add', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3062, 'cms:model:edit',      3060, '编辑模型', 'F', NULL, NULL, 'platform:cms:model:edit', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3063, 'cms:model:remove',    3060, '删除模型', 'F', NULL, NULL, 'platform:cms:model:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3064, 'cms:field:add',       3060, '新增字段', 'F', NULL, NULL, 'platform:cms:field:add', NULL, 4, '0', '0', 'cms', NOW(3), '0', 0),
  (3065, 'cms:field:edit',      3060, '编辑字段', 'F', NULL, NULL, 'platform:cms:field:edit', NULL, 5, '0', '0', 'cms', NOW(3), '0', 0),
  (3066, 'cms:field:remove',    3060, '删除字段', 'F', NULL, NULL, 'platform:cms:field:remove', NULL, 6, '0', '0', 'cms', NOW(3), '0', 0),
  (3070, 'cms:page',            3000, '单页管理', 'C', '/cms/page', 'cms/page/index', 'platform:cms:page:list', 'document', 7, '0', '0', 'cms', NOW(3), '0', 0),
  (3071, 'cms:page:add',        3070, '新增单页', 'F', NULL, NULL, 'platform:cms:page:add', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3072, 'cms:page:edit',       3070, '编辑单页', 'F', NULL, NULL, 'platform:cms:page:edit', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3073, 'cms:page:remove',     3070, '删除单页', 'F', NULL, NULL, 'platform:cms:page:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3074, 'cms:page:publish',    3070, '发布单页', 'F', NULL, NULL, 'platform:cms:page:publish', NULL, 4, '0', '0', 'cms', NOW(3), '0', 0),
  (3075, 'cms:page:offline',    3070, '下线单页', 'F', NULL, NULL, 'platform:cms:page:offline', NULL, 5, '0', '0', 'cms', NOW(3), '0', 0),
  (3076, 'cms:page:preview',    3070, '预览单页', 'F', NULL, NULL, 'platform:cms:page:preview', NULL, 6, '0', '0', 'cms', NOW(3), '0', 0),
  (3080, 'cms:nav',             3000, '导航管理', 'C', '/cms/nav', 'cms/nav/index', 'platform:cms:nav:list', 'tree', 8, '0', '0', 'cms', NOW(3), '0', 0),
  (3081, 'cms:nav:add',         3080, '新增导航', 'F', NULL, NULL, 'platform:cms:nav:add', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3082, 'cms:nav:edit',        3080, '编辑导航', 'F', NULL, NULL, 'platform:cms:nav:edit', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3083, 'cms:nav:remove',      3080, '删除导航', 'F', NULL, NULL, 'platform:cms:nav:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3084, 'cms:nav:item:add',    3080, '新增导航项', 'F', NULL, NULL, 'platform:cms:nav:item:add', NULL, 4, '0', '0', 'cms', NOW(3), '0', 0),
  (3085, 'cms:nav:item:edit',   3080, '编辑导航项', 'F', NULL, NULL, 'platform:cms:nav:item:edit', NULL, 5, '0', '0', 'cms', NOW(3), '0', 0),
  (3086, 'cms:nav:item:remove', 3080, '删除导航项', 'F', NULL, NULL, 'platform:cms:nav:item:remove', NULL, 6, '0', '0', 'cms', NOW(3), '0', 0),
  (3090, 'cms:block',           3000, '区块管理', 'C', '/cms/block', 'cms/block/index', 'platform:cms:block:list', 'color', 9, '0', '0', 'cms', NOW(3), '0', 0),
  (3091, 'cms:block:add',       3090, '新增区块', 'F', NULL, NULL, 'platform:cms:block:add', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3092, 'cms:block:edit',      3090, '编辑区块', 'F', NULL, NULL, 'platform:cms:block:edit', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3093, 'cms:block:remove',    3090, '删除区块', 'F', NULL, NULL, 'platform:cms:block:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0),
  (3100, 'cms:banner',          3000, '轮播管理', 'C', '/cms/banner', 'cms/banner/index', 'platform:cms:banner:list', 'picture', 10, '0', '0', 'cms', NOW(3), '0', 0),
  (3101, 'cms:banner:add',      3100, '新增轮播', 'F', NULL, NULL, 'platform:cms:banner:add', NULL, 1, '0', '0', 'cms', NOW(3), '0', 0),
  (3102, 'cms:banner:edit',     3100, '编辑轮播', 'F', NULL, NULL, 'platform:cms:banner:edit', NULL, 2, '0', '0', 'cms', NOW(3), '0', 0),
  (3103, 'cms:banner:remove',   3100, '删除轮播', 'F', NULL, NULL, 'platform:cms:banner:remove', NULL, 3, '0', '0', 'cms', NOW(3), '0', 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE module_code = 'cms';
