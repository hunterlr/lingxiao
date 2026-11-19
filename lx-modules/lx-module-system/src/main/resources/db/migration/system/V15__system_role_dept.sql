-- =====================================================================
-- 凌霄 · 角色自定义数据范围（V15）
-- =====================================================================
-- 目的：
--   补齐 sys_role.data_scope = '2'（自定义部门）的落地表。
--   v1.0 早期没有这张表，保存 2 会被拒绝、历史数据降级为“仅本人”；
--   本次补齐后，2 与 1 / 3 / 4 / 5 走同一套 DataScopeService。
--
-- 语义：
--   - 一行表示“该角色的数据范围包含该部门”；
--   - 自定义范围是**精确集合**，不自动包含下级部门；
--     需要下级时请显式勾选，或改用 4 本部门及以下；
--   - 多角色时按并集取最大范围（见 DataScopeService）。
--
-- 约定：
--   - 不使用外键（与 V1 一致，跨表一致性由应用层保证）；
--   - (role_id, dept_id) 联合主键天然去重；
--   - dept_id 单列索引用于删除部门时清理关联。
-- =====================================================================

CREATE TABLE sys_role_dept (
  role_id bigint NOT NULL COMMENT '角色ID',
  dept_id bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (role_id, dept_id),
  KEY idx_role_dept_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据范围：data_scope=2 时生效';
