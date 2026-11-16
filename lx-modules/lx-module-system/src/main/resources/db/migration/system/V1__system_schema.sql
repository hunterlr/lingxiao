-- =====================================================================
-- 凌霄 · 系统模块表结构（v1.0）
-- =====================================================================
-- 约定：
--   1. 平台后台域，全部不带 tenant_id；
--   2. 主键 bigint，由应用侧雪花算法生成，不用 auto_increment；
--   3. 审计四字段 + del_flag + version；
--   4. 不使用外键，跨表一致性由应用层保证；
--   5. 时间统一 datetime(3)，避免秒级精度丢失。
-- =====================================================================

CREATE TABLE sys_dept (
  dept_id      bigint       NOT NULL,
  parent_id    bigint       NOT NULL DEFAULT 0,
  dept_name    varchar(64)  NOT NULL,
  order_num    int          NOT NULL DEFAULT 0,
  leader       varchar(64)  DEFAULT NULL,
  phone        varchar(32)  DEFAULT NULL,
  email        varchar(128) DEFAULT NULL,
  status       char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0' COMMENT '0存在 1删除',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (dept_id),
  KEY idx_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE sys_dept_closure (
  ancestor_id   bigint NOT NULL,
  descendant_id bigint NOT NULL,
  depth         int    NOT NULL DEFAULT 0,
  PRIMARY KEY (ancestor_id, descendant_id),
  KEY idx_dept_closure_desc (descendant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门闭包表：替代 find_in_set';

CREATE TABLE sys_post (
  post_id      bigint       NOT NULL,
  post_code    varchar(64)  NOT NULL,
  post_name    varchar(64)  NOT NULL,
  sort         int          NOT NULL DEFAULT 0,
  status       char(1)      NOT NULL DEFAULT '0',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (post_id),
  UNIQUE KEY uk_post_code (post_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位';

CREATE TABLE sys_user (
  user_id      bigint       NOT NULL,
  dept_id      bigint       DEFAULT NULL,
  username     varchar(64)  NOT NULL,
  nickname     varchar(64)  DEFAULT NULL,
  password     varchar(100) DEFAULT NULL COMMENT 'BCrypt 哈希',
  email        varchar(128) DEFAULT NULL,
  phone        varchar(32)  DEFAULT NULL,
  sex          char(1)      DEFAULT '2' COMMENT '0男 1女 2未知',
  avatar       varchar(255) DEFAULT NULL,
  status       char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  login_ip     varchar(64)  DEFAULT NULL,
  login_time   datetime(3)  DEFAULT NULL,
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户';

CREATE TABLE sys_user_post (
  user_id bigint NOT NULL,
  post_id bigint NOT NULL,
  PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位';

CREATE TABLE sys_role (
  role_id      bigint       NOT NULL,
  role_key     varchar(64)  NOT NULL,
  role_name    varchar(64)  NOT NULL,
  sort         int          NOT NULL DEFAULT 0,
  data_scope   char(1)      NOT NULL DEFAULT '1' COMMENT '1全部 2自定义 3本部门 4本部门及以下 5仅本人',
  status       char(1)      NOT NULL DEFAULT '0',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_user_role (
  user_id bigint NOT NULL,
  role_id bigint NOT NULL,
  PRIMARY KEY (user_id, role_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE sys_menu (
  menu_id      bigint       NOT NULL,
  menu_key     varchar(128) NOT NULL COMMENT '稳定键，菜单 SQL 幂等导入用',
  parent_id    bigint       NOT NULL DEFAULT 0,
  menu_name    varchar(64)  NOT NULL,
  menu_type    char(1)      NOT NULL COMMENT 'M目录 C菜单 F按钮',
  path         varchar(255) DEFAULT NULL,
  component    varchar(255) DEFAULT NULL,
  perms        varchar(128) DEFAULT NULL,
  icon         varchar(64)  DEFAULT NULL,
  order_num    int          NOT NULL DEFAULT 0,
  visible      char(1)      NOT NULL DEFAULT '0' COMMENT '0显示 1隐藏',
  status       char(1)      NOT NULL DEFAULT '0',
  module_code  varchar(32)  NOT NULL DEFAULT 'system',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (menu_id),
  UNIQUE KEY uk_menu_key (menu_key),
  KEY idx_menu_parent (parent_id),
  KEY idx_menu_module (module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限';

CREATE TABLE sys_role_menu (
  role_id bigint NOT NULL,
  menu_id bigint NOT NULL,
  PRIMARY KEY (role_id, menu_id),
  KEY idx_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单';

CREATE TABLE sys_dict_type (
  dict_id      bigint       NOT NULL,
  dict_name    varchar(100) NOT NULL,
  dict_type    varchar(100) NOT NULL,
  status       char(1)      NOT NULL DEFAULT '0',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (dict_id),
  UNIQUE KEY uk_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

CREATE TABLE sys_dict_data (
  dict_code    bigint       NOT NULL,
  dict_sort    int          NOT NULL DEFAULT 0,
  dict_label   varchar(100) NOT NULL,
  dict_value   varchar(100) NOT NULL,
  dict_type    varchar(100) NOT NULL,
  css_class    varchar(100) DEFAULT NULL,
  list_class   varchar(100) DEFAULT NULL,
  is_default   char(1)      NOT NULL DEFAULT 'N',
  status       char(1)      NOT NULL DEFAULT '0',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (dict_code),
  KEY idx_dict_data_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据';

CREATE TABLE sys_config (
  config_id    bigint       NOT NULL,
  config_name  varchar(100) NOT NULL,
  config_key   varchar(100) NOT NULL,
  config_value varchar(500) NOT NULL,
  config_type  char(1)      NOT NULL DEFAULT 'N' COMMENT 'Y内置 N普通',
  create_by    bigint       DEFAULT NULL,
  create_time  datetime(3)  NOT NULL,
  update_by    bigint       DEFAULT NULL,
  update_time  datetime(3)  DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  version      int          NOT NULL DEFAULT 0,
  remark       varchar(500) DEFAULT NULL,
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

CREATE TABLE sys_notice (
  notice_id      bigint       NOT NULL,
  notice_title   varchar(200) NOT NULL,
  notice_type    char(1)      NOT NULL DEFAULT '1' COMMENT '1通知 2公告',
  notice_content longtext,
  status         char(1)      NOT NULL DEFAULT '0',
  create_by      bigint       DEFAULT NULL,
  create_time    datetime(3)  NOT NULL,
  update_by      bigint       DEFAULT NULL,
  update_time    datetime(3)  DEFAULT NULL,
  del_flag       char(1)      NOT NULL DEFAULT '0',
  version        int          NOT NULL DEFAULT 0,
  remark         varchar(500) DEFAULT NULL,
  PRIMARY KEY (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

CREATE TABLE sys_login_log (
  info_id    bigint       NOT NULL,
  username   varchar(64)  DEFAULT NULL,
  ip         varchar(64)  DEFAULT NULL,
  browser    varchar(200) DEFAULT NULL,
  os         varchar(100) DEFAULT NULL,
  status     char(1)      NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  msg        varchar(500) DEFAULT NULL,
  login_time datetime(3)  NOT NULL,
  PRIMARY KEY (info_id),
  KEY idx_login_log_time (login_time),
  KEY idx_login_log_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE sys_oper_log (
  oper_id        bigint       NOT NULL,
  title          varchar(100) DEFAULT NULL,
  business_type  int          NOT NULL DEFAULT 0,
  method         varchar(255) DEFAULT NULL,
  request_method varchar(16)  DEFAULT NULL,
  oper_name      varchar(64)  DEFAULT NULL,
  dept_name      varchar(64)  DEFAULT NULL,
  oper_url       varchar(500) DEFAULT NULL,
  oper_ip        varchar(64)  DEFAULT NULL,
  oper_param     text,
  json_result    text,
  status         char(1)      NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  error_msg      text,
  oper_time      datetime(3)  NOT NULL,
  cost_time      bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (oper_id),
  KEY idx_oper_log_time (oper_time),
  KEY idx_oper_log_user (oper_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

CREATE TABLE sys_job (
  job_id          bigint       NOT NULL,
  job_name        varchar(64)  NOT NULL,
  job_group       varchar(64)  NOT NULL DEFAULT 'DEFAULT',
  invoke_target   varchar(500) NOT NULL,
  cron_expression varchar(255) NOT NULL,
  misfire_policy  char(1)      NOT NULL DEFAULT '3' COMMENT '1立即 2执行一次 3放弃',
  concurrent      char(1)      NOT NULL DEFAULT '1' COMMENT '0允许 1禁止',
  status          char(1)      NOT NULL DEFAULT '1' COMMENT '0正常 1暂停',
  create_by       bigint       DEFAULT NULL,
  create_time     datetime(3)  NOT NULL,
  update_by       bigint       DEFAULT NULL,
  update_time     datetime(3)  DEFAULT NULL,
  del_flag        char(1)      NOT NULL DEFAULT '0',
  version         int          NOT NULL DEFAULT 0,
  remark          varchar(500) DEFAULT NULL,
  PRIMARY KEY (job_id),
  KEY idx_job_group (job_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';

CREATE TABLE sys_job_log (
  job_log_id     bigint       NOT NULL,
  job_name       varchar(64)  DEFAULT NULL,
  job_group      varchar(64)  DEFAULT NULL,
  invoke_target  varchar(500) DEFAULT NULL,
  job_message    varchar(1000) DEFAULT NULL,
  status         char(1)      NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  exception_info text,
  create_time    datetime(3)  NOT NULL,
  PRIMARY KEY (job_log_id),
  KEY idx_job_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志';
