-- =====================================================================
-- 迁移 — 会员设置页与运行期总开关（V28）
-- =====================================================================
-- 范围
--   1. 新增运行期开关 mem.enabled，放在「系统管理 → 参数设置」；
--   2. 新增「会员管理 → 会员设置」页面，集中管理其余 mem.* 参数；
--   3. 菜单与按钮权限（menu_id 4030 段，module_code = 'member'）。
--
-- 约定
--   - 总开关仍是 sys_config 参数，不是 Spring 模块开关：
--     lx.module.modules.member.enabled 决定是否装配会员 Bean（启动期、需重启）；
--     mem.enabled 决定运行期是否对用户开放（改完立即生效、不需要重启）。
--   - 其余会员参数从通用参数页移出，按 mem. 前缀在会员设置页展示，
--     通用参数页用 excludeConfigKeyPrefix=mem. 排除。
--   - sys_config.config_id 非自增，必须写死：V25 占 11~18、V26 占 19~25，
--     本脚本占用 26。
--   - 菜单与角色授权都写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会员功能总开关（系统管理 → 参数设置）
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 26, '会员功能开关', 'mem.enabled', 'true', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭后门户会员注册 / 登录 / 个人中心不可用，会员管理菜单与后台会员接口同时关闭；改完立即生效，不需要重启'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 26);

-- ---------------------------------------------------------------------
-- 2. 会员管理 → 会员设置
-- ---------------------------------------------------------------------
-- 权限码
--   platform:system:config:list|edit
--   会员设置页编辑的是 sys_config，因此沿用参数管理权限；
--   菜单归属 member 模块，会员总开关关闭时随模块菜单一起隐藏。

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4030, 'member:setting', 4000, '会员设置', 'C', '/member/setting', 'member/setting/index', 'platform:system:config:list', 'setting', 3, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4030);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4031, 'member:setting:edit', 4030, '保存会员设置', 'F', NULL, NULL, 'platform:system:config:edit', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4031);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (4030, 4031)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
