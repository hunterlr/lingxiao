-- =====================================================================
-- 迁移 — 多语言菜单与权限（V40）
-- =====================================================================
-- 约定
--   - 使用空闲的 5000 段，避开 system / file / cms / member。
--   - module_code='i18n'，模块关闭时 AuthService 按模块过滤，菜单不下发。
--   - INSERT IGNORE：Flyway 只执行一次；手工重复导入也不会产生重复。
--   - 只给超级管理员（role_id=1）授权；普通角色由管理员在角色页勾选。
--
-- 权限码
--   platform:i18n:language:list|add|edit|remove|status
--   platform:i18n:key:list|add|edit|remove
--   platform:i18n:value:batch
--   platform:i18n:import|export|coverage|missing
--   platform:i18n:pack:list|publish|rollback
-- =====================================================================

INSERT IGNORE INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
  (5000, 'i18n',                  0,    '多语言',     'M', '/i18n',            NULL,                    NULL,                                'language',  6, '0', '0', 'i18n', NOW(3), '0', 0),
  (5010, 'i18n:language',         5000, '语言管理',   'C', '/i18n/language',   'i18n/language/index',   'platform:i18n:language:list',       'language',  1, '0', '0', 'i18n', NOW(3), '0', 0),
  (5011, 'i18n:language:add',     5010, '新增语言',   'F', NULL,               NULL,                    'platform:i18n:language:add',        NULL,        1, '0', '0', 'i18n', NOW(3), '0', 0),
  (5012, 'i18n:language:edit',    5010, '编辑语言',   'F', NULL,               NULL,                    'platform:i18n:language:edit',       NULL,        2, '0', '0', 'i18n', NOW(3), '0', 0),
  (5013, 'i18n:language:remove',  5010, '删除语言',   'F', NULL,               NULL,                    'platform:i18n:language:remove',     NULL,        3, '0', '0', 'i18n', NOW(3), '0', 0),
  (5014, 'i18n:language:status',  5010, '启停语言',   'F', NULL,               NULL,                    'platform:i18n:language:status',     NULL,        4, '0', '0', 'i18n', NOW(3), '0', 0),
  (5020, 'i18n:translation',      5000, '翻译维护',   'C', '/i18n/translation','i18n/translation/index','platform:i18n:key:list',            'edit',      2, '0', '0', 'i18n', NOW(3), '0', 0),
  (5021, 'i18n:key:add',          5020, '新增翻译键', 'F', NULL,               NULL,                    'platform:i18n:key:add',             NULL,        1, '0', '0', 'i18n', NOW(3), '0', 0),
  (5022, 'i18n:key:edit',         5020, '编辑翻译键', 'F', NULL,               NULL,                    'platform:i18n:key:edit',            NULL,        2, '0', '0', 'i18n', NOW(3), '0', 0),
  (5023, 'i18n:key:remove',       5020, '删除翻译键', 'F', NULL,               NULL,                    'platform:i18n:key:remove',          NULL,        3, '0', '0', 'i18n', NOW(3), '0', 0),
  (5024, 'i18n:value:batch',      5020, '批量保存译文','F', NULL,              NULL,                    'platform:i18n:value:batch',         NULL,        4, '0', '0', 'i18n', NOW(3), '0', 0),
  (5025, 'i18n:import',           5020, '导入译文',   'F', NULL,               NULL,                    'platform:i18n:import',              NULL,        5, '0', '0', 'i18n', NOW(3), '0', 0),
  (5026, 'i18n:export',           5020, '导出译文',   'F', NULL,               NULL,                    'platform:i18n:export',              NULL,        6, '0', '0', 'i18n', NOW(3), '0', 0),
  (5027, 'i18n:coverage',         5020, '翻译覆盖率', 'F', NULL,               NULL,                    'platform:i18n:coverage',            NULL,        7, '0', '0', 'i18n', NOW(3), '0', 0),
  (5028, 'i18n:missing',          5020, '缺失记录',   'F', NULL,               NULL,                    'platform:i18n:missing',             NULL,        8, '0', '0', 'i18n', NOW(3), '0', 0),
  (5030, 'i18n:pack',             5000, '语言包',     'C', '/i18n/pack',       'i18n/pack/index',       'platform:i18n:pack:list',           'component', 3, '0', '0', 'i18n', NOW(3), '0', 0),
  (5031, 'i18n:pack:publish',     5030, '发布语言包', 'F', NULL,               NULL,                    'platform:i18n:pack:publish',        NULL,        1, '0', '0', 'i18n', NOW(3), '0', 0),
  (5032, 'i18n:pack:rollback',    5030, '回滚语言包', 'F', NULL,               NULL,                    'platform:i18n:pack:rollback',       NULL,        2, '0', '0', 'i18n', NOW(3), '0', 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE module_code = 'i18n';
