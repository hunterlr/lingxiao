-- =====================================================================
-- 迁移 — 会员消息通道配置（V27）
-- =====================================================================
-- 范围
--   1. mem_channel_config        短信 / 邮件通道配置（dev / prod、服务商、密钥）
--   2. 会员管理 → 渠道配置菜单与按钮权限（menu_id 4020 段）
--
-- 约定
--   - 每个通道一行，channel_type 唯一：sms / email。
--   - 默认 mode=dev，使用写日志的开发实现，开箱可用。
--   - 密钥落库但接口只写不读；生产上线前由运营在后台填写并切到 prod。
--   - 切 prod 前必须由部署方提供 MemberSmsSender / MemberEmailSender 实现，
--     否则后台保存会被拒绝，避免找回密码静默失效。
--   - 菜单与角色授权写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

CREATE TABLE mem_channel_config (
  config_id      bigint       NOT NULL,
  channel_type   varchar(16)  NOT NULL COMMENT 'sms / email',
  mode           varchar(16)  NOT NULL DEFAULT 'dev' COMMENT 'dev 开发日志 / prod 生产通道',
  provider       varchar(32)  DEFAULT NULL COMMENT 'dev / aliyun / tencent / smtp / custom',
  endpoint       varchar(255) DEFAULT NULL COMMENT '服务商接口地址；SMTP 可填主机与端口',
  access_key     varchar(128) DEFAULT NULL,
  access_secret  varchar(255) DEFAULT NULL COMMENT '只写不读，接口不回显',
  sign_name      varchar(64)  DEFAULT NULL COMMENT '短信签名',
  template_code  varchar(64)  DEFAULT NULL COMMENT '短信模板编码',
  sender         varchar(128) DEFAULT NULL COMMENT '邮件发件人 / 短信发送方标识',
  status         char(1)      NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  create_by      bigint       DEFAULT NULL,
  create_time    datetime(3)  NOT NULL,
  update_by      bigint       DEFAULT NULL,
  update_time    datetime(3)  DEFAULT NULL,
  del_flag       char(1)      NOT NULL DEFAULT '0',
  version        int          NOT NULL DEFAULT 0,
  remark         varchar(500) DEFAULT NULL,
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_mem_channel_type (channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员消息通道配置';

INSERT INTO mem_channel_config
  (config_id, channel_type, mode, provider, status, create_time, del_flag, version, remark)
SELECT 1, 'sms', 'dev', 'dev', '0', NOW(3), '0', 0,
       '短信通道：默认开发实现只写日志；生产需实现 MemberSmsSender 后切到 prod'
WHERE NOT EXISTS (SELECT 1 FROM mem_channel_config WHERE config_id = 1);

INSERT INTO mem_channel_config
  (config_id, channel_type, mode, provider, status, create_time, del_flag, version, remark)
SELECT 2, 'email', 'dev', 'dev', '0', NOW(3), '0', 0,
       '邮件通道：默认开发实现只写日志；生产需实现 MemberEmailSender 后切到 prod'
WHERE NOT EXISTS (SELECT 1 FROM mem_channel_config WHERE config_id = 2);

-- ---------------------------------------------------------------------
-- 会员管理 → 渠道配置
-- ---------------------------------------------------------------------
-- 权限码
--   platform:member:channel:query|edit|test

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4020, 'member:channel', 4000, '渠道配置', 'C', '/member/channel', 'member/channel/index', 'platform:member:channel:query', 'message', 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4020);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4021, 'member:channel:edit', 4020, '编辑渠道', 'F', NULL, NULL, 'platform:member:channel:edit', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4021);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4022, 'member:channel:test', 4020, '测试发送', 'F', NULL, NULL, 'platform:member:channel:test', NULL, 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4022);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (4020, 4021, 4022)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
