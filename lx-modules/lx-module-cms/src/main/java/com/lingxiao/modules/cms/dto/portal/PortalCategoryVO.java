package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户栏目节点。
 *
 * <p>只包含“显示且启用”的栏目；隐藏栏目用于承载落地页时，
 * 文章仍可通过直接链接访问，但不会出现在导航树里。</p>
 */
@Data
public class PortalCategoryVO {

    private Long categoryId;

    private Long parentId;

    private String categoryKey;

    private String categoryName;

    private String slug;

    private String path;

    private String template;

    private String icon;

    /** fileId 或 http(s) URL */
    private String cover;

    private String seoTitle;

    private String seoKeywords;

    private String seoDescription;

    private Integer sort;

    private List<PortalCategoryVO> children = new ArrayList<>();
}
