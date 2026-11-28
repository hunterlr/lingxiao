-- =====================================================================
-- 迁移 — 工作流业务表（V46）
-- =====================================================================
-- 范围
--   1. wf_category     流程分类（后台列表分组）
--   2. wf_process_def  流程定义缓存（部署信息 + 启停状态）
--
-- 约定
--   - 引擎表 ACT_* 由 Flowable 自建（flowable.database-schema-update=true），
--     本迁移只管 wf_* 业务表，两边互不扫描。
--   - 一个流程 key 可能有多版本：wf_process_def 按 (process_key, version) 唯一，
--     同 key 最新版本由查询取 version 最大者，不额外加 is_latest 列。
--   - 审批意见不落本模块表：直接用 Flowable 的 ACT_HI_COMMENT。
--   - 第一刀不建 wf_business / wf_form* / wf_cc / wf_delegate，等业务接入再加。
--   - status：0 启用 / 1 停用；停用后不可新发起，已运行实例不受影响。
--   - 幂等插入用 INSERT ... SELECT ... WHERE NOT EXISTS（多库兼容约定，不用 INSERT IGNORE）。
-- =====================================================================

CREATE TABLE wf_category (
  category_id   bigint        NOT NULL,
  category_key  varchar(64)   NOT NULL,
  name          varchar(64)   NOT NULL,
  sort          int           NOT NULL DEFAULT 0,
  status        char(1)       NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  remark        varchar(255)  DEFAULT NULL,
  create_by     bigint        DEFAULT NULL,
  create_time   datetime(3)   NOT NULL,
  update_by     bigint        DEFAULT NULL,
  update_time   datetime(3)   DEFAULT NULL,
  del_flag      char(1)       NOT NULL DEFAULT '0',
  version       int           NOT NULL DEFAULT 0,
  PRIMARY KEY (category_id),
  UNIQUE KEY uk_wf_category_key (category_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流流程分类';

CREATE TABLE wf_process_def (
  def_id         bigint        NOT NULL,
  process_key    varchar(128)  NOT NULL,
  name           varchar(128)  NOT NULL,
  category_key   varchar(64)   DEFAULT NULL,
  version        int           NOT NULL DEFAULT 1,
  deployment_id  varchar(64)   NOT NULL,
  definition_id  varchar(64)   NOT NULL,
  resource_name  varchar(255)  DEFAULT NULL,
  status         char(1)       NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  remark         varchar(255)  DEFAULT NULL,
  create_by      bigint        DEFAULT NULL,
  create_time    datetime(3)   NOT NULL,
  update_by      bigint        DEFAULT NULL,
  update_time    datetime(3)   DEFAULT NULL,
  del_flag       char(1)       NOT NULL DEFAULT '0',
  version_num    int           NOT NULL DEFAULT 0,
  PRIMARY KEY (def_id),
  UNIQUE KEY uk_wf_def_key_version (process_key, version),
  KEY idx_wf_def_key (process_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流流程定义缓存';

INSERT INTO wf_category
  (category_id, category_key, name, sort, status, remark, create_time, del_flag, version)
SELECT 6100, 'common', '通用', 1, '0', '内置示例与通用审批', NOW(3), '0', 0
WHERE NOT EXISTS (SELECT 1 FROM wf_category WHERE category_id = 6100);