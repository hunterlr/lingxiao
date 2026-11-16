-- =====================================================================
-- 凌霄 · 在线用户强退权限（v1.0 第十次迁移）
-- =====================================================================
-- 背景：
--   V2 已种入菜单 13（在线用户）及 list 权限码，但“强退”是危险操作：
--   被强退的人会立刻掉线，正在编辑的表单可能丢数据。它与“能看在线列表”
--   必须分开授权，避免只有查看权限的人顺手把别人踢下线。
--
-- 设计：
--   menu_type = 'F'，不进侧边栏，只提供 perms 码；
--   parent_id 指向菜单 13，角色授权树里挂在“在线用户”下方；
--   ID 沿用“菜单号 * 100 + 序号”规则：1301。
--
-- 注意：
--   本文件只新增数据，不修改 V2 / V8；Flyway 已执行过的版本不允许改内容。
--   授权用 NOT EXISTS 而不是 INSERT IGNORE，保持标准 SQL，
--   后续切 PostgreSQL / 达梦 / 金仓时不用改。
-- =====================================================================

-- 在线用户（菜单 13）
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(1301, 'monitor:online:kickout', 13, '强制退出', 'F', NULL, NULL, 'platform:monitor:online:kickout', NULL, 1, '0', '0', 'system', NOW(3), '0', 0);

-- 超级管理员补授新增按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.perms = 'platform:monitor:online:kickout'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
);
