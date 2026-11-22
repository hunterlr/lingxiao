-- =====================================================================
-- 迁移 — 会员评论与后台审核（V30）
-- =====================================================================
-- 范围
--   1. mem_comment           会员对文章的评论，先审核后展示
--   2. mem.comment.enabled   评论运行期开关（会员管理 → 会员设置）
--   3. 评论管理菜单与按钮权限（menu_id 4040 段，module_code = 'member'）
--
-- 约定
--   - 只存 member_id + article_id，不建跨模块外键，不复制文章标题；
--     文章是否可见仍由 CMS 门户接口负责。
--   - 新评论默认待审核（status='0'），后台通过（'1'）后门户才展示；
--     拒绝（'2'）保留记录与审核备注，便于追溯。
--   - 后台删除是逻辑删除（del_flag=1），不物理删除审核记录。
--   - sys_config.config_id 非自增，必须写死：V25 占 11~18、V26 占 19~25、
--     V28 占 26，本脚本占用 27。
--   - 菜单与角色授权都写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

CREATE TABLE mem_comment (
  comment_id   bigint        NOT NULL,
  tenant_id    bigint        NOT NULL DEFAULT 0,
  article_id   bigint        NOT NULL,
  member_id    bigint        NOT NULL,
  content      varchar(1000) NOT NULL,
  status       char(1)       NOT NULL DEFAULT '0' COMMENT '0 待审核 / 1 已通过 / 2 已拒绝',
  audit_by     bigint        DEFAULT NULL,
  audit_time   datetime(3)   DEFAULT NULL,
  audit_remark varchar(255)  DEFAULT NULL,
  create_ip    varchar(64)   DEFAULT NULL,
  create_by    bigint        DEFAULT NULL,
  create_time  datetime(3)   NOT NULL,
  update_by    bigint        DEFAULT NULL,
  update_time  datetime(3)   DEFAULT NULL,
  del_flag     char(1)       NOT NULL DEFAULT '0',
  version      int           NOT NULL DEFAULT 0,
  PRIMARY KEY (comment_id),
  KEY idx_mem_comment_article (tenant_id, article_id, status, create_time),
  KEY idx_mem_comment_member (tenant_id, member_id, create_time),
  KEY idx_mem_comment_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员文章评论';

-- ---------------------------------------------------------------------
-- 1. 评论运行期开关（会员管理 → 会员设置）
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 27, '评论功能开关', 'mem.comment.enabled', 'true', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭后门户评论读取与提交接口返回“评论功能已关闭”；后台评论审核仍可查看与清理历史评论'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 27);

-- ---------------------------------------------------------------------
-- 2. 会员管理 → 评论管理
-- ---------------------------------------------------------------------
-- 权限码
--   platform:member:comment:list|audit|remove
-- 评论开关只影响 /api/app/** 与 /api/app/public/**；后台审核不受影响。

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4040, 'member:comment', 4000, '评论管理', 'C', '/member/comment', 'member/comment/index', 'platform:member:comment:list', 'comment', 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4040);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4041, 'member:comment:audit', 4040, '审核评论', 'F', NULL, NULL, 'platform:member:comment:audit', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4041);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4042, 'member:comment:remove', 4040, '删除评论', 'F', NULL, NULL, 'platform:member:comment:remove', NULL, 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4042);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (4040, 4041, 4042)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
