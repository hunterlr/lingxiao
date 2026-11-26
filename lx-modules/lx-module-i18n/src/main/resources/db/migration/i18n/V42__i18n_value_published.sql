-- =====================================================================
-- 迁移 — 译文增加“最近发布值”（V42）
-- =====================================================================
-- 背景
--   i18n_value 是工作副本：后台保存草稿会改写 value_text 并把
--   review_status 置 0。若 MessageSource 只读 review_status=1，
--   草稿一保存，线上就会退回资源包（新键甚至直接消失），
--   与 docs/15 “保存草稿不影响线上”相矛盾。
--
-- 方案
--   value_text     工作副本，翻译工作台看到 / 编辑的就是它；
--   published_text 最近一次发布的值，MessageSource 与门户只读它；
--   review_status  0=有未发布修改 / 1=工作副本已发布。
--
--   V41 种子的 review_status 都是 1，回填 published_text 后
--   首次启动即可作为“已发布基线”继续对外服务。
-- =====================================================================

ALTER TABLE i18n_value
  ADD COLUMN published_text text COMMENT '最近一次发布的译文；草稿修改不影响线上' AFTER value_text;

UPDATE i18n_value SET published_text = value_text WHERE review_status = '1';
