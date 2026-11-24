package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.dto.CmsPreviewTicketVO;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.service.CmsPageService;
import com.lingxiao.modules.cms.service.CmsPreviewTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * CMS 单页管理接口。
 */
@RestController
@RequestMapping("/api/admin/cms/page")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsPageController {

    private final CmsPageService pageService;
    private final CmsPreviewTicketService previewTicketService;

    @RequiresPermission("platform:cms:page:list")
    @GetMapping("/list")
    public R<PageResult<CmsPage>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) Long siteId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status) {
        return R.ok(pageService.page(pageNum, pageSize, siteId, keyword, status));
    }

    @RequiresPermission("platform:cms:page:list")
    @GetMapping("/{pageId}")
    public R<CmsPage> detail(@PathVariable Long pageId) {
        return R.ok(pageService.detail(pageId));
    }

    @RequiresPermission("platform:cms:page:add")
    @Log(title = "CMS 单页", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsPage page) {
        return R.ok(pageService.create(page));
    }

    @RequiresPermission("platform:cms:page:edit")
    @Log(title = "CMS 单页", businessType = BusinessType.UPDATE)
    @PutMapping("/{pageId}")
    public R<Void> edit(@PathVariable Long pageId, @Valid @RequestBody CmsPage page) {
        pageService.update(pageId, page);
        return R.ok();
    }

    @RequiresPermission("platform:cms:page:remove")
    @Log(title = "CMS 单页", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pageId}")
    public R<Void> remove(@PathVariable Long pageId) {
        pageService.delete(pageId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:page:publish")
    @Log(title = "CMS 单页发布", businessType = BusinessType.UPDATE)
    @PutMapping("/{pageId}/publish")
    public R<Void> publish(@PathVariable Long pageId) {
        pageService.publish(pageId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:page:offline")
    @Log(title = "CMS 单页下线", businessType = BusinessType.UPDATE)
    @PutMapping("/{pageId}/offline")
    public R<Void> offline(@PathVariable Long pageId) {
        pageService.offline(pageId);
        return R.ok();
    }

    /** 生成一次性预览票据，门户凭票打开草稿。 */
    @RequiresPermission("platform:cms:page:preview")
    @PostMapping("/{pageId}/preview-ticket")
    public R<CmsPreviewTicketVO> previewTicket(@PathVariable Long pageId,
                                               @RequestParam(required = false) String template) {
        return R.ok(previewTicketService.issue(
                CmsPreviewTicketService.TARGET_PAGE, pageId, template));
    }
}
