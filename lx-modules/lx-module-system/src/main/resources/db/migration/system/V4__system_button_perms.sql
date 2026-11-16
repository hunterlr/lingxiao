-- =====================================================================
-- 凌霄 · 系统模块按钮权限（v1.0 第五次迁移）
-- =====================================================================
-- 背景：
--   V2 只种了 8 个「列表」权限（platform:system:*:list），
--   后台 CRUD 接口新增 / 修改 / 删除 / 重置密码没有对应权限码，
--   权限切面会一律拒绝，导致除超管外无人能操作。
--
-- 设计：
--   menu_type = 'F'，不进侧边栏，只提供 perms 码；
--   parent_id 指向对应菜单，角色授权树里挂在菜单下方，取消父级即可整体收回；
--   ID 段按菜单分块（1xx 用户、2xx 角色 …），留出空位便于以后插入。
--
-- 注意：
--   本文件只新增数据，不修改 V2；Flyway 已执行过的版本不允许改内容。
-- =====================================================================

-- 用户管理（菜单 5）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(101, 'system:user:add',      5, '用户新增', 'F', NULL, NULL, 'platform:system:user:add',      NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(102, 'system:user:edit',     5, '用户修改', 'F', NULL, NULL, 'platform:system:user:edit',     NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(103, 'system:user:remove',   5, '用户删除', 'F', NULL, NULL, 'platform:system:user:remove',   NULL, 3, '0', '0', 'system', NOW(3), '0', 0),
(104, 'system:user:resetPwd', 5, '重置密码', 'F', NULL, NULL, 'platform:system:user:resetPwd', NULL, 4, '0', '0', 'system', NOW(3), '0', 0);

-- 角色管理（菜单 6）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(201, 'system:role:add',    6, '角色新增', 'F', NULL, NULL, 'platform:system:role:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(202, 'system:role:edit',   6, '角色修改', 'F', NULL, NULL, 'platform:system:role:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(203, 'system:role:remove', 6, '角色删除', 'F', NULL, NULL, 'platform:system:role:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 菜单管理（菜单 7）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(301, 'system:menu:add',    7, '菜单新增', 'F', NULL, NULL, 'platform:system:menu:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(302, 'system:menu:edit',   7, '菜单修改', 'F', NULL, NULL, 'platform:system:menu:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(303, 'system:menu:remove', 7, '菜单删除', 'F', NULL, NULL, 'platform:system:menu:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 部门管理（菜单 8）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(401, 'system:dept:add',    8, '部门新增', 'F', NULL, NULL, 'platform:system:dept:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(402, 'system:dept:edit',   8, '部门修改', 'F', NULL, NULL, 'platform:system:dept:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(403, 'system:dept:remove', 8, '部门删除', 'F', NULL, NULL, 'platform:system:dept:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 岗位管理（菜单 9）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(501, 'system:post:add',    9, '岗位新增', 'F', NULL, NULL, 'platform:system:post:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(502, 'system:post:edit',   9, '岗位修改', 'F', NULL, NULL, 'platform:system:post:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(503, 'system:post:remove', 9, '岗位删除', 'F', NULL, NULL, 'platform:system:post:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 字典管理（菜单 10）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(601, 'system:dict:add',    10, '字典新增', 'F', NULL, NULL, 'platform:system:dict:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(602, 'system:dict:edit',   10, '字典修改', 'F', NULL, NULL, 'platform:system:dict:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(603, 'system:dict:remove', 10, '字典删除', 'F', NULL, NULL, 'platform:system:dict:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 参数设置（菜单 11）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(701, 'system:config:add',    11, '参数新增', 'F', NULL, NULL, 'platform:system:config:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(702, 'system:config:edit',   11, '参数修改', 'F', NULL, NULL, 'platform:system:config:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(703, 'system:config:remove', 11, '参数删除', 'F', NULL, NULL, 'platform:system:config:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 公告管理（菜单 12）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(801, 'system:notice:add',    12, '公告新增', 'F', NULL, NULL, 'platform:system:notice:add',    NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(802, 'system:notice:edit',   12, '公告修改', 'F', NULL, NULL, 'platform:system:notice:edit',   NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(803, 'system:notice:remove', 12, '公告删除', 'F', NULL, NULL, 'platform:system:notice:remove', NULL, 3, '0', '0', 'system', NOW(3), '0', 0);

-- 超级管理员补授新增按钮。
-- 用 NOT EXISTS 而不是 INSERT IGNORE / ON DUPLICATE KEY：前者是标准 SQL，
-- 后续迁 PostgreSQL / 达梦 / 金仓时不用改。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
);
