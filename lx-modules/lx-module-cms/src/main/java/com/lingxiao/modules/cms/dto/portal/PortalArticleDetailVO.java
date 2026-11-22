package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户文章详情。
 *
 * <p>{@code content} 是服务端按白名单清洗后的 HTML，门户可以直接渲染；
 * 不要在门户侧再信任编辑器原始内容。</p>
 */
@Data
public class PortalArticleDetailVO {

    private Long articleId;

    private String title;

    private String slug;

    private String summary;

    /** fileId 或 http(s) URL */
    private String cover;

    /** 已清洗的富文本 HTML */
    private String content;

    private String authorName;

    private String source;

    private String sourceUrl;

    private LocalDateTime publishTime;

    private Long viewCount;

    private PortalCategoryVO category;

    private List<PortalTagVO> tags = new ArrayList<>();
}
