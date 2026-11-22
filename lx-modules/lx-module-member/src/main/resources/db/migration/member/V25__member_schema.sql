-- =====================================================================
-- 迁移 — 会员模块（V25）
-- =====================================================================
-- 范围
--   1. mem_level          会员等级（含 3 条种子：注册 / 活跃 / VIP）
--   2. mem_user           会员账号（与 sys_user 物理隔离）
--   3. mem_password_reset 找回密码验证码（只存 BCrypt 哈希）
--   4. mem.* 参数种子     验证码 / 密码策略 / 锁定 / 多端登录 / 注册必填
--   5. 会员管理菜单与按钮权限（menu_id 4000 段，module_code = 'member'）
--
-- 约定
--   - 表名统一 mem_ 前缀；只操作 mem_*，不碰 sys_user / sys_role 的业务数据。
--   - 会员与平台后台不共 Token、不共权限缓存；member_id / user_id 互不引用。
--   - tenant_id 单租户部署固定 0，唯一性都带 tenant_id，给后续 SaaS 化留位。
--   - 用户名 / 手机号 / 邮箱不建唯一键：逻辑删除后要允许同名重新注册，
--     带 del_flag 的唯一键会在“两条已删除记录”上互相冲突，唯一性由应用层保证。
--   - 菜单与参数都写 NOT EXISTS，重复导入不产生重复行，不使用 INSERT IGNORE。
--   - sys_config.config_id 非自增，必须写死：V2 占 1~3、V5 占 4、V11/V12 占 5~6、
--     V14 占 7~9、V16 占 10；本脚本从 11 起占用 11~18。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会员等级
-- ---------------------------------------------------------------------
CREATE TABLE mem_level (
  level_id       bigint       NOT NULL,
  tenant_id      bigint       NOT NULL DEFAULT 0,
  level_code     varchar(32)  NOT NULL COMMENT '租户内稳定键，例如 registered / active / vip',
  level_name     varchar(64)  NOT NULL,
  min_growth     int          NOT NULL DEFAULT 0 COMMENT '区间下界，含',
  max_growth     int          DEFAULT NULL COMMENT '区间上界，含；NULL 表示不封顶',
  benefits_json  varchar(2000) DEFAULT NULL COMMENT '权益 JSON，v1.2 预留',
  sort           int          NOT NULL DEFAULT 0 COMMENT '升序，越小等级越低',
  status         char(1)      NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  create_by      bigint       DEFAULT NULL,
  create_time    datetime(3)  NOT NULL,
  update_by      bigint       DEFAULT NULL,
  update_time    datetime(3)  DEFAULT NULL,
  del_flag       char(1)      NOT NULL DEFAULT '0',
  version        int          NOT NULL DEFAULT 0,
  remark         varchar(500) DEFAULT NULL,
  PRIMARY KEY (level_id),
  UNIQUE KEY uk_mem_level_code (tenant_id, level_code),
  KEY idx_mem_level_sort (tenant_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';

INSERT INTO mem_level
  (level_id, tenant_id, level_code, level_name, min_growth, max_growth, sort, status,
   create_time, del_flag, version, remark)
SELECT 1, 0, 'registered', '注册会员', 0, 999, 1, '0', NOW(3), '0', 0, '注册即获得，默认等级'
WHERE NOT EXISTS (SELECT 1 FROM mem_level WHERE level_id = 1);

INSERT INTO mem_level
  (level_id, tenant_id, level_code, level_name, min_growth, max_growth, sort, status,
   create_time, del_flag, version, remark)
SELECT 2, 0, 'active', '活跃会员', 1000, 4999, 2, '0', NOW(3), '0', 0, '成长值达到 1000 后升级'
WHERE NOT EXISTS (SELECT 1 FROM mem_level WHERE level_id = 2);

INSERT INTO mem_level
  (level_id, tenant_id, level_code, level_name, min_growth, max_growth, sort, status,
   create_time, del_flag, version, remark)
SELECT 3, 0, 'vip', 'VIP 会员', 5000, NULL, 3, '0', NOW(3), '0', 0, '成长值达到 5000 后升级，不封顶'
WHERE NOT EXISTS (SELECT 1 FROM mem_level WHERE level_id = 3);

-- ---------------------------------------------------------------------
-- 2. 会员账号
-- ---------------------------------------------------------------------
CREATE TABLE mem_user (
  member_id         bigint       NOT NULL,
  tenant_id         bigint       NOT NULL DEFAULT 0,
  username          varchar(64)  NOT NULL COMMENT '登录账号，租户内应用层唯一',
  nickname          varchar(64)  NOT NULL,
  password          varchar(100) NOT NULL COMMENT 'BCrypt 哈希，接口只写不读',
  mobile            varchar(32)  DEFAULT NULL,
  email             varchar(128) DEFAULT NULL,
  avatar            varchar(255) DEFAULT NULL COMMENT 'fileId 或 http(s) 外链，不建 mem_file',
  sex               char(1)      NOT NULL DEFAULT '2' COMMENT '0 男 / 1 女 / 2 未知',
  level_id          bigint       DEFAULT NULL,
  points            int          NOT NULL DEFAULT 0 COMMENT '可消耗积分',
  growth            int          NOT NULL DEFAULT 0 COMMENT '成长值，只增不减',
  status            char(1)      NOT NULL DEFAULT '0' COMMENT '0 正常 / 1 停用',
  register_channel  varchar(20)  NOT NULL DEFAULT 'web' COMMENT 'web / h5 / app / admin',
  register_ip       varchar(64)  DEFAULT NULL,
  login_ip          varchar(64)  DEFAULT NULL,
  login_time        datetime(3)  DEFAULT NULL,
  login_fail_count  int          NOT NULL DEFAULT 0,
  lock_time         datetime(3)  DEFAULT NULL COMMENT '锁定截止时间，NULL 表示未锁定',
  last_active_time  datetime(3)  DEFAULT NULL,
  create_by         bigint       DEFAULT NULL,
  create_time       datetime(3)  NOT NULL,
  update_by         bigint       DEFAULT NULL,
  update_time       datetime(3)  DEFAULT NULL,
  del_flag          char(1)      NOT NULL DEFAULT '0',
  version           int          NOT NULL DEFAULT 0,
  remark            varchar(500) DEFAULT NULL,
  PRIMARY KEY (member_id),
  KEY idx_mem_user_username (tenant_id, username),
  KEY idx_mem_user_mobile (tenant_id, mobile),
  KEY idx_mem_user_email (tenant_id, email),
  KEY idx_mem_user_level (tenant_id, level_id),
  KEY idx_mem_user_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台会员账号';

-- ---------------------------------------------------------------------
-- 3. 找回密码验证码（一次性，审计数据不做逻辑删除）
-- ---------------------------------------------------------------------
CREATE TABLE mem_password_reset (
  reset_id     bigint       NOT NULL,
  member_id    bigint       NOT NULL,
  account      varchar(128) DEFAULT NULL COMMENT '申请时使用的账号，仅用于排查',
  code_hash    varchar(100) NOT NULL COMMENT '验证码 BCrypt 哈希',
  expire_time  datetime(3)  NOT NULL,
  used         char(1)      NOT NULL DEFAULT '0' COMMENT '0 未使用 / 1 已使用',
  create_time  datetime(3)  NOT NULL,
  use_time     datetime(3)  DEFAULT NULL,
  PRIMARY KEY (reset_id),
  KEY idx_mem_password_reset_member (member_id, used, create_time),
  KEY idx_mem_password_reset_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员找回密码验证码';

-- ---------------------------------------------------------------------
-- 4. 会员参数种子
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 11, '会员验证码开关', 'mem.security.captchaEnabled', 'true', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '会员注册 / 登录 / 找回密码是否要求图形验证码；同时受 lx.captcha.enabled 总闸控制'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 11);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 12, '会员密码策略', 'mem.security.passwordPolicy', 'basic', 'Y',
       'select', 'off:不校验,basic:至少8位且含字母和数字,strong:至少10位且含大小写数字和符号',
       NOW(3), '0', 0,
       '只在注册 / 找回 / 改密 / 后台重置时校验；登录不校验，避免策略调整挡住老用户'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 12);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 13, '会员登录失败阈值', 'mem.account.maxRetryCount', '5', 'Y',
       'number', '3:10', NOW(3), '0', 0,
       '连续失败达到该次数锁定会员账号（3~10 次）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 13);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 14, '会员账号锁定时长', 'mem.account.lockMinutes', '10', 'Y',
       'number', '1:1440', NOW(3), '0', 0,
       '锁定后多少分钟自动解锁（1~1440 分钟）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 14);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 15, '会员允许多端登录', 'mem.account.multiDeviceLogin', 'false', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭时新登录踢掉该会员的全部旧会话；开启后允许多端并存'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 15);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 16, '会员注册手机号必填', 'mem.register.mobileRequired', 'false', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '开启后注册必须填写手机号；关闭时可只填用户名和密码'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 16);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 17, '会员注册邮箱必填', 'mem.register.emailRequired', 'false', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '开启后注册必须填写邮箱；关闭时邮箱选填'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 17);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 18, '找回密码验证码有效期', 'mem.register.resetCodeExpireMinutes', '10', 'Y',
       'number', '5:60', NOW(3), '0', 0,
       '会员找回密码验证码有效分钟数（5~60 分钟）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 18);

-- ---------------------------------------------------------------------
-- 5. 会员管理菜单与权限
-- ---------------------------------------------------------------------
-- 权限码
--   platform:member:user:list|query|add|edit|remove|resetPwd|status
-- 会员端不读取任何 platform:* 权限码，这里只控制后台页面与按钮。

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4000, 'member', 0, '会员管理', 'M', '/member', NULL, NULL, 'peoples', 5, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4000);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4010, 'member:user', 4000, '会员列表', 'C', '/member/user', 'member/user/index', 'platform:member:user:list', 'user', 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4010);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4011, 'member:user:query', 4010, '会员详情', 'F', NULL, NULL, 'platform:member:user:query', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4011);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4012, 'member:user:add', 4010, '新增会员', 'F', NULL, NULL, 'platform:member:user:add', NULL, 2, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4012);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4013, 'member:user:edit', 4010, '编辑会员', 'F', NULL, NULL, 'platform:member:user:edit', NULL, 3, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4013);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4014, 'member:user:status', 4010, '启停会员', 'F', NULL, NULL, 'platform:member:user:status', NULL, 4, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4014);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4015, 'member:user:resetPwd', 4010, '重置密码', 'F', NULL, NULL, 'platform:member:user:resetPwd', NULL, 5, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4015);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4016, 'member:user:remove', 4010, '删除会员', 'F', NULL, NULL, 'platform:member:user:remove', NULL, 6, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4016);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.module_code = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
