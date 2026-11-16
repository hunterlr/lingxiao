-- =====================================================================
-- 凌霄 · 监控日志按钮权限（v1.0 第八次迁移）
-- =====================================================================
-- 背景：
--   V2 已种入菜单 14（登录日志）/ 15（操作日志）及其 list 权限码，
--   本轮把日志落库、查询、清空做完整，清空属于不可逆的危险操作，
--   必须与列表权限分开授权，否则“能看日志”的人就能顺手清空审计数据。
--
-- 设计：
--   menu_type = 'F'，不进侧边栏，只提供 perms 码；
--   parent_id 指向对应菜单，角色授权树里挂在菜单下方，取消父级即可整体收回；
--   ID 段沿用 V4 的“菜单号 * 100 + 序号”规则：1401 / 1501。
--
-- 注意：
--   本文件只新增数据，不修改 V2 / V4；Flyway 已执行过的版本不允许改内容。
--   授权用 NOT EXISTS 而不是 INSERT IGNORE，保持标准 SQL，
--   后续切 PostgreSQL / 达梦 / 金仓时不用改。
-- =====================================================================

-- 登录日志（菜单 14）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(1401, 'monitor:login-log:clean', 14, '清空日志', 'F', NULL, NULL, 'platform:monitor:loginlog:clean', NULL, 1, '0', '0', 'system', NOW(3), '0', 0);

-- 操作日志（菜单 15）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(1501, 'monitor:oper-log:clean', 15, '清空日志', 'F', NULL, NULL, 'platform:monitor:operlog:clean', NULL, 1, '0', '0', 'system', NOW(3), '0', 0);

-- 超级管理员补授新增按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
);
