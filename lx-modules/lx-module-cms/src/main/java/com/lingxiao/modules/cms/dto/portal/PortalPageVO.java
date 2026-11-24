package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门户单页。
 *
 * <p>{@code content} 为服务端清洗后的 HTML；{@code ext} 是 page 模型的
 * 扩展字段（副标题、联系方式等）。</p>
 */
@Data
public class PortalPageVO {

    private Long pageId;

    private String pageKey;

    private String pageName;

    private String title;

    private String summary;

    /** fileId 或 http(s) URL */
    private String cover;

    /** 已清洗的富文本 HTML */
    private String content;

    private String template;

    private String seoTitle;

    private String seoKeywords;

    private String seoDescription;

    private LocalDateTime publishTime;

    private Map<String, Object> ext = new LinkedHashMap<>();
}
