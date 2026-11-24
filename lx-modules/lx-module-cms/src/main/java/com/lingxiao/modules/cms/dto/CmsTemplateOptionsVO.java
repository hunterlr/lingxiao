package com.lingxiao.modules.cms.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台模板选项：主题 + 三类模板编码。
 *
 * <p>只读接口，供站点 / 栏目 / 单页表单与模板预览使用；不落库、不缓存。</p>
 */
@Data
public class CmsTemplateOptionsVO {

    private List<CmsTemplateOptionVO> themes;

    private List<CmsTemplateOptionVO> listTemplates;

    private List<CmsTemplateOptionVO> detailTemplates;

    private List<CmsTemplateOptionVO> pageTemplates;
}
