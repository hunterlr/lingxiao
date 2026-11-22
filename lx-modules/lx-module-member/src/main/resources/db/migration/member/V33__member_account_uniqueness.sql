-- =====================================================================
-- 迁移 — 会员账号唯一性、密码标记与第三方绑定约束（V33）
-- =====================================================================
-- 范围
--   1. mem_user：password_set 标记、用户名 / 手机号 / 邮箱唯一键
--   2. mem_user_social：已验证联系方式快照、会员 + 平台唯一键
--   3. mem_merge_log：后台账号合并审计
--   4. 会员账号合并按钮权限（menu_id 4017）
--
-- 约定
--   - 唯一键用生成列排除逻辑删除行：del_flag='0' 才生成值，删除后为 NULL；
--     MySQL 唯一索引允许多个 NULL，因此同一个用户名可以重新注册。
--   - 唯一键带 tenant_id，单租户部署固定 0，给后续 SaaS 化留位。
--   - password_set：0 未设置（第三方自动建号）/ 1 已设置；老数据按注册渠道回填。
--   - mem_user_social 唯一键 (tenant_id, member_id, provider)：同一会员同一平台
--     只保留一条绑定；open_id 维度的唯一键 V31 已存在。
--   - 执行前可先跑本文件末尾的“重复数据预检”查询；本库检查结果为空。
--   - 菜单与角色授权都写 NOT EXISTS，重复导入不产生重复行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会员密码标记
-- ---------------------------------------------------------------------
ALTER TABLE mem_user
  ADD COLUMN password_set char(1) NOT NULL DEFAULT '0'
    COMMENT '0 未设置（第三方自动建号）/ 1 已设置' AFTER password;

-- 老会员全部有真实密码；第三方渠道创建的老账号保留未设置状态，
-- 后续首次设置密码时再改为 1。
UPDATE mem_user
SET password_set = CASE WHEN register_channel = 'oauth' THEN '0' ELSE '1' END;

-- ---------------------------------------------------------------------
-- 2. 登录标识唯一键（生成列 + 唯一索引）
-- ---------------------------------------------------------------------
ALTER TABLE mem_user
  ADD COLUMN username_key varchar(64)
    GENERATED ALWAYS AS (IF(del_flag = '0', username, NULL)) STORED
    COMMENT '未删除行的用户名，用于唯一约束',
  ADD COLUMN mobile_key varchar(32)
    GENERATED ALWAYS AS (IF(del_flag = '0' AND mobile IS NOT NULL AND mobile <> '', mobile, NULL)) STORED
    COMMENT '未删除行且非空的手机号，用于唯一约束',
  ADD COLUMN email_key varchar(128)
    GENERATED ALWAYS AS (IF(del_flag = '0' AND email IS NOT NULL AND email <> '', email, NULL)) STORED
    COMMENT '未删除行且非空的邮箱，用于唯一约束';

ALTER TABLE mem_user
  DROP INDEX idx_mem_user_username,
  DROP INDEX idx_mem_user_mobile,
  DROP INDEX idx_mem_user_email;

ALTER TABLE mem_user
  ADD UNIQUE KEY uk_mem_user_username (tenant_id, username_key),
  ADD UNIQUE KEY uk_mem_user_mobile (tenant_id, mobile_key),
  ADD UNIQUE KEY uk_mem_user_email (tenant_id, email_key);

-- ---------------------------------------------------------------------
-- 3. 第三方绑定：已验证联系方式与“会员 + 平台”唯一键
-- ---------------------------------------------------------------------
ALTER TABLE mem_user_social
  ADD COLUMN email varchar(128) DEFAULT NULL COMMENT '平台返回并确认已验证的邮箱',
  ADD COLUMN mobile varchar(32) DEFAULT NULL COMMENT '平台返回并确认已验证的手机号',
  ADD COLUMN email_verified char(1) NOT NULL DEFAULT '0' COMMENT '0 未验证 / 1 已验证',
  ADD COLUMN mobile_verified char(1) NOT NULL DEFAULT '0' COMMENT '0 未验证 / 1 已验证';

-- 历史数据理论上不会有同一会员同一平台多条；若存在，保留最早的一条，
-- 避免加唯一键时失败。真正需要合并的账号由后台合并工具处理。
DELETE dup FROM mem_user_social dup
JOIN mem_user_social keep
  ON keep.tenant_id = dup.tenant_id
 AND keep.member_id = dup.member_id
 AND keep.provider = dup.provider
 AND keep.social_id < dup.social_id;

ALTER TABLE mem_user_social
  ADD UNIQUE KEY uk_mem_user_social_member (tenant_id, member_id, provider);

-- ---------------------------------------------------------------------
-- 4. 账号合并审计
-- ---------------------------------------------------------------------
CREATE TABLE mem_merge_log (
  merge_id         bigint       NOT NULL,
  tenant_id        bigint       NOT NULL DEFAULT 0,
  source_member_id bigint       NOT NULL,
  target_member_id bigint       NOT NULL,
  source_username  varchar(64)  DEFAULT NULL,
  target_username  varchar(64)  DEFAULT NULL,
  favorite_moved   int          NOT NULL DEFAULT 0,
  favorite_skipped int          NOT NULL DEFAULT 0,
  like_moved       int          NOT NULL DEFAULT 0,
  like_skipped     int          NOT NULL DEFAULT 0,
  comment_moved    int          NOT NULL DEFAULT 0,
  social_moved     int          NOT NULL DEFAULT 0,
  social_skipped   int          NOT NULL DEFAULT 0,
  operator_id      bigint       DEFAULT NULL,
  remark           varchar(500) DEFAULT NULL,
  create_time      datetime(3)  NOT NULL,
  PRIMARY KEY (merge_id),
  KEY idx_mem_merge_target (tenant_id, target_member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员账号合并审计';

-- ---------------------------------------------------------------------
-- 5. 会员列表 → 合并账号
-- ---------------------------------------------------------------------
-- 权限码：platform:member:user:merge
INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 4017, 'member:user:merge', 4010, '合并账号', 'F', NULL, NULL, 'platform:member:user:merge', NULL, 7, '0', '0', 'member', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4017);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 4017
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 4017
);

-- ---------------------------------------------------------------------
-- 6. 重复数据预检（只读，便于运维在升级前排查）
-- ---------------------------------------------------------------------
-- 升级前执行以下查询；返回任何一行都说明需要先人工处理重复账号，
-- 否则唯一索引会因 Duplicate entry 失败：
--
--   SELECT 'username' AS field_name, username AS field_value,
--          COUNT(*) AS duplicate_count, GROUP_CONCAT(member_id) AS member_ids
--     FROM mem_user WHERE del_flag = '0'
--    GROUP BY username HAVING COUNT(*) > 1
--   UNION ALL
--   SELECT 'mobile', mobile, COUNT(*), GROUP_CONCAT(member_id)
--     FROM mem_user WHERE del_flag = '0' AND mobile IS NOT NULL AND mobile <> ''
--    GROUP BY mobile HAVING COUNT(*) > 1
--   UNION ALL
--   SELECT 'email', email, COUNT(*), GROUP_CONCAT(member_id)
--     FROM mem_user WHERE del_flag = '0' AND email IS NOT NULL AND email <> ''
--    GROUP BY email HAVING COUNT(*) > 1;
