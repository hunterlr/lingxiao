package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户文章摘要。
 *
 * <p>列表接口不返回正文，避免一页文章把 longtext 全部拖出来；
 * 正文只在详情接口返回。</p>
 */
@Data
public class PortalArticleSummaryVO {

    private Long articleId;

    private String title;

    private String slug;

    private String summary;

    /** fileId 或 http(s) URL */
    private String cover;

    private String authorName;

    private String source;

    private LocalDateTime publishTime;

    private Long viewCount;

    private Long categoryId;

    private String categoryKey;

    private String categoryName;

    private List<PortalTagVO> tags = new ArrayList<>();
}
