package com.lingxiao.modules.cms.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容多语言版本（后台编辑用）。
 */
@Data
public class CmsContentI18nVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long i18nId;

    private String ownerType;

    private Long ownerId;

    private String lang;

    private String title;

    private String summary;

    private String content;

    private String seoTitle;

    private String seoKeywords;

    private String seoDescription;

    /** 0 草稿 / 1 已发布 */
    private String status;

    private LocalDateTime updateTime;
}