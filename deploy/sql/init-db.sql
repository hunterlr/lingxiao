-- =====================================================================
-- 凌霄 · 数据库初始化（MySQL 5.7 / 8.0）
-- =====================================================================
-- 只建库、建账号、授权；业务表由应用启动时的 Flyway 自动创建。
-- 执行前把 CHANGE_ME 换成真实密码，并与 application-prod.yml 的
-- DB_PASSWORD 保持一致。
--
--   mysql -uroot -p < deploy/sql/init-db.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS lingxiao
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

-- 应用账号：生产建议限制来源网段，例如 'lingxiao'@'10.0.0.%'
CREATE USER IF NOT EXISTS 'lingxiao'@'%' IDENTIFIED BY 'CHANGE_ME';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON lingxiao.* TO 'lingxiao'@'%';

FLUSH PRIVILEGES;
