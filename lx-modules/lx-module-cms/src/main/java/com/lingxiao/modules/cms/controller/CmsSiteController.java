package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.service.CmsSiteService;
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
 * CMS 站点管理接口。
 *
 * <p>关闭 CMS 模块时整组接口不装配，访问返回 404，而不是“能进但报错”。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/site")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsSiteController {

    private final CmsSiteService siteService;

    @RequiresPermission("platform:cms:site:list")
    @GetMapping("/list")
    public R<PageResult<CmsSite>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status) {
        return R.ok(siteService.page(pageNum, pageSize, keyword, status));
    }

    /** 下拉选项：只返回启用站点，不分页。 */
    @RequiresPermission("platform:cms:site:list")
    @GetMapping("/options")
    public R<List<CmsSite>> options() {
        return R.ok(siteService.listEnabled());
    }

    @RequiresPermission("platform:cms:site:list")
    @GetMapping("/{siteId}")
    public R<CmsSite> detail(@PathVariable Long siteId) {
        return R.ok(siteService.detail(siteId));
    }

    @RequiresPermission("platform:cms:site:add")
    @Log(title = "CMS 站点", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsSite site) {
        return R.ok(siteService.create(site));
    }

    @RequiresPermission("platform:cms:site:edit")
    @Log(title = "CMS 站点", businessType = BusinessType.UPDATE)
    @PutMapping("/{siteId}")
    public R<Void> edit(@PathVariable Long siteId, @Valid @RequestBody CmsSite site) {
        siteService.update(siteId, site);
        return R.ok();
    }

    @RequiresPermission("platform:cms:site:remove")
    @Log(title = "CMS 站点", businessType = BusinessType.DELETE)
    @DeleteMapping("/{siteId}")
    public R<Void> remove(@PathVariable Long siteId) {
        siteService.delete(siteId);
        return R.ok();
    }
}
