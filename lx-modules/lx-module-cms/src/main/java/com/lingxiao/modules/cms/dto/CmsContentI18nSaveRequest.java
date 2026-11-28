package com.lingxiao.modules.cms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 保存某个语言版本（草稿）。
 *
 * <p>标题必填；正文是富文本，服务端按与主表同一套白名单清洗。</p>
 */
@Data
public class CmsContentI18nSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.cms.i18n.title.required}")
    @Size(max = 200, message = "{valid.cms.i18n.title.max}")
    private String title;

    @Size(max = 500, message = "{valid.cms.i18n.summary.max}")
    private String summary;

    private String content;

    @Size(max = 200, message = "{valid.cms.i18n.seo.max}")
    private String seoTitle;

    @Size(max = 255, message = "{valid.cms.i18n.seo.max}")
    private String seoKeywords;

    @Size(max = 500, message = "{valid.cms.i18n.seo.max}")
    private String seoDescription;
}