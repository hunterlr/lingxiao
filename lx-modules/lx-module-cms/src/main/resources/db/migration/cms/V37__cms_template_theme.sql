-- =====================================================================
-- 迁移 37：CMS 第四刀 —— 站点主题、模板编码与模板预览（V37）
-- =====================================================================
-- 约定
--   - 模板编码是稳定契约：list.* / detail.* / page.*，未知编码由门户回退默认版式；
--   - 站点主题只影响门户 CSS 变量，不影响接口字段与 URL；
--   - 预览票据增加 template：后台可按指定模板打开预览，空值沿用目标自身模板；
--   - 不改历史迁移，V20~V24、V35、V36 保持原样。
-- =====================================================================

ALTER TABLE cms_site
    ADD COLUMN theme VARCHAR(32) NOT NULL DEFAULT 'default'
        COMMENT '门户主题编码：default / magazine / minimal' AFTER seo_description;

ALTER TABLE cms_preview_ticket
    ADD COLUMN template VARCHAR(64) NULL
        COMMENT '预览时覆盖的模板编码；空值沿用目标自身模板' AFTER target_id;

ALTER TABLE cms_category
    MODIFY COLUMN template VARCHAR(100) NULL
        COMMENT '列表模板编码：list.default / list.news / list.gallery；空值回退默认版式';

ALTER TABLE cms_page
    MODIFY COLUMN template VARCHAR(100) NULL
        COMMENT '单页模板编码：page.default / page.about；空值回退默认版式';
