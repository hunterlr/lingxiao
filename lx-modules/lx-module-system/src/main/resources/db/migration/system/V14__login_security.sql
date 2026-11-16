-- =====================================================================
-- 凌霄 · 登录安全（V14）
-- =====================================================================
-- 目标：
--   1) 连续登录失败达到阈值后锁定账号，到期自动解锁；
--   2) 密码复杂度策略参数化（off / basic / strong）；
--   3) 管理员可手动解锁，避免只能干等锁定时间。
--
-- 为什么把失败次数放数据库而不是缓存：
--   缓存（Caffeine）在重启后清零，攻击者重启窗口即可继续试错；
--   Redis 是可选依赖，单机不装时也要能工作。
--   放 sys_user 两列后，单实例、重启、后续多实例都读同一份状态。
--
-- 计数语义：
--   login_fail_count  连续失败次数，登录成功 / 解锁 / 重置密码时清零
--   lock_time         锁定截止时间，NULL 表示未锁定
--   锁定期间即使输入正确密码也不放行；到期后下一次尝试从 0 重新计数。
-- =====================================================================

ALTER TABLE sys_user
  ADD COLUMN login_fail_count int NOT NULL DEFAULT 0
      COMMENT '连续登录失败次数，成功登录 / 解锁后清零' AFTER login_time,
  ADD COLUMN lock_time datetime DEFAULT NULL
      COMMENT '锁定截止时间，NULL 表示未锁定' AFTER login_fail_count;

-- ---------------------------------------------------------------------
-- 参数：三个 key 都有真实读取方
--   maxRetryCount / lockMinutes  → AuthService（登录失败处理）
--   passwordPolicy               → PasswordPolicy（新增用户 / 重置密码 / 自助改密）
-- config_type='Y' 表示内置参数：只允许改值，不允许改 key / 控件类型。
--
-- 主键说明：sys_config.config_id 不是自增列，由 MyBatis-Plus 雪花算法赋值，
-- 迁移里的种子数据必须写死。V2 占用 1~3，V5 占用 4，V11 占用 5，V12 占用 6，
-- 所以这里从 7 开始；运行期新建的参数是 19 位雪花 ID，不会撞号。
-- ---------------------------------------------------------------------
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
VALUES
(7, '登录失败锁定阈值', 'sys.account.maxRetryCount', '5', 'Y',
 'number', '3:10', NOW(3), '0', 0,
 '连续登录失败达到该次数后锁定账号（3~10 次）'),
(8, '账号锁定时长', 'sys.account.lockMinutes', '10', 'Y',
 'number', '1:1440', NOW(3), '0', 0,
 '账号锁定分钟数，到期自动解锁（1~1440 分钟）'),
(9, '密码复杂度策略', 'sys.account.passwordPolicy', 'basic', 'Y',
 'select', 'off:不校验,basic:至少 8 位且含字母和数字,strong:至少 10 位且含大写/小写/数字/符号',
 NOW(3), '0', 0,
 '新增用户、管理员重置密码、个人中心改密时生效');

-- ---------------------------------------------------------------------
-- 按钮权限：解锁账号
-- menu_id=105 接在 V4 的用户按钮（101~104）之后，父菜单 5 = 用户管理。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component,
                      perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES (105, 'system:user:unlock', 5, '解锁账号', 'F', NULL, NULL,
        'platform:system:user:unlock', NULL, 5, '0', '0', 'system', NOW(3), '0', 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id = 105
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
  );
