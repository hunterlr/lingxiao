-- =====================================================================
-- 迁移 38：CMS 第四刀按钮权限（V38）
-- =====================================================================
-- 约定
--   - 复用 CMS 3000 根菜单与 3020 栏目管理，不新增菜单节点；
--   - 只新增一个按钮：栏目模板预览（3024）；
--   - INSERT IGNORE：Flyway 只执行一次，手工重复导入也不会产生重复；
--   - 只给超级管理员（role_id=1）授权；普通角色由管理员在角色页勾选。
--
-- 权限码
--   platform:cms:category:preview
-- =====================================================================

INSERT IGNORE INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
  (3024, 'cms:category:preview', 3020, '预览模板', 'F', NULL, NULL, 'platform:cms:category:preview', NULL, 4, '0', '0', 'cms', NOW(3), '0', 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 3024);
