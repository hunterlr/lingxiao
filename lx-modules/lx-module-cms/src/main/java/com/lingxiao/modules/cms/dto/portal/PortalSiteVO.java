package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

/**
 * 门户站点信息。
 *
 * <p>只返回门户渲染需要的字段；后台的 `createBy / status / remark` 等
 * 内部字段不对外暴露。</p>
 */
@Data
public class PortalSiteVO {

    private String siteKey;

    private String siteName;

    private String domain;

    /** fileId 或 http(s) URL */
    private String logo;

    private String seoTitle;

    private String seoKeywords;

    private String seoDescription;

    /** 门户主题编码，前端据此切换 CSS 变量 */
    private String theme;
}
