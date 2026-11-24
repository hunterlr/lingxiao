package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户导航项。
 *
 * <p>{@code url} 由服务端根据目标类型解析成可直接使用的门户路径；
 * 栏目 / 单页目标已校验同站点且启用。</p>
 */
@Data
public class PortalNavItemVO {

    private Long itemId;

    private String itemName;

    /** category / page / link */
    private String targetType;

    private Long targetId;

    private String url;

    private String icon;

    private Integer sort;

    private List<PortalNavItemVO> children = new ArrayList<>();
}
