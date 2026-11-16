-- =====================================================================
-- 凌霄 · v1.0.0 数据库快照（结构 + 初始数据）
-- =====================================================================
-- 用途：clone 仓库后，不想等 Flyway 逐条迁移、或想直接拿到一套可用的
--       后台数据时，导入本文件即可。
--
-- 内容：
--   23 张表（22 张业务表 + flyway_schema_history）
--   Flyway V1~V14 全部迁移记录（导入后启动不会再建表）
--   admin 账号、64 条菜单、角色 / 字典 / 参数 / 定时任务等初始数据
--
-- 已清理（相对于开发机的实时库）：
--   登录日志 / 操作日志 / 任务日志 / 锁记录 / 文件记录 / 代码生成的临时表 / 测试任务 / 测试公告
--
-- 导入（先建库建号，见同目录 init-db.sql）：
--   mysql -uroot -p < deploy/sql/lingxiao-1.0.0.sql
--   或 mysql -h127.0.0.1 -P3306 -ulingxiao -p lingxiao < deploy/sql/lingxiao-1.0.0.sql
--
-- 注意：
--   1. 文件里是 DROP TABLE IF EXISTS + CREATE TABLE，重复导入会把库重置成快照状态；
--   2. 内含 flyway_schema_history，所以不要修改已执行过的 V*.sql，
--      否则启动时 Flyway 会报 checksum mismatch；
--   3. 默认账号 admin / admin123，导入后请立刻改密码；
--   4. 上传文件不在本文件里（data/upload 不入库），文件中心会是空列表。
--
-- 重新生成（结构或初始数据变化后）：
--   mysqldump --host=127.0.0.1 --port=3307 --user=lingxiao --password=*** \
--     --default-character-set=utf8mb4 --single-transaction --quick \
--     --triggers --no-create-db --databases lingxiao \
--     --result-file=deploy/sql/lingxiao-1.0.0.sql
-- =====================================================================

-- MySQL dump 10.13  Distrib 5.7.26, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: lingxiao
-- ------------------------------------------------------
-- Server version	5.7.26

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `lingxiao_snapshot`
--

USE `lingxiao`;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','system schema','SQL','V1__system_schema.sql',-1420895125,'lingxiao','2026-11-15 05:15:08',212,1),(2,'2','system seed','SQL','V2__system_seed.sql',-1808537696,'lingxiao','2026-11-15 05:15:08',37,1),(3,'3','file schema','SQL','V3__file_schema.sql',-1485486679,'lingxiao','2026-11-15 05:15:08',54,1),(4,'4','system button perms','SQL','V4__system_button_perms.sql',-460862906,'lingxiao','2026-11-15 08:11:17',17,1),(5,'5','system editor setting','SQL','V5__system_editor_setting.sql',112983719,'lingxiao','2026-11-15 08:49:14',9,1),(6,'6','file button perms','SQL','V6__file_button_perms.sql',-797253466,'lingxiao','2026-11-15 09:15:40',11,1),(7,'7','remove duplicate file menus','SQL','V7__remove_duplicate_file_menus.sql',1044910462,'lingxiao','2026-11-15 09:32:19',10,1),(8,'8','monitor log perms','SQL','V8__monitor_log_perms.sql',454752005,'lingxiao','2026-11-15 11:08:14',9,1),(9,'9','system config control','SQL','V9__system_config_control.sql',52135458,'lingxiao','2026-11-15 11:40:47',85,1),(10,'10','monitor online kickout','SQL','V10__monitor_online_kickout.sql',-1364584559,'lingxiao','2026-11-15 11:59:47',7,1),(11,'11','job schema','SQL','V11__job_schema.sql',1531858291,'lingxiao','2026-11-15 12:20:12',156,1),(12,'12','generator schema','SQL','V12__generator_schema.sql',-1115767411,'lingxiao','2026-11-15 13:07:08',44,1),(13,'13','file image variants','SQL','V13__file_image_variants.sql',-205937422,'lingxiao','2026-11-15 13:31:17',83,1),(14,'14','login security','SQL','V14__login_security.sql',-765509084,'lingxiao','2026-11-15 13:47:11',84,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gen_table` (
  `table_id` bigint(20) NOT NULL COMMENT '生成配置 ID',
  `table_name` varchar(200) NOT NULL COMMENT '数据库表名',
  `table_comment` varchar(500) DEFAULT NULL COMMENT '表注释（生成物里的功能说明）',
  `class_name` varchar(100) NOT NULL COMMENT '实体类名，PascalCase',
  `module_name` varchar(64) NOT NULL COMMENT '模块名，决定包名与接口前缀',
  `business_name` varchar(100) NOT NULL COMMENT '业务名，camelCase，用于变量 / 权限码',
  `function_name` varchar(100) NOT NULL COMMENT '功能名，用于菜单标题与注释',
  `function_author` varchar(64) DEFAULT NULL COMMENT '生成物作者',
  `gen_type` char(1) NOT NULL DEFAULT '0' COMMENT '生成类型：0 单表（v1.0 仅支持）',
  `gen_path` varchar(255) NOT NULL DEFAULT '/' COMMENT '前端路由路径',
  `template_group` varchar(64) NOT NULL DEFAULT 'default' COMMENT '模板组（v1.0 仅 default）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态：0 已配置 / 1 待配置（保留）',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(3) NOT NULL COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '最后修改人',
  `update_time` datetime(3) DEFAULT NULL COMMENT '最后修改时间',
  PRIMARY KEY (`table_id`),
  UNIQUE KEY `uk_gen_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成-表配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint(20) NOT NULL COMMENT '字段配置 ID',
  `table_id` bigint(20) NOT NULL COMMENT '所属表配置 ID',
  `column_name` varchar(200) NOT NULL COMMENT '数据库列名',
  `column_comment` varchar(500) DEFAULT NULL COMMENT '列注释',
  `column_type` varchar(100) NOT NULL COMMENT '数据库类型（JDBC TYPE_NAME）',
  `java_type` varchar(64) NOT NULL COMMENT 'Java 类型',
  `java_field` varchar(100) NOT NULL COMMENT 'Java 字段名，camelCase',
  `is_pk` char(1) NOT NULL DEFAULT '0' COMMENT '是否主键：0 否 / 1 是',
  `is_increment` char(1) NOT NULL DEFAULT '0' COMMENT '是否自增：0 否 / 1 是',
  `is_required` char(1) NOT NULL DEFAULT '0' COMMENT '是否必填：0 否 / 1 是',
  `is_insert` char(1) NOT NULL DEFAULT '1' COMMENT '是否出现在新增表单：0 否 / 1 是',
  `is_edit` char(1) NOT NULL DEFAULT '1' COMMENT '是否出现在编辑表单：0 否 / 1 是',
  `is_list` char(1) NOT NULL DEFAULT '1' COMMENT '是否出现在列表：0 否 / 1 是',
  `is_query` char(1) NOT NULL DEFAULT '0' COMMENT '是否作为查询条件：0 否 / 1 是',
  `query_type` varchar(16) NOT NULL DEFAULT 'EQ' COMMENT '查询方式：EQ / LIKE / GT / GE / LT / LE',
  `html_type` varchar(32) NOT NULL DEFAULT 'input' COMMENT '表单控件：input/textarea/select/radio/checkbox/datetime/upload/editor',
  `dict_type` varchar(100) DEFAULT NULL COMMENT '字典类型；非空时前端用 DictSelect / DictTag',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序号，越小越靠前',
  PRIMARY KEY (`column_id`),
  KEY `idx_gen_column_table` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成-字段配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_config` (
  `config_id` bigint(20) NOT NULL,
  `config_name` varchar(100) NOT NULL,
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(500) NOT NULL,
  `control_type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '控件类型 text/number/switch/select',
  `config_options` varchar(1000) DEFAULT NULL COMMENT '控件选项：select=值:显示名,number=最小值:最大值,switch=开:关',
  `config_type` char(1) NOT NULL DEFAULT 'N' COMMENT 'Y内置 N普通',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'登录验证码开关','sys.account.captchaEnabled','true','switch','true:false','Y',NULL,'2026-11-15 13:15:08.658',1,'2026-11-15 17:45:33.296','0',19,'开关参数：true 开启验证码，false 关闭验证码'),(2,'日志保留天数','sys.log.keepDays','180','number','1:3650','Y',NULL,'2026-11-15 13:15:08.658',NULL,NULL,'0',0,'超过天数的操作日志 / 登录日志由定时任务清理（1~3650 天）'),(3,'单文件上传上限','sys.file.uploadMaxMb','10','number','1:20','Y',NULL,'2026-11-15 13:15:08.658',1,'2026-11-15 19:42:11.804','0',8,'单位 MB；受容器 multipart 上限 20MB 约束，库里只能调小'),(4,'富文本编辑器','sys.editor.type','wangeditor-next','select','wangeditor-next:wangEditor Next,ueditor-plus:UEditor Plus','Y',NULL,'2026-11-15 16:49:14.589',1,'2026-11-15 16:49:57.697','0',6,'平台默认富文本编辑器；用户可在编辑器内临时切换'),(5,'任务日志保留天数','sys.job.logKeepDays','30','number','1:3650','Y',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,'定时任务日志超过该天数由 jobLogClean 执行器清理（1~3650 天）'),(6,'代码生成作者','sys.gen.author','lingxiao','text',NULL,'Y',NULL,'2026-11-15 21:07:08.810',NULL,NULL,'0',0,'代码生成物头部注释里的作者；导入表时写入 gen_table.function_author'),(7,'????????','sys.account.maxRetryCount','5','number','3:10','Y',NULL,'2026-11-15 21:47:11.828',1,'2026-11-15 21:47:39.050','0',9,'????????????????(3~10 ?)'),(8,'账号锁定时长','sys.account.lockMinutes','10','number','1:1440','Y',NULL,'2026-11-15 21:47:11.828',NULL,NULL,'0',0,'账号锁定分钟数，到期自动解锁（1~1440 分钟）'),(9,'密码复杂度策略','sys.account.passwordPolicy','basic','select','off:不校验,basic:至少 8 位且含字母和数字,strong:至少 10 位且含大写/小写/数字/符号','Y',NULL,'2026-11-15 21:47:11.828',1,'2026-11-15 21:51:00.939','0',8,'新增用户、管理员重置密码、个人中心改密时生效');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) NOT NULL DEFAULT '0',
  `dept_name` varchar(64) NOT NULL,
  `order_num` int(11) NOT NULL DEFAULT '0',
  `leader` varchar(64) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '0存在 1删除',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`dept_id`),
  KEY `idx_dept_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'凌霄科技',1,'管理员','13800000000','admin@lingxiao.local','0',NULL,'2026-11-15 13:15:08.630',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept_closure`
--

DROP TABLE IF EXISTS `sys_dept_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dept_closure` (
  `ancestor_id` bigint(20) NOT NULL,
  `descendant_id` bigint(20) NOT NULL,
  `depth` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ancestor_id`,`descendant_id`),
  KEY `idx_dept_closure_desc` (`descendant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门闭包表：替代 find_in_set';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept_closure`
--

LOCK TABLES `sys_dept_closure` WRITE;
/*!40000 ALTER TABLE `sys_dept_closure` DISABLE KEYS */;
INSERT INTO `sys_dept_closure` VALUES (1,1,0);
/*!40000 ALTER TABLE `sys_dept_closure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint(20) NOT NULL,
  `dict_sort` int(11) NOT NULL DEFAULT '0',
  `dict_label` varchar(100) NOT NULL,
  `dict_value` varchar(100) NOT NULL,
  `dict_type` varchar(100) NOT NULL,
  `css_class` varchar(100) DEFAULT NULL,
  `list_class` varchar(100) DEFAULT NULL,
  `is_default` char(1) NOT NULL DEFAULT 'N',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`dict_code`),
  KEY `idx_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'正常','0','sys_common_status',NULL,'primary','Y','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(2,2,'停用','1','sys_common_status',NULL,'danger','N','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(3,1,'男','0','sys_user_sex',NULL,NULL,'Y','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(4,2,'女','1','sys_user_sex',NULL,NULL,'N','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(5,3,'未知','2','sys_user_sex',NULL,'info','N','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(6,1,'是','Y','sys_yes_no',NULL,'primary','Y','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL),(7,2,'否','N','sys_yes_no',NULL,'danger','N','0',NULL,'2026-11-15 13:15:08.651',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint(20) NOT NULL,
  `dict_name` varchar(100) NOT NULL,
  `dict_type` varchar(100) NOT NULL,
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'系统开关','sys_common_status','0',NULL,'2026-11-15 13:15:08.649',NULL,NULL,'0',0,'0正常 1停用'),(2,'用户性别','sys_user_sex','0',NULL,'2026-11-15 13:15:08.649',NULL,NULL,'0',0,'0男 1女 2未知'),(3,'是否','sys_yes_no','0',NULL,'2026-11-15 13:15:08.649',NULL,NULL,'0',0,'Y是 N否');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_file`
--

DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_file` (
  `file_id` bigint(20) NOT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  `storage` varchar(32) NOT NULL DEFAULT 'local' COMMENT 'local/minio/oss/cos/s3',
  `bucket` varchar(128) DEFAULT NULL,
  `path` varchar(500) NOT NULL COMMENT '相对存储路径',
  `url` varchar(1000) NOT NULL COMMENT '访问地址',
  `original_name` varchar(255) NOT NULL COMMENT '上传原始名，不可改',
  `display_name` varchar(255) DEFAULT NULL COMMENT '当前展示名，可改；空则回退 original_name',
  `file_name` varchar(255) NOT NULL COMMENT '存储名 UUID，不可改',
  `ext` varchar(32) DEFAULT NULL,
  `mime` varchar(128) DEFAULT NULL,
  `size` bigint(20) NOT NULL DEFAULT '0',
  `hash` char(64) DEFAULT NULL COMMENT 'SHA-256',
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `duration` int(11) DEFAULT NULL COMMENT '视频秒数',
  `thumb_path` varchar(500) DEFAULT NULL COMMENT '缩略图路径',
  `medium_path` varchar(500) DEFAULT NULL COMMENT '中图相对路径，最长边约 800px',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务场景',
  `remark` varchar(500) DEFAULT NULL COMMENT '人可读描述',
  `ref_count` int(11) NOT NULL DEFAULT '0',
  `derived_from` bigint(20) DEFAULT NULL COMMENT '衍生来源 file_id',
  `status` char(1) NOT NULL DEFAULT '0',
  `uploader_id` bigint(20) DEFAULT NULL,
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`file_id`),
  KEY `idx_file_hash` (`hash`),
  KEY `idx_file_biz` (`biz_type`),
  KEY `idx_file_uploader` (`uploader_id`),
  KEY `idx_file_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_file`
--

LOCK TABLES `sys_file` WRITE;
/*!40000 ALTER TABLE `sys_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_file_name_log`
--

DROP TABLE IF EXISTS `sys_file_name_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_file_name_log` (
  `id` bigint(20) NOT NULL,
  `file_id` bigint(20) NOT NULL,
  `old_name` varchar(255) NOT NULL,
  `new_name` varchar(255) NOT NULL,
  `operator_id` bigint(20) DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_file_name_log` (`file_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件改名历史';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_file_name_log`
--

LOCK TABLES `sys_file_name_log` WRITE;
/*!40000 ALTER TABLE `sys_file_name_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file_name_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_job` (
  `job_id` bigint(20) NOT NULL,
  `job_name` varchar(64) NOT NULL,
  `job_group` varchar(64) NOT NULL DEFAULT 'DEFAULT',
  `invoke_target` varchar(500) NOT NULL,
  `cron_expression` varchar(255) NOT NULL,
  `misfire_policy` char(1) NOT NULL DEFAULT '3' COMMENT '1立即 2执行一次 3放弃',
  `concurrent` char(1) NOT NULL DEFAULT '1' COMMENT '0允许 1禁止',
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '0正常 1暂停',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`job_id`),
  KEY `idx_job_group` (`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'清理过期日志','SYSTEM','systemLogClean','0 0 2 * * ?','3','1','1',NULL,'2026-11-15 13:15:08.664',NULL,'2026-11-15 20:33:23.517','0',2,'每天 02:00 清理超过 sys.log.keepDays 天的登录日志 / 操作日志；默认暂停');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_lock`
--

DROP TABLE IF EXISTS `sys_job_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_job_lock` (
  `job_id` bigint(20) NOT NULL,
  `lock_owner` varchar(128) DEFAULT NULL COMMENT '持有者：hostname + UUID，便于排查',
  `lock_time` datetime(3) DEFAULT NULL COMMENT '加锁时间；超过超时时间视为失效',
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务集群锁';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_lock`
--

LOCK TABLES `sys_job_lock` WRITE;
/*!40000 ALTER TABLE `sys_job_lock` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_lock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint(20) NOT NULL,
  `job_name` varchar(64) DEFAULT NULL,
  `job_group` varchar(64) DEFAULT NULL,
  `invoke_target` varchar(500) DEFAULT NULL,
  `job_message` varchar(1000) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  `exception_info` text,
  `create_time` datetime(3) NOT NULL,
  `job_id` bigint(20) DEFAULT NULL COMMENT '所属任务 ID',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'CRON' COMMENT '触发方式 CRON 调度 / MANUAL 手动',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '耗时毫秒',
  PRIMARY KEY (`job_log_id`),
  KEY `idx_job_log_time` (`create_time`),
  KEY `idx_job_log_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_login_log` (
  `info_id` bigint(20) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `browser` varchar(200) DEFAULT NULL,
  `os` varchar(100) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  `msg` varchar(500) DEFAULT NULL,
  `login_time` datetime(3) NOT NULL,
  PRIMARY KEY (`info_id`),
  KEY `idx_login_log_time` (`login_time`),
  KEY `idx_login_log_user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint(20) NOT NULL,
  `menu_key` varchar(128) NOT NULL COMMENT '稳定键，菜单 SQL 幂等导入用',
  `parent_id` bigint(20) NOT NULL DEFAULT '0',
  `menu_name` varchar(64) NOT NULL,
  `menu_type` char(1) NOT NULL COMMENT 'M目录 C菜单 F按钮',
  `path` varchar(255) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `perms` varchar(128) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `order_num` int(11) NOT NULL DEFAULT '0',
  `visible` char(1) NOT NULL DEFAULT '0' COMMENT '0显示 1隐藏',
  `status` char(1) NOT NULL DEFAULT '0',
  `module_code` varchar(32) NOT NULL DEFAULT 'system',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`menu_id`),
  UNIQUE KEY `uk_menu_key` (`menu_key`),
  KEY `idx_menu_parent` (`parent_id`),
  KEY `idx_menu_module` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'system',0,'系统管理','M','/system',NULL,NULL,'setting',1,'0','0','system',NULL,'2026-11-15 13:15:08.643',NULL,NULL,'0',0,NULL),(2,'monitor',0,'系统监控','M','/monitor',NULL,NULL,'monitor',2,'0','0','system',NULL,'2026-11-15 13:15:08.643',NULL,NULL,'0',0,NULL),(3,'tool',0,'系统工具','M','/tool',NULL,NULL,'tool',3,'0','0','system',NULL,'2026-11-15 13:15:08.643',NULL,NULL,'0',0,NULL),(4,'profile',0,'个人中心','C','/profile','profile/index','platform:profile:view','user',4,'0','0','system',NULL,'2026-11-15 13:15:08.643',NULL,NULL,'0',0,NULL),(5,'system:user',1,'用户管理','C','/system/user','system/user/index','platform:system:user:list','user',1,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(6,'system:role',1,'角色管理','C','/system/role','system/role/index','platform:system:role:list','peoples',2,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(7,'system:menu',1,'菜单管理','C','/system/menu','system/menu/index','platform:system:menu:list','tree-table',3,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(8,'system:dept',1,'部门管理','C','/system/dept','system/dept/index','platform:system:dept:list','tree',4,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(9,'system:post',1,'岗位管理','C','/system/post','system/post/index','platform:system:post:list','post',5,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(10,'system:dict',1,'字典管理','C','/system/dict','system/dict/index','platform:system:dict:list','dict',6,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(11,'system:config',1,'参数设置','C','/system/config','system/config/index','platform:system:config:list','edit',7,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(12,'system:notice',1,'公告管理','C','/system/notice','system/notice/index','platform:system:notice:list','message',8,'0','0','system',NULL,'2026-11-15 13:15:08.645',NULL,NULL,'0',0,NULL),(13,'monitor:online',2,'在线用户','C','/monitor/online','monitor/online/index','platform:monitor:online:list','online',1,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(14,'monitor:login-log',2,'登录日志','C','/monitor/login-log','monitor/loginlog/index','platform:monitor:loginlog:list','logininfor',2,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(15,'monitor:oper-log',2,'操作日志','C','/monitor/oper-log','monitor/operlog/index','platform:monitor:operlog:list','form',3,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(16,'monitor:server',2,'服务监控','C','/monitor/server','monitor/server/index','platform:monitor:server:list','server',4,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(17,'monitor:cache',2,'缓存监控','C','/monitor/cache','monitor/cache/index','platform:monitor:cache:list','redis',5,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(18,'tool:file',3,'文件中心','C','/tool/file','tool/file/index','platform:file:list','upload',1,'0','0','file',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(21,'tool:job',3,'定时任务','C','/tool/job','tool/job/index','platform:monitor:job:list','time-range',4,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(22,'tool:gen',3,'代码生成','C','/tool/gen','tool/gen/index','platform:tool:gen:list','code',5,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(23,'tool:api-doc',3,'接口文档','C','/tool/api-doc','tool/apidoc/index','platform:tool:apidoc:list','documentation',6,'0','0','system',NULL,'2026-11-15 13:15:08.646',NULL,NULL,'0',0,NULL),(101,'system:user:add',5,'用户新增','F',NULL,NULL,'platform:system:user:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.980',NULL,NULL,'0',0,NULL),(102,'system:user:edit',5,'用户修改','F',NULL,NULL,'platform:system:user:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.980',NULL,NULL,'0',0,NULL),(103,'system:user:remove',5,'用户删除','F',NULL,NULL,'platform:system:user:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.980',NULL,NULL,'0',0,NULL),(104,'system:user:resetPwd',5,'重置密码','F',NULL,NULL,'platform:system:user:resetPwd',NULL,4,'0','0','system',NULL,'2026-11-15 16:11:17.980',NULL,NULL,'0',0,NULL),(105,'system:user:unlock',5,'解锁账号','F',NULL,NULL,'platform:system:user:unlock',NULL,5,'0','0','system',NULL,'2026-11-15 21:47:11.828',NULL,NULL,'0',0,NULL),(201,'system:role:add',6,'角色新增','F',NULL,NULL,'platform:system:role:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.981',NULL,NULL,'0',0,NULL),(202,'system:role:edit',6,'角色修改','F',NULL,NULL,'platform:system:role:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.981',NULL,NULL,'0',0,NULL),(203,'system:role:remove',6,'角色删除','F',NULL,NULL,'platform:system:role:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.981',NULL,NULL,'0',0,NULL),(301,'system:menu:add',7,'菜单新增','F',NULL,NULL,'platform:system:menu:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.982',NULL,NULL,'0',0,NULL),(302,'system:menu:edit',7,'菜单修改','F',NULL,NULL,'platform:system:menu:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.982',NULL,NULL,'0',0,NULL),(303,'system:menu:remove',7,'菜单删除','F',NULL,NULL,'platform:system:menu:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.982',NULL,NULL,'0',0,NULL),(401,'system:dept:add',8,'部门新增','F',NULL,NULL,'platform:system:dept:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(402,'system:dept:edit',8,'部门修改','F',NULL,NULL,'platform:system:dept:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(403,'system:dept:remove',8,'部门删除','F',NULL,NULL,'platform:system:dept:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(501,'system:post:add',9,'岗位新增','F',NULL,NULL,'platform:system:post:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(502,'system:post:edit',9,'岗位修改','F',NULL,NULL,'platform:system:post:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(503,'system:post:remove',9,'岗位删除','F',NULL,NULL,'platform:system:post:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.983',NULL,NULL,'0',0,NULL),(601,'system:dict:add',10,'字典新增','F',NULL,NULL,'platform:system:dict:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(602,'system:dict:edit',10,'字典修改','F',NULL,NULL,'platform:system:dict:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(603,'system:dict:remove',10,'字典删除','F',NULL,NULL,'platform:system:dict:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(701,'system:config:add',11,'参数新增','F',NULL,NULL,'platform:system:config:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(702,'system:config:edit',11,'参数修改','F',NULL,NULL,'platform:system:config:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(703,'system:config:remove',11,'参数删除','F',NULL,NULL,'platform:system:config:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.984',NULL,NULL,'0',0,NULL),(801,'system:notice:add',12,'公告新增','F',NULL,NULL,'platform:system:notice:add',NULL,1,'0','0','system',NULL,'2026-11-15 16:11:17.985',NULL,NULL,'0',0,NULL),(802,'system:notice:edit',12,'公告修改','F',NULL,NULL,'platform:system:notice:edit',NULL,2,'0','0','system',NULL,'2026-11-15 16:11:17.985',NULL,NULL,'0',0,NULL),(803,'system:notice:remove',12,'公告删除','F',NULL,NULL,'platform:system:notice:remove',NULL,3,'0','0','system',NULL,'2026-11-15 16:11:17.985',NULL,NULL,'0',0,NULL),(1301,'monitor:online:kickout',13,'强制退出','F',NULL,NULL,'platform:monitor:online:kickout',NULL,1,'0','0','system',NULL,'2026-11-15 19:59:47.981',NULL,NULL,'0',0,NULL),(1401,'monitor:login-log:clean',14,'清空日志','F',NULL,NULL,'platform:monitor:loginlog:clean',NULL,1,'0','0','system',NULL,'2026-11-15 19:08:14.644',NULL,NULL,'0',0,NULL),(1501,'monitor:oper-log:clean',15,'清空日志','F',NULL,NULL,'platform:monitor:operlog:clean',NULL,1,'0','0','system',NULL,'2026-11-15 19:08:14.645',NULL,NULL,'0',0,NULL),(1801,'file:upload',18,'文件上传','F',NULL,NULL,'platform:file:upload',NULL,1,'0','0','file',NULL,'2026-11-15 17:15:40.752',NULL,NULL,'0',0,NULL),(1802,'file:rename',18,'文件改名','F',NULL,NULL,'platform:file:rename',NULL,2,'0','0','file',NULL,'2026-11-15 17:15:40.752',NULL,NULL,'0',0,NULL),(2101,'monitor:job:add',21,'新增任务','F',NULL,NULL,'platform:monitor:job:add',NULL,1,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2102,'monitor:job:edit',21,'编辑任务','F',NULL,NULL,'platform:monitor:job:edit',NULL,2,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2103,'monitor:job:remove',21,'删除任务','F',NULL,NULL,'platform:monitor:job:remove',NULL,3,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2104,'monitor:job:changeStatus',21,'启停任务','F',NULL,NULL,'platform:monitor:job:changeStatus',NULL,4,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2105,'monitor:job:run',21,'执行一次','F',NULL,NULL,'platform:monitor:job:run',NULL,5,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2106,'monitor:job:log',21,'执行日志','F',NULL,NULL,'platform:monitor:job:log',NULL,6,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2107,'monitor:job:log:clean',21,'清空日志','F',NULL,NULL,'platform:monitor:job:log:clean',NULL,7,'0','0','system',NULL,'2026-11-15 20:20:12.786',NULL,NULL,'0',0,NULL),(2201,'tool:gen:import',22,'导入表','F',NULL,NULL,'platform:tool:gen:import',NULL,1,'0','0','system',NULL,'2026-11-15 21:07:08.811',NULL,NULL,'0',0,NULL),(2202,'tool:gen:edit',22,'配置表','F',NULL,NULL,'platform:tool:gen:edit',NULL,2,'0','0','system',NULL,'2026-11-15 21:07:08.811',NULL,NULL,'0',0,NULL),(2203,'tool:gen:remove',22,'删除配置','F',NULL,NULL,'platform:tool:gen:remove',NULL,3,'0','0','system',NULL,'2026-11-15 21:07:08.811',NULL,NULL,'0',0,NULL),(2204,'tool:gen:preview',22,'预览代码','F',NULL,NULL,'platform:tool:gen:preview',NULL,4,'0','0','system',NULL,'2026-11-15 21:07:08.811',NULL,NULL,'0',0,NULL),(2205,'tool:gen:download',22,'下载代码','F',NULL,NULL,'platform:tool:gen:download',NULL,5,'0','0','system',NULL,'2026-11-15 21:07:08.811',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_notice` (
  `notice_id` bigint(20) NOT NULL,
  `notice_title` varchar(200) NOT NULL,
  `notice_type` char(1) NOT NULL DEFAULT '1' COMMENT '1通知 2公告',
  `notice_content` longtext,
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1,'凌霄后台基座已启动','2','<p>欢迎使用凌霄后台基座。默认账号 <b>admin</b>，首次登录后请立即修改密码。</p>','0',NULL,'2026-11-15 13:15:08.660',NULL,NULL,'0',0,'初始化演示数据');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint(20) NOT NULL,
  `title` varchar(100) DEFAULT NULL,
  `business_type` int(11) NOT NULL DEFAULT '0',
  `method` varchar(255) DEFAULT NULL,
  `request_method` varchar(16) DEFAULT NULL,
  `oper_name` varchar(64) DEFAULT NULL,
  `dept_name` varchar(64) DEFAULT NULL,
  `oper_url` varchar(500) DEFAULT NULL,
  `oper_ip` varchar(64) DEFAULT NULL,
  `oper_param` text,
  `json_result` text,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0成功 1失败',
  `error_msg` text,
  `oper_time` datetime(3) NOT NULL,
  `cost_time` bigint(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`oper_id`),
  KEY `idx_oper_log_time` (`oper_time`),
  KEY `idx_oper_log_user` (`oper_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_post` (
  `post_id` bigint(20) NOT NULL,
  `post_code` varchar(64) NOT NULL,
  `post_name` varchar(64) NOT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`post_id`),
  UNIQUE KEY `uk_post_code` (`post_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','总经理',1,'0',NULL,'2026-11-15 13:15:08.634',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role` (
  `role_id` bigint(20) NOT NULL,
  `role_key` varchar(64) NOT NULL,
  `role_name` varchar(64) NOT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `data_scope` char(1) NOT NULL DEFAULT '1' COMMENT '1全部 2自定义 3本部门 4本部门及以下 5仅本人',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'admin','超级管理员',1,'1','0',NULL,'2026-11-15 13:15:08.640',NULL,NULL,'0',0,'拥有全部权限');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint(20) NOT NULL,
  `menu_id` bigint(20) NOT NULL,
  PRIMARY KEY (`role_id`,`menu_id`),
  KEY `idx_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,21),(1,22),(1,23),(1,101),(1,102),(1,103),(1,104),(1,105),(1,201),(1,202),(1,203),(1,301),(1,302),(1,303),(1,401),(1,402),(1,403),(1,501),(1,502),(1,503),(1,601),(1,602),(1,603),(1,701),(1,702),(1,703),(1,801),(1,802),(1,803),(1,1301),(1,1401),(1,1501),(1,1801),(1,1802),(1,2101),(1,2102),(1,2103),(1,2104),(1,2105),(1,2106),(1,2107),(1,2201),(1,2202),(1,2203),(1,2204),(1,2205);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user` (
  `user_id` bigint(20) NOT NULL,
  `dept_id` bigint(20) DEFAULT NULL,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL COMMENT 'BCrypt 哈希',
  `email` varchar(128) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `sex` char(1) DEFAULT '2' COMMENT '0男 1女 2未知',
  `avatar` varchar(255) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0正常 1停用',
  `login_ip` varchar(64) DEFAULT NULL,
  `login_time` datetime(3) DEFAULT NULL,
  `login_fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '连续登录失败次数，成功登录 / 解锁后清零',
  `lock_time` datetime DEFAULT NULL COMMENT '锁定截止时间，NULL 表示未锁定',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,1,'admin','超级管理员','$2a$10$MyqaRpRtV3e0F673s3oIzOXta1XWkOknfwll6m/Gqgccrb.D44lUa','admin@lingxiao.local','13800000000','2',NULL,'0','127.0.0.1','2026-11-15 22:38:10.551',0,NULL,NULL,'2026-11-15 13:15:08.636',1,NULL,'0',0,'系统内置超级管理员');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-15 23:42:35
