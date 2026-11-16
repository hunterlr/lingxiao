-- =====================================================================
-- 凌霄 · 定时任务（v1.0 第十一次迁移）
-- =====================================================================
-- 背景：
--   V1 已建 sys_job / sys_job_log（RuoYi 风格字段），V2 种了一条演示任务。
--   本轮把“定时任务”从占位页做成真实能力，需要补三样东西：
--     1. 日志能按任务查：sys_job_log 增加 job_id、开始 / 结束 / 耗时、触发方式；
--     2. 集群不重复执行：新增 sys_job_lock，靠行级 UPDATE 抢锁；
--     3. 页面按钮权限：执行一次、改状态、看日志、清日志必须分开授权。
--
-- 设计说明：
--   - 调度实现是 Spring Task + 数据库锁，不建 QRTZ_* 表；
--   - sys_job_lock 一行对应一个任务，任务创建时写入，删除任务时清理；
--   - 锁按 lock_time + 超时时间失效，节点宕机后无需人工解锁；
--   - 按钮权限 ID 沿用“菜单号 * 100 + 序号”：2101~2107。
--
-- 注意：
--   本文件只新增 / 修正数据，不修改 V1 / V2；Flyway 已执行版本不允许改内容。
--   ALTER 拆成单列单句，MySQL / PostgreSQL / 达梦 / 金仓都按标准写法执行。
-- =====================================================================

-- 1) 任务日志补字段 -----------------------------------------------------
ALTER TABLE sys_job_log
    ADD COLUMN job_id bigint DEFAULT NULL COMMENT '所属任务 ID';

ALTER TABLE sys_job_log
    ADD COLUMN trigger_type varchar(16) NOT NULL DEFAULT 'CRON' COMMENT '触发方式 CRON 调度 / MANUAL 手动';

ALTER TABLE sys_job_log
    ADD COLUMN start_time datetime(3) DEFAULT NULL COMMENT '开始时间';

ALTER TABLE sys_job_log
    ADD COLUMN end_time datetime(3) DEFAULT NULL COMMENT '结束时间';

ALTER TABLE sys_job_log
    ADD COLUMN duration_ms bigint DEFAULT NULL COMMENT '耗时毫秒';

CREATE INDEX idx_job_log_job ON sys_job_log (job_id);

-- 2) 集群锁表 -----------------------------------------------------------
CREATE TABLE sys_job_lock (
  job_id     bigint       NOT NULL,
  lock_owner varchar(128) DEFAULT NULL COMMENT '持有者：hostname + UUID，便于排查',
  lock_time  datetime(3)  DEFAULT NULL COMMENT '加锁时间；超过超时时间视为失效',
  PRIMARY KEY (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务集群锁';

-- 3) 演示任务改用真实执行器 ---------------------------------------------
-- 原值 logCleanTask.clean 是“类.方法”风格，v1.0 的 JobHandler SPI 按名称查找，
-- 统一改成 systemLogClean（由 lx-module-system 提供实现）。
UPDATE sys_job
SET invoke_target = 'systemLogClean',
    remark = '每天 02:00 清理超过 sys.log.keepDays 天的登录日志 / 操作日志；默认暂停'
WHERE job_id = 1
  AND invoke_target = 'logCleanTask.clean';

-- 4) 给已有任务补锁行 ---------------------------------------------------
-- 新任务由 Service 在创建时写入；这里只补 V2 种下的 job_id = 1。
INSERT INTO sys_job_lock (job_id, lock_owner, lock_time)
SELECT j.job_id, NULL, NULL
FROM sys_job j
WHERE j.job_id = 1
  AND NOT EXISTS (SELECT 1 FROM sys_job_lock l WHERE l.job_id = j.job_id);

-- 5) 任务日志保留天数参数 -----------------------------------------------
-- 每个内置参数都必须有人读：jobLogClean 执行器读取该键，避免任务日志无限增长。
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, control_type, config_options, create_time, del_flag, version, remark)
VALUES
(5, '任务日志保留天数', 'sys.job.logKeepDays', '30', 'Y', 'number', '1:3650', NOW(3), '0', 0,
 '定时任务日志超过该天数由 jobLogClean 执行器清理（1~3650 天）');

-- 6) 定时任务按钮权限 ---------------------------------------------------
-- 菜单 21 已有 list 权限 platform:monitor:job:list，这里只补写操作按钮。
-- “执行一次”和“清空日志”都是危险动作，单独授权，避免只看列表的人误触。
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(2101, 'monitor:job:add',          21, '新增任务',   'F', NULL, NULL, 'platform:monitor:job:add',          NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(2102, 'monitor:job:edit',         21, '编辑任务',   'F', NULL, NULL, 'platform:monitor:job:edit',         NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(2103, 'monitor:job:remove',       21, '删除任务',   'F', NULL, NULL, 'platform:monitor:job:remove',       NULL, 3, '0', '0', 'system', NOW(3), '0', 0),
(2104, 'monitor:job:changeStatus', 21, '启停任务',   'F', NULL, NULL, 'platform:monitor:job:changeStatus', NULL, 4, '0', '0', 'system', NOW(3), '0', 0),
(2105, 'monitor:job:run',          21, '执行一次',   'F', NULL, NULL, 'platform:monitor:job:run',          NULL, 5, '0', '0', 'system', NOW(3), '0', 0),
(2106, 'monitor:job:log',          21, '执行日志',   'F', NULL, NULL, 'platform:monitor:job:log',          NULL, 6, '0', '0', 'system', NOW(3), '0', 0),
(2107, 'monitor:job:log:clean',    21, '清空日志',   'F', NULL, NULL, 'platform:monitor:job:log:clean',    NULL, 7, '0', '0', 'system', NOW(3), '0', 0);

-- 超级管理员补授新增按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.perms IN (
    'platform:monitor:job:add',
    'platform:monitor:job:edit',
    'platform:monitor:job:remove',
    'platform:monitor:job:changeStatus',
    'platform:monitor:job:run',
    'platform:monitor:job:log',
    'platform:monitor:job:log:clean'
)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
);
