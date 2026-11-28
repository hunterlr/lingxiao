package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CMS 内容多语言版本（cms_content_i18n）。
 *
 * <p>默认语言仍存主表，本表只存“其它语言”的人工翻译；一个内容一个语言一行，
 * 唯一键 {@code (owner_type, owner_id, lang)}。</p>
 *
 * <p>{@code ownerType} 目前是 {@code article} / {@code page}；不建跨表外键，
 * 内容彻底删除时由业务层清理。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_content_i18n")
public class CmsContentI18n extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "i18n_id", type = IdType.ASSIGN_ID)
    private Long i18nId;

    /** 单租户部署固定 0 */
    private Long tenantId;

    /** article / page */
    private String ownerType;

    private Long ownerId;

    /** 语言代码，如 en-US */
    private String lang;

    private String title;

    private String summary;

    private String content;

    private String seoTitle;

    private String seoKeywords;

    private String seoDescription;

    /** 0 草稿 / 1 已发布 */
    private String status;
}