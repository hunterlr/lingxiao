package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsBlock;
import com.lingxiao.modules.cms.service.CmsBlockService;
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
 * CMS 区块管理接口。
 */
@RestController
@RequestMapping("/api/admin/cms/block")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsBlockController {

    private final CmsBlockService blockService;

    @RequiresPermission("platform:cms:block:list")
    @GetMapping("/list")
    public R<PageResult<CmsBlock>> list(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "20") long pageSize,
                                        @RequestParam(required = false) Long siteId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        return R.ok(blockService.page(pageNum, pageSize, siteId, keyword, status));
    }

    @RequiresPermission("platform:cms:block:list")
    @GetMapping("/{blockId}")
    public R<CmsBlock> detail(@PathVariable Long blockId) {
        return R.ok(blockService.detail(blockId));
    }

    @RequiresPermission("platform:cms:block:add")
    @Log(title = "CMS 区块", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsBlock block) {
        return R.ok(blockService.create(block));
    }

    @RequiresPermission("platform:cms:block:edit")
    @Log(title = "CMS 区块", businessType = BusinessType.UPDATE)
    @PutMapping("/{blockId}")
    public R<Void> edit(@PathVariable Long blockId, @Valid @RequestBody CmsBlock block) {
        blockService.update(blockId, block);
        return R.ok();
    }

    @RequiresPermission("platform:cms:block:remove")
    @Log(title = "CMS 区块", businessType = BusinessType.DELETE)
    @DeleteMapping("/{blockId}")
    public R<Void> remove(@PathVariable Long blockId) {
        blockService.delete(blockId);
        return R.ok();
    }
}
