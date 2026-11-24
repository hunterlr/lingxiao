package com.lingxiao.modules.cms.dto;

import lombok.Data;

/**
 * 模板 / 主题下拉选项。
 *
 * <p>同时返回中英文名称：后台按当前语言展示，不在前端重复维护编码表。</p>
 */
@Data
public class CmsTemplateOptionVO {

    /** 稳定编码，例如 list.news / detail.gallery / page.about / magazine */
    private String code;

    private String name;

    private String nameEn;
}
