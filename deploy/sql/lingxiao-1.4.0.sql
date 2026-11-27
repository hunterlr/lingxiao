-- =====================================================================
-- 凌霄 · v1.4.0 数据库快照（结构 + 初始数据）
-- =====================================================================
-- 用途：clone 仓库后，不想等 Flyway 逐条迁移、或想直接拿到一套可用的
--       后台数据时，导入本文件即可。
--
-- 内容：
--   57 张表（56 张业务表 + flyway_schema_history）
--   Flyway V1~V42 全部迁移记录（导入后启动只校验，不会再建表）
--   admin 账号、157 条菜单、角色 / 字典 / 33 项参数 / 定时任务等初始数据
--   348 个多语言键（zh-CN / en-US，已发布）
--
-- 生成方式（2026-11-27）：
--   用 1.4.0 的后端连一个全新空库启动，Flyway 跑完 V1~V42 后直接 mysqldump；
--   不含登录日志、操作日志与任何内容数据，导入后是一个干净的空站。
--
-- 导入（先建库建号，见同目录 init-db.sql）：
--   mysql -uroot -p < deploy/sql/lingxiao-1.4.0.sql
--   或 mysql -h127.0.0.1 -P3306 -ulingxiao -p lingxiao < deploy/sql/lingxiao-1.4.0.sql
--
-- 注意：
--   1. 文件里是 DROP TABLE IF EXISTS + CREATE TABLE，重复导入会把库重置成快照状态；
--   2. 内含 flyway_schema_history，所以不要修改已执行过的 V*.sql，
--      否则启动时 Flyway 会报 checksum mismatch；
--   3. 默认账号 admin / admin123，导入后请立刻改密码；
--   4. 上传文件不在本文件里（data/upload 不入库），文件中心会是空列表；
--   5. 快照里没有站点 / 栏目 / 文章，门户首页需先在后台建站点（siteKey=main）并发布内容。
--
-- 重新生成：结构或初始数据变化后，重复上面的“全新空库 + 启动 + mysqldump”流程，
--   不要用开发库直接导，避免把日志和测试数据带进快照。
-- =====================================================================
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
DROP TABLE IF EXISTS `cms_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_article` (
  `article_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `category_id` bigint(20) NOT NULL,
  `title` varchar(200) NOT NULL,
  `slug` varchar(200) DEFAULT NULL COMMENT '门户 URL 片段，站点内建议唯一',
  `summary` varchar(500) DEFAULT NULL,
  `cover` varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  `content` longtext COMMENT '已清洗的 HTML',
  `author_id` bigint(20) DEFAULT NULL COMMENT 'sys_user.user_id；第三方作者可空',
  `author_name` varchar(64) DEFAULT NULL COMMENT '署名快照，用户改名不回溯历史文章',
  `source` varchar(100) DEFAULT NULL,
  `source_url` varchar(255) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0草稿 1已发布 2已下线',
  `publish_time` datetime(3) DEFAULT NULL,
  `view_count` bigint(20) NOT NULL DEFAULT '0',
  `sort` int(11) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `delete_by` bigint(20) DEFAULT NULL,
  `delete_by_name` varchar(64) DEFAULT NULL,
  `delete_time` datetime(3) DEFAULT NULL,
  `schedule_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`article_id`),
  KEY `idx_cms_article_list` (`site_id`,`status`,`publish_time`),
  KEY `idx_cms_article_category` (`category_id`,`status`),
  KEY `idx_cms_article_slug` (`site_id`,`slug`),
  KEY `idx_cms_article_author` (`author_id`),
  KEY `idx_cms_article_del` (`del_flag`,`delete_time`),
  KEY `idx_cms_article_schedule` (`status`,`schedule_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_article` WRITE;
/*!40000 ALTER TABLE `cms_article` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_article` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_article_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_article_tag` (
  `article_id` bigint(20) NOT NULL,
  `tag_id` bigint(20) NOT NULL,
  PRIMARY KEY (`article_id`,`tag_id`),
  KEY `idx_cms_article_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章-标签';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_article_tag` WRITE;
/*!40000 ALTER TABLE `cms_article_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_article_tag` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_article_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_article_version` (
  `version_id` bigint(20) NOT NULL,
  `article_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `version_no` int(11) NOT NULL,
  `change_type` varchar(20) NOT NULL COMMENT 'create / edit / publish / rollback / restore',
  `category_id` bigint(20) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `slug` varchar(200) DEFAULT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `cover` varchar(255) DEFAULT NULL,
  `content` longtext,
  `source` varchar(100) DEFAULT NULL,
  `source_url` varchar(255) DEFAULT NULL,
  `tag_ids` varchar(1000) DEFAULT NULL COMMENT '逗号分隔的标签 ID 快照',
  `status` char(1) DEFAULT NULL COMMENT '快照时的文章状态',
  `content_hash` varchar(64) NOT NULL COMMENT 'SHA-256，用于相邻版本去重',
  `operator_id` bigint(20) DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_cms_article_version_no` (`article_id`,`version_no`),
  KEY `idx_cms_article_version_article` (`article_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章内容版本';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_article_version` WRITE;
/*!40000 ALTER TABLE `cms_article_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_article_version` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_banner`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_banner` (
  `banner_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `banner_group` varchar(32) NOT NULL DEFAULT 'home',
  `title` varchar(200) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  `link_url` varchar(500) DEFAULT NULL,
  `start_time` datetime(3) DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`banner_id`),
  KEY `idx_cms_banner_group` (`site_id`,`banner_group`,`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 轮播 / 广告位';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_banner` WRITE;
/*!40000 ALTER TABLE `cms_banner` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_banner` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_block`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_block` (
  `block_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `block_key` varchar(64) NOT NULL,
  `block_name` varchar(100) NOT NULL,
  `block_type` varchar(16) NOT NULL DEFAULT 'html' COMMENT 'html / image',
  `content` longtext COMMENT '已清洗的 HTML',
  `cover` varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  `link_url` varchar(500) DEFAULT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`block_id`),
  UNIQUE KEY `uk_cms_block_key` (`site_id`,`block_key`),
  KEY `idx_cms_block_site` (`site_id`,`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 区块 / 碎片';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_block` WRITE;
/*!40000 ALTER TABLE `cms_block` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_block` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_category` (
  `category_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) NOT NULL DEFAULT '0',
  `category_key` varchar(64) NOT NULL COMMENT '站点内稳定键，栏目迁移/门户路径用',
  `category_name` varchar(100) NOT NULL,
  `slug` varchar(100) DEFAULT NULL COMMENT '门户 URL 片段',
  `path` varchar(255) DEFAULT NULL COMMENT '门户完整路径，可空；由应用层拼',
  `template` varchar(100) DEFAULT NULL COMMENT '列表模板编码：list.default / list.news / list.gallery；空值回退默认版式',
  `icon` varchar(64) DEFAULT NULL,
  `cover` varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  `seo_title` varchar(200) DEFAULT NULL,
  `seo_keywords` varchar(255) DEFAULT NULL,
  `seo_description` varchar(500) DEFAULT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `visible` char(1) NOT NULL DEFAULT '0' COMMENT '0显示 1隐藏',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `model_id` bigint(20) DEFAULT NULL COMMENT '内容模型；空=内置文章模型',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_cms_category_key` (`site_id`,`category_key`),
  KEY `idx_cms_category_parent` (`site_id`,`parent_id`),
  KEY `idx_cms_category_status` (`site_id`,`status`),
  KEY `idx_cms_category_model` (`site_id`,`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 栏目';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_category` WRITE;
/*!40000 ALTER TABLE `cms_category` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_category` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_category_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_category_closure` (
  `category_id` bigint(20) NOT NULL,
  `ancestor_id` bigint(20) NOT NULL,
  `depth` int(11) NOT NULL COMMENT '0=自身，1=父级，依此类推',
  PRIMARY KEY (`category_id`,`ancestor_id`),
  KEY `idx_cms_closure_ancestor` (`ancestor_id`,`depth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 栏目闭包';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_category_closure` WRITE;
/*!40000 ALTER TABLE `cms_category_closure` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_category_closure` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_content_ext`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_content_ext` (
  `ext_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `owner_type` varchar(16) NOT NULL COMMENT 'article / page',
  `owner_id` bigint(20) NOT NULL,
  `model_id` bigint(20) DEFAULT NULL,
  `ext_json` longtext COMMENT '扩展字段 JSON',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`ext_id`),
  UNIQUE KEY `uk_cms_content_ext_owner` (`owner_type`,`owner_id`),
  KEY `idx_cms_content_ext_site` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 内容扩展字段';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_content_ext` WRITE;
/*!40000 ALTER TABLE `cms_content_ext` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_content_ext` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_model` (
  `model_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `model_key` varchar(64) NOT NULL,
  `model_name` varchar(100) NOT NULL,
  `model_type` varchar(16) NOT NULL DEFAULT 'article' COMMENT 'article / gallery / page',
  `built_in` char(1) NOT NULL DEFAULT '0' COMMENT '1=内置模型，不可删除 / 改 key',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `sort` int(11) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`model_id`),
  UNIQUE KEY `uk_cms_model_key` (`site_id`,`model_key`),
  KEY `idx_cms_model_site` (`site_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 内容模型';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_model` WRITE;
/*!40000 ALTER TABLE `cms_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_model` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_model_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_model_field` (
  `field_id` bigint(20) NOT NULL,
  `model_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `field_key` varchar(64) NOT NULL,
  `field_name` varchar(100) NOT NULL,
  `field_type` varchar(32) NOT NULL COMMENT 'text/textarea/richtext/number/date/select/checkbox/image/images/file',
  `is_required` char(1) NOT NULL DEFAULT '0' COMMENT '1=必填',
  `default_value` varchar(500) DEFAULT NULL,
  `options_json` text COMMENT '选项：[{"label":"A","value":"a"}]',
  `validation_json` varchar(1000) DEFAULT NULL COMMENT '长度 / 正则 / 范围等附加校验',
  `is_list` char(1) NOT NULL DEFAULT '0',
  `is_search` char(1) NOT NULL DEFAULT '0',
  `sort` int(11) NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`field_id`),
  UNIQUE KEY `uk_cms_model_field_key` (`model_id`,`field_key`),
  KEY `idx_cms_model_field_site` (`site_id`,`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 模型字段';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_model_field` WRITE;
/*!40000 ALTER TABLE `cms_model_field` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_model_field` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_nav`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_nav` (
  `nav_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `nav_key` varchar(64) NOT NULL,
  `nav_name` varchar(100) NOT NULL,
  `position` varchar(16) NOT NULL DEFAULT 'top' COMMENT 'top / footer / mobile',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`nav_id`),
  UNIQUE KEY `uk_cms_nav_key` (`site_id`,`nav_key`),
  KEY `idx_cms_nav_site` (`site_id`,`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 导航';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_nav` WRITE;
/*!40000 ALTER TABLE `cms_nav` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_nav` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_nav_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_nav_item` (
  `item_id` bigint(20) NOT NULL,
  `nav_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) NOT NULL DEFAULT '0',
  `item_name` varchar(100) NOT NULL,
  `target_type` varchar(16) NOT NULL DEFAULT 'link' COMMENT 'category / page / link',
  `target_id` bigint(20) DEFAULT NULL,
  `url` varchar(500) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `visible` char(1) NOT NULL DEFAULT '0' COMMENT '0显示 1隐藏',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  KEY `idx_cms_nav_item_nav` (`nav_id`,`parent_id`),
  KEY `idx_cms_nav_item_site` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 导航项';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_nav_item` WRITE;
/*!40000 ALTER TABLE `cms_nav_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_nav_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_page`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_page` (
  `page_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `model_id` bigint(20) DEFAULT NULL,
  `page_key` varchar(100) NOT NULL COMMENT '站点内稳定键 / 门户 URL',
  `page_name` varchar(100) NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `cover` varchar(255) DEFAULT NULL COMMENT 'fileId 或 URL',
  `content` longtext COMMENT '已清洗的 HTML',
  `template` varchar(100) DEFAULT NULL COMMENT '单页模板编码：page.default / page.about；空值回退默认版式',
  `seo_title` varchar(200) DEFAULT NULL,
  `seo_keywords` varchar(255) DEFAULT NULL,
  `seo_description` varchar(500) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0草稿 1已发布 2已下线',
  `publish_time` datetime(3) DEFAULT NULL,
  `sort` int(11) NOT NULL DEFAULT '0',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`page_id`),
  UNIQUE KEY `uk_cms_page_key` (`site_id`,`page_key`),
  KEY `idx_cms_page_site` (`site_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 单页';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_page` WRITE;
/*!40000 ALTER TABLE `cms_page` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_page` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_preview_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_preview_ticket` (
  `ticket_id` bigint(20) NOT NULL,
  `ticket` varchar(64) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `target_type` varchar(16) NOT NULL COMMENT 'article / page',
  `target_id` bigint(20) NOT NULL,
  `template` varchar(64) DEFAULT NULL COMMENT '预览时覆盖的模板编码；空值沿用目标自身模板',
  `expire_time` datetime(3) NOT NULL,
  `used` char(1) NOT NULL DEFAULT '0' COMMENT '0未使用 1已使用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ticket_id`),
  UNIQUE KEY `uk_cms_preview_ticket` (`ticket`),
  KEY `idx_cms_preview_target` (`site_id`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 草稿预览票据';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_preview_ticket` WRITE;
/*!40000 ALTER TABLE `cms_preview_ticket` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_preview_ticket` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_site`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_site` (
  `site_id` bigint(20) NOT NULL,
  `site_key` varchar(64) NOT NULL COMMENT '稳定键，菜单/门户/脚本引用用',
  `site_name` varchar(100) NOT NULL,
  `domain` varchar(255) DEFAULT NULL COMMENT '绑定域名，可空；多站点时用于门户识别',
  `logo` varchar(255) DEFAULT NULL COMMENT 'fileId 或 http(s) URL',
  `seo_title` varchar(200) DEFAULT NULL,
  `seo_keywords` varchar(255) DEFAULT NULL,
  `seo_description` varchar(500) DEFAULT NULL,
  `theme` varchar(32) NOT NULL DEFAULT 'default' COMMENT '门户主题编码：default / magazine / minimal',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`site_id`),
  UNIQUE KEY `uk_cms_site_key` (`site_key`),
  KEY `idx_cms_site_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 站点';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_site` WRITE;
/*!40000 ALTER TABLE `cms_site` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_site` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `cms_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cms_tag` (
  `tag_id` bigint(20) NOT NULL,
  `site_id` bigint(20) NOT NULL,
  `tag_name` varchar(64) NOT NULL,
  `tag_key` varchar(64) NOT NULL COMMENT '站点内稳定键 / 门户 URL 片段',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_cms_tag_key` (`site_id`,`tag_key`),
  KEY `idx_cms_tag_name` (`site_id`,`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 标签';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cms_tag` WRITE;
/*!40000 ALTER TABLE `cms_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `cms_tag` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','system schema','SQL','V1__system_schema.sql',-1420895125,'lingxiao','2026-11-27 12:17:51',209,1),(2,'2','system seed','SQL','V2__system_seed.sql',-1808537696,'lingxiao','2026-11-27 12:17:51',40,1),(3,'3','file schema','SQL','V3__file_schema.sql',-1485486679,'lingxiao','2026-11-27 12:17:51',27,1),(4,'4','system button perms','SQL','V4__system_button_perms.sql',-460862906,'lingxiao','2026-11-27 12:17:51',8,1),(5,'5','system editor setting','SQL','V5__system_editor_setting.sql',112983719,'lingxiao','2026-11-27 12:17:51',1,1),(6,'6','file button perms','SQL','V6__file_button_perms.sql',-797253466,'lingxiao','2026-11-27 12:17:51',2,1),(7,'7','remove duplicate file menus','SQL','V7__remove_duplicate_file_menus.sql',1044910462,'lingxiao','2026-11-27 12:17:51',2,1),(8,'8','monitor log perms','SQL','V8__monitor_log_perms.sql',454752005,'lingxiao','2026-11-27 12:17:51',2,1),(9,'9','system config control','SQL','V9__system_config_control.sql',52135458,'lingxiao','2026-11-27 12:17:51',66,1),(10,'10','monitor online kickout','SQL','V10__monitor_online_kickout.sql',-1364584559,'lingxiao','2026-11-27 12:17:51',1,1),(11,'11','job schema','SQL','V11__job_schema.sql',1531858291,'lingxiao','2026-11-27 12:17:51',128,1),(12,'12','generator schema','SQL','V12__generator_schema.sql',-1115767411,'lingxiao','2026-11-27 12:17:51',25,1),(13,'13','file image variants','SQL','V13__file_image_variants.sql',-205937422,'lingxiao','2026-11-27 12:17:52',26,1),(14,'14','login security','SQL','V14__login_security.sql',-765509084,'lingxiao','2026-11-27 12:17:52',70,1),(15,'15','system role dept','SQL','V15__system_role_dept.sql',-1223705966,'lingxiao','2026-11-27 12:17:52',12,1),(16,'16','system session policy','SQL','V16__system_session_policy.sql',-1596402811,'lingxiao','2026-11-27 12:17:52',1,1),(17,'17','fix config encoding','SQL','V17__fix_config_encoding.sql',1795083158,'lingxiao','2026-11-27 12:17:52',1,1),(18,'18','menu module code','SQL','V18__menu_module_code.sql',-490642699,'lingxiao','2026-11-27 12:17:52',2,1),(19,'19','system user social','SQL','V19__system_user_social.sql',1730773934,'lingxiao','2026-11-27 12:17:52',14,1),(20,'20','cms schema','SQL','V20__cms_schema.sql',947275088,'lingxiao','2026-11-27 12:17:52',80,1),(21,'21','cms menu','SQL','V21__cms_menu.sql',-244766868,'lingxiao','2026-11-27 12:17:52',2,1),(22,'22','cms article recycle','SQL','V22__cms_article_recycle.sql',-534003446,'lingxiao','2026-11-27 12:17:52',87,1),(23,'23','cms article schedule','SQL','V23__cms_article_schedule.sql',-649820441,'lingxiao','2026-11-27 12:17:52',36,1),(24,'24','cms article version','SQL','V24__cms_article_version.sql',-622725259,'lingxiao','2026-11-27 12:17:52',15,1),(25,'25','member schema','SQL','V25__member_schema.sql',606864581,'lingxiao','2026-11-27 12:17:52',53,1),(26,'26','member security hardening','SQL','V26__member_security_hardening.sql',339614013,'lingxiao','2026-11-27 12:17:52',2,1),(27,'27','member channel config','SQL','V27__member_channel_config.sql',1147579075,'lingxiao','2026-11-27 12:17:52',16,1),(28,'28','member settings','SQL','V28__member_settings.sql',1514771775,'lingxiao','2026-11-27 12:17:52',2,1),(29,'29','member interaction','SQL','V29__member_interaction.sql',-2090837374,'lingxiao','2026-11-27 12:17:52',31,1),(30,'30','member comment','SQL','V30__member_comment.sql',-711932334,'lingxiao','2026-11-27 12:17:52',16,1),(31,'31','member oauth','SQL','V31__member_oauth.sql',-1471764501,'lingxiao','2026-11-27 12:17:52',32,1),(32,'32','member oauth wechat mini','SQL','V32__member_oauth_wechat_mini.sql',-903493808,'lingxiao','2026-11-27 12:17:52',1,1),(33,'33','member account uniqueness','SQL','V33__member_account_uniqueness.sql',2009992455,'lingxiao','2026-11-27 12:17:52',146,1),(34,'34','member contact change','SQL','V34__member_contact_change.sql',-831154363,'lingxiao','2026-11-27 12:17:52',14,1),(35,'35','cms model page nav','SQL','V35__cms_model_page_nav.sql',120371873,'lingxiao','2026-11-27 12:17:53',167,1),(36,'36','cms model menu','SQL','V36__cms_model_menu.sql',348800484,'lingxiao','2026-11-27 12:17:53',4,1),(37,'37','cms template theme','SQL','V37__cms_template_theme.sql',-742681349,'lingxiao','2026-11-27 12:17:53',55,1),(38,'38','cms category preview menu','SQL','V38__cms_category_preview_menu.sql',1743721913,'lingxiao','2026-11-27 12:17:53',1,1),(39,'39','i18n schema','SQL','V39__i18n_schema.sql',-758321579,'lingxiao','2026-11-27 12:17:53',68,1),(40,'40','i18n menu','SQL','V40__i18n_menu.sql',1714554610,'lingxiao','2026-11-27 12:17:53',2,1),(41,'41','i18n seed messages','SQL','V41__i18n_seed_messages.sql',1786442453,'lingxiao','2026-11-27 12:17:53',36,1),(42,'42','i18n value published','SQL','V42__i18n_value_published.sql',306772870,'lingxiao','2026-11-27 12:17:53',93,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `i18n_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18n_key` (
  `key_id` bigint(20) NOT NULL,
  `namespace` varchar(64) NOT NULL COMMENT 'common / error / valid / cms ...',
  `key_code` varchar(255) NOT NULL COMMENT '完整语言包键，例如 error.2001',
  `default_value` varchar(2000) DEFAULT NULL COMMENT '默认语言文案，缺译文时兜底',
  `module_code` varchar(64) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用（废弃键保留）',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`key_id`),
  UNIQUE KEY `uk_i18n_key` (`namespace`,`key_code`),
  KEY `idx_i18n_key_code` (`key_code`),
  KEY `idx_i18n_key_module` (`module_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译键';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `i18n_key` WRITE;
/*!40000 ALTER TABLE `i18n_key` DISABLE KEYS */;
INSERT INTO `i18n_key` VALUES (1,'cms','cms.article.notfound','文章不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(2,'cms','cms.banner.notfound','轮播不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(3,'cms','cms.banner.time.invalid','结束时间必须晚于开始时间','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(4,'cms','cms.block.key.duplicate','区块标识已存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(5,'cms','cms.block.notfound','区块不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(6,'cms','cms.block.type.invalid','区块类型只支持 html / image','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(7,'cms','cms.category.notfound','栏目不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(8,'cms','cms.field.invalid','{0}格式不正确','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(9,'cms','cms.field.key.duplicate','字段标识已存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(10,'cms','cms.field.notfound','模型字段不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(11,'cms','cms.field.options.required','单选 / 多选字段必须配置选项','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(12,'cms','cms.field.required','{0}不能为空','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(13,'cms','cms.field.type.invalid','字段类型不支持','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(14,'cms','cms.model.builtin.undeletable','内置模型不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(15,'cms','cms.model.invalid','内容模型不存在或不属于当前站点','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(16,'cms','cms.model.key.duplicate','模型标识已存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(17,'cms','cms.model.notfound','内容模型不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(18,'cms','cms.model.referenced','模型已被栏目、单页或内容字段引用，无法删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(19,'cms','cms.model.type.invalid','模型类型只支持 article / gallery / page','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(20,'cms','cms.nav.item.haschildren','请先删除下级导航项','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(21,'cms','cms.nav.item.notfound','导航项不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(22,'cms','cms.nav.key.duplicate','导航标识已存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(23,'cms','cms.nav.notfound','导航不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(24,'cms','cms.nav.parent.invalid','上级导航项不合法','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(25,'cms','cms.nav.target.invalid','导航项目标不存在或不属于当前站点','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(26,'cms','cms.nav.url.required','外链导航项必须填写地址','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(27,'cms','cms.page.key.duplicate','单页标识已存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(28,'cms','cms.page.notfound','单页不存在','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(29,'cms','cms.page.referenced','单页已被导航项引用，请先移除引用','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(30,'cms','cms.preview.target.missing','预览目标类型不支持','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(31,'cms','cms.preview.template.invalid','预览模板与目标类型不匹配','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(32,'cms','cms.preview.ticket.invalid','预览票据无效、已过期或已使用','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(33,'cms','cms.site.delete.banners','站点下还有轮播，不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(34,'cms','cms.site.delete.blocks','站点下还有区块，不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(35,'cms','cms.site.delete.models','站点下还有自定义模型，不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(36,'cms','cms.site.delete.navs','站点下还有导航，不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(37,'cms','cms.site.delete.pages','站点下还有单页，不能删除','cms','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(38,'common','common.listSeparator','；','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(39,'error','error.0','操作成功','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(40,'error','error.1000','操作失败','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(41,'error','error.1001','请求参数不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(42,'error','error.1001.member.agreement','请先阅读并同意用户协议','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(43,'error','error.1001.member.channelDevMissing','开发消息通道未装配','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(44,'error','error.1001.member.channelDisabled','通道已停用，请先启用再测试发送','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(45,'error','error.1001.member.channelProdUnavailable','生产通道尚未实现，请先部署 MemberSmsSender / MemberEmailSender','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(46,'error','error.1001.member.contactChannelUnavailable','验证码发送失败，请稍后重试或联系管理员','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(47,'error','error.1001.member.contactCodeExpired','换绑验证码已过期，请重新获取','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(48,'error','error.1001.member.contactCodeInvalid','换绑验证码不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(49,'error','error.1001.member.contactCodeMismatch','验证码与当前填写的新联系方式不一致，请重新获取','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(50,'error','error.1001.member.contactUnchanged','新联系方式与当前一致，请更换后再试','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(51,'error','error.1001.member.emailExists','该邮箱已被使用','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(52,'error','error.1001.member.emailRequired','注册必须填写邮箱','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(53,'error','error.1001.member.mergeSame','不能把账号合并到自身','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(54,'error','error.1001.member.mergeTargetDisabled','目标会员不存在或已停用，请先启用目标账号','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(55,'error','error.1001.member.mobileExists','该手机号已被使用','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(56,'error','error.1001.member.mobileRequired','注册必须填写手机号','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(57,'error','error.1001.member.oauthAlreadyBound','当前会员已绑定该平台，请先在个人中心解绑','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(58,'error','error.1001.member.oauthBindConflict','该第三方账号已绑定其他会员，请更换账号或先解绑','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(59,'error','error.1001.member.oauthBindTargetMismatch','当前登录会员与待绑定账号不一致，请登录原账号后再绑定','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(60,'error','error.1001.member.oauthBindTicketInvalid','绑定凭证无效或已过期，请重新发起第三方登录','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(61,'error','error.1001.member.oauthCodeRequired','授权码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(62,'error','error.1001.member.oauthExtraJson','扩展参数必须是合法的 JSON','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(63,'error','error.1001.member.oauthLastLoginMethod','当前账号尚未设置密码，解绑后可能无法登录；请先设置密码','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(64,'error','error.1001.member.oauthMiniOnly','该平台不支持小程序登录接口，请从门户发起授权','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(65,'error','error.1001.member.oauthNotBound','该第三方账号尚未绑定会员，请先用账号密码登录后在个人中心绑定','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(66,'error','error.1001.member.oauthNotConfigured','该第三方登录平台尚未配置完成，请联系管理员','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(67,'error','error.1001.member.oauthTicketInvalid','登录凭证无效或已过期，请重新发起第三方登录','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(68,'error','error.1001.member.passwordAlreadySet','账号已设置密码，请使用修改密码','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(69,'error','error.1001.member.passwordSetRequired','该账号尚未设置密码，请先使用第三方登录后在个人中心设置','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(70,'error','error.1001.member.resetCodeExpired','验证码已过期，请重新获取','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(71,'error','error.1001.member.resetCodeInvalid','验证码不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(72,'error','error.1001.member.sex','性别取值不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(73,'error','error.1001.member.status','会员状态只能是启用或停用','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(74,'error','error.1001.member.usernameExists','该用户名已被使用','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(75,'error','error.1001.member.usernameFormat','用户名不能包含 @，也不能全部为数字','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(76,'error','error.1001.nickname','昵称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(77,'error','error.1001.samePassword','新密码不能与原密码相同','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(78,'error','error.1002','未登录或登录已过期','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(79,'error','error.1003','没有权限执行该操作','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(80,'error','error.1003.member.commentDisabled','评论功能已关闭，请先在会员设置中开启','system','0','seed from messages*.properties','2026-11-27 20:17:53.206',NULL),(81,'error','error.1003.member.disabled','会员功能已关闭，请先在系统参数中开启','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(82,'error','error.1003.member.oauthDisabled','第三方登录已关闭，请先在会员设置中开启','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(83,'error','error.1003.member.oauthProviderDisabled','第三方登录平台「{0}」已停用','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(84,'error','error.1004','资源不存在','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(85,'error','error.1004.member','会员不存在或已被删除','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(86,'error','error.1004.member.channel','消息通道配置不存在','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(87,'error','error.1004.member.comment','评论不存在或已被删除','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(88,'error','error.1004.member.oauthBinding','第三方账号绑定不存在','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(89,'error','error.1004.member.oauthProvider','第三方登录平台不存在','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(90,'error','error.1005','请求方法不支持','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(91,'error','error.1005.method','不支持的请求方法：{0}','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(92,'error','error.1006','请求过于频繁，请稍后再试','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(93,'error','error.1006.rateLimit','操作过于频繁，请 {0} 分钟后再试','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(94,'error','error.2001','用户名或密码错误','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(95,'error','error.2002','账号已被停用','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(96,'error','error.2003','验证码错误或已过期','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(97,'error','error.2003.attempts','验证码错误次数过多，请重新获取','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(98,'error','error.2004','账号已被锁定，请稍后再试','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(99,'error','error.2004.failures','密码错误次数过多，账号已锁定 {0} 分钟','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(100,'error','error.2004.locked','账号已锁定，请 {0} 分钟后再试','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(101,'error','error.2005','原密码不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(102,'error','error.2006','账号尚未设置登录密码','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(103,'error','error.2006.passwordNotSet','该账号通过第三方登录创建，尚未设置登录密码。请使用已绑定的第三方账号登录，并在个人中心设置密码','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(104,'error','error.3001','文件上传失败','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(105,'error','error.3002','文件类型不允许','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(106,'error','error.3003','文件超过大小限制','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(107,'error','error.3004','文件不存在','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(108,'error','error.9000','系统内部错误','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(109,'i18n','i18n.import.empty','导入文件为空','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(110,'i18n','i18n.import.format','CSV 格式错误：{0}','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(111,'i18n','i18n.import.language.unknown','未知语言：{0}','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(112,'i18n','i18n.import.placeholder.mismatch','第 {0} 行占位符不一致','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(113,'i18n','i18n.key.code.duplicate','翻译键已存在','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(114,'i18n','i18n.key.inuse','该翻译键已有译文，请停用而不是删除','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(115,'i18n','i18n.key.notfound','翻译键不存在','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(116,'i18n','i18n.language.code.duplicate','语言编码已存在','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(117,'i18n','i18n.language.code.invalid','语言编码不合法，请使用 BCP 47 格式，例如 zh-CN','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(118,'i18n','i18n.language.default.locked','默认语言不能停用','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(119,'i18n','i18n.language.inuse','该语言已有译文或语言包，不能删除','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(120,'i18n','i18n.language.notfound','语言不存在','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(121,'i18n','i18n.pack.empty','没有可发布的译文','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(122,'i18n','i18n.pack.notfound','语言包版本不存在','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(123,'i18n','i18n.pack.snapshot.invalid','语言包快照损坏，无法回滚','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(124,'i18n','i18n.value.language.disabled','目标语言未启用','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(125,'i18n','i18n.value.placeholder.mismatch','占位符与默认文案不一致','i18n','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(126,'monitor','monitor.cache.duration.days','{0} 天','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(127,'monitor','monitor.cache.duration.hours','{0} 小时','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(128,'monitor','monitor.cache.duration.minutes','{0} 分','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(129,'monitor','monitor.cache.duration.zero','0 秒','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(130,'monitor','monitor.cache.note.local','当前未启用 Redis：验证码与会话保存在本机内存，以下为 Caffeine 统计。多实例部署请把 lx.redis.enabled 改为 true。','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(131,'monitor','monitor.cache.note.redis','已启用 Redis：以下为 INFO 摘要与验证码 Key 统计。','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(132,'monitor','monitor.cache.note.redisFailed','已启用 Redis，但本次读取失败，已降级展示验证码存储统计。','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(133,'monitor','monitor.cache.note.redisNoTemplate','已启用 Redis，但容器里没有 StringRedisTemplate，已降级展示验证码存储统计。','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(134,'monitor','monitor.cache.warning.noTemplate','未找到 StringRedisTemplate，请检查 spring-boot-starter-data-redis 依赖','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(135,'monitor','monitor.cache.warning.redisError','Redis 读取失败: {0}','monitor','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(136,'valid','valid.avatar.max','头像地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(137,'valid','valid.cms.article.author.max','作者署名不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(138,'valid','valid.cms.article.schedule.required','请选择定时发布时间','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(139,'valid','valid.cms.article.source.max','来源不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(140,'valid','valid.cms.article.summary.max','摘要不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(141,'valid','valid.cms.article.title.max','文章标题不能超过 200 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(142,'valid','valid.cms.article.title.required','文章标题不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(143,'valid','valid.cms.banner.group.max','轮播分组不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(144,'valid','valid.cms.banner.group.required','轮播分组不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(145,'valid','valid.cms.banner.image.max','轮播图片地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(146,'valid','valid.cms.banner.link.max','跳转地址不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(147,'valid','valid.cms.banner.title.max','轮播标题不能超过 200 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(148,'valid','valid.cms.block.key.max','区块标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(149,'valid','valid.cms.block.key.required','区块标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(150,'valid','valid.cms.block.name.max','区块名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(151,'valid','valid.cms.block.name.required','区块名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(152,'valid','valid.cms.block.type','请选择区块类型','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(153,'valid','valid.cms.category.key.max','栏目标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(154,'valid','valid.cms.category.key.required','栏目标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(155,'valid','valid.cms.category.name.max','栏目名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(156,'valid','valid.cms.category.name.required','栏目名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(157,'valid','valid.cms.category.required','请选择栏目','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(158,'valid','valid.cms.category.template','栏目模板不在白名单内','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(159,'valid','valid.cms.cover.max','封面地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(160,'valid','valid.cms.field.default.max','默认值不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.210',NULL),(161,'valid','valid.cms.field.key.max','字段标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(162,'valid','valid.cms.field.key.required','字段标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(163,'valid','valid.cms.field.name.max','字段名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(164,'valid','valid.cms.field.name.required','字段名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(165,'valid','valid.cms.field.options.max','选项配置不能超过 4000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(166,'valid','valid.cms.field.type','请选择字段类型','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(167,'valid','valid.cms.field.validation.max','校验配置不能超过 1000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(168,'valid','valid.cms.icon.max','图标标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(169,'valid','valid.cms.logo.max','Logo 地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(170,'valid','valid.cms.model.key.max','模型标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(171,'valid','valid.cms.model.key.required','模型标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(172,'valid','valid.cms.model.name.max','模型名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(173,'valid','valid.cms.model.name.required','模型名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(174,'valid','valid.cms.model.required','请选择内容模型','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(175,'valid','valid.cms.model.type','请选择模型类型','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(176,'valid','valid.cms.nav.item.name.max','导航项名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(177,'valid','valid.cms.nav.item.name.required','导航项名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(178,'valid','valid.cms.nav.item.target.type','请选择导航项目标类型','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(179,'valid','valid.cms.nav.item.url.max','导航项地址不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(180,'valid','valid.cms.nav.key.max','导航标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(181,'valid','valid.cms.nav.key.required','导航标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(182,'valid','valid.cms.nav.name.max','导航名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(183,'valid','valid.cms.nav.name.required','导航名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(184,'valid','valid.cms.nav.position','请选择导航位置','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(185,'valid','valid.cms.nav.required','请选择导航','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(186,'valid','valid.cms.page.key.max','单页标识不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(187,'valid','valid.cms.page.key.required','单页标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(188,'valid','valid.cms.page.name.max','单页名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(189,'valid','valid.cms.page.name.required','单页名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(190,'valid','valid.cms.page.template','单页模板不在白名单内','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(191,'valid','valid.cms.path.max','栏目路径不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(192,'valid','valid.cms.seo.description.max','SEO 描述不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(193,'valid','valid.cms.seo.keywords.max','SEO 关键词不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(194,'valid','valid.cms.seo.title.max','SEO 标题不能超过 200 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(195,'valid','valid.cms.site.domain.max','绑定域名不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(196,'valid','valid.cms.site.key.max','站点标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(197,'valid','valid.cms.site.key.required','站点标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(198,'valid','valid.cms.site.name.max','站点名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(199,'valid','valid.cms.site.name.required','站点名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(200,'valid','valid.cms.site.required','请选择站点','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(201,'valid','valid.cms.site.theme','门户主题不在白名单内','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(202,'valid','valid.cms.site.theme.max','门户主题编码不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(203,'valid','valid.cms.slug.max','URL 片段不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(204,'valid','valid.cms.source.url.max','来源地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(205,'valid','valid.cms.tag.key.max','标签标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(206,'valid','valid.cms.tag.key.required','标签标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(207,'valid','valid.cms.tag.name.max','标签名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(208,'valid','valid.cms.tag.name.required','标签名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(209,'valid','valid.cms.template.max','模板标识不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(210,'valid','valid.comment.content.max','评论内容不能超过 1000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(211,'valid','valid.comment.content.required','评论内容不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(212,'valid','valid.config.id.required','参数 ID 不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(213,'valid','valid.config.items.required','至少提交一个参数','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(214,'valid','valid.config.key.max','参数键名不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(215,'valid','valid.config.key.required','参数键名不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(216,'valid','valid.config.name.max','参数名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(217,'valid','valid.config.name.required','参数名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(218,'valid','valid.config.options.max','控件选项不能超过 1000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(219,'valid','valid.config.type.max','控件类型不能超过 20 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(220,'valid','valid.config.value.max','参数键值不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(221,'valid','valid.config.value.required','参数键值不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(222,'valid','valid.dept.name.max','部门名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(223,'valid','valid.dept.name.required','部门名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(224,'valid','valid.dictData.label.max','字典标签不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(225,'valid','valid.dictData.label.required','字典标签不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(226,'valid','valid.dictData.type.max','字典类型不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(227,'valid','valid.dictData.type.required','字典类型不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(228,'valid','valid.dictData.value.max','字典键值不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(229,'valid','valid.dictData.value.required','字典键值不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(230,'valid','valid.dictType.name.max','字典名称不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(231,'valid','valid.dictType.name.required','字典名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(232,'valid','valid.dictType.type.max','字典类型编码不能超过 100 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(233,'valid','valid.dictType.type.required','字典类型编码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(234,'valid','valid.email.format','邮箱格式不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(235,'valid','valid.email.max','邮箱不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(236,'valid','valid.file.name.max','名称长度不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(237,'valid','valid.file.name.required','文件名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(238,'valid','valid.gen.tables.max','一次最多导入 50 张表','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(239,'valid','valid.gen.tables.required','请至少选择一张表','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(240,'valid','valid.i18n.key.code.max','翻译键不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.213',NULL),(241,'valid','valid.i18n.key.code.required','翻译键不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(242,'valid','valid.i18n.key.default.max','默认文案不能超过 2000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(243,'valid','valid.i18n.key.namespace.required','命名空间不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(244,'valid','valid.i18n.language.code.max','语言编码不能超过 16 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(245,'valid','valid.i18n.language.code.required','语言编码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(246,'valid','valid.i18n.language.name.max','语言名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(247,'valid','valid.i18n.language.name.required','语言名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(248,'valid','valid.i18n.value.text.max','译文不能超过 2000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(249,'valid','valid.job.cron.max','Cron 表达式不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(250,'valid','valid.job.cron.required','Cron 表达式不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(251,'valid','valid.job.group.max','任务分组不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(252,'valid','valid.job.invokeTarget.max','执行器不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(253,'valid','valid.job.invokeTarget.required','执行器不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(254,'valid','valid.job.name.max','任务名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(255,'valid','valid.job.name.required','任务名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(256,'valid','valid.login.captchaCode.max','验证码长度不能超过 8 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(257,'valid','valid.login.captchaKey.max','验证码标识长度不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(258,'valid','valid.login.password.max','密码长度不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(259,'valid','valid.login.password.required','密码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(260,'valid','valid.login.username.max','用户名长度不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(261,'valid','valid.member.account.max','账号不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(262,'valid','valid.member.account.required','账号不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(263,'valid','valid.member.captcha.scene','验证码场景只能是 login / register / reset','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(264,'valid','valid.member.channel.accessKey.max','AccessKey 不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(265,'valid','valid.member.channel.accessSecret.max','AccessSecret 不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(266,'valid','valid.member.channel.email','邮箱格式不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(267,'valid','valid.member.channel.endpoint.max','服务商地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(268,'valid','valid.member.channel.mobile','手机号格式不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(269,'valid','valid.member.channel.mode','通道模式只能是 dev 或 prod','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(270,'valid','valid.member.channel.mode.required','通道模式不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(271,'valid','valid.member.channel.provider.max','服务商标识不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(272,'valid','valid.member.channel.remark.max','备注不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(273,'valid','valid.member.channel.sender.max','发送方标识不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(274,'valid','valid.member.channel.signName.max','短信签名不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(275,'valid','valid.member.channel.status','通道状态只能是 0（启用）或 1（停用）','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(276,'valid','valid.member.channel.status.required','通道状态不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(277,'valid','valid.member.channel.target.max','测试目标不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(278,'valid','valid.member.channel.targetRequired','请填写测试接收的手机号或邮箱','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(279,'valid','valid.member.channel.templateCode.max','模板编码不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(280,'valid','valid.member.channel.type','不支持的通道类型','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(281,'valid','valid.member.contact.code.max','换绑验证码不能超过 16 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(282,'valid','valid.member.contact.code.required','换绑验证码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(283,'valid','valid.member.contact.email','邮箱格式不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(284,'valid','valid.member.contact.mobile','手机号格式不正确','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(285,'valid','valid.member.contact.passwordRequired','请先输入当前登录密码','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(286,'valid','valid.member.contact.type','联系方式类型只能是 mobile 或 email','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(287,'valid','valid.member.contact.type.required','联系方式类型不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(288,'valid','valid.member.contact.value.max','新联系方式不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(289,'valid','valid.member.contact.value.required','请输入新的手机号或邮箱','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(290,'valid','valid.member.level.invalid','会员等级不存在或已停用','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(291,'valid','valid.member.level.required','请选择会员等级','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(292,'valid','valid.member.merge.source.required','请选择要停用的旧账号','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(293,'valid','valid.member.merge.target.required','请选择要保留的目标账号','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(294,'valid','valid.member.oauth.authorizeUrl.max','授权端点不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(295,'valid','valid.member.oauth.clientId.max','ClientId 不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(296,'valid','valid.member.oauth.clientSecret.max','ClientSecret 不能超过 4000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(297,'valid','valid.member.oauth.code.max','授权码长度不能超过 512 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(298,'valid','valid.member.oauth.code.required','授权码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(299,'valid','valid.member.oauth.displayName.max','平台名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(300,'valid','valid.member.oauth.extraJson.max','扩展参数不能超过 2000 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(301,'valid','valid.member.oauth.provider.max','平台标识不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(302,'valid','valid.member.oauth.provider.required','平台标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(303,'valid','valid.member.oauth.redirectUri.max','回调地址不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(304,'valid','valid.member.oauth.remark.max','备注不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(305,'valid','valid.member.oauth.scope.max','Scope 不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(306,'valid','valid.member.oauth.status','平台状态只能是 0（启用）或 1（停用）','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(307,'valid','valid.member.oauth.ticket.max','登录凭证长度不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(308,'valid','valid.member.oauth.ticket.required','登录凭证不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(309,'valid','valid.member.oauth.tokenUrl.max','Token 端点不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(310,'valid','valid.member.oauth.userInfoUrl.max','用户信息端点不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(311,'valid','valid.member.reset.code.max','验证码不能超过 16 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(312,'valid','valid.member.reset.code.required','验证码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(313,'valid','valid.member.status','会员状态只能是 0（正常）或 1（停用）','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(314,'valid','valid.member.status.required','会员状态不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(315,'valid','valid.menu.component.max','组件路径不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(316,'valid','valid.menu.key.max','菜单标识不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(317,'valid','valid.menu.key.required','菜单标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(318,'valid','valid.menu.moduleCode.max','模块编码不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(319,'valid','valid.menu.name.max','菜单名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(320,'valid','valid.menu.name.required','菜单名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.214',NULL),(321,'valid','valid.menu.path.max','路由地址不能超过 255 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(322,'valid','valid.menu.perms.max','权限码不能超过 128 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(323,'valid','valid.menu.type.pattern','菜单类型只能是 M / C / F','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(324,'valid','valid.menu.type.required','菜单类型不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(325,'valid','valid.nickname.max','昵称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(326,'valid','valid.nickname.required','昵称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(327,'valid','valid.notice.title.max','公告标题不能超过 200 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(328,'valid','valid.notice.title.required','公告标题不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(329,'valid','valid.online.sessions.required','请选择要强退的会话','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(330,'valid','valid.password.new.required','新密码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(331,'valid','valid.password.new.size','新密码长度必须在 6~64 个字符之间','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(332,'valid','valid.password.old.max','原密码不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(333,'valid','valid.password.old.required','原密码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(334,'valid','valid.password.policy.basic','密码至少 8 位，且必须同时包含英文字母和数字','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(335,'valid','valid.password.policy.strong','密码至少 10 位，且必须同时包含大写字母、小写字母、数字和符号','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(336,'valid','valid.password.size','密码长度必须在 6~64 个字符之间','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(337,'valid','valid.phone.max','手机号不能超过 32 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(338,'valid','valid.post.code.max','岗位编码不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(339,'valid','valid.post.code.required','岗位编码不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(340,'valid','valid.post.name.max','岗位名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(341,'valid','valid.post.name.required','岗位名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(342,'valid','valid.remark.max','备注不能超过 500 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(343,'valid','valid.role.key.max','角色标识不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(344,'valid','valid.role.key.required','角色标识不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(345,'valid','valid.role.name.max','角色名称不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(346,'valid','valid.role.name.required','角色名称不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(347,'valid','valid.user.username.max','用户名不能超过 64 个字符','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL),(348,'valid','valid.username.required','用户名不能为空','system','0','seed from messages*.properties','2026-11-27 20:17:53.217',NULL);
/*!40000 ALTER TABLE `i18n_key` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `i18n_language`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18n_language` (
  `language_id` bigint(20) NOT NULL,
  `language_code` varchar(16) NOT NULL COMMENT 'BCP 47，例如 zh-CN',
  `language_name` varchar(64) NOT NULL,
  `native_name` varchar(64) NOT NULL,
  `locale` varchar(32) NOT NULL,
  `direction` varchar(8) NOT NULL DEFAULT 'ltr' COMMENT 'ltr / rtl',
  `is_default` smallint(6) NOT NULL DEFAULT '0' COMMENT '1=平台默认语言',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  `sort` int(11) NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`language_id`),
  UNIQUE KEY `uk_i18n_language_code` (`language_code`),
  KEY `idx_i18n_language_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台语言定义';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `i18n_language` WRITE;
/*!40000 ALTER TABLE `i18n_language` DISABLE KEYS */;
INSERT INTO `i18n_language` VALUES (1,'zh-CN','简体中文','简体中文','zh_CN','ltr',1,'0',1,'2026-11-27 20:17:53.167',NULL),(2,'en-US','English','English','en_US','ltr',0,'0',2,'2026-11-27 20:17:53.167',NULL);
/*!40000 ALTER TABLE `i18n_language` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `i18n_missing_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18n_missing_log` (
  `log_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `language_code` varchar(16) NOT NULL,
  `namespace` varchar(64) NOT NULL,
  `key_code` varchar(255) NOT NULL,
  `hit_count` bigint(20) NOT NULL DEFAULT '1',
  `first_seen` datetime(3) NOT NULL,
  `last_seen` datetime(3) NOT NULL,
  `sample_locale` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  UNIQUE KEY `uk_i18n_missing` (`tenant_id`,`language_code`,`namespace`,`key_code`),
  KEY `idx_i18n_missing_last` (`tenant_id`,`language_code`,`last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺失翻译记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `i18n_missing_log` WRITE;
/*!40000 ALTER TABLE `i18n_missing_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `i18n_missing_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `i18n_pack`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18n_pack` (
  `pack_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `language_code` varchar(16) NOT NULL,
  `namespace` varchar(64) NOT NULL,
  `version` int(11) NOT NULL,
  `checksum` varchar(64) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 草稿/历史 / 1 当前发布 / 2 已废弃',
  `snapshot_json` longtext COMMENT '发布时译文快照 {keyId:valueText}，用于回滚',
  `published_by` bigint(20) DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`pack_id`),
  UNIQUE KEY `uk_i18n_pack` (`tenant_id`,`language_code`,`namespace`,`version`),
  KEY `idx_i18n_pack_current` (`tenant_id`,`language_code`,`namespace`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语言包版本';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `i18n_pack` WRITE;
/*!40000 ALTER TABLE `i18n_pack` DISABLE KEYS */;
/*!40000 ALTER TABLE `i18n_pack` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `i18n_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `i18n_value` (
  `value_id` bigint(20) NOT NULL,
  `key_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '0=平台；租户覆盖留位',
  `language_code` varchar(16) NOT NULL,
  `value_text` text,
  `published_text` text COMMENT '最近一次发布的译文；草稿修改不影响线上',
  `review_status` char(1) NOT NULL DEFAULT '0' COMMENT '0 草稿 / 1 已发布',
  `version` int(11) NOT NULL DEFAULT '1',
  `translated_by` bigint(20) DEFAULT NULL,
  `translated_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`value_id`),
  UNIQUE KEY `uk_i18n_value` (`key_id`,`tenant_id`,`language_code`),
  KEY `idx_i18n_value_lang` (`tenant_id`,`language_code`,`review_status`),
  KEY `idx_i18n_value_key` (`key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译值';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `i18n_value` WRITE;
/*!40000 ALTER TABLE `i18n_value` DISABLE KEYS */;
INSERT INTO `i18n_value` VALUES (1,1,0,'zh-CN','文章不存在','文章不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(2,1,0,'en-US','Article not found','Article not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(3,2,0,'zh-CN','轮播不存在','轮播不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(4,2,0,'en-US','Banner not found','Banner not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(5,3,0,'zh-CN','结束时间必须晚于开始时间','结束时间必须晚于开始时间','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(6,3,0,'en-US','End time must be later than start time','End time must be later than start time','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(7,4,0,'zh-CN','区块标识已存在','区块标识已存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(8,4,0,'en-US','Block key already exists','Block key already exists','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(9,5,0,'zh-CN','区块不存在','区块不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(10,5,0,'en-US','Block not found','Block not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(11,6,0,'zh-CN','区块类型只支持 html / image','区块类型只支持 html / image','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(12,6,0,'en-US','Block type must be html or image','Block type must be html or image','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(13,7,0,'zh-CN','栏目不存在','栏目不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(14,7,0,'en-US','Category not found','Category not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(15,8,0,'zh-CN','{0}格式不正确','{0}格式不正确','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(16,8,0,'en-US','{0} has an invalid format','{0} has an invalid format','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(17,9,0,'zh-CN','字段标识已存在','字段标识已存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(18,9,0,'en-US','Field key already exists','Field key already exists','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(19,10,0,'zh-CN','模型字段不存在','模型字段不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(20,10,0,'en-US','Model field not found','Model field not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(21,11,0,'zh-CN','单选 / 多选字段必须配置选项','单选 / 多选字段必须配置选项','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(22,11,0,'en-US','Select and checkbox fields require options','Select and checkbox fields require options','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(23,12,0,'zh-CN','{0}不能为空','{0}不能为空','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(24,12,0,'en-US','{0} must not be empty','{0} must not be empty','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(25,13,0,'zh-CN','字段类型不支持','字段类型不支持','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(26,13,0,'en-US','Unsupported field type','Unsupported field type','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(27,14,0,'zh-CN','内置模型不能删除','内置模型不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(28,14,0,'en-US','Built-in models cannot be deleted','Built-in models cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(29,15,0,'zh-CN','内容模型不存在或不属于当前站点','内容模型不存在或不属于当前站点','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(30,15,0,'en-US','Content model not found or does not belong to this site','Content model not found or does not belong to this site','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(31,16,0,'zh-CN','模型标识已存在','模型标识已存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(32,16,0,'en-US','Model key already exists','Model key already exists','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(33,17,0,'zh-CN','内容模型不存在','内容模型不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(34,17,0,'en-US','Content model not found','Content model not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(35,18,0,'zh-CN','模型已被栏目、单页或内容字段引用，无法删除','模型已被栏目、单页或内容字段引用，无法删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(36,18,0,'en-US','The model is referenced by categories, pages or content fields','The model is referenced by categories, pages or content fields','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(37,19,0,'zh-CN','模型类型只支持 article / gallery / page','模型类型只支持 article / gallery / page','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(38,19,0,'en-US','Model type must be article, gallery or page','Model type must be article, gallery or page','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(39,20,0,'zh-CN','请先删除下级导航项','请先删除下级导航项','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(40,20,0,'en-US','Delete child navigation items first','Delete child navigation items first','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(41,21,0,'zh-CN','导航项不存在','导航项不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(42,21,0,'en-US','Navigation item not found','Navigation item not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(43,22,0,'zh-CN','导航标识已存在','导航标识已存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(44,22,0,'en-US','Navigation key already exists','Navigation key already exists','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(45,23,0,'zh-CN','导航不存在','导航不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(46,23,0,'en-US','Navigation not found','Navigation not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(47,24,0,'zh-CN','上级导航项不合法','上级导航项不合法','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(48,24,0,'en-US','Invalid navigation parent','Invalid navigation parent','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(49,25,0,'zh-CN','导航项目标不存在或不属于当前站点','导航项目标不存在或不属于当前站点','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(50,25,0,'en-US','Navigation target not found or does not belong to this site','Navigation target not found or does not belong to this site','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(51,26,0,'zh-CN','外链导航项必须填写地址','外链导航项必须填写地址','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(52,26,0,'en-US','External navigation items require a URL','External navigation items require a URL','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(53,27,0,'zh-CN','单页标识已存在','单页标识已存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(54,27,0,'en-US','Page key already exists','Page key already exists','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(55,28,0,'zh-CN','单页不存在','单页不存在','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(56,28,0,'en-US','Page not found','Page not found','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(57,29,0,'zh-CN','单页已被导航项引用，请先移除引用','单页已被导航项引用，请先移除引用','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(58,29,0,'en-US','The page is referenced by navigation items; remove those references first','The page is referenced by navigation items; remove those references first','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(59,30,0,'zh-CN','预览目标类型不支持','预览目标类型不支持','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(60,30,0,'en-US','Unsupported preview target','Unsupported preview target','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(61,31,0,'zh-CN','预览模板与目标类型不匹配','预览模板与目标类型不匹配','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(62,31,0,'en-US','Preview template does not match the target type','Preview template does not match the target type','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(63,32,0,'zh-CN','预览票据无效、已过期或已使用','预览票据无效、已过期或已使用','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(64,32,0,'en-US','Preview ticket is invalid, expired or already used','Preview ticket is invalid, expired or already used','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(65,33,0,'zh-CN','站点下还有轮播，不能删除','站点下还有轮播，不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(66,33,0,'en-US','Site still has banners and cannot be deleted','Site still has banners and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(67,34,0,'zh-CN','站点下还有区块，不能删除','站点下还有区块，不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(68,34,0,'en-US','Site still has blocks and cannot be deleted','Site still has blocks and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(69,35,0,'zh-CN','站点下还有自定义模型，不能删除','站点下还有自定义模型，不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(70,35,0,'en-US','Site still has custom models and cannot be deleted','Site still has custom models and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(71,36,0,'zh-CN','站点下还有导航，不能删除','站点下还有导航，不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(72,36,0,'en-US','Site still has navigations and cannot be deleted','Site still has navigations and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(73,37,0,'zh-CN','站点下还有单页，不能删除','站点下还有单页，不能删除','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(74,37,0,'en-US','Site still has pages and cannot be deleted','Site still has pages and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(75,38,0,'zh-CN','；','；','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(76,38,0,'en-US','; ','; ','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(77,39,0,'zh-CN','操作成功','操作成功','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(78,39,0,'en-US','Success','Success','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(79,40,0,'zh-CN','操作失败','操作失败','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(80,40,0,'en-US','Operation failed','Operation failed','1',1,NULL,'2026-11-27 20:17:53.218','2026-11-27 20:17:53.218',NULL),(81,41,0,'zh-CN','请求参数不正确','请求参数不正确','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(82,41,0,'en-US','Invalid request parameters','Invalid request parameters','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(83,42,0,'zh-CN','请先阅读并同意用户协议','请先阅读并同意用户协议','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(84,42,0,'en-US','Please read and accept the user agreement first','Please read and accept the user agreement first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(85,43,0,'zh-CN','开发消息通道未装配','开发消息通道未装配','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(86,43,0,'en-US','The development message channel is not wired','The development message channel is not wired','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(87,44,0,'zh-CN','通道已停用，请先启用再测试发送','通道已停用，请先启用再测试发送','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(88,44,0,'en-US','The channel is disabled. Enable it before sending a test','The channel is disabled. Enable it before sending a test','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(89,45,0,'zh-CN','生产通道尚未实现，请先部署 MemberSmsSender / MemberEmailSender','生产通道尚未实现，请先部署 MemberSmsSender / MemberEmailSender','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(90,45,0,'en-US','The production channel is not implemented yet. Deploy MemberSmsSender / MemberEmailSender first','The production channel is not implemented yet. Deploy MemberSmsSender / MemberEmailSender first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(91,46,0,'zh-CN','验证码发送失败，请稍后重试或联系管理员','验证码发送失败，请稍后重试或联系管理员','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(92,46,0,'en-US','Failed to send the verification code. Try again later or contact the administrator','Failed to send the verification code. Try again later or contact the administrator','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(93,47,0,'zh-CN','换绑验证码已过期，请重新获取','换绑验证码已过期，请重新获取','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(94,47,0,'en-US','The contact change verification code has expired. Request a new one','The contact change verification code has expired. Request a new one','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(95,48,0,'zh-CN','换绑验证码不正确','换绑验证码不正确','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(96,48,0,'en-US','The contact change verification code is incorrect','The contact change verification code is incorrect','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(97,49,0,'zh-CN','验证码与当前填写的新联系方式不一致，请重新获取','验证码与当前填写的新联系方式不一致，请重新获取','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(98,49,0,'en-US','The code was sent to a different contact. Request a new code for this value','The code was sent to a different contact. Request a new code for this value','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(99,50,0,'zh-CN','新联系方式与当前一致，请更换后再试','新联系方式与当前一致，请更换后再试','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(100,50,0,'en-US','The new contact is the same as the current one. Use a different value','The new contact is the same as the current one. Use a different value','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(101,51,0,'zh-CN','该邮箱已被使用','该邮箱已被使用','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(102,51,0,'en-US','This email address is already in use','This email address is already in use','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(103,52,0,'zh-CN','注册必须填写邮箱','注册必须填写邮箱','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(104,52,0,'en-US','Email address is required for registration','Email address is required for registration','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(105,53,0,'zh-CN','不能把账号合并到自身','不能把账号合并到自身','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(106,53,0,'en-US','A member cannot be merged into itself','A member cannot be merged into itself','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(107,54,0,'zh-CN','目标会员不存在或已停用，请先启用目标账号','目标会员不存在或已停用，请先启用目标账号','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(108,54,0,'en-US','The target member does not exist or is disabled. Enable the target account first','The target member does not exist or is disabled. Enable the target account first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(109,55,0,'zh-CN','该手机号已被使用','该手机号已被使用','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(110,55,0,'en-US','This mobile number is already in use','This mobile number is already in use','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(111,56,0,'zh-CN','注册必须填写手机号','注册必须填写手机号','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(112,56,0,'en-US','Mobile number is required for registration','Mobile number is required for registration','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(113,57,0,'zh-CN','当前会员已绑定该平台，请先在个人中心解绑','当前会员已绑定该平台，请先在个人中心解绑','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(114,57,0,'en-US','This provider is already linked to your account. Unlink it from your profile first','This provider is already linked to your account. Unlink it from your profile first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(115,58,0,'zh-CN','该第三方账号已绑定其他会员，请更换账号或先解绑','该第三方账号已绑定其他会员，请更换账号或先解绑','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(116,58,0,'en-US','This social account is already linked to another member. Use a different account or unlink it first','This social account is already linked to another member. Use a different account or unlink it first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(117,59,0,'zh-CN','当前登录会员与待绑定账号不一致，请登录原账号后再绑定','当前登录会员与待绑定账号不一致，请登录原账号后再绑定','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(118,59,0,'en-US','The signed-in member does not match the account to bind. Sign in with the original account first','The signed-in member does not match the account to bind. Sign in with the original account first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(119,60,0,'zh-CN','绑定凭证无效或已过期，请重新发起第三方登录','绑定凭证无效或已过期，请重新发起第三方登录','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(120,60,0,'en-US','The binding ticket is invalid or expired. Start social login again','The binding ticket is invalid or expired. Start social login again','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(121,61,0,'zh-CN','授权码不能为空','授权码不能为空','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(122,61,0,'en-US','Authorization code must not be empty','Authorization code must not be empty','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(123,62,0,'zh-CN','扩展参数必须是合法的 JSON','扩展参数必须是合法的 JSON','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(124,62,0,'en-US','Extra parameters must be valid JSON','Extra parameters must be valid JSON','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(125,63,0,'zh-CN','当前账号尚未设置密码，解绑后可能无法登录；请先设置密码','当前账号尚未设置密码，解绑后可能无法登录；请先设置密码','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(126,63,0,'en-US','This account has no password. Set a password before unlinking the last social login method','This account has no password. Set a password before unlinking the last social login method','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(127,64,0,'zh-CN','该平台不支持小程序登录接口，请从门户发起授权','该平台不支持小程序登录接口，请从门户发起授权','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(128,64,0,'en-US','This provider does not support the mini program login API. Start authorization from the portal','This provider does not support the mini program login API. Start authorization from the portal','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(129,65,0,'zh-CN','该第三方账号尚未绑定会员，请先用账号密码登录后在个人中心绑定','该第三方账号尚未绑定会员，请先用账号密码登录后在个人中心绑定','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(130,65,0,'en-US','This social account is not linked to a member yet. Sign in with your password and link it from your profile first','This social account is not linked to a member yet. Sign in with your password and link it from your profile first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(131,66,0,'zh-CN','该第三方登录平台尚未配置完成，请联系管理员','该第三方登录平台尚未配置完成，请联系管理员','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(132,66,0,'en-US','This social login provider is not fully configured. Contact the administrator','This social login provider is not fully configured. Contact the administrator','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(133,67,0,'zh-CN','登录凭证无效或已过期，请重新发起第三方登录','登录凭证无效或已过期，请重新发起第三方登录','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(134,67,0,'en-US','The login ticket is invalid or expired. Start social login again','The login ticket is invalid or expired. Start social login again','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(135,68,0,'zh-CN','账号已设置密码，请使用修改密码','账号已设置密码，请使用修改密码','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(136,68,0,'en-US','A password is already set. Use the change password form','A password is already set. Use the change password form','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(137,69,0,'zh-CN','该账号尚未设置密码，请先使用第三方登录后在个人中心设置','该账号尚未设置密码，请先使用第三方登录后在个人中心设置','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(138,69,0,'en-US','This account has no password yet. Sign in with social login and set one in your profile','This account has no password yet. Sign in with social login and set one in your profile','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(139,70,0,'zh-CN','验证码已过期，请重新获取','验证码已过期，请重新获取','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(140,70,0,'en-US','Verification code has expired. Please request a new one','Verification code has expired. Please request a new one','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(141,71,0,'zh-CN','验证码不正确','验证码不正确','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(142,71,0,'en-US','Verification code is incorrect','Verification code is incorrect','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(143,72,0,'zh-CN','性别取值不正确','性别取值不正确','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(144,72,0,'en-US','Invalid gender value','Invalid gender value','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(145,73,0,'zh-CN','会员状态只能是启用或停用','会员状态只能是启用或停用','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(146,73,0,'en-US','Member status must be enabled or disabled','Member status must be enabled or disabled','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(147,74,0,'zh-CN','该用户名已被使用','该用户名已被使用','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(148,74,0,'en-US','This username is already in use','This username is already in use','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(149,75,0,'zh-CN','用户名不能包含 @，也不能全部为数字','用户名不能包含 @，也不能全部为数字','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(150,75,0,'en-US','The username must not contain @ or consist only of digits','The username must not contain @ or consist only of digits','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(151,76,0,'zh-CN','昵称不能为空','昵称不能为空','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(152,76,0,'en-US','Nickname must not be empty','Nickname must not be empty','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(153,77,0,'zh-CN','新密码不能与原密码相同','新密码不能与原密码相同','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(154,77,0,'en-US','The new password must differ from the current one','The new password must differ from the current one','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(155,78,0,'zh-CN','未登录或登录已过期','未登录或登录已过期','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(156,78,0,'en-US','Not signed in or session expired','Not signed in or session expired','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(157,79,0,'zh-CN','没有权限执行该操作','没有权限执行该操作','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(158,79,0,'en-US','You do not have permission to perform this action','You do not have permission to perform this action','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(159,80,0,'zh-CN','评论功能已关闭，请先在会员设置中开启','评论功能已关闭，请先在会员设置中开启','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(160,80,0,'en-US','Comments are disabled. Enable them in member settings first','Comments are disabled. Enable them in member settings first','1',1,NULL,'2026-11-27 20:17:53.223','2026-11-27 20:17:53.223',NULL),(161,81,0,'zh-CN','会员功能已关闭，请先在系统参数中开启','会员功能已关闭，请先在系统参数中开启','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(162,81,0,'en-US','Member features are disabled. Enable them in system settings first','Member features are disabled. Enable them in system settings first','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(163,82,0,'zh-CN','第三方登录已关闭，请先在会员设置中开启','第三方登录已关闭，请先在会员设置中开启','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(164,82,0,'en-US','Social login is disabled. Enable it in member settings first','Social login is disabled. Enable it in member settings first','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(165,83,0,'zh-CN','第三方登录平台「{0}」已停用','第三方登录平台「{0}」已停用','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(166,83,0,'en-US','Social login provider \"{0}\" is disabled','Social login provider \"{0}\" is disabled','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(167,84,0,'zh-CN','资源不存在','资源不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(168,84,0,'en-US','Resource not found','Resource not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(169,85,0,'zh-CN','会员不存在或已被删除','会员不存在或已被删除','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(170,85,0,'en-US','Member not found or already deleted','Member not found or already deleted','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(171,86,0,'zh-CN','消息通道配置不存在','消息通道配置不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(172,86,0,'en-US','Channel configuration not found','Channel configuration not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(173,87,0,'zh-CN','评论不存在或已被删除','评论不存在或已被删除','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(174,87,0,'en-US','Comment not found or already deleted','Comment not found or already deleted','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(175,88,0,'zh-CN','第三方账号绑定不存在','第三方账号绑定不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(176,88,0,'en-US','Social account binding not found','Social account binding not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(177,89,0,'zh-CN','第三方登录平台不存在','第三方登录平台不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(178,89,0,'en-US','Social login provider not found','Social login provider not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(179,90,0,'zh-CN','请求方法不支持','请求方法不支持','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(180,90,0,'en-US','Request method not supported','Request method not supported','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(181,91,0,'zh-CN','不支持的请求方法：{0}','不支持的请求方法：{0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(182,91,0,'en-US','Request method not supported: {0}','Request method not supported: {0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(183,92,0,'zh-CN','请求过于频繁，请稍后再试','请求过于频繁，请稍后再试','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(184,92,0,'en-US','Too many requests, please try again later','Too many requests, please try again later','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(185,93,0,'zh-CN','操作过于频繁，请 {0} 分钟后再试','操作过于频繁，请 {0} 分钟后再试','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(186,93,0,'en-US','Too many requests. Please try again in {0} minute(s)','Too many requests. Please try again in {0} minute(s)','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(187,94,0,'zh-CN','用户名或密码错误','用户名或密码错误','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(188,94,0,'en-US','Incorrect username or password','Incorrect username or password','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(189,95,0,'zh-CN','账号已被停用','账号已被停用','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(190,95,0,'en-US','This account has been disabled','This account has been disabled','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(191,96,0,'zh-CN','验证码错误或已过期','验证码错误或已过期','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(192,96,0,'en-US','Captcha is incorrect or has expired','Captcha is incorrect or has expired','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(193,97,0,'zh-CN','验证码错误次数过多，请重新获取','验证码错误次数过多，请重新获取','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(194,97,0,'en-US','Too many incorrect captcha attempts. Please request a new captcha','Too many incorrect captcha attempts. Please request a new captcha','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(195,98,0,'zh-CN','账号已被锁定，请稍后再试','账号已被锁定，请稍后再试','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(196,98,0,'en-US','This account is locked, please try again later','This account is locked, please try again later','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(197,99,0,'zh-CN','密码错误次数过多，账号已锁定 {0} 分钟','密码错误次数过多，账号已锁定 {0} 分钟','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(198,99,0,'en-US','Too many failed attempts. The account is locked for {0} minute(s)','Too many failed attempts. The account is locked for {0} minute(s)','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(199,100,0,'zh-CN','账号已锁定，请 {0} 分钟后再试','账号已锁定，请 {0} 分钟后再试','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(200,100,0,'en-US','Account is locked. Please try again in {0} minute(s)','Account is locked. Please try again in {0} minute(s)','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(201,101,0,'zh-CN','原密码不正确','原密码不正确','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(202,101,0,'en-US','Current password is incorrect','Current password is incorrect','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(203,102,0,'zh-CN','账号尚未设置登录密码','账号尚未设置登录密码','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(204,102,0,'en-US','This account has not set a login password','This account has not set a login password','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(205,103,0,'zh-CN','该账号通过第三方登录创建，尚未设置登录密码。请使用已绑定的第三方账号登录，并在个人中心设置密码','该账号通过第三方登录创建，尚未设置登录密码。请使用已绑定的第三方账号登录，并在个人中心设置密码','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(206,103,0,'en-US','This account was created through social login and has no password yet. Sign in with a linked social account and set a password in your profile','This account was created through social login and has no password yet. Sign in with a linked social account and set a password in your profile','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(207,104,0,'zh-CN','文件上传失败','文件上传失败','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(208,104,0,'en-US','File upload failed','File upload failed','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(209,105,0,'zh-CN','文件类型不允许','文件类型不允许','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(210,105,0,'en-US','File type is not allowed','File type is not allowed','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(211,106,0,'zh-CN','文件超过大小限制','文件超过大小限制','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(212,106,0,'en-US','File exceeds the size limit','File exceeds the size limit','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(213,107,0,'zh-CN','文件不存在','文件不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(214,107,0,'en-US','File not found','File not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(215,108,0,'zh-CN','系统内部错误','系统内部错误','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(216,108,0,'en-US','Internal server error','Internal server error','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(217,109,0,'zh-CN','导入文件为空','导入文件为空','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(218,109,0,'en-US','The import file is empty','The import file is empty','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(219,110,0,'zh-CN','CSV 格式错误：{0}','CSV 格式错误：{0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(220,110,0,'en-US','CSV format error: {0}','CSV format error: {0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(221,111,0,'zh-CN','未知语言：{0}','未知语言：{0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(222,111,0,'en-US','Unknown language: {0}','Unknown language: {0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(223,112,0,'zh-CN','第 {0} 行占位符不一致','第 {0} 行占位符不一致','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(224,112,0,'en-US','Placeholder mismatch on line {0}','Placeholder mismatch on line {0}','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(225,113,0,'zh-CN','翻译键已存在','翻译键已存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(226,113,0,'en-US','Translation key already exists','Translation key already exists','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(227,114,0,'zh-CN','该翻译键已有译文，请停用而不是删除','该翻译键已有译文，请停用而不是删除','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(228,114,0,'en-US','This key already has translations. Disable it instead of deleting it','This key already has translations. Disable it instead of deleting it','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(229,115,0,'zh-CN','翻译键不存在','翻译键不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(230,115,0,'en-US','Translation key not found','Translation key not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(231,116,0,'zh-CN','语言编码已存在','语言编码已存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(232,116,0,'en-US','Language code already exists','Language code already exists','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(233,117,0,'zh-CN','语言编码不合法，请使用 BCP 47 格式，例如 zh-CN','语言编码不合法，请使用 BCP 47 格式，例如 zh-CN','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(234,117,0,'en-US','Invalid language code. Use BCP 47, for example zh-CN','Invalid language code. Use BCP 47, for example zh-CN','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(235,118,0,'zh-CN','默认语言不能停用','默认语言不能停用','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(236,118,0,'en-US','The default language cannot be disabled','The default language cannot be disabled','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(237,119,0,'zh-CN','该语言已有译文或语言包，不能删除','该语言已有译文或语言包，不能删除','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(238,119,0,'en-US','This language already has translations or packs and cannot be deleted','This language already has translations or packs and cannot be deleted','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(239,120,0,'zh-CN','语言不存在','语言不存在','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(240,120,0,'en-US','Language not found','Language not found','1',1,NULL,'2026-11-27 20:17:53.224','2026-11-27 20:17:53.224',NULL),(241,121,0,'zh-CN','没有可发布的译文','没有可发布的译文','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(242,121,0,'en-US','No translation is available to publish','No translation is available to publish','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(243,122,0,'zh-CN','语言包版本不存在','语言包版本不存在','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(244,122,0,'en-US','Language pack version not found','Language pack version not found','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(245,123,0,'zh-CN','语言包快照损坏，无法回滚','语言包快照损坏，无法回滚','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(246,123,0,'en-US','Language pack snapshot is damaged and cannot be rolled back','Language pack snapshot is damaged and cannot be rolled back','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(247,124,0,'zh-CN','目标语言未启用','目标语言未启用','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(248,124,0,'en-US','Target language is disabled','Target language is disabled','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(249,125,0,'zh-CN','占位符与默认文案不一致','占位符与默认文案不一致','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(250,125,0,'en-US','Placeholders do not match the default text','Placeholders do not match the default text','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(251,126,0,'zh-CN','{0} 天','{0} 天','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(252,126,0,'en-US','{0} d','{0} d','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(253,127,0,'zh-CN','{0} 小时','{0} 小时','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(254,127,0,'en-US','{0} h','{0} h','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(255,128,0,'zh-CN','{0} 分','{0} 分','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(256,128,0,'en-US','{0} min','{0} min','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(257,129,0,'zh-CN','0 秒','0 秒','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(258,129,0,'en-US','0 s','0 s','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(259,130,0,'zh-CN','当前未启用 Redis：验证码与会话保存在本机内存，以下为 Caffeine 统计。多实例部署请把 lx.redis.enabled 改为 true。','当前未启用 Redis：验证码与会话保存在本机内存，以下为 Caffeine 统计。多实例部署请把 lx.redis.enabled 改为 true。','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(260,130,0,'en-US','Redis is not enabled: captchas and sessions live in this machine\'s memory and the figures below are Caffeine statistics. Set lx.redis.enabled to true for multi-instance deployments.','Redis is not enabled: captchas and sessions live in this machine\'s memory and the figures below are Caffeine statistics. Set lx.redis.enabled to true for multi-instance deployments.','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(261,131,0,'zh-CN','已启用 Redis：以下为 INFO 摘要与验证码 Key 统计。','已启用 Redis：以下为 INFO 摘要与验证码 Key 统计。','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(262,131,0,'en-US','Redis is enabled: the INFO summary and captcha key statistics are shown below.','Redis is enabled: the INFO summary and captcha key statistics are shown below.','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(263,132,0,'zh-CN','已启用 Redis，但本次读取失败，已降级展示验证码存储统计。','已启用 Redis，但本次读取失败，已降级展示验证码存储统计。','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(264,132,0,'en-US','Redis is enabled but this read failed, so the captcha cache statistics are shown instead.','Redis is enabled but this read failed, so the captcha cache statistics are shown instead.','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(265,133,0,'zh-CN','已启用 Redis，但容器里没有 StringRedisTemplate，已降级展示验证码存储统计。','已启用 Redis，但容器里没有 StringRedisTemplate，已降级展示验证码存储统计。','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(266,133,0,'en-US','Redis is enabled but no StringRedisTemplate is available in the container, so the captcha cache statistics are shown instead.','Redis is enabled but no StringRedisTemplate is available in the container, so the captcha cache statistics are shown instead.','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(267,134,0,'zh-CN','未找到 StringRedisTemplate，请检查 spring-boot-starter-data-redis 依赖','未找到 StringRedisTemplate，请检查 spring-boot-starter-data-redis 依赖','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(268,134,0,'en-US','No StringRedisTemplate found; check the spring-boot-starter-data-redis dependency','No StringRedisTemplate found; check the spring-boot-starter-data-redis dependency','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(269,135,0,'zh-CN','Redis 读取失败: {0}','Redis 读取失败: {0}','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(270,135,0,'en-US','Failed to read Redis: {0}','Failed to read Redis: {0}','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(271,136,0,'zh-CN','头像地址不能超过 255 个字符','头像地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(272,136,0,'en-US','Avatar URL must not exceed 255 characters','Avatar URL must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(273,137,0,'zh-CN','作者署名不能超过 64 个字符','作者署名不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(274,137,0,'en-US','Author name must not exceed 64 characters','Author name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(275,138,0,'zh-CN','请选择定时发布时间','请选择定时发布时间','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(276,138,0,'en-US','Select a schedule time','Select a schedule time','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(277,139,0,'zh-CN','来源不能超过 100 个字符','来源不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(278,139,0,'en-US','Source must not exceed 100 characters','Source must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(279,140,0,'zh-CN','摘要不能超过 500 个字符','摘要不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(280,140,0,'en-US','Summary must not exceed 500 characters','Summary must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(281,141,0,'zh-CN','文章标题不能超过 200 个字符','文章标题不能超过 200 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(282,141,0,'en-US','Article title must not exceed 200 characters','Article title must not exceed 200 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(283,142,0,'zh-CN','文章标题不能为空','文章标题不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(284,142,0,'en-US','Article title must not be empty','Article title must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(285,143,0,'zh-CN','轮播分组不能超过 32 个字符','轮播分组不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(286,143,0,'en-US','Banner group must not exceed 32 characters','Banner group must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(287,144,0,'zh-CN','轮播分组不能为空','轮播分组不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(288,144,0,'en-US','Banner group must not be empty','Banner group must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(289,145,0,'zh-CN','轮播图片地址不能超过 255 个字符','轮播图片地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(290,145,0,'en-US','Banner image must not exceed 255 characters','Banner image must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(291,146,0,'zh-CN','跳转地址不能超过 500 个字符','跳转地址不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(292,146,0,'en-US','Banner link must not exceed 500 characters','Banner link must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(293,147,0,'zh-CN','轮播标题不能超过 200 个字符','轮播标题不能超过 200 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(294,147,0,'en-US','Banner title must not exceed 200 characters','Banner title must not exceed 200 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(295,148,0,'zh-CN','区块标识不能超过 64 个字符','区块标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(296,148,0,'en-US','Block key must not exceed 64 characters','Block key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(297,149,0,'zh-CN','区块标识不能为空','区块标识不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(298,149,0,'en-US','Block key must not be empty','Block key must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(299,150,0,'zh-CN','区块名称不能超过 100 个字符','区块名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(300,150,0,'en-US','Block name must not exceed 100 characters','Block name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(301,151,0,'zh-CN','区块名称不能为空','区块名称不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(302,151,0,'en-US','Block name must not be empty','Block name must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(303,152,0,'zh-CN','请选择区块类型','请选择区块类型','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(304,152,0,'en-US','Select a block type','Select a block type','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(305,153,0,'zh-CN','栏目标识不能超过 64 个字符','栏目标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(306,153,0,'en-US','Category key must not exceed 64 characters','Category key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(307,154,0,'zh-CN','栏目标识不能为空','栏目标识不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(308,154,0,'en-US','Category key must not be empty','Category key must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(309,155,0,'zh-CN','栏目名称不能超过 100 个字符','栏目名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(310,155,0,'en-US','Category name must not exceed 100 characters','Category name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(311,156,0,'zh-CN','栏目名称不能为空','栏目名称不能为空','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(312,156,0,'en-US','Category name must not be empty','Category name must not be empty','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(313,157,0,'zh-CN','请选择栏目','请选择栏目','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(314,157,0,'en-US','Select a category','Select a category','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(315,158,0,'zh-CN','栏目模板不在白名单内','栏目模板不在白名单内','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(316,158,0,'en-US','Category template is not in the allowed list','Category template is not in the allowed list','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(317,159,0,'zh-CN','封面地址不能超过 255 个字符','封面地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(318,159,0,'en-US','Cover must not exceed 255 characters','Cover must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(319,160,0,'zh-CN','默认值不能超过 500 个字符','默认值不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(320,160,0,'en-US','Default value must not exceed 500 characters','Default value must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.228','2026-11-27 20:17:53.228',NULL),(321,161,0,'zh-CN','字段标识不能超过 64 个字符','字段标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(322,161,0,'en-US','Field key must not exceed 64 characters','Field key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(323,162,0,'zh-CN','字段标识不能为空','字段标识不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(324,162,0,'en-US','Field key must not be empty','Field key must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(325,163,0,'zh-CN','字段名称不能超过 100 个字符','字段名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(326,163,0,'en-US','Field name must not exceed 100 characters','Field name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(327,164,0,'zh-CN','字段名称不能为空','字段名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(328,164,0,'en-US','Field name must not be empty','Field name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(329,165,0,'zh-CN','选项配置不能超过 4000 个字符','选项配置不能超过 4000 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(330,165,0,'en-US','Options must not exceed 4000 characters','Options must not exceed 4000 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(331,166,0,'zh-CN','请选择字段类型','请选择字段类型','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(332,166,0,'en-US','Select a field type','Select a field type','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(333,167,0,'zh-CN','校验配置不能超过 1000 个字符','校验配置不能超过 1000 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(334,167,0,'en-US','Validation rules must not exceed 1000 characters','Validation rules must not exceed 1000 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(335,168,0,'zh-CN','图标标识不能超过 64 个字符','图标标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(336,168,0,'en-US','Icon must not exceed 64 characters','Icon must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(337,169,0,'zh-CN','Logo 地址不能超过 255 个字符','Logo 地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(338,169,0,'en-US','Logo must not exceed 255 characters','Logo must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(339,170,0,'zh-CN','模型标识不能超过 64 个字符','模型标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(340,170,0,'en-US','Model key must not exceed 64 characters','Model key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(341,171,0,'zh-CN','模型标识不能为空','模型标识不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(342,171,0,'en-US','Model key must not be empty','Model key must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(343,172,0,'zh-CN','模型名称不能超过 100 个字符','模型名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(344,172,0,'en-US','Model name must not exceed 100 characters','Model name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(345,173,0,'zh-CN','模型名称不能为空','模型名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(346,173,0,'en-US','Model name must not be empty','Model name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(347,174,0,'zh-CN','请选择内容模型','请选择内容模型','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(348,174,0,'en-US','Select a content model','Select a content model','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(349,175,0,'zh-CN','请选择模型类型','请选择模型类型','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(350,175,0,'en-US','Select a model type','Select a model type','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(351,176,0,'zh-CN','导航项名称不能超过 100 个字符','导航项名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(352,176,0,'en-US','Navigation item name must not exceed 100 characters','Navigation item name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(353,177,0,'zh-CN','导航项名称不能为空','导航项名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(354,177,0,'en-US','Navigation item name must not be empty','Navigation item name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(355,178,0,'zh-CN','请选择导航项目标类型','请选择导航项目标类型','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(356,178,0,'en-US','Select a navigation item target type','Select a navigation item target type','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(357,179,0,'zh-CN','导航项地址不能超过 500 个字符','导航项地址不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(358,179,0,'en-US','Navigation item URL must not exceed 500 characters','Navigation item URL must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(359,180,0,'zh-CN','导航标识不能超过 64 个字符','导航标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(360,180,0,'en-US','Navigation key must not exceed 64 characters','Navigation key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(361,181,0,'zh-CN','导航标识不能为空','导航标识不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(362,181,0,'en-US','Navigation key must not be empty','Navigation key must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(363,182,0,'zh-CN','导航名称不能超过 100 个字符','导航名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(364,182,0,'en-US','Navigation name must not exceed 100 characters','Navigation name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(365,183,0,'zh-CN','导航名称不能为空','导航名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(366,183,0,'en-US','Navigation name must not be empty','Navigation name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(367,184,0,'zh-CN','请选择导航位置','请选择导航位置','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(368,184,0,'en-US','Select a navigation position','Select a navigation position','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(369,185,0,'zh-CN','请选择导航','请选择导航','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(370,185,0,'en-US','Select a navigation','Select a navigation','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(371,186,0,'zh-CN','单页标识不能超过 100 个字符','单页标识不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(372,186,0,'en-US','Page key must not exceed 100 characters','Page key must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(373,187,0,'zh-CN','单页标识不能为空','单页标识不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(374,187,0,'en-US','Page key must not be empty','Page key must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(375,188,0,'zh-CN','单页名称不能超过 100 个字符','单页名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(376,188,0,'en-US','Page name must not exceed 100 characters','Page name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(377,189,0,'zh-CN','单页名称不能为空','单页名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(378,189,0,'en-US','Page name must not be empty','Page name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(379,190,0,'zh-CN','单页模板不在白名单内','单页模板不在白名单内','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(380,190,0,'en-US','Page template is not in the allowed list','Page template is not in the allowed list','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(381,191,0,'zh-CN','栏目路径不能超过 255 个字符','栏目路径不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(382,191,0,'en-US','Category path must not exceed 255 characters','Category path must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(383,192,0,'zh-CN','SEO 描述不能超过 500 个字符','SEO 描述不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(384,192,0,'en-US','SEO description must not exceed 500 characters','SEO description must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(385,193,0,'zh-CN','SEO 关键词不能超过 255 个字符','SEO 关键词不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(386,193,0,'en-US','SEO keywords must not exceed 255 characters','SEO keywords must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(387,194,0,'zh-CN','SEO 标题不能超过 200 个字符','SEO 标题不能超过 200 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(388,194,0,'en-US','SEO title must not exceed 200 characters','SEO title must not exceed 200 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(389,195,0,'zh-CN','绑定域名不能超过 255 个字符','绑定域名不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(390,195,0,'en-US','Domain must not exceed 255 characters','Domain must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(391,196,0,'zh-CN','站点标识不能超过 64 个字符','站点标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(392,196,0,'en-US','Site key must not exceed 64 characters','Site key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(393,197,0,'zh-CN','站点标识不能为空','站点标识不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(394,197,0,'en-US','Site key must not be empty','Site key must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(395,198,0,'zh-CN','站点名称不能超过 100 个字符','站点名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(396,198,0,'en-US','Site name must not exceed 100 characters','Site name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(397,199,0,'zh-CN','站点名称不能为空','站点名称不能为空','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(398,199,0,'en-US','Site name must not be empty','Site name must not be empty','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(399,200,0,'zh-CN','请选择站点','请选择站点','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(400,200,0,'en-US','Select a site','Select a site','1',1,NULL,'2026-11-27 20:17:53.229','2026-11-27 20:17:53.229',NULL),(401,201,0,'zh-CN','门户主题不在白名单内','门户主题不在白名单内','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(402,201,0,'en-US','Theme is not in the allowed list','Theme is not in the allowed list','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(403,202,0,'zh-CN','门户主题编码不能超过 32 个字符','门户主题编码不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(404,202,0,'en-US','Theme code must not exceed 32 characters','Theme code must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(405,203,0,'zh-CN','URL 片段不能超过 100 个字符','URL 片段不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(406,203,0,'en-US','URL slug must not exceed 100 characters','URL slug must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(407,204,0,'zh-CN','来源地址不能超过 255 个字符','来源地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(408,204,0,'en-US','Source URL must not exceed 255 characters','Source URL must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(409,205,0,'zh-CN','标签标识不能超过 64 个字符','标签标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(410,205,0,'en-US','Tag key must not exceed 64 characters','Tag key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(411,206,0,'zh-CN','标签标识不能为空','标签标识不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(412,206,0,'en-US','Tag key must not be empty','Tag key must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(413,207,0,'zh-CN','标签名称不能超过 64 个字符','标签名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(414,207,0,'en-US','Tag name must not exceed 64 characters','Tag name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(415,208,0,'zh-CN','标签名称不能为空','标签名称不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(416,208,0,'en-US','Tag name must not be empty','Tag name must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(417,209,0,'zh-CN','模板标识不能超过 100 个字符','模板标识不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(418,209,0,'en-US','Template must not exceed 100 characters','Template must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(419,210,0,'zh-CN','评论内容不能超过 1000 个字符','评论内容不能超过 1000 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(420,210,0,'en-US','Comment must not exceed 1000 characters','Comment must not exceed 1000 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(421,211,0,'zh-CN','评论内容不能为空','评论内容不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(422,211,0,'en-US','Comment must not be empty','Comment must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(423,212,0,'zh-CN','参数 ID 不能为空','参数 ID 不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(424,212,0,'en-US','Setting ID must not be empty','Setting ID must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(425,213,0,'zh-CN','至少提交一个参数','至少提交一个参数','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(426,213,0,'en-US','Submit at least one setting','Submit at least one setting','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(427,214,0,'zh-CN','参数键名不能超过 100 个字符','参数键名不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(428,214,0,'en-US','Setting key must not exceed 100 characters','Setting key must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(429,215,0,'zh-CN','参数键名不能为空','参数键名不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(430,215,0,'en-US','Setting key must not be empty','Setting key must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(431,216,0,'zh-CN','参数名称不能超过 100 个字符','参数名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(432,216,0,'en-US','Setting name must not exceed 100 characters','Setting name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(433,217,0,'zh-CN','参数名称不能为空','参数名称不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(434,217,0,'en-US','Setting name must not be empty','Setting name must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(435,218,0,'zh-CN','控件选项不能超过 1000 个字符','控件选项不能超过 1000 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(436,218,0,'en-US','Options must not exceed 1000 characters','Options must not exceed 1000 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(437,219,0,'zh-CN','控件类型不能超过 20 个字符','控件类型不能超过 20 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(438,219,0,'en-US','Control type must not exceed 20 characters','Control type must not exceed 20 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(439,220,0,'zh-CN','参数键值不能超过 500 个字符','参数键值不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(440,220,0,'en-US','Setting value must not exceed 500 characters','Setting value must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(441,221,0,'zh-CN','参数键值不能为空','参数键值不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(442,221,0,'en-US','Setting value must not be empty','Setting value must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(443,222,0,'zh-CN','部门名称不能超过 64 个字符','部门名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(444,222,0,'en-US','Department name must not exceed 64 characters','Department name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(445,223,0,'zh-CN','部门名称不能为空','部门名称不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(446,223,0,'en-US','Department name must not be empty','Department name must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(447,224,0,'zh-CN','字典标签不能超过 100 个字符','字典标签不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(448,224,0,'en-US','Dictionary label must not exceed 100 characters','Dictionary label must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(449,225,0,'zh-CN','字典标签不能为空','字典标签不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(450,225,0,'en-US','Dictionary label must not be empty','Dictionary label must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(451,226,0,'zh-CN','字典类型不能超过 100 个字符','字典类型不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(452,226,0,'en-US','Dictionary type must not exceed 100 characters','Dictionary type must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(453,227,0,'zh-CN','字典类型不能为空','字典类型不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(454,227,0,'en-US','Dictionary type must not be empty','Dictionary type must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(455,228,0,'zh-CN','字典键值不能超过 100 个字符','字典键值不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(456,228,0,'en-US','Dictionary value must not exceed 100 characters','Dictionary value must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(457,229,0,'zh-CN','字典键值不能为空','字典键值不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(458,229,0,'en-US','Dictionary value must not be empty','Dictionary value must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(459,230,0,'zh-CN','字典名称不能超过 100 个字符','字典名称不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(460,230,0,'en-US','Dictionary name must not exceed 100 characters','Dictionary name must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(461,231,0,'zh-CN','字典名称不能为空','字典名称不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(462,231,0,'en-US','Dictionary name must not be empty','Dictionary name must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(463,232,0,'zh-CN','字典类型编码不能超过 100 个字符','字典类型编码不能超过 100 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(464,232,0,'en-US','Dictionary type must not exceed 100 characters','Dictionary type must not exceed 100 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(465,233,0,'zh-CN','字典类型编码不能为空','字典类型编码不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(466,233,0,'en-US','Dictionary type must not be empty','Dictionary type must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(467,234,0,'zh-CN','邮箱格式不正确','邮箱格式不正确','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(468,234,0,'en-US','Email format is invalid','Email format is invalid','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(469,235,0,'zh-CN','邮箱不能超过 128 个字符','邮箱不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(470,235,0,'en-US','Email must not exceed 128 characters','Email must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(471,236,0,'zh-CN','名称长度不能超过 255 个字符','名称长度不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(472,236,0,'en-US','File name must not exceed 255 characters','File name must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(473,237,0,'zh-CN','文件名称不能为空','文件名称不能为空','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(474,237,0,'en-US','File name must not be empty','File name must not be empty','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(475,238,0,'zh-CN','一次最多导入 50 张表','一次最多导入 50 张表','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(476,238,0,'en-US','At most 50 tables can be imported at once','At most 50 tables can be imported at once','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(477,239,0,'zh-CN','请至少选择一张表','请至少选择一张表','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(478,239,0,'en-US','Select at least one table','Select at least one table','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(479,240,0,'zh-CN','翻译键不能超过 255 个字符','翻译键不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(480,240,0,'en-US','Translation key must not exceed 255 characters','Translation key must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.231','2026-11-27 20:17:53.231',NULL),(481,241,0,'zh-CN','翻译键不能为空','翻译键不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(482,241,0,'en-US','Translation key must not be empty','Translation key must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(483,242,0,'zh-CN','默认文案不能超过 2000 个字符','默认文案不能超过 2000 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(484,242,0,'en-US','Default text must not exceed 2000 characters','Default text must not exceed 2000 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(485,243,0,'zh-CN','命名空间不能为空','命名空间不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(486,243,0,'en-US','Namespace must not be empty','Namespace must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(487,244,0,'zh-CN','语言编码不能超过 16 个字符','语言编码不能超过 16 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(488,244,0,'en-US','Language code must not exceed 16 characters','Language code must not exceed 16 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(489,245,0,'zh-CN','语言编码不能为空','语言编码不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(490,245,0,'en-US','Language code must not be empty','Language code must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(491,246,0,'zh-CN','语言名称不能超过 64 个字符','语言名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(492,246,0,'en-US','Language name must not exceed 64 characters','Language name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(493,247,0,'zh-CN','语言名称不能为空','语言名称不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(494,247,0,'en-US','Language name must not be empty','Language name must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(495,248,0,'zh-CN','译文不能超过 2000 个字符','译文不能超过 2000 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(496,248,0,'en-US','Translation must not exceed 2000 characters','Translation must not exceed 2000 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(497,249,0,'zh-CN','Cron 表达式不能超过 255 个字符','Cron 表达式不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(498,249,0,'en-US','Cron expression must not exceed 255 characters','Cron expression must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(499,250,0,'zh-CN','Cron 表达式不能为空','Cron 表达式不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(500,250,0,'en-US','Cron expression must not be empty','Cron expression must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(501,251,0,'zh-CN','任务分组不能超过 64 个字符','任务分组不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(502,251,0,'en-US','Job group must not exceed 64 characters','Job group must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(503,252,0,'zh-CN','执行器不能超过 500 个字符','执行器不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(504,252,0,'en-US','Invoke target must not exceed 500 characters','Invoke target must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(505,253,0,'zh-CN','执行器不能为空','执行器不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(506,253,0,'en-US','Invoke target must not be empty','Invoke target must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(507,254,0,'zh-CN','任务名称不能超过 64 个字符','任务名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(508,254,0,'en-US','Job name must not exceed 64 characters','Job name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(509,255,0,'zh-CN','任务名称不能为空','任务名称不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(510,255,0,'en-US','Job name must not be empty','Job name must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(511,256,0,'zh-CN','验证码长度不能超过 8 个字符','验证码长度不能超过 8 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(512,256,0,'en-US','Captcha code must not exceed 8 characters','Captcha code must not exceed 8 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(513,257,0,'zh-CN','验证码标识长度不能超过 64 个字符','验证码标识长度不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(514,257,0,'en-US','Captcha key must not exceed 64 characters','Captcha key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(515,258,0,'zh-CN','密码长度不能超过 128 个字符','密码长度不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(516,258,0,'en-US','Password must not exceed 128 characters','Password must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(517,259,0,'zh-CN','密码不能为空','密码不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(518,259,0,'en-US','Password must not be empty','Password must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(519,260,0,'zh-CN','用户名长度不能超过 64 个字符','用户名长度不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(520,260,0,'en-US','Username must not exceed 64 characters','Username must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(521,261,0,'zh-CN','账号不能超过 128 个字符','账号不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(522,261,0,'en-US','Account must not exceed 128 characters','Account must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(523,262,0,'zh-CN','账号不能为空','账号不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(524,262,0,'en-US','Account must not be empty','Account must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(525,263,0,'zh-CN','验证码场景只能是 login / register / reset','验证码场景只能是 login / register / reset','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(526,263,0,'en-US','Captcha scene must be one of login, register or reset','Captcha scene must be one of login, register or reset','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(527,264,0,'zh-CN','AccessKey 不能超过 128 个字符','AccessKey 不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(528,264,0,'en-US','AccessKey must not exceed 128 characters','AccessKey must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(529,265,0,'zh-CN','AccessSecret 不能超过 255 个字符','AccessSecret 不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(530,265,0,'en-US','AccessSecret must not exceed 255 characters','AccessSecret must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(531,266,0,'zh-CN','邮箱格式不正确','邮箱格式不正确','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(532,266,0,'en-US','Email format is invalid','Email format is invalid','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(533,267,0,'zh-CN','服务商地址不能超过 255 个字符','服务商地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(534,267,0,'en-US','Endpoint must not exceed 255 characters','Endpoint must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(535,268,0,'zh-CN','手机号格式不正确','手机号格式不正确','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(536,268,0,'en-US','Mobile number format is invalid','Mobile number format is invalid','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(537,269,0,'zh-CN','通道模式只能是 dev 或 prod','通道模式只能是 dev 或 prod','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(538,269,0,'en-US','Channel mode must be dev or prod','Channel mode must be dev or prod','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(539,270,0,'zh-CN','通道模式不能为空','通道模式不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(540,270,0,'en-US','Channel mode must not be empty','Channel mode must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(541,271,0,'zh-CN','服务商标识不能超过 32 个字符','服务商标识不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(542,271,0,'en-US','Provider must not exceed 32 characters','Provider must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(543,272,0,'zh-CN','备注不能超过 500 个字符','备注不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(544,272,0,'en-US','Remark must not exceed 500 characters','Remark must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(545,273,0,'zh-CN','发送方标识不能超过 128 个字符','发送方标识不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(546,273,0,'en-US','Sender must not exceed 128 characters','Sender must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(547,274,0,'zh-CN','短信签名不能超过 64 个字符','短信签名不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(548,274,0,'en-US','Sign name must not exceed 64 characters','Sign name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(549,275,0,'zh-CN','通道状态只能是 0（启用）或 1（停用）','通道状态只能是 0（启用）或 1（停用）','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(550,275,0,'en-US','Channel status must be 0 (enabled) or 1 (disabled)','Channel status must be 0 (enabled) or 1 (disabled)','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(551,276,0,'zh-CN','通道状态不能为空','通道状态不能为空','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(552,276,0,'en-US','Channel status must not be empty','Channel status must not be empty','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(553,277,0,'zh-CN','测试目标不能超过 128 个字符','测试目标不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(554,277,0,'en-US','Test target must not exceed 128 characters','Test target must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(555,278,0,'zh-CN','请填写测试接收的手机号或邮箱','请填写测试接收的手机号或邮箱','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(556,278,0,'en-US','Please enter the mobile number or email for the test','Please enter the mobile number or email for the test','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(557,279,0,'zh-CN','模板编码不能超过 64 个字符','模板编码不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(558,279,0,'en-US','Template code must not exceed 64 characters','Template code must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(559,280,0,'zh-CN','不支持的通道类型','不支持的通道类型','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(560,280,0,'en-US','Unsupported channel type','Unsupported channel type','1',1,NULL,'2026-11-27 20:17:53.234','2026-11-27 20:17:53.234',NULL),(561,281,0,'zh-CN','换绑验证码不能超过 16 个字符','换绑验证码不能超过 16 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(562,281,0,'en-US','The contact change verification code must not exceed 16 characters','The contact change verification code must not exceed 16 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(563,282,0,'zh-CN','换绑验证码不能为空','换绑验证码不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(564,282,0,'en-US','The contact change verification code must not be empty','The contact change verification code must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(565,283,0,'zh-CN','邮箱格式不正确','邮箱格式不正确','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(566,283,0,'en-US','Email format is invalid','Email format is invalid','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(567,284,0,'zh-CN','手机号格式不正确','手机号格式不正确','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(568,284,0,'en-US','Mobile number format is invalid','Mobile number format is invalid','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(569,285,0,'zh-CN','请先输入当前登录密码','请先输入当前登录密码','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(570,285,0,'en-US','Enter your current login password first','Enter your current login password first','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(571,286,0,'zh-CN','联系方式类型只能是 mobile 或 email','联系方式类型只能是 mobile 或 email','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(572,286,0,'en-US','Contact type must be mobile or email','Contact type must be mobile or email','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(573,287,0,'zh-CN','联系方式类型不能为空','联系方式类型不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(574,287,0,'en-US','Contact type must not be empty','Contact type must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(575,288,0,'zh-CN','新联系方式不能超过 128 个字符','新联系方式不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(576,288,0,'en-US','The new contact must not exceed 128 characters','The new contact must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(577,289,0,'zh-CN','请输入新的手机号或邮箱','请输入新的手机号或邮箱','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(578,289,0,'en-US','Enter the new mobile number or email address','Enter the new mobile number or email address','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(579,290,0,'zh-CN','会员等级不存在或已停用','会员等级不存在或已停用','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(580,290,0,'en-US','Member level does not exist or is disabled','Member level does not exist or is disabled','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(581,291,0,'zh-CN','请选择会员等级','请选择会员等级','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(582,291,0,'en-US','Please select a member level','Please select a member level','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(583,292,0,'zh-CN','请选择要停用的旧账号','请选择要停用的旧账号','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(584,292,0,'en-US','Select the old account to disable','Select the old account to disable','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(585,293,0,'zh-CN','请选择要保留的目标账号','请选择要保留的目标账号','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(586,293,0,'en-US','Select the target account to keep','Select the target account to keep','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(587,294,0,'zh-CN','授权端点不能超过 500 个字符','授权端点不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(588,294,0,'en-US','Authorize endpoint must not exceed 500 characters','Authorize endpoint must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(589,295,0,'zh-CN','ClientId 不能超过 255 个字符','ClientId 不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(590,295,0,'en-US','ClientId must not exceed 255 characters','ClientId must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(591,296,0,'zh-CN','ClientSecret 不能超过 4000 个字符','ClientSecret 不能超过 4000 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(592,296,0,'en-US','ClientSecret must not exceed 4000 characters','ClientSecret must not exceed 4000 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(593,297,0,'zh-CN','授权码长度不能超过 512 个字符','授权码长度不能超过 512 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(594,297,0,'en-US','Authorization code must not exceed 512 characters','Authorization code must not exceed 512 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(595,298,0,'zh-CN','授权码不能为空','授权码不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(596,298,0,'en-US','Authorization code must not be empty','Authorization code must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(597,299,0,'zh-CN','平台名称不能超过 64 个字符','平台名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(598,299,0,'en-US','Display name must not exceed 64 characters','Display name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(599,300,0,'zh-CN','扩展参数不能超过 2000 个字符','扩展参数不能超过 2000 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(600,300,0,'en-US','Extra parameters must not exceed 2000 characters','Extra parameters must not exceed 2000 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(601,301,0,'zh-CN','平台标识不能超过 32 个字符','平台标识不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(602,301,0,'en-US','Provider code must not exceed 32 characters','Provider code must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(603,302,0,'zh-CN','平台标识不能为空','平台标识不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(604,302,0,'en-US','Provider code must not be empty','Provider code must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(605,303,0,'zh-CN','回调地址不能超过 500 个字符','回调地址不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(606,303,0,'en-US','Redirect URI must not exceed 500 characters','Redirect URI must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(607,304,0,'zh-CN','备注不能超过 500 个字符','备注不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(608,304,0,'en-US','Remark must not exceed 500 characters','Remark must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(609,305,0,'zh-CN','Scope 不能超过 500 个字符','Scope 不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(610,305,0,'en-US','Scope must not exceed 500 characters','Scope must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(611,306,0,'zh-CN','平台状态只能是 0（启用）或 1（停用）','平台状态只能是 0（启用）或 1（停用）','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(612,306,0,'en-US','Provider status must be 0 (enabled) or 1 (disabled)','Provider status must be 0 (enabled) or 1 (disabled)','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(613,307,0,'zh-CN','登录凭证长度不能超过 64 个字符','登录凭证长度不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(614,307,0,'en-US','Login ticket must not exceed 64 characters','Login ticket must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(615,308,0,'zh-CN','登录凭证不能为空','登录凭证不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(616,308,0,'en-US','Login ticket must not be empty','Login ticket must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(617,309,0,'zh-CN','Token 端点不能超过 500 个字符','Token 端点不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(618,309,0,'en-US','Token endpoint must not exceed 500 characters','Token endpoint must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(619,310,0,'zh-CN','用户信息端点不能超过 500 个字符','用户信息端点不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(620,310,0,'en-US','User info endpoint must not exceed 500 characters','User info endpoint must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(621,311,0,'zh-CN','验证码不能超过 16 个字符','验证码不能超过 16 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(622,311,0,'en-US','Verification code must not exceed 16 characters','Verification code must not exceed 16 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(623,312,0,'zh-CN','验证码不能为空','验证码不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(624,312,0,'en-US','Verification code must not be empty','Verification code must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(625,313,0,'zh-CN','会员状态只能是 0（正常）或 1（停用）','会员状态只能是 0（正常）或 1（停用）','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(626,313,0,'en-US','Member status must be 0 (normal) or 1 (disabled)','Member status must be 0 (normal) or 1 (disabled)','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(627,314,0,'zh-CN','会员状态不能为空','会员状态不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(628,314,0,'en-US','Member status must not be empty','Member status must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(629,315,0,'zh-CN','组件路径不能超过 255 个字符','组件路径不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(630,315,0,'en-US','Component path must not exceed 255 characters','Component path must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(631,316,0,'zh-CN','菜单标识不能超过 128 个字符','菜单标识不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(632,316,0,'en-US','Menu key must not exceed 128 characters','Menu key must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(633,317,0,'zh-CN','菜单标识不能为空','菜单标识不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(634,317,0,'en-US','Menu key must not be empty','Menu key must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(635,318,0,'zh-CN','模块编码不能超过 32 个字符','模块编码不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(636,318,0,'en-US','Module code must not exceed 32 characters','Module code must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(637,319,0,'zh-CN','菜单名称不能超过 64 个字符','菜单名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(638,319,0,'en-US','Menu name must not exceed 64 characters','Menu name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(639,320,0,'zh-CN','菜单名称不能为空','菜单名称不能为空','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(640,320,0,'en-US','Menu name must not be empty','Menu name must not be empty','1',1,NULL,'2026-11-27 20:17:53.237','2026-11-27 20:17:53.237',NULL),(641,321,0,'zh-CN','路由地址不能超过 255 个字符','路由地址不能超过 255 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(642,321,0,'en-US','Route path must not exceed 255 characters','Route path must not exceed 255 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(643,322,0,'zh-CN','权限码不能超过 128 个字符','权限码不能超过 128 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(644,322,0,'en-US','Permission code must not exceed 128 characters','Permission code must not exceed 128 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(645,323,0,'zh-CN','菜单类型只能是 M / C / F','菜单类型只能是 M / C / F','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(646,323,0,'en-US','Menu type must be one of M, C or F','Menu type must be one of M, C or F','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(647,324,0,'zh-CN','菜单类型不能为空','菜单类型不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(648,324,0,'en-US','Menu type must not be empty','Menu type must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(649,325,0,'zh-CN','昵称不能超过 64 个字符','昵称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(650,325,0,'en-US','Nickname must not exceed 64 characters','Nickname must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(651,326,0,'zh-CN','昵称不能为空','昵称不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(652,326,0,'en-US','Nickname must not be empty','Nickname must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(653,327,0,'zh-CN','公告标题不能超过 200 个字符','公告标题不能超过 200 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(654,327,0,'en-US','Notice title must not exceed 200 characters','Notice title must not exceed 200 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(655,328,0,'zh-CN','公告标题不能为空','公告标题不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(656,328,0,'en-US','Notice title must not be empty','Notice title must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(657,329,0,'zh-CN','请选择要强退的会话','请选择要强退的会话','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(658,329,0,'en-US','Select at least one session to force logout','Select at least one session to force logout','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(659,330,0,'zh-CN','新密码不能为空','新密码不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(660,330,0,'en-US','New password must not be empty','New password must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(661,331,0,'zh-CN','新密码长度必须在 6~64 个字符之间','新密码长度必须在 6~64 个字符之间','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(662,331,0,'en-US','New password must be between 6 and 64 characters','New password must be between 6 and 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(663,332,0,'zh-CN','原密码不能超过 64 个字符','原密码不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(664,332,0,'en-US','Current password must not exceed 64 characters','Current password must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(665,333,0,'zh-CN','原密码不能为空','原密码不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(666,333,0,'en-US','Current password must not be empty','Current password must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(667,334,0,'zh-CN','密码至少 8 位，且必须同时包含英文字母和数字','密码至少 8 位，且必须同时包含英文字母和数字','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(668,334,0,'en-US','Password must be at least 8 characters and contain both letters and digits','Password must be at least 8 characters and contain both letters and digits','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(669,335,0,'zh-CN','密码至少 10 位，且必须同时包含大写字母、小写字母、数字和符号','密码至少 10 位，且必须同时包含大写字母、小写字母、数字和符号','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(670,335,0,'en-US','Password must be at least 10 characters and contain uppercase, lowercase, digits and symbols','Password must be at least 10 characters and contain uppercase, lowercase, digits and symbols','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(671,336,0,'zh-CN','密码长度必须在 6~64 个字符之间','密码长度必须在 6~64 个字符之间','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(672,336,0,'en-US','Password must be between 6 and 64 characters','Password must be between 6 and 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(673,337,0,'zh-CN','手机号不能超过 32 个字符','手机号不能超过 32 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(674,337,0,'en-US','Phone number must not exceed 32 characters','Phone number must not exceed 32 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(675,338,0,'zh-CN','岗位编码不能超过 64 个字符','岗位编码不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(676,338,0,'en-US','Post code must not exceed 64 characters','Post code must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(677,339,0,'zh-CN','岗位编码不能为空','岗位编码不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(678,339,0,'en-US','Post code must not be empty','Post code must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(679,340,0,'zh-CN','岗位名称不能超过 64 个字符','岗位名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(680,340,0,'en-US','Post name must not exceed 64 characters','Post name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(681,341,0,'zh-CN','岗位名称不能为空','岗位名称不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(682,341,0,'en-US','Post name must not be empty','Post name must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(683,342,0,'zh-CN','备注不能超过 500 个字符','备注不能超过 500 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(684,342,0,'en-US','Remark must not exceed 500 characters','Remark must not exceed 500 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(685,343,0,'zh-CN','角色标识不能超过 64 个字符','角色标识不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(686,343,0,'en-US','Role key must not exceed 64 characters','Role key must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(687,344,0,'zh-CN','角色标识不能为空','角色标识不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(688,344,0,'en-US','Role key must not be empty','Role key must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(689,345,0,'zh-CN','角色名称不能超过 64 个字符','角色名称不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(690,345,0,'en-US','Role name must not exceed 64 characters','Role name must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(691,346,0,'zh-CN','角色名称不能为空','角色名称不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(692,346,0,'en-US','Role name must not be empty','Role name must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(693,347,0,'zh-CN','用户名不能超过 64 个字符','用户名不能超过 64 个字符','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(694,347,0,'en-US','Username must not exceed 64 characters','Username must not exceed 64 characters','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(695,348,0,'zh-CN','用户名不能为空','用户名不能为空','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL),(696,348,0,'en-US','Username must not be empty','Username must not be empty','1',1,NULL,'2026-11-27 20:17:53.239','2026-11-27 20:17:53.239',NULL);
/*!40000 ALTER TABLE `i18n_value` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_channel_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_channel_config` (
  `config_id` bigint(20) NOT NULL,
  `channel_type` varchar(16) NOT NULL COMMENT 'sms / email',
  `mode` varchar(16) NOT NULL DEFAULT 'dev' COMMENT 'dev 开发日志 / prod 生产通道',
  `provider` varchar(32) DEFAULT NULL COMMENT 'dev / aliyun / tencent / smtp / custom',
  `endpoint` varchar(255) DEFAULT NULL COMMENT '服务商接口地址；SMTP 可填主机与端口',
  `access_key` varchar(128) DEFAULT NULL,
  `access_secret` varchar(255) DEFAULT NULL COMMENT '只写不读，接口不回显',
  `sign_name` varchar(64) DEFAULT NULL COMMENT '短信签名',
  `template_code` varchar(64) DEFAULT NULL COMMENT '短信模板编码',
  `sender` varchar(128) DEFAULT NULL COMMENT '邮件发件人 / 短信发送方标识',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_mem_channel_type` (`channel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员消息通道配置';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_channel_config` WRITE;
/*!40000 ALTER TABLE `mem_channel_config` DISABLE KEYS */;
INSERT INTO `mem_channel_config` VALUES (1,'sms','dev','dev',NULL,NULL,NULL,NULL,NULL,NULL,'0',NULL,'2026-11-27 20:17:52.529',NULL,NULL,'0',0,'短信通道：默认开发实现只写日志；生产需实现 MemberSmsSender 后切到 prod'),(2,'email','dev','dev',NULL,NULL,NULL,NULL,NULL,NULL,'0',NULL,'2026-11-27 20:17:52.532',NULL,NULL,'0',0,'邮件通道：默认开发实现只写日志；生产需实现 MemberEmailSender 后切到 prod');
/*!40000 ALTER TABLE `mem_channel_config` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_comment` (
  `comment_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `article_id` bigint(20) NOT NULL,
  `member_id` bigint(20) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 待审核 / 1 已通过 / 2 已拒绝',
  `audit_by` bigint(20) DEFAULT NULL,
  `audit_time` datetime(3) DEFAULT NULL,
  `audit_remark` varchar(255) DEFAULT NULL,
  `create_ip` varchar(64) DEFAULT NULL,
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`comment_id`),
  KEY `idx_mem_comment_article` (`tenant_id`,`article_id`,`status`,`create_time`),
  KEY `idx_mem_comment_member` (`tenant_id`,`member_id`,`create_time`),
  KEY `idx_mem_comment_status` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员文章评论';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_comment` WRITE;
/*!40000 ALTER TABLE `mem_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_comment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_contact_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_contact_change` (
  `change_id` bigint(20) NOT NULL,
  `member_id` bigint(20) NOT NULL,
  `contact_type` varchar(16) NOT NULL COMMENT 'mobile / email',
  `old_value` varchar(128) DEFAULT NULL COMMENT '申请时的旧联系方式，仅审计',
  `new_value` varchar(128) NOT NULL COMMENT '待绑定的新手机号 / 邮箱',
  `code_hash` varchar(100) NOT NULL COMMENT '验证码 BCrypt 哈希',
  `expire_time` datetime(3) NOT NULL,
  `used` char(1) NOT NULL DEFAULT '0' COMMENT '0 未使用 / 1 已使用',
  `request_ip` varchar(64) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `use_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`change_id`),
  KEY `idx_mem_contact_change_member` (`member_id`,`contact_type`,`used`,`create_time`),
  KEY `idx_mem_contact_change_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员手机号 / 邮箱换绑验证码';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_contact_change` WRITE;
/*!40000 ALTER TABLE `mem_contact_change` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_contact_change` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_favorite` (
  `favorite_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `member_id` bigint(20) NOT NULL,
  `article_id` bigint(20) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`favorite_id`),
  UNIQUE KEY `uk_mem_favorite_member_article` (`tenant_id`,`member_id`,`article_id`),
  KEY `idx_mem_favorite_member_time` (`tenant_id`,`member_id`,`create_time`),
  KEY `idx_mem_favorite_article` (`tenant_id`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员收藏文章';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_favorite` WRITE;
/*!40000 ALTER TABLE `mem_favorite` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_favorite` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_level`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_level` (
  `level_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `level_code` varchar(32) NOT NULL COMMENT '租户内稳定键，例如 registered / active / vip',
  `level_name` varchar(64) NOT NULL,
  `min_growth` int(11) NOT NULL DEFAULT '0' COMMENT '区间下界，含',
  `max_growth` int(11) DEFAULT NULL COMMENT '区间上界，含；NULL 表示不封顶',
  `benefits_json` varchar(2000) DEFAULT NULL COMMENT '权益 JSON，v1.2 预留',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '升序，越小等级越低',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 启用 / 1 停用',
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`level_id`),
  UNIQUE KEY `uk_mem_level_code` (`tenant_id`,`level_code`),
  KEY `idx_mem_level_sort` (`tenant_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_level` WRITE;
/*!40000 ALTER TABLE `mem_level` DISABLE KEYS */;
INSERT INTO `mem_level` VALUES (1,0,'registered','注册会员',0,999,NULL,1,'0',NULL,'2026-11-27 20:17:52.460',NULL,NULL,'0',0,'注册即获得，默认等级'),(2,0,'active','活跃会员',1000,4999,NULL,2,'0',NULL,'2026-11-27 20:17:52.463',NULL,NULL,'0',0,'成长值达到 1000 后升级'),(3,0,'vip','VIP 会员',5000,NULL,NULL,3,'0',NULL,'2026-11-27 20:17:52.464',NULL,NULL,'0',0,'成长值达到 5000 后升级，不封顶');
/*!40000 ALTER TABLE `mem_level` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_like` (
  `like_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `member_id` bigint(20) NOT NULL,
  `article_id` bigint(20) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_mem_like_member_article` (`tenant_id`,`member_id`,`article_id`),
  KEY `idx_mem_like_member_time` (`tenant_id`,`member_id`,`create_time`),
  KEY `idx_mem_like_article` (`tenant_id`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员点赞文章';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_like` WRITE;
/*!40000 ALTER TABLE `mem_like` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_like` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_merge_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_merge_log` (
  `merge_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `source_member_id` bigint(20) NOT NULL,
  `target_member_id` bigint(20) NOT NULL,
  `source_username` varchar(64) DEFAULT NULL,
  `target_username` varchar(64) DEFAULT NULL,
  `favorite_moved` int(11) NOT NULL DEFAULT '0',
  `favorite_skipped` int(11) NOT NULL DEFAULT '0',
  `like_moved` int(11) NOT NULL DEFAULT '0',
  `like_skipped` int(11) NOT NULL DEFAULT '0',
  `comment_moved` int(11) NOT NULL DEFAULT '0',
  `social_moved` int(11) NOT NULL DEFAULT '0',
  `social_skipped` int(11) NOT NULL DEFAULT '0',
  `operator_id` bigint(20) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  PRIMARY KEY (`merge_id`),
  KEY `idx_mem_merge_target` (`tenant_id`,`target_member_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员账号合并审计';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_merge_log` WRITE;
/*!40000 ALTER TABLE `mem_merge_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_merge_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_oauth_provider`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_oauth_provider` (
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `provider` varchar(32) NOT NULL COMMENT '稳定编码，例如 wechat_open / github',
  `display_name` varchar(64) NOT NULL,
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '0 启用 / 1 停用',
  `client_id` varchar(255) DEFAULT NULL,
  `client_secret` text COMMENT 'ClientSecret / AppSecret / 私钥；接口只写不读',
  `redirect_uri` varchar(500) DEFAULT NULL COMMENT '留空时按门户地址自动生成回调',
  `scope` varchar(500) DEFAULT NULL COMMENT '留空使用内置默认 scope',
  `authorize_url` varchar(500) DEFAULT NULL COMMENT '留空使用内置授权端点',
  `token_url` varchar(500) DEFAULT NULL COMMENT '留空使用内置换 Token 端点',
  `user_info_url` varchar(500) DEFAULT NULL COMMENT '留空使用内置用户信息端点',
  `extra_json` varchar(2000) DEFAULT NULL COMMENT '预留：PKCE / 团队 ID / 密钥 ID 等服务商差异参数',
  `sort` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`tenant_id`,`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员第三方登录服务商配置';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_oauth_provider` WRITE;
/*!40000 ALTER TABLE `mem_oauth_provider` DISABLE KEYS */;
INSERT INTO `mem_oauth_provider` VALUES (0,'alipay','支付宝','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,50,NULL,'2026-11-27 20:17:52.623',NULL),(0,'amazon','Amazon','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,230,NULL,'2026-11-27 20:17:52.623',NULL),(0,'apple','Apple','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,150,NULL,'2026-11-27 20:17:52.623',NULL),(0,'dingtalk','钉钉','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,70,NULL,'2026-11-27 20:17:52.623',NULL),(0,'discord','Discord','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,190,NULL,'2026-11-27 20:17:52.623',NULL),(0,'douyin','抖音','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,60,NULL,'2026-11-27 20:17:52.623',NULL),(0,'facebook','Facebook','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,160,NULL,'2026-11-27 20:17:52.623',NULL),(0,'gitee','Gitee','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,80,NULL,'2026-11-27 20:17:52.623',NULL),(0,'github','GitHub','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,110,NULL,'2026-11-27 20:17:52.623',NULL),(0,'gitlab','GitLab','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,120,NULL,'2026-11-27 20:17:52.623',NULL),(0,'google','Google','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,130,NULL,'2026-11-27 20:17:52.623',NULL),(0,'kakao','Kakao','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,210,NULL,'2026-11-27 20:17:52.623',NULL),(0,'line','LINE','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,200,NULL,'2026-11-27 20:17:52.623',NULL),(0,'linkedin','LinkedIn','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,180,NULL,'2026-11-27 20:17:52.623',NULL),(0,'microsoft','Microsoft','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,140,NULL,'2026-11-27 20:17:52.623',NULL),(0,'naver','Naver','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,220,NULL,'2026-11-27 20:17:52.623',NULL),(0,'qq','QQ','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,30,NULL,'2026-11-27 20:17:52.623',NULL),(0,'reddit','Reddit','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,250,NULL,'2026-11-27 20:17:52.623',NULL),(0,'slack','Slack','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,260,NULL,'2026-11-27 20:17:52.623',NULL),(0,'spotify','Spotify','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,280,NULL,'2026-11-27 20:17:52.623',NULL),(0,'twitch','Twitch','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,270,NULL,'2026-11-27 20:17:52.623',NULL),(0,'wechat_mini','微信小程序','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,25,NULL,'2026-11-27 20:17:52.652',NULL),(0,'wechat_mp','微信公众号','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,20,NULL,'2026-11-27 20:17:52.623',NULL),(0,'wechat_open','微信开放平台','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10,NULL,'2026-11-27 20:17:52.623',NULL),(0,'weibo','新浪微博','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,40,NULL,'2026-11-27 20:17:52.623',NULL),(0,'x','X (Twitter)','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,170,NULL,'2026-11-27 20:17:52.623',NULL),(0,'yahoo','Yahoo','1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,240,NULL,'2026-11-27 20:17:52.623',NULL);
/*!40000 ALTER TABLE `mem_oauth_provider` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_password_reset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_password_reset` (
  `reset_id` bigint(20) NOT NULL,
  `member_id` bigint(20) NOT NULL,
  `account` varchar(128) DEFAULT NULL COMMENT '申请时使用的账号，仅用于排查',
  `code_hash` varchar(100) NOT NULL COMMENT '验证码 BCrypt 哈希',
  `expire_time` datetime(3) NOT NULL,
  `used` char(1) NOT NULL DEFAULT '0' COMMENT '0 未使用 / 1 已使用',
  `create_time` datetime(3) NOT NULL,
  `use_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`reset_id`),
  KEY `idx_mem_password_reset_member` (`member_id`,`used`,`create_time`),
  KEY `idx_mem_password_reset_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员找回密码验证码';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_password_reset` WRITE;
/*!40000 ALTER TABLE `mem_password_reset` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_password_reset` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_user` (
  `member_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `username` varchar(64) NOT NULL COMMENT '登录账号，租户内应用层唯一',
  `nickname` varchar(64) NOT NULL,
  `password` varchar(100) NOT NULL COMMENT 'BCrypt 哈希，接口只写不读',
  `password_set` char(1) NOT NULL DEFAULT '0' COMMENT '0 未设置（第三方自动建号）/ 1 已设置',
  `mobile` varchar(32) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL COMMENT 'fileId 或 http(s) 外链，不建 mem_file',
  `sex` char(1) NOT NULL DEFAULT '2' COMMENT '0 男 / 1 女 / 2 未知',
  `level_id` bigint(20) DEFAULT NULL,
  `points` int(11) NOT NULL DEFAULT '0' COMMENT '可消耗积分',
  `growth` int(11) NOT NULL DEFAULT '0' COMMENT '成长值，只增不减',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0 正常 / 1 停用',
  `register_channel` varchar(20) NOT NULL DEFAULT 'web' COMMENT 'web / h5 / app / admin',
  `register_ip` varchar(64) DEFAULT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `login_time` datetime(3) DEFAULT NULL,
  `login_fail_count` int(11) NOT NULL DEFAULT '0',
  `lock_time` datetime(3) DEFAULT NULL COMMENT '锁定截止时间，NULL 表示未锁定',
  `last_active_time` datetime(3) DEFAULT NULL,
  `create_by` bigint(20) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_by` bigint(20) DEFAULT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  `version` int(11) NOT NULL DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `username_key` varchar(64) GENERATED ALWAYS AS (if((`del_flag` = '0'),`username`,NULL)) STORED COMMENT '未删除行的用户名，用于唯一约束',
  `mobile_key` varchar(32) GENERATED ALWAYS AS (if(((`del_flag` = '0') and (`mobile` is not null) and (`mobile` <> '')),`mobile`,NULL)) STORED COMMENT '未删除行且非空的手机号，用于唯一约束',
  `email_key` varchar(128) GENERATED ALWAYS AS (if(((`del_flag` = '0') and (`email` is not null) and (`email` <> '')),`email`,NULL)) STORED COMMENT '未删除行且非空的邮箱，用于唯一约束',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_mem_user_username` (`tenant_id`,`username_key`),
  UNIQUE KEY `uk_mem_user_mobile` (`tenant_id`,`mobile_key`),
  UNIQUE KEY `uk_mem_user_email` (`tenant_id`,`email_key`),
  KEY `idx_mem_user_level` (`tenant_id`,`level_id`),
  KEY `idx_mem_user_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台会员账号';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_user` WRITE;
/*!40000 ALTER TABLE `mem_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_user` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `mem_user_social`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mem_user_social` (
  `social_id` bigint(20) NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0',
  `member_id` bigint(20) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `open_id` varchar(128) NOT NULL,
  `union_id` varchar(128) DEFAULT NULL,
  `nickname` varchar(64) DEFAULT NULL COMMENT '首次授权时的昵称快照',
  `avatar` varchar(255) DEFAULT NULL COMMENT '首次授权时的头像快照',
  `raw_data` varchar(4000) DEFAULT NULL COMMENT '最近一次用户信息 JSON，仅排障用，接口不下发',
  `last_login_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL COMMENT '平台返回并确认已验证的邮箱',
  `mobile` varchar(32) DEFAULT NULL COMMENT '平台返回并确认已验证的手机号',
  `email_verified` char(1) NOT NULL DEFAULT '0' COMMENT '0 未验证 / 1 已验证',
  `mobile_verified` char(1) NOT NULL DEFAULT '0' COMMENT '0 未验证 / 1 已验证',
  PRIMARY KEY (`social_id`),
  UNIQUE KEY `uk_mem_user_social_open` (`tenant_id`,`provider`,`open_id`),
  UNIQUE KEY `uk_mem_user_social_member` (`tenant_id`,`member_id`,`provider`),
  KEY `idx_mem_user_social_member` (`tenant_id`,`member_id`),
  KEY `idx_mem_user_social_union` (`tenant_id`,`provider`,`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员第三方账号绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `mem_user_social` WRITE;
/*!40000 ALTER TABLE `mem_user_social` DISABLE KEYS */;
/*!40000 ALTER TABLE `mem_user_social` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'登录验证码开关','sys.account.captchaEnabled','true','switch','true:false','Y',NULL,'2026-11-27 20:17:51.642',NULL,NULL,'0',0,'开关参数：true 开启验证码，false 关闭验证码'),(2,'日志保留天数','sys.log.keepDays','180','number','1:3650','Y',NULL,'2026-11-27 20:17:51.642',NULL,NULL,'0',0,'超过天数的操作日志 / 登录日志由定时任务清理（1~3650 天）'),(3,'单文件上传上限','sys.file.uploadMaxMb','10','number','1:20','Y',NULL,'2026-11-27 20:17:51.642',NULL,NULL,'0',0,'单位 MB；受容器 multipart 上限 20MB 约束，库里只能调小'),(4,'富文本编辑器','sys.editor.type','wangeditor-next','select','wangeditor-next:wangEditor Next,ueditor-plus:UEditor Plus','Y',NULL,'2026-11-27 20:17:51.713',NULL,NULL,'0',0,'平台默认富文本编辑器；用户可在编辑器内临时切换'),(5,'任务日志保留天数','sys.job.logKeepDays','30','number','1:3650','Y',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,'定时任务日志超过该天数由 jobLogClean 执行器清理（1~3650 天）'),(6,'代码生成作者','sys.gen.author','lingxiao','text',NULL,'Y',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,'代码生成物头部注释里的作者；导入表时写入 gen_table.function_author'),(7,'登录失败锁定阈值','sys.account.maxRetryCount','5','number','3:10','Y',NULL,'2026-11-27 20:17:52.107',NULL,'2026-11-27 20:17:52.146','0',0,'连续登录失败达到该次数后锁定账号（3~10 次）'),(8,'账号锁定时长','sys.account.lockMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.107',NULL,NULL,'0',0,'账号锁定分钟数，到期自动解锁（1~1440 分钟）'),(9,'密码复杂度策略','sys.account.passwordPolicy','basic','select','off:不校验,basic:至少 8 位且含字母和数字,strong:至少 10 位且含大写/小写/数字/符号','Y',NULL,'2026-11-27 20:17:52.107',NULL,NULL,'0',0,'新增用户、管理员重置密码、个人中心改密时生效'),(10,'允许多端登录','sys.account.multiDeviceLogin','false','switch','true:false','Y',NULL,'2026-11-27 20:17:52.137',NULL,NULL,'0',0,'关闭时新登录会踢掉该账号的旧会话（默认关闭）'),(11,'会员验证码开关','mem.security.captchaEnabled','true','switch','true:false','Y',NULL,'2026-11-27 20:17:52.494',NULL,NULL,'0',0,'会员注册 / 登录 / 找回密码是否要求图形验证码；同时受 lx.captcha.enabled 总闸控制'),(12,'会员密码策略','mem.security.passwordPolicy','basic','select','off:不校验,basic:至少8位且含字母和数字,strong:至少10位且含大小写数字和符号','Y',NULL,'2026-11-27 20:17:52.495',NULL,NULL,'0',0,'只在注册 / 找回 / 改密 / 后台重置时校验；登录不校验，避免策略调整挡住老用户'),(13,'会员登录失败阈值','mem.account.maxRetryCount','5','number','3:10','Y',NULL,'2026-11-27 20:17:52.496',NULL,NULL,'0',0,'连续失败达到该次数锁定会员账号（3~10 次）'),(14,'会员账号锁定时长','mem.account.lockMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.496',NULL,NULL,'0',0,'锁定后多少分钟自动解锁（1~1440 分钟）'),(15,'会员允许多端登录','mem.account.multiDeviceLogin','false','switch','true:false','Y',NULL,'2026-11-27 20:17:52.496',NULL,NULL,'0',0,'关闭时新登录踢掉该会员的全部旧会话；开启后允许多端并存'),(16,'会员注册手机号必填','mem.register.mobileRequired','false','switch','true:false','Y',NULL,'2026-11-27 20:17:52.496',NULL,NULL,'0',0,'开启后注册必须填写手机号；关闭时可只填用户名和密码'),(17,'会员注册邮箱必填','mem.register.emailRequired','false','switch','true:false','Y',NULL,'2026-11-27 20:17:52.497',NULL,NULL,'0',0,'开启后注册必须填写邮箱；关闭时邮箱选填'),(18,'找回密码验证码有效期','mem.register.resetCodeExpireMinutes','10','number','5:60','Y',NULL,'2026-11-27 20:17:52.497',NULL,NULL,'0',0,'会员找回密码验证码有效分钟数（5~60 分钟）'),(19,'会员验证码错误次数上限','mem.security.captchaMaxAttempts','3','number','1:10','Y',NULL,'2026-11-27 20:17:52.507',NULL,NULL,'0',0,'同一图形验证码允许输错的次数，达到上限后验证码立即作废'),(20,'会员注册限流次数','mem.register.rateLimitCount','5','number','1:100','Y',NULL,'2026-11-27 20:17:52.507',NULL,NULL,'0',0,'同一用户名 / IP 在窗口内允许的注册请求数；IP 阈值自动放宽为账号阈值的 10 倍'),(21,'会员注册限流窗口','mem.register.rateLimitMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.508',NULL,NULL,'0',0,'会员注册限流窗口分钟数'),(22,'会员登录限流次数','mem.account.loginRateLimitCount','30','number','1:1000','Y',NULL,'2026-11-27 20:17:52.508',NULL,NULL,'0',0,'同一账号 / IP 在窗口内允许的登录请求数；与登录失败锁定相互独立'),(23,'会员登录限流窗口','mem.account.loginRateLimitMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.508',NULL,NULL,'0',0,'会员登录限流窗口分钟数'),(24,'会员找回密码限流次数','mem.register.resetRateLimitCount','5','number','1:100','Y',NULL,'2026-11-27 20:17:52.508',NULL,NULL,'0',0,'同一账号 / IP 在窗口内允许的找回密码与重置密码请求数，IP 阈值自动放宽为账号阈值的 10 倍'),(25,'会员找回密码限流窗口','mem.register.resetRateLimitMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.509',NULL,NULL,'0',0,'会员找回密码与重置密码限流窗口分钟数'),(26,'会员功能开关','mem.enabled','true','switch','true:false','Y',NULL,'2026-11-27 20:17:52.541',NULL,NULL,'0',0,'关闭后门户会员注册 / 登录 / 个人中心不可用，会员管理菜单与后台会员接口同时关闭；改完立即生效，不需要重启'),(27,'评论功能开关','mem.comment.enabled','true','switch','true:false','Y',NULL,'2026-11-27 20:17:52.603',NULL,NULL,'0',0,'关闭后门户评论读取与提交接口返回“评论功能已关闭”；后台评论审核仍可查看与清理历史评论'),(28,'第三方登录总开关','mem.oauth.enabled','false','switch','true:false','Y',NULL,'2026-11-27 20:17:52.642',NULL,NULL,'0',0,'关闭后门户登录 / 注册页不展示第三方登录入口，授权与回调接口返回“第三方登录已关闭”；已绑定关系保留'),(29,'第三方登录自动建号','mem.oauth.autoRegister','true','switch','true:false','Y',NULL,'2026-11-27 20:17:52.642',NULL,NULL,'0',0,'开启后首次第三方授权可直接创建会员账号；关闭时必须先登录再绑定，未绑定的授权返回 not_bound'),(30,'门户对外地址','mem.oauth.portalBaseUrl','http://127.0.0.1:3000','input',NULL,'Y',NULL,'2026-11-27 20:17:52.642',NULL,NULL,'0',0,'第三方登录回调后跳回门户的根地址，例如 https://www.example.com；生产环境必须改成真实域名'),(31,'换绑验证码有效期','mem.account.contactCodeExpireMinutes','10','number','5:60','Y',NULL,'2026-11-27 20:17:52.826',NULL,NULL,'0',0,'更换手机号 / 邮箱的验证码有效分钟数（5~60 分钟）'),(32,'换绑验证码限流次数','mem.account.contactRateLimitCount','5','number','1:100','Y',NULL,'2026-11-27 20:17:52.826',NULL,NULL,'0',0,'同一会员 / IP 在窗口内允许的换绑发码与确认次数；IP 阈值自动放宽为会员阈值的 10 倍'),(33,'换绑验证码限流窗口','mem.account.contactRateLimitMinutes','10','number','1:1440','Y',NULL,'2026-11-27 20:17:52.826',NULL,NULL,'0',0,'会员换绑手机号 / 邮箱的限流窗口分钟数');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'凌霄科技',1,'管理员','13800000000','admin@lingxiao.local','0',NULL,'2026-11-27 20:17:51.613',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_dept_closure` WRITE;
/*!40000 ALTER TABLE `sys_dept_closure` DISABLE KEYS */;
INSERT INTO `sys_dept_closure` VALUES (1,1,0);
/*!40000 ALTER TABLE `sys_dept_closure` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'正常','0','sys_common_status',NULL,'primary','Y','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(2,2,'停用','1','sys_common_status',NULL,'danger','N','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(3,1,'男','0','sys_user_sex',NULL,NULL,'Y','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(4,2,'女','1','sys_user_sex',NULL,NULL,'N','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(5,3,'未知','2','sys_user_sex',NULL,'info','N','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(6,1,'是','Y','sys_yes_no',NULL,'primary','Y','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL),(7,2,'否','N','sys_yes_no',NULL,'danger','N','0',NULL,'2026-11-27 20:17:51.639',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'系统开关','sys_common_status','0',NULL,'2026-11-27 20:17:51.637',NULL,NULL,'0',0,'0正常 1停用'),(2,'用户性别','sys_user_sex','0',NULL,'2026-11-27 20:17:51.637',NULL,NULL,'0',0,'0男 1女 2未知'),(3,'是否','sys_yes_no','0',NULL,'2026-11-27 20:17:51.637',NULL,NULL,'0',0,'Y是 N否');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_file` WRITE;
/*!40000 ALTER TABLE `sys_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_file_name_log` WRITE;
/*!40000 ALTER TABLE `sys_file_name_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file_name_log` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'清理过期日志','SYSTEM','systemLogClean','0 0 2 * * ?','3','1','1',NULL,'2026-11-27 20:17:51.649',NULL,NULL,'0',0,'每天 02:00 清理超过 sys.log.keepDays 天的登录日志 / 操作日志；默认暂停');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_job_lock` WRITE;
/*!40000 ALTER TABLE `sys_job_lock` DISABLE KEYS */;
INSERT INTO `sys_job_lock` VALUES (1,NULL,NULL);
/*!40000 ALTER TABLE `sys_job_lock` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'system',0,'系统管理','M','/system',NULL,NULL,'setting',1,'0','0','system',NULL,'2026-11-27 20:17:51.628',NULL,NULL,'0',0,NULL),(2,'monitor',0,'系统监控','M','/monitor',NULL,NULL,'monitor',2,'0','0','system',NULL,'2026-11-27 20:17:51.628',NULL,NULL,'0',0,NULL),(3,'tool',0,'系统工具','M','/tool',NULL,NULL,'tool',3,'0','0','system',NULL,'2026-11-27 20:17:51.628',NULL,NULL,'0',0,NULL),(4,'profile',0,'个人中心','C','/profile','profile/index','platform:profile:view','user',4,'0','0','system',NULL,'2026-11-27 20:17:51.628',NULL,NULL,'0',0,NULL),(5,'system:user',1,'用户管理','C','/system/user','system/user/index','platform:system:user:list','user',1,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(6,'system:role',1,'角色管理','C','/system/role','system/role/index','platform:system:role:list','peoples',2,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(7,'system:menu',1,'菜单管理','C','/system/menu','system/menu/index','platform:system:menu:list','tree-table',3,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(8,'system:dept',1,'部门管理','C','/system/dept','system/dept/index','platform:system:dept:list','tree',4,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(9,'system:post',1,'岗位管理','C','/system/post','system/post/index','platform:system:post:list','post',5,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(10,'system:dict',1,'字典管理','C','/system/dict','system/dict/index','platform:system:dict:list','dict',6,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(11,'system:config',1,'参数设置','C','/system/config','system/config/index','platform:system:config:list','edit',7,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(12,'system:notice',1,'公告管理','C','/system/notice','system/notice/index','platform:system:notice:list','message',8,'0','0','system',NULL,'2026-11-27 20:17:51.630',NULL,NULL,'0',0,NULL),(13,'monitor:online',2,'在线用户','C','/monitor/online','monitor/online/index','platform:monitor:online:list','online',1,'0','0','system',NULL,'2026-11-27 20:17:51.631',NULL,NULL,'0',0,NULL),(14,'monitor:login-log',2,'登录日志','C','/monitor/login-log','monitor/loginlog/index','platform:monitor:loginlog:list','logininfor',2,'0','0','system',NULL,'2026-11-27 20:17:51.631',NULL,NULL,'0',0,NULL),(15,'monitor:oper-log',2,'操作日志','C','/monitor/oper-log','monitor/operlog/index','platform:monitor:operlog:list','form',3,'0','0','system',NULL,'2026-11-27 20:17:51.631',NULL,NULL,'0',0,NULL),(16,'monitor:server',2,'服务监控','C','/monitor/server','monitor/server/index','platform:monitor:server:list','server',4,'0','0','system',NULL,'2026-11-27 20:17:51.631',NULL,NULL,'0',0,NULL),(17,'monitor:cache',2,'缓存监控','C','/monitor/cache','monitor/cache/index','platform:monitor:cache:list','redis',5,'0','0','system',NULL,'2026-11-27 20:17:51.631',NULL,NULL,'0',0,NULL),(18,'tool:file',3,'文件中心','C','/tool/file','tool/file/index','platform:file:list','upload',1,'0','0','file',NULL,'2026-11-27 20:17:51.632',NULL,NULL,'0',0,NULL),(21,'tool:job',3,'定时任务','C','/tool/job','tool/job/index','platform:monitor:job:list','time-range',4,'0','0','job',NULL,'2026-11-27 20:17:51.632',NULL,NULL,'0',0,NULL),(22,'tool:gen',3,'代码生成','C','/tool/gen','tool/gen/index','platform:tool:gen:list','code',5,'0','0','generator',NULL,'2026-11-27 20:17:51.632',NULL,NULL,'0',0,NULL),(23,'tool:api-doc',3,'接口文档','C','/tool/api-doc','tool/apidoc/index','platform:tool:apidoc:list','documentation',6,'0','0','system',NULL,'2026-11-27 20:17:51.632',NULL,NULL,'0',0,NULL),(101,'system:user:add',5,'用户新增','F',NULL,NULL,'platform:system:user:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(102,'system:user:edit',5,'用户修改','F',NULL,NULL,'platform:system:user:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(103,'system:user:remove',5,'用户删除','F',NULL,NULL,'platform:system:user:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(104,'system:user:resetPwd',5,'重置密码','F',NULL,NULL,'platform:system:user:resetPwd',NULL,4,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(105,'system:user:unlock',5,'解锁账号','F',NULL,NULL,'platform:system:user:unlock',NULL,5,'0','0','system',NULL,'2026-11-27 20:17:52.107',NULL,NULL,'0',0,NULL),(201,'system:role:add',6,'角色新增','F',NULL,NULL,'platform:system:role:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(202,'system:role:edit',6,'角色修改','F',NULL,NULL,'platform:system:role:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(203,'system:role:remove',6,'角色删除','F',NULL,NULL,'platform:system:role:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.698',NULL,NULL,'0',0,NULL),(301,'system:menu:add',7,'菜单新增','F',NULL,NULL,'platform:system:menu:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(302,'system:menu:edit',7,'菜单修改','F',NULL,NULL,'platform:system:menu:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(303,'system:menu:remove',7,'菜单删除','F',NULL,NULL,'platform:system:menu:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(401,'system:dept:add',8,'部门新增','F',NULL,NULL,'platform:system:dept:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(402,'system:dept:edit',8,'部门修改','F',NULL,NULL,'platform:system:dept:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(403,'system:dept:remove',8,'部门删除','F',NULL,NULL,'platform:system:dept:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(501,'system:post:add',9,'岗位新增','F',NULL,NULL,'platform:system:post:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(502,'system:post:edit',9,'岗位修改','F',NULL,NULL,'platform:system:post:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(503,'system:post:remove',9,'岗位删除','F',NULL,NULL,'platform:system:post:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.699',NULL,NULL,'0',0,NULL),(601,'system:dict:add',10,'字典新增','F',NULL,NULL,'platform:system:dict:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(602,'system:dict:edit',10,'字典修改','F',NULL,NULL,'platform:system:dict:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(603,'system:dict:remove',10,'字典删除','F',NULL,NULL,'platform:system:dict:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(701,'system:config:add',11,'参数新增','F',NULL,NULL,'platform:system:config:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(702,'system:config:edit',11,'参数修改','F',NULL,NULL,'platform:system:config:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(703,'system:config:remove',11,'参数删除','F',NULL,NULL,'platform:system:config:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.700',NULL,NULL,'0',0,NULL),(801,'system:notice:add',12,'公告新增','F',NULL,NULL,'platform:system:notice:add',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.701',NULL,NULL,'0',0,NULL),(802,'system:notice:edit',12,'公告修改','F',NULL,NULL,'platform:system:notice:edit',NULL,2,'0','0','system',NULL,'2026-11-27 20:17:51.701',NULL,NULL,'0',0,NULL),(803,'system:notice:remove',12,'公告删除','F',NULL,NULL,'platform:system:notice:remove',NULL,3,'0','0','system',NULL,'2026-11-27 20:17:51.701',NULL,NULL,'0',0,NULL),(1301,'monitor:online:kickout',13,'强制退出','F',NULL,NULL,'platform:monitor:online:kickout',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.824',NULL,NULL,'0',0,NULL),(1401,'monitor:login-log:clean',14,'清空日志','F',NULL,NULL,'platform:monitor:loginlog:clean',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.741',NULL,NULL,'0',0,NULL),(1501,'monitor:oper-log:clean',15,'清空日志','F',NULL,NULL,'platform:monitor:operlog:clean',NULL,1,'0','0','system',NULL,'2026-11-27 20:17:51.742',NULL,NULL,'0',0,NULL),(1801,'file:upload',18,'文件上传','F',NULL,NULL,'platform:file:upload',NULL,1,'0','0','file',NULL,'2026-11-27 20:17:51.723',NULL,NULL,'0',0,NULL),(1802,'file:rename',18,'文件改名','F',NULL,NULL,'platform:file:rename',NULL,2,'0','0','file',NULL,'2026-11-27 20:17:51.723',NULL,NULL,'0',0,NULL),(2101,'monitor:job:add',21,'新增任务','F',NULL,NULL,'platform:monitor:job:add',NULL,1,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2102,'monitor:job:edit',21,'编辑任务','F',NULL,NULL,'platform:monitor:job:edit',NULL,2,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2103,'monitor:job:remove',21,'删除任务','F',NULL,NULL,'platform:monitor:job:remove',NULL,3,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2104,'monitor:job:changeStatus',21,'启停任务','F',NULL,NULL,'platform:monitor:job:changeStatus',NULL,4,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2105,'monitor:job:run',21,'执行一次','F',NULL,NULL,'platform:monitor:job:run',NULL,5,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2106,'monitor:job:log',21,'执行日志','F',NULL,NULL,'platform:monitor:job:log',NULL,6,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2107,'monitor:job:log:clean',21,'清空日志','F',NULL,NULL,'platform:monitor:job:log:clean',NULL,7,'0','0','job',NULL,'2026-11-27 20:17:51.961',NULL,NULL,'0',0,NULL),(2201,'tool:gen:import',22,'导入表','F',NULL,NULL,'platform:tool:gen:import',NULL,1,'0','0','generator',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,NULL),(2202,'tool:gen:edit',22,'配置表','F',NULL,NULL,'platform:tool:gen:edit',NULL,2,'0','0','generator',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,NULL),(2203,'tool:gen:remove',22,'删除配置','F',NULL,NULL,'platform:tool:gen:remove',NULL,3,'0','0','generator',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,NULL),(2204,'tool:gen:preview',22,'预览代码','F',NULL,NULL,'platform:tool:gen:preview',NULL,4,'0','0','generator',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,NULL),(2205,'tool:gen:download',22,'下载代码','F',NULL,NULL,'platform:tool:gen:download',NULL,5,'0','0','generator',NULL,'2026-11-27 20:17:51.995',NULL,NULL,'0',0,NULL),(3000,'cms',0,'内容管理','M','/cms',NULL,NULL,'documentation',0,'0','0','cms',NULL,'2026-11-27 20:17:52.272',NULL,NULL,'0',0,NULL),(3010,'cms:site',3000,'站点管理','C','/cms/site','cms/site/index','platform:cms:site:list','monitor',1,'0','0','cms',NULL,'2026-11-27 20:17:52.272',NULL,NULL,'0',0,NULL),(3011,'cms:site:add',3010,'新增站点','F',NULL,NULL,'platform:cms:site:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3012,'cms:site:edit',3010,'编辑站点','F',NULL,NULL,'platform:cms:site:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3013,'cms:site:remove',3010,'删除站点','F',NULL,NULL,'platform:cms:site:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3020,'cms:category',3000,'栏目管理','C','/cms/category','cms/category/index','platform:cms:category:list','tree-table',2,'0','0','cms',NULL,'2026-11-27 20:17:52.272',NULL,NULL,'0',0,NULL),(3021,'cms:category:add',3020,'新增栏目','F',NULL,NULL,'platform:cms:category:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3022,'cms:category:edit',3020,'编辑栏目','F',NULL,NULL,'platform:cms:category:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3023,'cms:category:remove',3020,'删除栏目','F',NULL,NULL,'platform:cms:category:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3024,'cms:category:preview',3020,'预览模板','F',NULL,NULL,'platform:cms:category:preview',NULL,4,'0','0','cms',NULL,'2026-11-27 20:17:53.088',NULL,NULL,'0',0,NULL),(3030,'cms:article',3000,'文章管理','C','/cms/article','cms/article/index','platform:cms:article:list','form',3,'0','0','cms',NULL,'2026-11-27 20:17:52.272',NULL,NULL,'0',0,NULL),(3031,'cms:article:add',3030,'新增文章','F',NULL,NULL,'platform:cms:article:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3032,'cms:article:edit',3030,'编辑文章','F',NULL,NULL,'platform:cms:article:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3033,'cms:article:remove',3030,'删除文章','F',NULL,NULL,'platform:cms:article:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3034,'cms:article:publish',3030,'发布文章','F',NULL,NULL,'platform:cms:article:publish',NULL,4,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3035,'cms:article:offline',3030,'下线文章','F',NULL,NULL,'platform:cms:article:offline',NULL,5,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3040,'cms:tag',3000,'标签管理','C','/cms/tag','cms/tag/index','platform:cms:tag:list','dict',4,'0','0','cms',NULL,'2026-11-27 20:17:52.272',NULL,NULL,'0',0,NULL),(3041,'cms:tag:add',3040,'新增标签','F',NULL,NULL,'platform:cms:tag:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3042,'cms:tag:edit',3040,'编辑标签','F',NULL,NULL,'platform:cms:tag:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3043,'cms:tag:remove',3040,'删除标签','F',NULL,NULL,'platform:cms:tag:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:52.273',NULL,NULL,'0',0,NULL),(3050,'cms:recycle',3000,'回收站','C','/cms/recycle','cms/recycle/index','platform:cms:article:recycle','delete',5,'0','0','cms',NULL,'2026-11-27 20:17:52.365',NULL,NULL,'0',0,NULL),(3051,'cms:article:restore',3050,'恢复文章','F',NULL,NULL,'platform:cms:article:restore',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:52.367',NULL,NULL,'0',0,NULL),(3052,'cms:article:purge',3050,'彻底删除','F',NULL,NULL,'platform:cms:article:purge',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:52.368',NULL,NULL,'0',0,NULL),(3053,'cms:article:schedule',3030,'定时发布','F',NULL,NULL,'platform:cms:article:schedule',NULL,6,'0','0','cms',NULL,'2026-11-27 20:17:52.411',NULL,NULL,'0',0,NULL),(3054,'cms:article:version',3030,'历史版本','F',NULL,NULL,'platform:cms:article:version',NULL,7,'0','0','cms',NULL,'2026-11-27 20:17:52.436',NULL,NULL,'0',0,NULL),(3055,'cms:article:rollback',3030,'回滚版本','F',NULL,NULL,'platform:cms:article:rollback',NULL,8,'0','0','cms',NULL,'2026-11-27 20:17:52.436',NULL,NULL,'0',0,NULL),(3056,'cms:article:preview',3030,'预览文章','F',NULL,NULL,'platform:cms:article:preview',NULL,9,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3060,'cms:model',3000,'模型管理','C','/cms/model','cms/model/index','platform:cms:model:list','component',6,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3061,'cms:model:add',3060,'新增模型','F',NULL,NULL,'platform:cms:model:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3062,'cms:model:edit',3060,'编辑模型','F',NULL,NULL,'platform:cms:model:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3063,'cms:model:remove',3060,'删除模型','F',NULL,NULL,'platform:cms:model:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3064,'cms:field:add',3060,'新增字段','F',NULL,NULL,'platform:cms:field:add',NULL,4,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3065,'cms:field:edit',3060,'编辑字段','F',NULL,NULL,'platform:cms:field:edit',NULL,5,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3066,'cms:field:remove',3060,'删除字段','F',NULL,NULL,'platform:cms:field:remove',NULL,6,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3070,'cms:page',3000,'单页管理','C','/cms/page','cms/page/index','platform:cms:page:list','document',7,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3071,'cms:page:add',3070,'新增单页','F',NULL,NULL,'platform:cms:page:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3072,'cms:page:edit',3070,'编辑单页','F',NULL,NULL,'platform:cms:page:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3073,'cms:page:remove',3070,'删除单页','F',NULL,NULL,'platform:cms:page:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3074,'cms:page:publish',3070,'发布单页','F',NULL,NULL,'platform:cms:page:publish',NULL,4,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3075,'cms:page:offline',3070,'下线单页','F',NULL,NULL,'platform:cms:page:offline',NULL,5,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3076,'cms:page:preview',3070,'预览单页','F',NULL,NULL,'platform:cms:page:preview',NULL,6,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3080,'cms:nav',3000,'导航管理','C','/cms/nav','cms/nav/index','platform:cms:nav:list','tree',8,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3081,'cms:nav:add',3080,'新增导航','F',NULL,NULL,'platform:cms:nav:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3082,'cms:nav:edit',3080,'编辑导航','F',NULL,NULL,'platform:cms:nav:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3083,'cms:nav:remove',3080,'删除导航','F',NULL,NULL,'platform:cms:nav:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3084,'cms:nav:item:add',3080,'新增导航项','F',NULL,NULL,'platform:cms:nav:item:add',NULL,4,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3085,'cms:nav:item:edit',3080,'编辑导航项','F',NULL,NULL,'platform:cms:nav:item:edit',NULL,5,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3086,'cms:nav:item:remove',3080,'删除导航项','F',NULL,NULL,'platform:cms:nav:item:remove',NULL,6,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3090,'cms:block',3000,'区块管理','C','/cms/block','cms/block/index','platform:cms:block:list','color',9,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3091,'cms:block:add',3090,'新增区块','F',NULL,NULL,'platform:cms:block:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3092,'cms:block:edit',3090,'编辑区块','F',NULL,NULL,'platform:cms:block:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3093,'cms:block:remove',3090,'删除区块','F',NULL,NULL,'platform:cms:block:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3100,'cms:banner',3000,'轮播管理','C','/cms/banner','cms/banner/index','platform:cms:banner:list','picture',10,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3101,'cms:banner:add',3100,'新增轮播','F',NULL,NULL,'platform:cms:banner:add',NULL,1,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3102,'cms:banner:edit',3100,'编辑轮播','F',NULL,NULL,'platform:cms:banner:edit',NULL,2,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(3103,'cms:banner:remove',3100,'删除轮播','F',NULL,NULL,'platform:cms:banner:remove',NULL,3,'0','0','cms',NULL,'2026-11-27 20:17:53.016',NULL,NULL,'0',0,NULL),(4000,'member',0,'会员管理','M','/member',NULL,NULL,'peoples',5,'0','0','member',NULL,'2026-11-27 20:17:52.498',NULL,NULL,'0',0,NULL),(4010,'member:user',4000,'会员列表','C','/member/user','member/user/index','platform:member:user:list','user',1,'0','0','member',NULL,'2026-11-27 20:17:52.498',NULL,NULL,'0',0,NULL),(4011,'member:user:query',4010,'会员详情','F',NULL,NULL,'platform:member:user:query',NULL,1,'0','0','member',NULL,'2026-11-27 20:17:52.498',NULL,NULL,'0',0,NULL),(4012,'member:user:add',4010,'新增会员','F',NULL,NULL,'platform:member:user:add',NULL,2,'0','0','member',NULL,'2026-11-27 20:17:52.499',NULL,NULL,'0',0,NULL),(4013,'member:user:edit',4010,'编辑会员','F',NULL,NULL,'platform:member:user:edit',NULL,3,'0','0','member',NULL,'2026-11-27 20:17:52.499',NULL,NULL,'0',0,NULL),(4014,'member:user:status',4010,'启停会员','F',NULL,NULL,'platform:member:user:status',NULL,4,'0','0','member',NULL,'2026-11-27 20:17:52.499',NULL,NULL,'0',0,NULL),(4015,'member:user:resetPwd',4010,'重置密码','F',NULL,NULL,'platform:member:user:resetPwd',NULL,5,'0','0','member',NULL,'2026-11-27 20:17:52.499',NULL,NULL,'0',0,NULL),(4016,'member:user:remove',4010,'删除会员','F',NULL,NULL,'platform:member:user:remove',NULL,6,'0','0','member',NULL,'2026-11-27 20:17:52.499',NULL,NULL,'0',0,NULL),(4017,'member:user:merge',4010,'合并账号','F',NULL,NULL,'platform:member:user:merge',NULL,7,'0','0','member',NULL,'2026-11-27 20:17:52.805',NULL,NULL,'0',0,NULL),(4020,'member:channel',4000,'渠道配置','C','/member/channel','member/channel/index','platform:member:channel:query','message',2,'0','0','member',NULL,'2026-11-27 20:17:52.532',NULL,NULL,'0',0,NULL),(4021,'member:channel:edit',4020,'编辑渠道','F',NULL,NULL,'platform:member:channel:edit',NULL,1,'0','0','member',NULL,'2026-11-27 20:17:52.532',NULL,NULL,'0',0,NULL),(4022,'member:channel:test',4020,'测试发送','F',NULL,NULL,'platform:member:channel:test',NULL,2,'0','0','member',NULL,'2026-11-27 20:17:52.533',NULL,NULL,'0',0,NULL),(4030,'member:setting',4000,'会员设置','C','/member/setting','member/setting/index','platform:system:config:list','setting',3,'0','0','member',NULL,'2026-11-27 20:17:52.541',NULL,NULL,'0',0,NULL),(4031,'member:setting:edit',4030,'保存会员设置','F',NULL,NULL,'platform:system:config:edit',NULL,1,'0','0','member',NULL,'2026-11-27 20:17:52.541',NULL,NULL,'0',0,NULL),(4040,'member:comment',4000,'评论管理','C','/member/comment','member/comment/index','platform:member:comment:list','comment',2,'0','0','member',NULL,'2026-11-27 20:17:52.603',NULL,NULL,'0',0,NULL),(4041,'member:comment:audit',4040,'审核评论','F',NULL,NULL,'platform:member:comment:audit',NULL,1,'0','0','member',NULL,'2026-11-27 20:17:52.603',NULL,NULL,'0',0,NULL),(4042,'member:comment:remove',4040,'删除评论','F',NULL,NULL,'platform:member:comment:remove',NULL,2,'0','0','member',NULL,'2026-11-27 20:17:52.604',NULL,NULL,'0',0,NULL),(4050,'member:oauth',4000,'第三方登录','C','/member/oauth','member/oauth/index','platform:member:oauth:list','link',4,'0','0','member',NULL,'2026-11-27 20:17:52.643',NULL,NULL,'0',0,NULL),(4051,'member:oauth:edit',4050,'编辑第三方登录','F',NULL,NULL,'platform:member:oauth:edit',NULL,1,'0','0','member',NULL,'2026-11-27 20:17:52.643',NULL,NULL,'0',0,NULL),(5000,'i18n',0,'多语言','M','/i18n',NULL,NULL,'language',6,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5010,'i18n:language',5000,'语言管理','C','/i18n/language','i18n/language/index','platform:i18n:language:list','language',1,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5011,'i18n:language:add',5010,'新增语言','F',NULL,NULL,'platform:i18n:language:add',NULL,1,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5012,'i18n:language:edit',5010,'编辑语言','F',NULL,NULL,'platform:i18n:language:edit',NULL,2,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5013,'i18n:language:remove',5010,'删除语言','F',NULL,NULL,'platform:i18n:language:remove',NULL,3,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5014,'i18n:language:status',5010,'启停语言','F',NULL,NULL,'platform:i18n:language:status',NULL,4,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5020,'i18n:translation',5000,'翻译维护','C','/i18n/translation','i18n/translation/index','platform:i18n:key:list','edit',2,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5021,'i18n:key:add',5020,'新增翻译键','F',NULL,NULL,'platform:i18n:key:add',NULL,1,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5022,'i18n:key:edit',5020,'编辑翻译键','F',NULL,NULL,'platform:i18n:key:edit',NULL,2,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5023,'i18n:key:remove',5020,'删除翻译键','F',NULL,NULL,'platform:i18n:key:remove',NULL,3,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5024,'i18n:value:batch',5020,'批量保存译文','F',NULL,NULL,'platform:i18n:value:batch',NULL,4,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5025,'i18n:import',5020,'导入译文','F',NULL,NULL,'platform:i18n:import',NULL,5,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5026,'i18n:export',5020,'导出译文','F',NULL,NULL,'platform:i18n:export',NULL,6,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5027,'i18n:coverage',5020,'翻译覆盖率','F',NULL,NULL,'platform:i18n:coverage',NULL,7,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5028,'i18n:missing',5020,'缺失记录','F',NULL,NULL,'platform:i18n:missing',NULL,8,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5030,'i18n:pack',5000,'语言包','C','/i18n/pack','i18n/pack/index','platform:i18n:pack:list','component',3,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5031,'i18n:pack:publish',5030,'发布语言包','F',NULL,NULL,'platform:i18n:pack:publish',NULL,1,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL),(5032,'i18n:pack:rollback',5030,'回滚语言包','F',NULL,NULL,'platform:i18n:pack:rollback',NULL,2,'0','0','i18n',NULL,'2026-11-27 20:17:53.179',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1,'凌霄后台基座已启动','2','<p>欢迎使用凌霄后台基座。默认账号 <b>admin</b>，首次登录后请立即修改密码。</p>','0',NULL,'2026-11-27 20:17:51.645',NULL,NULL,'0',0,'初始化演示数据');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','总经理',1,'0',NULL,'2026-11-27 20:17:51.618',NULL,NULL,'0',0,NULL);
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'admin','超级管理员',1,'1','0',NULL,'2026-11-27 20:17:51.624',NULL,NULL,'0',0,'拥有全部权限');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `dept_id` bigint(20) NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`),
  KEY `idx_role_dept_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据范围：data_scope=2 时生效';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,21),(1,22),(1,23),(1,101),(1,102),(1,103),(1,104),(1,105),(1,201),(1,202),(1,203),(1,301),(1,302),(1,303),(1,401),(1,402),(1,403),(1,501),(1,502),(1,503),(1,601),(1,602),(1,603),(1,701),(1,702),(1,703),(1,801),(1,802),(1,803),(1,1301),(1,1401),(1,1501),(1,1801),(1,1802),(1,2101),(1,2102),(1,2103),(1,2104),(1,2105),(1,2106),(1,2107),(1,2201),(1,2202),(1,2203),(1,2204),(1,2205),(1,3000),(1,3010),(1,3011),(1,3012),(1,3013),(1,3020),(1,3021),(1,3022),(1,3023),(1,3024),(1,3030),(1,3031),(1,3032),(1,3033),(1,3034),(1,3035),(1,3040),(1,3041),(1,3042),(1,3043),(1,3050),(1,3051),(1,3052),(1,3053),(1,3054),(1,3055),(1,3056),(1,3060),(1,3061),(1,3062),(1,3063),(1,3064),(1,3065),(1,3066),(1,3070),(1,3071),(1,3072),(1,3073),(1,3074),(1,3075),(1,3076),(1,3080),(1,3081),(1,3082),(1,3083),(1,3084),(1,3085),(1,3086),(1,3090),(1,3091),(1,3092),(1,3093),(1,3100),(1,3101),(1,3102),(1,3103),(1,4000),(1,4010),(1,4011),(1,4012),(1,4013),(1,4014),(1,4015),(1,4016),(1,4017),(1,4020),(1,4021),(1,4022),(1,4030),(1,4031),(1,4040),(1,4041),(1,4042),(1,4050),(1,4051),(1,5000),(1,5010),(1,5011),(1,5012),(1,5013),(1,5014),(1,5020),(1,5021),(1,5022),(1,5023),(1,5024),(1,5025),(1,5026),(1,5027),(1,5028),(1,5030),(1,5031),(1,5032);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,1,'admin','超级管理员','$2a$10$cdvtHgM0Bd4t.3y5UibkPO4cJEZoxqyxASm/B6KkqX7mwga6/avu6','admin@lingxiao.local','13800000000','2',NULL,'0',NULL,NULL,0,NULL,NULL,'2026-11-27 20:17:51.620',NULL,NULL,'0',0,'系统内置超级管理员');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user_social`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sys_user_social` (
  `social_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `provider` varchar(32) NOT NULL COMMENT 'wechat_mp / wechat_open / qq / github / gitee / dingtalk',
  `open_id` varchar(128) NOT NULL COMMENT '第三方用户唯一标识',
  `union_id` varchar(128) DEFAULT NULL COMMENT '跨应用统一标识（微信等平台提供）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '首次授权时的第三方昵称快照',
  `avatar` varchar(255) DEFAULT NULL COMMENT '首次授权时的第三方头像快照（URL 或 fileId）',
  `raw_data` text COMMENT '平台返回的原始 JSON，仅排障用，不对外下发',
  `last_login_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`social_id`),
  UNIQUE KEY `uk_social_provider_open` (`provider`,`open_id`),
  KEY `idx_social_user` (`user_id`),
  KEY `idx_social_union` (`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户-第三方账号绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user_social` WRITE;
/*!40000 ALTER TABLE `sys_user_social` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_social` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

