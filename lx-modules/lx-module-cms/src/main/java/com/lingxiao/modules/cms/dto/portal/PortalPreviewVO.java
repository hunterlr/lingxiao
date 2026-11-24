package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

/**
 * 草稿预览结果。
 *
 * <p>{@code targetType=article} 时 {@code article} 有值；
 * {@code targetType=page} 时 {@code page} 有值。草稿按原状态返回，
 * 不累加阅读量。</p>
 */
@Data
public class PortalPreviewVO {

    /** article / page */
    private String targetType;

    private PortalArticleDetailVO article;

    private PortalPageVO page;
}
