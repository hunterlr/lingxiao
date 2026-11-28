-- =====================================================================
-- 迁移 — 工作流菜单与权限（V47）
-- =====================================================================
-- 约定
--   - 使用空闲的 6000 段，避开 system / file / cms / member / i18n。
--   - module_code='workflow'，模块关闭时 AuthService 按模块过滤，菜单不下发。
--   - 幂等插入用 INSERT ... SELECT ... WHERE NOT EXISTS（多库兼容约定，不用 INSERT IGNORE）。
--   - 只给超级管理员（role_id=1）授权；普通角色由管理员在角色页勾选。
--
-- 权限码
--   platform:workflow:process:list|import|status|remove
--   platform:workflow:task:todo|done|complete|reject
--   platform:workflow:instance:mine|start|withdraw
-- =====================================================================

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6000, 'workflow', 0, '工作流', 'M', '/workflow', NULL, NULL, 'documentation', 7, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6000);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6010, 'workflow:process', 6000, '流程定义', 'C', '/workflow/process', 'workflow/process/index', 'platform:workflow:process:list', 'documentation', 1, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6010);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6011, 'workflow:process:import', 6010, '导入流程', 'F', NULL, NULL, 'platform:workflow:process:import', NULL, 1, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6011);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6012, 'workflow:process:status', 6010, '启停流程', 'F', NULL, NULL, 'platform:workflow:process:status', NULL, 2, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6012);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6013, 'workflow:process:remove', 6010, '删除流程', 'F', NULL, NULL, 'platform:workflow:process:remove', NULL, 3, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6013);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6020, 'workflow:todo', 6000, '我的待办', 'C', '/workflow/todo', 'workflow/todo/index', 'platform:workflow:task:todo', 'message', 2, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6020);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6021, 'workflow:task:complete', 6020, '同意', 'F', NULL, NULL, 'platform:workflow:task:complete', NULL, 1, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6021);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6022, 'workflow:task:reject', 6020, '驳回', 'F', NULL, NULL, 'platform:workflow:task:reject', NULL, 2, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6022);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6030, 'workflow:mine', 6000, '我发起的', 'C', '/workflow/mine', 'workflow/mine/index', 'platform:workflow:instance:mine', 'edit', 3, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6030);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6031, 'workflow:instance:start', 6030, '发起流程', 'F', NULL, NULL, 'platform:workflow:instance:start', NULL, 1, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6031);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6032, 'workflow:instance:withdraw', 6030, '撤回', 'F', NULL, NULL, 'platform:workflow:instance:withdraw', NULL, 2, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6032);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6040, 'workflow:done', 6000, '我的已办', 'C', '/workflow/done', 'workflow/done/index', 'platform:workflow:task:done', 'comment', 4, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6040);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.module_code = 'workflow'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);