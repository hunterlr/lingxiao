-- =====================================================================
-- 迁移 — 会员手机号 / 邮箱换绑与密码重置补强（V34）
-- =====================================================================
-- 范围
--   1. mem_contact_change    手机号 / 邮箱换绑验证码（只存 BCrypt 哈希）
--   2. mem.account.contact*  换绑验证码有效期与限流参数
--
-- 约定
--   - 换绑只接受已登录会员，验证码发送到“新联系方式”，验证的是新值归属；
--     已设置密码的账号发码前还要校验当前密码，避免会话被盗后直接改绑。
--   - 验证码一次性使用；申请新码时同会员同类型的旧码立即作废。
--   - 不物理删除联系方式，只改 mem_user.mobile / email；
--     唯一键由 V33 的生成列兜底，换绑前应用层仍会做友好校验。
--   - sys_config.config_id 非自增，必须写死：V25 占 11~18、V26 占 19~25、
--     V28 占 26、V30 占 27、V31 占 28~30；本脚本占 31~33。
-- =====================================================================

CREATE TABLE mem_contact_change (
  change_id     bigint       NOT NULL,
  member_id     bigint       NOT NULL,
  contact_type  varchar(16)  NOT NULL COMMENT 'mobile / email',
  old_value     varchar(128) DEFAULT NULL COMMENT '申请时的旧联系方式，仅审计',
  new_value     varchar(128) NOT NULL COMMENT '待绑定的新手机号 / 邮箱',
  code_hash     varchar(100) NOT NULL COMMENT '验证码 BCrypt 哈希',
  expire_time   datetime(3)  NOT NULL,
  used          char(1)      NOT NULL DEFAULT '0' COMMENT '0 未使用 / 1 已使用',
  request_ip    varchar(64)  DEFAULT NULL,
  create_time   datetime(3)  NOT NULL,
  use_time      datetime(3)  DEFAULT NULL,
  PRIMARY KEY (change_id),
  KEY idx_mem_contact_change_member (member_id, contact_type, used, create_time),
  KEY idx_mem_contact_change_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员手机号 / 邮箱换绑验证码';

-- ---------------------------------------------------------------------
-- 换绑参数
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 31, '换绑验证码有效期', 'mem.account.contactCodeExpireMinutes', '10', 'Y',
       'number', '5:60', NOW(3), '0', 0,
       '更换手机号 / 邮箱的验证码有效分钟数（5~60 分钟）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 31);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 32, '换绑验证码限流次数', 'mem.account.contactRateLimitCount', '5', 'Y',
       'number', '1:100', NOW(3), '0', 0,
       '同一会员 / IP 在窗口内允许的换绑发码与确认次数；IP 阈值自动放宽为会员阈值的 10 倍'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 32);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 33, '换绑验证码限流窗口', 'mem.account.contactRateLimitMinutes', '10', 'Y',
       'number', '1:1440', NOW(3), '0', 0,
       '会员换绑手机号 / 邮箱的限流窗口分钟数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 33);
