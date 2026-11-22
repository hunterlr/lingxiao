-- =====================================================================
-- 凌霄 · 菜单模块归属修正（V18）
-- =====================================================================
-- 背景：
--   sys_menu 从 V1 起就有 module_code，但定时任务 / 代码生成器一直挂在
--   system 下。结果是关闭 job / generator 后接口 404、菜单仍然下发，
--   点进去只能看到占位页。
--
-- 本轮把菜单归属改到真实模块：
--   job        → tool:job、monitor:job:*（任务 CRUD / 执行日志）
--   generator  → tool:gen、tool:gen:*（导入 / 配置 / 预览 / 下载）
--
-- 基座目录「系统工具」保持 system；模块关闭后，它的子节点会被过滤，
-- 目录本身由 AuthService 的 pruneEmptyDirectories 一并隐藏。
--
-- 说明：Flyway 版本号全局递增，模块目录按开关裁剪；关闭模块时已执行的
-- 迁移记录保留在 flyway_schema_history，重新开启不会重复执行。
-- =====================================================================

UPDATE sys_menu
SET module_code = 'job'
WHERE module_code = 'system'
  AND (menu_key = 'tool:job' OR menu_key LIKE 'monitor:job:%');

UPDATE sys_menu
SET module_code = 'generator'
WHERE module_code = 'system'
  AND menu_key LIKE 'tool:gen%';
