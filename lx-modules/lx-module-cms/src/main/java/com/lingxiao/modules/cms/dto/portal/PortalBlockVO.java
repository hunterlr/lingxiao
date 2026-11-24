package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

/**
 * 门户区块 / 推荐位。
 */
@Data
public class PortalBlockVO {

    private Long blockId;

    private String blockKey;

    private String blockName;

    /** html / image */
    private String blockType;

    /** 已清洗的富文本 HTML */
    private String content;

    /** fileId 或 http(s) URL */
    private String cover;

    private String linkUrl;

    private Integer sort;
}
