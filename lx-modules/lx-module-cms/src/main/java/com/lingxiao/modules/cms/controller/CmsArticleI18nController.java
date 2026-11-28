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
 * 文章多语言版本（{@code /api/admin/cms/article/{id}/i18n}）。
 *
 * <p>只做人工翻译：保存一律落草稿，发布走单独接口。权限沿用文章的
 * {@code platform:cms:article:edit}，不新增按钮权限。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/article/{articleId}/i18n")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsArticleI18nController {

    private final CmsContentI18nService i18nService;

    @RequiresPermission("platform:cms:article:edit")
    @GetMapping
    public R<List<CmsContentI18nVO>> list(@PathVariable Long articleId) {
        return R.ok(i18nService.list(CmsContentI18nService.OWNER_ARTICLE, articleId));
    }

    @RequiresPermission("platform:cms:article:edit")
    @Log(title = "文章翻译", businessType = BusinessType.UPDATE)
    @PutMapping("/{lang}")
    public R<CmsContentI18nVO> save(@PathVariable Long articleId,
                                    @PathVariable String lang,
                                    @Valid @RequestBody CmsContentI18nSaveRequest request) {
        return R.ok(i18nService.saveDraft(CmsContentI18nService.OWNER_ARTICLE, articleId, lang, request));
    }

    @RequiresPermission("platform:cms:article:edit")
    @Log(title = "文章翻译", businessType = BusinessType.UPDATE)
    @PostMapping("/{lang}/publish")
    public R<Void> publish(@PathVariable Long articleId, @PathVariable String lang) {
        i18nService.publish(CmsContentI18nService.OWNER_ARTICLE, articleId, lang);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:edit")
    @Log(title = "文章翻译", businessType = BusinessType.UPDATE)
    @PostMapping("/{lang}/unpublish")
    public R<Void> unpublish(@PathVariable Long articleId, @PathVariable String lang) {
        i18nService.unpublish(CmsContentI18nService.OWNER_ARTICLE, articleId, lang);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:edit")
    @Log(title = "文章翻译", businessType = BusinessType.DELETE)
    @DeleteMapping("/{lang}")
    public R<Void> remove(@PathVariable Long articleId, @PathVariable String lang) {
        i18nService.delete(CmsContentI18nService.OWNER_ARTICLE, articleId, lang);
        return R.ok();
    }
}