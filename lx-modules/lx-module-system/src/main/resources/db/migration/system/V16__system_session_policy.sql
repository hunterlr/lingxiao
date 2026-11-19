-- =====================================================================
-- 凌霄 · 会话策略（V16）
-- =====================================================================
-- 参数 sys.account.multiDeviceLogin：
--   false（默认）：单会话。新登录时注销该账号的全部旧 Token，
--                  在线用户列表每个账号最多 1 行；
--   true：允许多端同时在线，列表按 Token 一行展示，可单独强退。
--
-- 读取方只有 AuthService（通过 SessionPolicy），参数缺失 / 非法时按
-- 单会话处理：失败收紧，不会因为一个参数写错就悄悄放开多端。
--
-- 主键说明：sys_config.config_id 不是自增列，由 MyBatis-Plus 雪花算法赋值，
-- 迁移里的种子数据必须写死。V2 占用 1~3，V5 占用 4，V11 占用 5，V12 占用 6，
-- V14 占用 7~9，所以这里用 10；运行期新建的参数是 19 位雪花 ID，不会撞号。
-- =====================================================================

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
VALUES (10, '允许多端登录', 'sys.account.multiDeviceLogin', 'false', 'Y',
        'switch', 'true:false', NOW(3), '0', 0,
        '关闭时新登录会踢掉该账号的旧会话（默认关闭）');
