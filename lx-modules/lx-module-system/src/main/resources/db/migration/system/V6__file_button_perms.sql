-- =====================================================================
-- 凌霄 · 文件模块按钮权限（v1.0 第六次迁移）
-- =====================================================================
-- 背景：
--   V2/V4 只给系统管理模块配了权限码。文件接口（/api/admin/file/**）
--   此前只校验登录，任何后台账号都能列文件、改名 —— 权限联调时发现。
-- 设计：
--   F 类型按钮，挂在「文件中心」（menu_id=18）下，不进侧边栏；
--   上传 / 改名分开授权，便于“只能传不能管”的角色。
-- 权限码：
--   platform:file:upload  上传（编辑器插图也走这个接口）
--   platform:file:rename  改名（会写 sys_file_name_log，属管理动作）
--   platform:file:list    列表 / 详情 / 改名历史（沿用 V2 的菜单权限码）
-- 注意：
--   文件库（menu_id=19）页面用的是 platform:file:library:list，
--   但它调用的 /file/list 仍需要 platform:file:list，授权时两个都要给。
-- =====================================================================

INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(1801, 'file:upload', 18, '文件上传', 'F', NULL, NULL, 'platform:file:upload', NULL, 1, '0', '0', 'file', NOW(3), '0', 0),
(1802, 'file:rename', 18, '文件改名', 'F', NULL, NULL, 'platform:file:rename', NULL, 2, '0', '0', 'file', NOW(3), '0', 0);

-- 超级管理员已通过 *:*:* 放行，这里只补角色-菜单关系，保证授权树里能看到
-- （超管角色的 sys_role_menu 是权限树的“全选”展示依据）。
-- 用 NOT EXISTS 而不是 INSERT IGNORE，保持多数据库可移植。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (1801, 1802)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
