package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

/**
 * 门户标签。
 *
 * <p>只返回启用标签；标签按站点隔离，不跨站点聚合。</p>
 */
@Data
public class PortalTagVO {

    private Long tagId;

    private String tagKey;

    private String tagName;
}
