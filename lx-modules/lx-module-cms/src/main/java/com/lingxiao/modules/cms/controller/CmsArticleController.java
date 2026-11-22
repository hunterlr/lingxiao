package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.service.CmsArticleService;
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
 * CMS 文章管理接口。
 *
 * <p>发布 / 下线单独开权限码：能写草稿不等于能对外发布，很多团队
 * 把这两个动作留给主编或运营负责人。</p>
 */
@RestController
@RequestMapping("/api/admin/cms/article")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsArticleController {

    private final CmsArticleService articleService;

    @RequiresPermission("platform:cms:article:list")
    @GetMapping("/list")
    public R<PageResult<CmsArticle>> list(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "20") long pageSize,
                                          @RequestParam(required = false) Long siteId,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) Long tagId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status) {
        return R.ok(articleService.page(pageNum, pageSize, siteId, categoryId, tagId, keyword, status));
    }

    @RequiresPermission("platform:cms:article:list")
    @GetMapping("/{articleId}")
    public R<CmsArticle> detail(@PathVariable Long articleId) {
        return R.ok(articleService.detail(articleId));
    }

    @RequiresPermission("platform:cms:article:add")
    @Log(title = "CMS 文章", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody CmsArticle article) {
        return R.ok(articleService.create(article));
    }

    @RequiresPermission("platform:cms:article:edit")
    @Log(title = "CMS 文章", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}")
    public R<Void> edit(@PathVariable Long articleId, @Valid @RequestBody CmsArticle article) {
        articleService.update(articleId, article);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:publish")
    @Log(title = "CMS 文章发布", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/publish")
    public R<Void> publish(@PathVariable Long articleId) {
        articleService.publish(articleId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:offline")
    @Log(title = "CMS 文章下线", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/offline")
    public R<Void> offline(@PathVariable Long articleId) {
        articleService.offline(articleId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:remove")
    @Log(title = "CMS 文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleId}")
    public R<Void> remove(@PathVariable Long articleId) {
        articleService.delete(articleId);
        return R.ok();
    }
}
