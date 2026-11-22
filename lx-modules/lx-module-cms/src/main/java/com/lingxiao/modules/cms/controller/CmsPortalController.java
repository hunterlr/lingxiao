package com.lingxiao.modules.cms.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.cms.dto.portal.PortalArticleDetailVO;
import com.lingxiao.modules.cms.dto.portal.PortalArticleSummaryVO;
import com.lingxiao.modules.cms.dto.portal.PortalCategoryVO;
import com.lingxiao.modules.cms.dto.portal.PortalSiteVO;
import com.lingxiao.modules.cms.dto.portal.PortalTagVO;
import com.lingxiao.modules.cms.service.CmsPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CMS 门户接口（公开、只读）。
 *
 * <p>与后台接口物理分开：</p>
 * <ul>
 *   <li>路径在 {@code /api/portal/**}，不经过 Sa-Token 登录拦截；</li>
 *   <li>不返回后台内部字段，只给门户渲染需要的白名单字段；</li>
 *   <li>只出启用站点 + 已发布文章 + 启用栏目 / 标签；</li>
 *   <li>CMS 模块关闭时整组接口不装配，返回 404。</li>
 * </ul>
 *
 * <p>门户应只调用本组接口，禁止直连后台 {@code /api/admin/cms/**}。</p>
 */
@RestController
@RequestMapping("/api/portal/cms")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsPortalController {

    private final CmsPortalService portalService;

    /** 启用站点列表，供门户做多站点切换。 */
    @GetMapping("/sites")
    public R<List<PortalSiteVO>> sites() {
        return R.ok(portalService.sites());
    }

    @GetMapping("/sites/{siteKey}")
    public R<PortalSiteVO> site(@PathVariable String siteKey) {
        return R.ok(portalService.site(siteKey));
    }

    /** 门户导航树：显示且启用的栏目。 */
    @GetMapping("/sites/{siteKey}/categories")
    public R<List<PortalCategoryVO>> categories(@PathVariable String siteKey) {
        return R.ok(portalService.categories(siteKey));
    }

    /** 启用标签列表。 */
    @GetMapping("/sites/{siteKey}/tags")
    public R<List<PortalTagVO>> tags(@PathVariable String siteKey) {
        return R.ok(portalService.tags(siteKey));
    }

    /**
     * 已发布文章分页。
     *
     * <p>栏目支持 {@code categoryId} 或 {@code categoryKey}，标签支持
     * {@code tagId} 或 {@code tagKey}；同时传时 ID 优先。</p>
     */
    @GetMapping("/sites/{siteKey}/articles")
    public R<PageResult<PortalArticleSummaryVO>> articles(
            @PathVariable String siteKey,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categoryKey,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String tagKey,
            @RequestParam(required = false) String keyword) {
        return R.ok(portalService.articles(siteKey, pageNum, pageSize,
                categoryId, categoryKey, tagId, tagKey, keyword));
    }

    @GetMapping("/sites/{siteKey}/articles/{articleId}")
    public R<PortalArticleDetailVO> article(@PathVariable String siteKey,
                                            @PathVariable Long articleId) {
        return R.ok(portalService.article(siteKey, articleId));
    }

    /** 按 slug 取详情；适合做门户的静态化 / 伪静态 URL。 */
    @GetMapping("/sites/{siteKey}/articles/by-slug/{slug}")
    public R<PortalArticleDetailVO> articleBySlug(@PathVariable String siteKey,
                                                  @PathVariable String slug) {
        return R.ok(portalService.articleBySlug(siteKey, slug));
    }

    /** 搜索：标题 / 摘要关键字；只搜已发布文章。 */
    @GetMapping("/sites/{siteKey}/search")
    public R<PageResult<PortalArticleSummaryVO>> search(
            @PathVariable String siteKey,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(portalService.search(siteKey, pageNum, pageSize, keyword));
    }
}
