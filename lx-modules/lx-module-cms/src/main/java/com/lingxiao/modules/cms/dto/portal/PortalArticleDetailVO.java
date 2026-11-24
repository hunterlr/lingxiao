package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 详情模板编码，由所属栏目的 list 模板推导，门户按它选择版式 */
    private String template;

    private PortalCategoryVO category;

    private List<PortalTagVO> tags = new ArrayList<>();

    /** 模型扩展字段（图集 images 等） */
    private Map<String, Object> ext = new LinkedHashMap<>();
}
