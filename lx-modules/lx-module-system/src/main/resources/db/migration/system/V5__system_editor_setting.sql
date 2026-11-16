-- =====================================================================
-- 凌霄 · 富文本编辑器默认引擎（v1.0 第六次迁移）
-- =====================================================================
-- 背景：
--   后台有两套富文本引擎可用：
--     1) wangEditor Next（默认，轻量）
--     2) UEditor Plus（备用，Word 粘贴 / 表格更强）
--   两个引擎的前端切换是“本机偏好”，平台级默认值放参数表，
--   这样运营可以在「参数设置」里统一调整，不用重新发版。
--
-- 设计：
--   config_type = 'Y'（内置）：允许改值，不允许改键名、不允许删除；
--   前端读的是 /api/admin/system/config/public/value 白名单接口，
--   因为公告管理员不一定有 config:list 权限。
--
-- 取值：
--   wangeditor-next | ueditor-plus（非法值前端会回落到构建默认值）
-- =====================================================================

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, del_flag, version, remark)
VALUES (4, '富文本编辑器', 'sys.editor.type', 'wangeditor-next', 'Y', NOW(3), '0', 0,
        'wangeditor-next / ueditor-plus；用户可在编辑器内临时切换，本参数是平台默认值');
