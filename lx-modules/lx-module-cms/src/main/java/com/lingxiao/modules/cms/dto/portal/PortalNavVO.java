package com.lingxiao.modules.cms.dto.portal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户导航。
 */
@Data
public class PortalNavVO {

    private Long navId;

    private String navKey;

    private String navName;

    /** top / footer / mobile */
    private String position;

    private List<PortalNavItemVO> items = new ArrayList<>();
}
