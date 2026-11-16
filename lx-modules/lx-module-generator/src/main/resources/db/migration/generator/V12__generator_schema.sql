-- =====================================================================
-- 凌霄 · 代码生成器（v1.0 第十二次迁移）
-- =====================================================================
-- 背景：
--   菜单 22「代码生成」从 v1.0 第一版起就是一个占位页；本轮补上：
--     1. 两张配置表：gen_table / gen_table_column；
--     2. 导入、配置、预览、下载、删除五个按钮权限（2201~2205）；
--     3. 生成物默认作者参数 sys.gen.author；
--     4. 超级管理员授权。
--
-- 设计说明：
--   - 生成器配置表是“工具表”，不是业务表，不参与逻辑删除：
--     删除一条生成配置就物理删除，避免 del_flag 把唯一索引 table_name 顶住；
--   - 不建外键：删配置时由 Service 先删子表再删主表，
--     和 sys_user_role 等关联表的处理方式保持一致；
--   - 按钮权限 ID 沿用“菜单号 * 100 + 序号”：2201~2205；
--   - 生成器只做预览与 ZIP 下载，不向工程目录写文件，
--     因此不需要额外的工作目录白名单表。
--
-- 注意：
--   Flyway 已执行版本不允许改内容；本文件只做新增。
-- =====================================================================

-- 1) 生成配置：表级 -----------------------------------------------------
CREATE TABLE gen_table (
  table_id        bigint       NOT NULL COMMENT '生成配置 ID',
  table_name      varchar(200) NOT NULL COMMENT '数据库表名',
  table_comment   varchar(500) DEFAULT NULL COMMENT '表注释（生成物里的功能说明）',
  class_name      varchar(100) NOT NULL COMMENT '实体类名，PascalCase',
  module_name     varchar(64)  NOT NULL COMMENT '模块名，决定包名与接口前缀',
  business_name   varchar(100) NOT NULL COMMENT '业务名，camelCase，用于变量 / 权限码',
  function_name   varchar(100) NOT NULL COMMENT '功能名，用于菜单标题与注释',
  function_author varchar(64)  DEFAULT NULL COMMENT '生成物作者',
  gen_type        char(1)      NOT NULL DEFAULT '0' COMMENT '生成类型：0 单表（v1.0 仅支持）',
  gen_path        varchar(255) NOT NULL DEFAULT '/' COMMENT '前端路由路径',
  template_group  varchar(64)  NOT NULL DEFAULT 'default' COMMENT '模板组（v1.0 仅 default）',
  status          char(1)      NOT NULL DEFAULT '0' COMMENT '状态：0 已配置 / 1 待配置（保留）',
  create_by       bigint       DEFAULT NULL COMMENT '创建人',
  create_time     datetime(3)  NOT NULL COMMENT '创建时间',
  update_by       bigint       DEFAULT NULL COMMENT '最后修改人',
  update_time     datetime(3)  DEFAULT NULL COMMENT '最后修改时间',
  PRIMARY KEY (table_id),
  UNIQUE KEY uk_gen_table_name (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成-表配置';

-- 2) 生成配置：字段级 ---------------------------------------------------
CREATE TABLE gen_table_column (
  column_id      bigint       NOT NULL COMMENT '字段配置 ID',
  table_id       bigint       NOT NULL COMMENT '所属表配置 ID',
  column_name    varchar(200) NOT NULL COMMENT '数据库列名',
  column_comment varchar(500) DEFAULT NULL COMMENT '列注释',
  column_type    varchar(100) NOT NULL COMMENT '数据库类型（JDBC TYPE_NAME）',
  java_type      varchar(64)  NOT NULL COMMENT 'Java 类型',
  java_field     varchar(100) NOT NULL COMMENT 'Java 字段名，camelCase',
  is_pk          char(1)      NOT NULL DEFAULT '0' COMMENT '是否主键：0 否 / 1 是',
  is_increment   char(1)      NOT NULL DEFAULT '0' COMMENT '是否自增：0 否 / 1 是',
  is_required    char(1)      NOT NULL DEFAULT '0' COMMENT '是否必填：0 否 / 1 是',
  is_insert      char(1)      NOT NULL DEFAULT '1' COMMENT '是否出现在新增表单：0 否 / 1 是',
  is_edit        char(1)      NOT NULL DEFAULT '1' COMMENT '是否出现在编辑表单：0 否 / 1 是',
  is_list        char(1)      NOT NULL DEFAULT '1' COMMENT '是否出现在列表：0 否 / 1 是',
  is_query       char(1)      NOT NULL DEFAULT '0' COMMENT '是否作为查询条件：0 否 / 1 是',
  query_type     varchar(16)  NOT NULL DEFAULT 'EQ' COMMENT '查询方式：EQ / LIKE / GT / GE / LT / LE',
  html_type      varchar(32)  NOT NULL DEFAULT 'input' COMMENT '表单控件：input/textarea/select/radio/checkbox/datetime/upload/editor',
  dict_type      varchar(100) DEFAULT NULL COMMENT '字典类型；非空时前端用 DictSelect / DictTag',
  sort           int          NOT NULL DEFAULT '0' COMMENT '排序号，越小越靠前',
  PRIMARY KEY (column_id),
  KEY idx_gen_column_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成-字段配置';

-- 3) 生成物作者参数 -----------------------------------------------------
-- 每个内置参数都必须有人读：CodeGenService 在未显式指定作者时读取该键。
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, control_type, config_options, create_time, del_flag, version, remark)
VALUES
(6, '代码生成作者', 'sys.gen.author', 'lingxiao', 'Y', 'text', NULL, NOW(3), '0', 0,
 '代码生成物头部注释里的作者；导入表时写入 gen_table.function_author');

-- 4) 代码生成按钮权限 ---------------------------------------------------
-- 菜单 22 已有 list 权限 platform:tool:gen:list，这里只补写操作 / 预览 / 下载。
-- 预览和下载都会把整张表的字段与生成代码暴露出来，必须单独授权。
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(2201, 'tool:gen:import',  22, '导入表',   'F', NULL, NULL, 'platform:tool:gen:import',   NULL, 1, '0', '0', 'system', NOW(3), '0', 0),
(2202, 'tool:gen:edit',    22, '配置表',   'F', NULL, NULL, 'platform:tool:gen:edit',     NULL, 2, '0', '0', 'system', NOW(3), '0', 0),
(2203, 'tool:gen:remove',  22, '删除配置', 'F', NULL, NULL, 'platform:tool:gen:remove',   NULL, 3, '0', '0', 'system', NOW(3), '0', 0),
(2204, 'tool:gen:preview', 22, '预览代码', 'F', NULL, NULL, 'platform:tool:gen:preview',  NULL, 4, '0', '0', 'system', NOW(3), '0', 0),
(2205, 'tool:gen:download',22, '下载代码', 'F', NULL, NULL, 'platform:tool:gen:download', NULL, 5, '0', '0', 'system', NOW(3), '0', 0);

-- 5) 超级管理员授权 -----------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.perms IN (
    'platform:tool:gen:import',
    'platform:tool:gen:edit',
    'platform:tool:gen:remove',
    'platform:tool:gen:preview',
    'platform:tool:gen:download'
)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id
);
