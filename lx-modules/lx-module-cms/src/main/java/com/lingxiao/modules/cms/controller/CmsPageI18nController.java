package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.dto.CmsContentI18nSaveRequest;
import com.lingxiao.modules.cms.dto.CmsContentI18nVO;
import com.lingxiao.modules.cms.service.CmsContentI18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 单页多语言版本（{@code /api/admin/cms/page/{id}/i18n}）。
 *
 * <p>权限沿用 {@code platform:cms:page:edit}，与文章翻译同一套服务。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/page/{pageId}/i18n")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsPageI18nController {

    private final CmsContentI18nService i18nService;

    @RequiresPermission("platform:cms:page:edit")
    @GetMapping
    public R<List<CmsContentI18nVO>> list(@PathVariable Long pageId) {
        return R.ok(i18nService.list(CmsContentI18nService.OWNER_PAGE, pageId));
    }

    @RequiresPermission("platform:cms:page:edit")
    @Log(title = "单页翻译", businessType = BusinessType.UPDATE)
    @PutMapping("/{lang}")
    public R<CmsContentI18nVO> save(@PathVariable Long pageId,
                                    @PathVariable String lang,
                                    @Valid @RequestBody CmsContentI18nSaveRequest request) {
        return R.ok(i18nService.saveDraft(CmsContentI18nService.OWNER_PAGE, pageId, lang, request));
    }

    @RequiresPermission("platform:cms:page:edit")
    @Log(title = "单页翻译", businessType = BusinessType.UPDATE)
    @PostMapping("/{lang}/publish")
    public R<Void> publish(@PathVariable Long pageId, @PathVariable String lang) {
        i18nService.publish(CmsContentI18nService.OWNER_PAGE, pageId, lang);
        return R.ok();
    }

    @RequiresPermission("platform:cms:page:edit")
    @Log(title = "单页翻译", businessType = BusinessType.UPDATE)
    @PostMapping("/{lang}/unpublish")
    public R<Void> unpublish(@PathVariable Long pageId, @PathVariable String lang) {
        i18nService.unpublish(CmsContentI18nService.OWNER_PAGE, pageId, lang);
        return R.ok();
    }

    @RequiresPermission("platform:cms:page:edit")
    @Log(title = "单页翻译", businessType = BusinessType.DELETE)
    @DeleteMapping("/{lang}")
    public R<Void> remove(@PathVariable Long pageId, @PathVariable String lang) {
        i18nService.delete(CmsContentI18nService.OWNER_PAGE, pageId, lang);
        return R.ok();
    }
}