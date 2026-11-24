package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

/**
 * 门户轮播图。
 */
@Data
public class PortalBannerVO {

    private Long bannerId;

    private String bannerGroup;

    private String title;

    /** fileId 或 http(s) URL */
    private String image;

    private String linkUrl;

    private Integer sort;
}
