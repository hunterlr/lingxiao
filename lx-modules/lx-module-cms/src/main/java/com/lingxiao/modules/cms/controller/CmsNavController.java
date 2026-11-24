package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsNav;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import com.lingxiao.modules.cms.service.CmsNavService;
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
 * CMS 导航与导航项管理接口。
 */
@RestController
@RequestMapping("/api/admin/cms/nav")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsNavController {

    private final CmsNavService navService;

    @RequiresPermission("platform:cms:nav:list")
    @GetMapping("/list")
    public R<List<CmsNav>> list(@RequestParam Long siteId) {
        return R.ok(navService.list(siteId));
    }

    @RequiresPermission("platform:cms:nav:list")
    @GetMapping("/{navId}")
    public R<CmsNav> detail(@PathVariable Long navId) {
        return R.ok(navService.detail(navId));
    }

    @RequiresPermission("platform:cms:nav:add")
    @Log(title = "CMS 导航", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsNav nav) {
        return R.ok(navService.create(nav));
    }

    @RequiresPermission("platform:cms:nav:edit")
    @Log(title = "CMS 导航", businessType = BusinessType.UPDATE)
    @PutMapping("/{navId}")
    public R<Void> edit(@PathVariable Long navId, @Valid @RequestBody CmsNav nav) {
        navService.update(navId, nav);
        return R.ok();
    }

    @RequiresPermission("platform:cms:nav:remove")
    @Log(title = "CMS 导航", businessType = BusinessType.DELETE)
    @DeleteMapping("/{navId}")
    public R<Void> remove(@PathVariable Long navId) {
        navService.delete(navId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:nav:list")
    @GetMapping("/{navId}/items")
    public R<List<CmsNavItem>> items(@PathVariable Long navId) {
        return R.ok(navService.items(navId));
    }

    @RequiresPermission("platform:cms:nav:item:add")
    @Log(title = "CMS 导航项", businessType = BusinessType.INSERT)
    @PostMapping("/{navId}/items")
    public R<Long> addItem(@PathVariable Long navId,
                           @Valid @RequestBody CmsNavItem item) {
        return R.ok(navService.addItem(navId, item));
    }

    @RequiresPermission("platform:cms:nav:item:edit")
    @Log(title = "CMS 导航项", businessType = BusinessType.UPDATE)
    @PutMapping("/{navId}/items/{itemId}")
    public R<Void> editItem(@PathVariable Long navId,
                            @PathVariable Long itemId,
                            @Valid @RequestBody CmsNavItem item) {
        navService.updateItem(navId, itemId, item);
        return R.ok();
    }

    @RequiresPermission("platform:cms:nav:item:remove")
    @Log(title = "CMS 导航项", businessType = BusinessType.DELETE)
    @DeleteMapping("/{navId}/items/{itemId}")
    public R<Void> removeItem(@PathVariable Long navId, @PathVariable Long itemId) {
        navService.deleteItem(navId, itemId);
        return R.ok();
    }
}
