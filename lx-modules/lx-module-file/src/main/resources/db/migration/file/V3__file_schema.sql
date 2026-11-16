-- =====================================================================
-- 凌霄 · 文件模块表结构（v1.0）
-- =====================================================================
-- 平台后台域，不带 tenant_id。
-- 三个名字分离：
--   original_name 上传原始名，不可改
--   display_name  展示名，可改
--   file_name     存储名 UUID，不可改
-- =====================================================================

CREATE TABLE sys_file (
  file_id       bigint        NOT NULL,
  group_id      bigint        DEFAULT NULL,
  storage       varchar(32)   NOT NULL DEFAULT 'local' COMMENT 'local/minio/oss/cos/s3',
  bucket        varchar(128)  DEFAULT NULL,
  path          varchar(500)  NOT NULL COMMENT '相对存储路径',
  url           varchar(1000) NOT NULL COMMENT '访问地址',
  original_name varchar(255)  NOT NULL COMMENT '上传原始名，不可改',
  display_name  varchar(255)  DEFAULT NULL COMMENT '当前展示名，可改；空则回退 original_name',
  file_name     varchar(255)  NOT NULL COMMENT '存储名 UUID，不可改',
  ext           varchar(32)   DEFAULT NULL,
  mime          varchar(128)  DEFAULT NULL,
  size          bigint        NOT NULL DEFAULT 0,
  hash          char(64)      DEFAULT NULL COMMENT 'SHA-256',
  width         int           DEFAULT NULL,
  height        int           DEFAULT NULL,
  duration      int           DEFAULT NULL COMMENT '视频秒数',
  thumb_path    varchar(500)  DEFAULT NULL COMMENT '缩略图路径',
  biz_type      varchar(64)   DEFAULT NULL COMMENT '业务场景',
  remark        varchar(500)  DEFAULT NULL COMMENT '人可读描述',
  ref_count     int           NOT NULL DEFAULT 0,
  derived_from  bigint        DEFAULT NULL COMMENT '衍生来源 file_id',
  status        char(1)       NOT NULL DEFAULT '0',
  uploader_id   bigint        DEFAULT NULL,
  create_by     bigint        DEFAULT NULL,
  create_time   datetime(3)   NOT NULL,
  update_by     bigint        DEFAULT NULL,
  update_time   datetime(3)   DEFAULT NULL,
  del_flag      char(1)       NOT NULL DEFAULT '0',
  version       int           NOT NULL DEFAULT 0,
  PRIMARY KEY (file_id),
  KEY idx_file_hash (hash),
  KEY idx_file_biz (biz_type),
  KEY idx_file_uploader (uploader_id),
  KEY idx_file_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件主表';

CREATE TABLE sys_file_name_log (
  id            bigint       NOT NULL,
  file_id       bigint       NOT NULL,
  old_name      varchar(255) NOT NULL,
  new_name      varchar(255) NOT NULL,
  operator_id   bigint       DEFAULT NULL,
  operator_name varchar(64)  DEFAULT NULL,
  create_time   datetime(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_file_name_log (file_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件改名历史';
