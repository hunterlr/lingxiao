package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.service.CmsTagService;
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
 * CMS 标签管理接口。
 */
@RestController
@RequestMapping("/api/admin/cms/tag")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsTagController {

    private final CmsTagService tagService;

    @RequiresPermission("platform:cms:tag:list")
    @GetMapping("/list")
    public R<PageResult<CmsTag>> list(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "20") long pageSize,
                                      @RequestParam Long siteId,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status) {
        return R.ok(tagService.page(pageNum, pageSize, siteId, keyword, status));
    }

    @RequiresPermission("platform:cms:tag:list")
    @GetMapping("/options")
    public R<List<CmsTag>> options(@RequestParam Long siteId) {
        return R.ok(tagService.listEnabled(siteId));
    }

    @RequiresPermission("platform:cms:tag:add")
    @Log(title = "CMS 标签", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsTag tag) {
        return R.ok(tagService.create(tag));
    }

    @RequiresPermission("platform:cms:tag:edit")
    @Log(title = "CMS 标签", businessType = BusinessType.UPDATE)
    @PutMapping("/{tagId}")
    public R<Void> edit(@PathVariable Long tagId, @Valid @RequestBody CmsTag tag) {
        tagService.update(tagId, tag);
        return R.ok();
    }

    @RequiresPermission("platform:cms:tag:remove")
    @Log(title = "CMS 标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tagId}")
    public R<Void> remove(@PathVariable Long tagId) {
        tagService.delete(tagId);
        return R.ok();
    }
}
