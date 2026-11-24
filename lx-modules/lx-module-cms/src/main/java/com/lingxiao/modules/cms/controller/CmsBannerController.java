package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsBanner;
import com.lingxiao.modules.cms.service.CmsBannerService;
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
 * CMS 轮播管理接口。
 */
@RestController
@RequestMapping("/api/admin/cms/banner")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsBannerController {

    private final CmsBannerService bannerService;

    @RequiresPermission("platform:cms:banner:list")
    @GetMapping("/list")
    public R<PageResult<CmsBanner>> list(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "20") long pageSize,
                                         @RequestParam(required = false) Long siteId,
                                         @RequestParam(required = false) String group,
                                         @RequestParam(required = false) String status) {
        return R.ok(bannerService.page(pageNum, pageSize, siteId, group, status));
    }

    @RequiresPermission("platform:cms:banner:list")
    @GetMapping("/{bannerId}")
    public R<CmsBanner> detail(@PathVariable Long bannerId) {
        return R.ok(bannerService.detail(bannerId));
    }

    @RequiresPermission("platform:cms:banner:add")
    @Log(title = "CMS 轮播", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsBanner banner) {
        return R.ok(bannerService.create(banner));
    }

    @RequiresPermission("platform:cms:banner:edit")
    @Log(title = "CMS 轮播", businessType = BusinessType.UPDATE)
    @PutMapping("/{bannerId}")
    public R<Void> edit(@PathVariable Long bannerId, @Valid @RequestBody CmsBanner banner) {
        bannerService.update(bannerId, banner);
        return R.ok();
    }

    @RequiresPermission("platform:cms:banner:remove")
    @Log(title = "CMS 轮播", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bannerId}")
    public R<Void> remove(@PathVariable Long bannerId) {
        bannerService.delete(bannerId);
        return R.ok();
    }
}
