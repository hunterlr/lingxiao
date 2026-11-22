-- =====================================================================
-- 迁移 — CMS 文章定时发布（V23）
-- =====================================================================
-- 范围
--   1. cms_article 增加 schedule_time：计划发布时间
--   2. 调度扫描索引 (status, schedule_time)
--   3. 新增“定时发布”按钮权限（设置与取消共用）
--
-- 约定
--   - 新增状态 '3'（定时待发布）：文章仍不可见，到点由后台调度改为 '1'（已发布）。
--   - publish_time 仍是“首次发布时间”，到点发布时为空才写入计划时间；
--     schedule_time 到点后清空，不参与历史展示。
--   - 调度用「条件 UPDATE 抢占」而不是全局锁：多实例同时扫描时，
--     只有第一个把 status 从 '3' 改成 '1' 的实例算成功，天然幂等。
--   - 索引与菜单插入保持可移植：不用 INSERT IGNORE / ON DUPLICATE KEY。
-- =====================================================================

ALTER TABLE cms_article ADD COLUMN schedule_time datetime(3) DEFAULT NULL;

CREATE INDEX idx_cms_article_schedule ON cms_article (status, schedule_time);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 3053, 'cms:article:schedule', 3030, '定时发布', 'F', NULL, NULL, 'platform:cms:article:schedule', NULL, 6, '0', '0', 'cms', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3053);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 3053
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 3053);
