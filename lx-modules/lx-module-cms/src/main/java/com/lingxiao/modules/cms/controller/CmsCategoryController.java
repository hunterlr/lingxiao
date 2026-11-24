package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.dto.CmsPreviewTicketVO;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.service.CmsCategoryService;
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
import java.util.List;

/**
 * CMS 栏目管理接口。
 *
 * <p>{@code /tree} 返回带 children 的树；{@code /list} 返回平铺列表，
 * 供文章编辑页的栏目下拉使用。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/category")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsCategoryController {

    private final CmsCategoryService categoryService;
    private final CmsPreviewTicketService previewTicketService;

    @RequiresPermission("platform:cms:category:list")
    @GetMapping("/tree")
    public R<List<CmsCategory>> tree(@RequestParam Long siteId,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status) {
        return R.ok(categoryService.tree(siteId, keyword, status));
    }

    @RequiresPermission("platform:cms:category:list")
    @GetMapping("/list")
    public R<List<CmsCategory>> list(@RequestParam Long siteId) {
        return R.ok(categoryService.list(siteId));
    }

    @RequiresPermission("platform:cms:category:list")
    @GetMapping("/{categoryId}")
    public R<CmsCategory> detail(@PathVariable Long categoryId) {
        return R.ok(categoryService.detail(categoryId));
    }

    @RequiresPermission("platform:cms:category:add")
    @Log(title = "CMS 栏目", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsCategory category) {
        return R.ok(categoryService.create(category));
    }

    @RequiresPermission("platform:cms:category:edit")
    @Log(title = "CMS 栏目", businessType = BusinessType.UPDATE)
    @PutMapping("/{categoryId}")
    public R<Void> edit(@PathVariable Long categoryId, @Valid @RequestBody CmsCategory category) {
        categoryService.update(categoryId, category);
        return R.ok();
    }

    @RequiresPermission("platform:cms:category:remove")
    @Log(title = "CMS 栏目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryId}")
    public R<Void> remove(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return R.ok();
    }

    /**
     * 栏目模板预览票据：按指定 list 模板打开门户预览页。
     *
     * <p>{@code template} 为空时沿用栏目自身模板；只接受 list.* 编码。</p>
     */
    @RequiresPermission("platform:cms:category:preview")
    @PostMapping("/{categoryId}/preview-ticket")
    public R<CmsPreviewTicketVO> previewTicket(@PathVariable Long categoryId,
                                               @RequestParam(required = false) String template) {
        return R.ok(previewTicketService.issue(
                CmsPreviewTicketService.TARGET_CATEGORY, categoryId, template));
    }
}
