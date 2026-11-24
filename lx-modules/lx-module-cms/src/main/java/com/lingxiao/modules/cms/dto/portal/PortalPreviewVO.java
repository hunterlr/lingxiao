package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 草稿 / 模板预览结果。
 *
 * <p>{@code targetType=article} 时 {@code article} 有值；
 * {@code targetType=page} 时 {@code page} 有值；
 * {@code targetType=category} 时 {@code category} 与 {@code articles} 有值
 * （只含已发布文章，用于后台按模板预览栏目列表）。</p>
 *
 * <p>草稿按原状态返回，不累加阅读量。</p>
 */
@Data
public class PortalPreviewVO {

    /** article / page / category */
    private String targetType;

    /** 预览票据覆盖的模板编码；空值由门户按目标自身模板回退 */
    private String template;

    private PortalArticleDetailVO article;

    private PortalPageVO page;

    private PortalCategoryVO category;

    private List<PortalArticleSummaryVO> articles = new ArrayList<>();
}
