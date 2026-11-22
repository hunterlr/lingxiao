-- =====================================================================
-- 迁移 — 会员第三方登录（V31）
-- =====================================================================
-- 范围
--   1. mem_oauth_provider  第三方登录服务商配置（启停、ClientId / Secret、回调、端点覆写）
--   2. mem_user_social     会员与第三方账号的绑定关系
--   3. mem.oauth.*         第三方登录总开关 / 自动建号 / 门户地址
--   4. 第三方登录菜单与按钮权限（menu_id 4050 段，module_code = 'member'）
--
-- 约定
--   - 只存 member_id，不建跨模块外键；与平台后台 sys_user_social 完全分开。
--   - 服务商端点只作为“内置默认值”，后台可覆写；密钥只写不读，接口返回是否已配置。
--   - 登录流程走一次性 state，回调后再用一次性 ticket 把会话交给门户，Token 不出现在 URL。
--   - sys_config.config_id 非自增，必须写死：V25 占 11~18、V26 占 19~25、
--     V28 占 26、V30 占 27，本脚本占用 28~30。
--   - 菜单与角色授权都写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 服务商配置
-- ---------------------------------------------------------------------
CREATE TABLE mem_oauth_provider (
  tenant_id      bigint       NOT NULL DEFAULT 0,
  provider       varchar(32)  NOT NULL COMMENT '稳定编码，例如 wechat_open / github',
  display_name   varchar(64)  NOT NULL,
  status         char(1)      NOT NULL DEFAULT '1' COMMENT '0 启用 / 1 停用',
  client_id      varchar(255) DEFAULT NULL,
  client_secret  text         DEFAULT NULL COMMENT 'ClientSecret / AppSecret / 私钥；接口只写不读',
  redirect_uri   varchar(500) DEFAULT NULL COMMENT '留空时按门户地址自动生成回调',
  scope          varchar(500) DEFAULT NULL COMMENT '留空使用内置默认 scope',
  authorize_url  varchar(500) DEFAULT NULL COMMENT '留空使用内置授权端点',
  token_url      varchar(500) DEFAULT NULL COMMENT '留空使用内置换 Token 端点',
  user_info_url  varchar(500) DEFAULT NULL COMMENT '留空使用内置用户信息端点',
  extra_json     varchar(2000) DEFAULT NULL COMMENT '预留：PKCE / 团队 ID / 密钥 ID 等服务商差异参数',
  sort           int          NOT NULL DEFAULT 0,
  remark         varchar(500) DEFAULT NULL,
  create_time    datetime(3)  NOT NULL,
  update_time    datetime(3)  DEFAULT NULL,
  PRIMARY KEY (tenant_id, provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员第三方登录服务商配置';

-- 内置服务商：默认全部停用，填写 ClientId / Secret 后再打开开关。
INSERT INTO mem_oauth_provider
  (tenant_id, provider, display_name, status, sort, create_time)
SELECT 0, t.provider, t.display_name, '1', t.sort, NOW(3)
FROM (
  SELECT 'wechat_open' AS provider, '微信开放平台' AS display_name, 10 AS sort
  UNION ALL SELECT 'wechat_mp', '微信公众号', 20
  UNION ALL SELECT 'qq', 'QQ', 30
  UNION ALL SELECT 'weibo', '新浪微博', 40
  UNION ALL SELECT 'alipay', '支付宝', 50
  UNION ALL SELECT 'douyin', '抖音', 60
  UNION ALL SELECT 'dingtalk', '钉钉', 70
  UNION ALL SELECT 'gitee', 'Gitee', 80
  UNION ALL SELECT 'github', 'GitHub', 110
  UNION ALL SELECT 'gitlab', 'GitLab', 120
  UNION ALL SELECT 'google', 'Google', 130
  UNION ALL SELECT 'microsoft', 'Microsoft', 140
  UNION ALL SELECT 'apple', 'Apple', 150
  UNION ALL SELECT 'facebook', 'Facebook', 160
  UNION ALL SELECT 'x', 'X (Twitter)', 170
  UNION ALL SELECT 'linkedin', 'LinkedIn', 180
  UNION ALL SELECT 'discord', 'Discord', 190
  UNION ALL SELECT 'line', 'LINE', 200
  UNION ALL SELECT 'kakao', 'Kakao', 210
  UNION ALL SELECT 'naver', 'Naver', 220
  UNION ALL SELECT 'amazon', 'Amazon', 230
  UNION ALL SELECT 'yahoo', 'Yahoo', 240
  UNION ALL SELECT 'reddit', 'Reddit', 250
  UNION ALL SELECT 'slack', 'Slack', 260
  UNION ALL SELECT 'twitch', 'Twitch', 270
  UNION ALL SELECT 'spotify', 'Spotify', 280
) t
WHERE NOT EXISTS (
  SELECT 1 FROM mem_oauth_provider p
  WHERE p.tenant_id = 0 AND p.provider = t.provider
);

-- ---------------------------------------------------------------------
-- 2. 会员第三方账号绑定
-- ---------------------------------------------------------------------
CREATE TABLE mem_user_social (
  social_id       bigint       NOT NULL,
  tenant_id       bigint       NOT NULL DEFAULT 0,
  member_id       bigint       NOT NULL,
  provider        varchar(32)  NOT NULL,
  open_id         varchar(128) NOT NULL,
  union_id        varchar(128) DEFAULT NULL,
  nickname        varchar(64)  DEFAULT NULL COMMENT '首次授权时的昵称快照',
  avatar          varchar(255) DEFAULT NULL COMMENT '首次授权时的头像快照',
  raw_data        varchar(4000) DEFAULT NULL COMMENT '最近一次用户信息 JSON，仅排障用，接口不下发',
  last_login_time datetime(3)  DEFAULT NULL,
  create_time     datetime(3)  NOT NULL,
  update_time     datetime(3)  DEFAULT NULL,
  PRIMARY KEY (social_id),
  UNIQUE KEY uk_mem_user_social_open (tenant_id, provider, open_id),
  KEY idx_mem_user_social_member (tenant_id, member_id),
  KEY idx_mem_user_social_union (tenant_id, provider, union_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员第三方账号绑定';

-- ---------------------------------------------------------------------
-- 3. 第三方登录参数（会员管理 → 会员设置）
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 28, '第三方登录总开关', 'mem.oauth.enabled', 'false', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭后门户登录 / 注册页不展示第三方登录入口，授权与回调接口返回“第三方登录已关闭”；已绑定关系保留'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 28);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 29, '第三方登录自动建号', 'mem.oauth.autoRegister', 'true', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '开启后首次第三方授权可直接创建会员账号；关闭时必须先登录再绑定，未绑定的授权返回 not_bound'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 29);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 30, '门户对外地址', 'mem.oauth.portalBaseUrl', 'http://127.0.0.1:3000', 'Y',
       'input', NULL, NOW(3), '0', 0,
       '第三方登录回调后跳回门户的根地址，例如 https://www.example.com；生产环境必须改成真实域名'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 30);

-- ---------------------------------------------------------------------
-- 4. 会员管理 → 第三方登录
-- ---------------------------------------------------------------------
-- 权限码
--   platform:member:oauth:list|edit
INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4050, 'member:oauth', 4000, '第三方登录', 'C', '/member/oauth', 'member/oauth/index', 'platform:member:oauth:list', 'link', 4, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4050);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4051, 'member:oauth:edit', 4050, '编辑第三方登录', 'F', NULL, NULL, 'platform:member:oauth:edit', NULL, 1, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4051);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (4050, 4051)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
