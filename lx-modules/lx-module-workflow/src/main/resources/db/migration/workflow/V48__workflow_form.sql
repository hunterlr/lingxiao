-- =====================================================================
-- 迁移 — 工作流动态表单（V48）
-- =====================================================================
-- 范围
--   1. wf_form            表单定义（工作副本，编辑不影响线上）
--   2. wf_form_version    已发布快照（只增不改，历史实例按旧版本渲染）
--   3. wf_instance_form   实例表单快照（发起时定格，表单改版不影响）
--   4. wf_process_def.form_key  流程定义绑定的表单
--   5. 表单管理菜单与权限（6050 段）
--
-- 约定
--   - wf_form.schema_json 是“正在编辑的版本”；发起流程读的是
--     wf_form_version 里 version = wf_form.version 的快照，所以改了没发布不会影响新单。
--   - 字段不拆表：schema_json 是唯一真源，后台不做按字段的 SQL 查询。
--   - 字段 key 直接作为流程变量名（条件分支、会签人都靠它对上）。
--   - 幂等插入用 INSERT ... SELECT ... WHERE NOT EXISTS。
-- =====================================================================

CREATE TABLE wf_form (
  form_id      bigint        NOT NULL,
  form_key     varchar(64)   NOT NULL,
  name         varchar(64)   NOT NULL,
  schema_json  longtext      NOT NULL COMMENT '正在编辑的 schema（草稿）',
  version      int           NOT NULL DEFAULT 0 COMMENT '已发布版本，0 = 从未发布',
  remark       varchar(255)  DEFAULT NULL,
  create_by    bigint        DEFAULT NULL,
  create_time  datetime(3)   NOT NULL,
  update_by    bigint        DEFAULT NULL,
  update_time  datetime(3)   DEFAULT NULL,
  del_flag     char(1)       NOT NULL DEFAULT '0',
  version_num  int           NOT NULL DEFAULT 0,
  PRIMARY KEY (form_id),
  UNIQUE KEY uk_wf_form_key (form_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表单定义';

CREATE TABLE wf_form_version (
  version_id   bigint        NOT NULL,
  form_id      bigint        NOT NULL,
  version      int           NOT NULL,
  schema_json  longtext      NOT NULL,
  create_by    bigint        DEFAULT NULL,
  create_time  datetime(3)   NOT NULL,
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_wf_form_version (form_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表单发布快照';

CREATE TABLE wf_instance_form (
  id                  bigint       NOT NULL,
  process_instance_id varchar(64)  NOT NULL,
  form_key            varchar(64)  NOT NULL,
  form_version        int          NOT NULL,
  data_json           longtext     NOT NULL COMMENT '发起时填写的原始值',
  create_time         datetime(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wf_instance_form (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流实例表单快照';

ALTER TABLE wf_process_def
  ADD COLUMN form_key varchar(64) DEFAULT NULL COMMENT '绑定的表单 key，空 = 无表单' AFTER category_key;

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6050, 'workflow:form', 6000, '表单管理', 'C', '/workflow/form', 'workflow/form/index', 'platform:workflow:form:list', 'form', 5, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6050);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6051, 'workflow:form:add', 6050, '新增表单', 'F', NULL, NULL, 'platform:workflow:form:add', NULL, 1, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6051);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6052, 'workflow:form:edit', 6050, '编辑表单', 'F', NULL, NULL, 'platform:workflow:form:edit', NULL, 2, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6052);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6053, 'workflow:form:publish', 6050, '发布表单', 'F', NULL, NULL, 'platform:workflow:form:publish', NULL, 3, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6053);

INSERT INTO sys_menu
  (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
SELECT 6054, 'workflow:form:remove', 6050, '删除表单', 'F', NULL, NULL, 'platform:workflow:form:remove', NULL, 4, '0', '0', 'workflow', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 6054);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.module_code = 'workflow'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);