-- =====================================================================
-- 迁移 — 平台用户第三方账号绑定（V19）
-- =====================================================================
-- 用途
--   为后台用户接入第三方登录（微信 / QQ / GitHub / 企业微信等）预留绑定表。
--   本轮只建表，不写 Provider 实现；接入时只需新增 provider 适配器，
--   不再改表结构。
--
-- 设计取舍
--   1. 与 sys_user 一对多：一个后台账号可绑定多个第三方；
--      同一个 (provider, open_id) 只能绑定一个后台账号。
--   2. 不把 open_id / union_id 塞进 sys_user：
--      第三方字段随 Provider 增加而增加，塞主表会让用户表变成协议字段仓库。
--   3. 不建外键：与项目其它表一致，删除用户时由应用层清理绑定。
--   4. raw_data 保留平台返回的原始 JSON，便于排查与后续补字段；
--      其中可能含敏感信息，接口不得原样下发。
--   5. nickname / avatar 是“第三方当时返回的快照”，用于首次建号；
--      用户之后在个人中心改过的资料以 sys_user 为准，不回写覆盖。
--
-- 说明：该表当前没有代码读写，属于第三方登录的 schema 预留；
--       没有配置 Provider 时不会产生任何副作用。
-- =====================================================================

CREATE TABLE sys_user_social (
  social_id       bigint       NOT NULL,
  user_id         bigint       NOT NULL,
  provider        varchar(32)  NOT NULL COMMENT 'wechat_mp / wechat_open / qq / github / gitee / dingtalk',
  open_id         varchar(128) NOT NULL COMMENT '第三方用户唯一标识',
  union_id        varchar(128) DEFAULT NULL COMMENT '跨应用统一标识（微信等平台提供）',
  nickname        varchar(64)  DEFAULT NULL COMMENT '首次授权时的第三方昵称快照',
  avatar          varchar(255) DEFAULT NULL COMMENT '首次授权时的第三方头像快照（URL 或 fileId）',
  raw_data        text         COMMENT '平台返回的原始 JSON，仅排障用，不对外下发',
  last_login_time datetime(3)  DEFAULT NULL,
  create_time     datetime(3)  NOT NULL,
  update_time     datetime(3)  DEFAULT NULL,
  PRIMARY KEY (social_id),
  UNIQUE KEY uk_social_provider_open (provider, open_id),
  KEY idx_social_user (user_id),
  KEY idx_social_union (union_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户-第三方账号绑定';
