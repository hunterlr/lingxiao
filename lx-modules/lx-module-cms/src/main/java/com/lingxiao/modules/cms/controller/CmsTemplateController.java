package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.dto.CmsTemplateOptionsVO;
import com.lingxiao.modules.cms.support.CmsTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CMS 模板 / 主题选项接口。
 *
 * <p>只返回稳定编码与名称，供站点、栏目、单页表单和模板预览使用；
 * 编码白名单以 {@link CmsTemplates} 为唯一来源，前端不重复维护。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/template")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsTemplateController {

    /** 读选项复用栏目查看权限：能打开栏目页就能看到模板下拉。 */
    @RequiresPermission("platform:cms:category:list")
    @GetMapping("/options")
    public R<CmsTemplateOptionsVO> options() {
        CmsTemplateOptionsVO vo = new CmsTemplateOptionsVO();
        vo.setThemes(CmsTemplates.themeOptions());
        vo.setListTemplates(CmsTemplates.listTemplateOptions());
        vo.setDetailTemplates(CmsTemplates.detailTemplateOptions());
        vo.setPageTemplates(CmsTemplates.pageTemplateOptions());
        return R.ok(vo);
    }
}
