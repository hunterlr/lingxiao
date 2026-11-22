-- =====================================================================
-- 迁移 — 会员安全补口（V26）
-- =====================================================================
-- 范围
--   1. 注册 / 登录 / 找回密码 / 重置密码限流参数
--   2. 图形验证码错误次数上限
--   3. 真实短信 / 邮件通道的开发开关说明
--
-- 约定
--   - 只新增 sys_config，不改 V25 已落库的表结构。
--   - config_id 非自增：V25 占用 11~18，本脚本从 19 起占 19~25。
--   - 参数删除后模块回退代码默认值，不影响登录 / 注册流程。
--   - 限流只挡短时间脚本重放；登录失败锁定仍以 mem_user 落库字段为准。
-- =====================================================================

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 19, '会员验证码错误次数上限', 'mem.security.captchaMaxAttempts', '3', 'Y',
       'number', '1:10', NOW(3), '0', 0,
       '同一图形验证码允许输错的次数，达到上限后验证码立即作废'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 19);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 20, '会员注册限流次数', 'mem.register.rateLimitCount', '5', 'Y',
       'number', '1:100', NOW(3), '0', 0,
       '同一用户名 / IP 在窗口内允许的注册请求数；IP 阈值自动放宽为账号阈值的 10 倍'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 20);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 21, '会员注册限流窗口', 'mem.register.rateLimitMinutes', '10', 'Y',
       'number', '1:1440', NOW(3), '0', 0,
       '会员注册限流窗口分钟数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 21);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 22, '会员登录限流次数', 'mem.account.loginRateLimitCount', '30', 'Y',
       'number', '1:1000', NOW(3), '0', 0,
       '同一账号 / IP 在窗口内允许的登录请求数；与登录失败锁定相互独立'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 22);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 23, '会员登录限流窗口', 'mem.account.loginRateLimitMinutes', '10', 'Y',
       'number', '1:1440', NOW(3), '0', 0,
       '会员登录限流窗口分钟数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 23);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 24, '会员找回密码限流次数', 'mem.register.resetRateLimitCount', '5', 'Y',
       'number', '1:100', NOW(3), '0', 0,
       '同一账号 / IP 在窗口内允许的找回密码与重置密码请求数，IP 阈值自动放宽为账号阈值的 10 倍'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 24);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 25, '会员找回密码限流窗口', 'mem.register.resetRateLimitMinutes', '10', 'Y',
       'number', '1:1440', NOW(3), '0', 0,
       '会员找回密码与重置密码限流窗口分钟数'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 25);
