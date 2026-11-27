-- =====================================================================
-- 迁移 — 会员站内消息（V43）
-- =====================================================================
-- 范围
--   1. mem_message            站内信：评论审核结果 + 后台手动发送的系统消息
--   2. mem.message.enabled    消息中心运行期开关（会员设置）
--   3. 消息管理菜单与按钮权限（menu_id 4060 段，module_code = 'member'）
--
-- 约定
--   - 只存 member_id + biz_type / biz_id，不建跨模块外键，不复制文章标题；
--     门户要展示“哪篇文章”时再用 CMS 公开接口补。
--   - 阅读状态 read_flag：0 未读 / 1 已读；重复标记已读幂等，不报错。
--   - 后台删除是逻辑删除（del_flag=1），保留发送记录，便于追溯。
--   - sys_config.config_id 非自增：V25 占 11~18、V26 占 19~25、V28 占 26、
--     V30 占 27、V31 占 28~30、V34 占 31~33，本脚本占用 34。
--   - 菜单与角色授权都写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

CREATE TABLE mem_message (
  msg_id      bigint        NOT NULL,
  tenant_id   bigint        NOT NULL DEFAULT 0,
  member_id   bigint        NOT NULL,
  msg_type    varchar(32)   NOT NULL COMMENT 'comment_approved / comment_rejected / system',
  title       varchar(128)  NOT NULL,
  content     varchar(1000) NOT NULL,
  biz_type    varchar(32)   DEFAULT NULL COMMENT 'article / 其它业务类型',
  biz_id      bigint        DEFAULT NULL,
  read_flag   char(1)       NOT NULL DEFAULT '0' COMMENT '0 未读 / 1 已读',
  read_time   datetime(3)   DEFAULT NULL,
  create_by   bigint        DEFAULT NULL,
  create_time datetime(3)   NOT NULL,
  update_by   bigint        DEFAULT NULL,
  update_time datetime(3)   DEFAULT NULL,
  del_flag    char(1)       NOT NULL DEFAULT '0',
  version     int           NOT NULL DEFAULT 0,
  PRIMARY KEY (msg_id),
  KEY idx_mem_message_member (tenant_id, member_id, read_flag, create_time),
  KEY idx_mem_message_time (tenant_id, member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员站内消息';

-- ---------------------------------------------------------------------
-- 1. 消息中心运行期开关（会员管理 → 会员设置）
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 34, '消息中心开关', 'mem.message.enabled', 'true', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭后门户消息接口返回“消息中心已关闭”，评论审核也不再产生站内信；后台消息管理仍可查看历史'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 34);

-- ---------------------------------------------------------------------
-- 2. 会员管理 → 消息管理
-- ---------------------------------------------------------------------
-- 权限码
--   platform:member:message:list|send|remove
-- 消息开关只影响 /api/app/messages/**；后台发送 / 清理不受影响。

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4060, 'member:message', 4000, '消息管理', 'C', '/member/message', 'member/message/index', 'platform:member:message:list', 'bell', 5, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4060);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4061, 'member:message:send', 4060, '发送消息', 'F', NULL, NULL, 'platform:member:message:send', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4061);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4062, 'member:message:remove', 4060, '删除消息', 'F', NULL, NULL, 'platform:member:message:remove', NULL, 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4062);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (4060, 4061, 4062)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );