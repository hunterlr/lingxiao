package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.cms.dto.CmsArticleRecycleVO;
import com.lingxiao.modules.cms.dto.CmsArticleScheduleDTO;
import com.lingxiao.modules.cms.dto.CmsArticleVersionVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsArticleVersion;
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

    /** 回收站列表：只读删除审计，不返回正文；需要独立权限，避免所有编辑都能看删除内容。 */
    @RequiresPermission("platform:cms:article:recycle")
    @GetMapping("/recycle/list")
    public R<PageResult<CmsArticleRecycleVO>> recycleList(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String keyword) {
        return R.ok(articleService.recyclePage(pageNum, pageSize, siteId, keyword));
    }

    @RequiresPermission("platform:cms:article:list")
    @GetMapping("/{articleId}")
    public R<CmsArticle> detail(@PathVariable Long articleId) {
        return R.ok(articleService.detail(articleId));
    }

    /** 历史版本列表：只出摘要，正文按版本详情单独读取。 */
    @RequiresPermission("platform:cms:article:version")
    @GetMapping("/{articleId}/versions")
    public R<PageResult<CmsArticleVersionVO>> versions(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(articleService.versionPage(articleId, pageNum, pageSize));
    }

    @RequiresPermission("platform:cms:article:version")
    @GetMapping("/{articleId}/versions/{versionId}")
    public R<CmsArticleVersion> versionDetail(@PathVariable Long articleId,
                                              @PathVariable Long versionId) {
        return R.ok(articleService.versionDetail(articleId, versionId));
    }

    /** 回滚：覆盖当前内容并回到草稿，需要重新发布才对外可见。 */
    @RequiresPermission("platform:cms:article:rollback")
    @Log(title = "CMS 文章版本回滚", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/versions/{versionId}/rollback")
    public R<Void> rollback(@PathVariable Long articleId,
                            @PathVariable Long versionId) {
        articleService.rollback(articleId, versionId);
        return R.ok();
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

    /** 定时发布：设置 / 修改计划时间；已发布文章需先下线。 */
    @RequiresPermission("platform:cms:article:schedule")
    @Log(title = "CMS 文章定时发布", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/schedule")
    public R<Void> schedule(@PathVariable Long articleId,
                            @Valid @RequestBody CmsArticleScheduleDTO dto) {
        articleService.schedule(articleId, dto.getScheduleTime());
        return R.ok();
    }

    /** 取消定时：定时待发布 → 草稿。 */
    @RequiresPermission("platform:cms:article:schedule")
    @Log(title = "CMS 文章取消定时", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/schedule/cancel")
    public R<Void> cancelSchedule(@PathVariable Long articleId) {
        articleService.cancelSchedule(articleId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:remove")
    @Log(title = "CMS 文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleId}")
    public R<Void> remove(@PathVariable Long articleId) {
        articleService.delete(articleId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:restore")
    @Log(title = "CMS 文章恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/{articleId}/restore")
    public R<Void> restore(@PathVariable Long articleId) {
        articleService.restore(articleId);
        return R.ok();
    }

    @RequiresPermission("platform:cms:article:purge")
    @Log(title = "CMS 文章彻底删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleId}/purge")
    public R<Void> purge(@PathVariable Long articleId) {
        articleService.purge(articleId);
        return R.ok();
    }
}
