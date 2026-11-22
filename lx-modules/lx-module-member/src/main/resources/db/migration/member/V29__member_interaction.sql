-- =====================================================================
-- 迁移 — 会员互动：收藏与点赞（V29）
-- =====================================================================
-- 范围
--   1. mem_favorite  会员收藏文章
--   2. mem_like      会员点赞文章
--
-- 约定
--   - 只存 member_id + article_id，不建跨模块外键，不复制标题 / 封面；
--     文章是否可见由 CMS 门户接口负责，会员模块不依赖 CMS 类。
--   - 收藏 / 点赞是“取消即删”的轻量关系，不做逻辑删除，避免同一会员
--     再次收藏时撞上 del_flag 唯一键；重复收藏由唯一键兜底。
--   - tenant_id 单租户部署固定 0，唯一键带 tenant_id，给后续 SaaS 化留位。
-- =====================================================================

CREATE TABLE mem_favorite (
  favorite_id  bigint      NOT NULL,
  tenant_id    bigint      NOT NULL DEFAULT 0,
  member_id    bigint      NOT NULL,
  article_id   bigint      NOT NULL,
  create_time  datetime(3) NOT NULL,
  PRIMARY KEY (favorite_id),
  UNIQUE KEY uk_mem_favorite_member_article (tenant_id, member_id, article_id),
  KEY idx_mem_favorite_member_time (tenant_id, member_id, create_time),
  KEY idx_mem_favorite_article (tenant_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员收藏文章';

CREATE TABLE mem_like (
  like_id      bigint      NOT NULL,
  tenant_id    bigint      NOT NULL DEFAULT 0,
  member_id    bigint      NOT NULL,
  article_id   bigint      NOT NULL,
  create_time  datetime(3) NOT NULL,
  PRIMARY KEY (like_id),
  UNIQUE KEY uk_mem_like_member_article (tenant_id, member_id, article_id),
  KEY idx_mem_like_member_time (tenant_id, member_id, create_time),
  KEY idx_mem_like_article (tenant_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员点赞文章';
