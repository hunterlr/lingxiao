package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.dto.portal.PortalArticleDetailVO;
import com.lingxiao.modules.cms.dto.portal.PortalBannerVO;
import com.lingxiao.modules.cms.dto.portal.PortalBlockVO;
import com.lingxiao.modules.cms.dto.portal.PortalNavItemVO;
import com.lingxiao.modules.cms.dto.portal.PortalNavVO;
import com.lingxiao.modules.cms.dto.portal.PortalPageVO;
import com.lingxiao.modules.cms.dto.portal.PortalPreviewVO;
import com.lingxiao.modules.cms.dto.portal.PortalTagVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsBanner;
import com.lingxiao.modules.cms.entity.CmsBlock;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsNav;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.entity.CmsPreviewTicket;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsBannerMapper;
import com.lingxiao.modules.cms.mapper.CmsBlockMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsNavMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CMS 门户内容服务：单页、导航、区块、轮播与草稿预览。
 *
 * <p>与 {@link CmsPortalService} 一样只读、只出启用内容；拆开是为了让
 * 文章链路保持轻量，也避免一个服务无限膨胀。</p>
 *
 * <p><b>预览：</b>票据一次性消费，消费成功才返回草稿；不累加阅读量。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPortalContentService {

    private static final String TARGET_CATEGORY = "category";
    private static final String TARGET_PAGE = "page";

    private final CmsSiteService siteService;
    private final CmsPageMapper pageMapper;
    private final CmsNavMapper navMapper;
    private final CmsNavItemMapper navItemMapper;
    private final CmsBlockMapper blockMapper;
    private final CmsBannerMapper bannerMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsArticleMapper articleMapper;
    private final CmsTagMapper tagMapper;
    private final CmsArticleTagMapper articleTagMapper;
    private final CmsContentExtService extService;
    private final CmsPreviewTicketService previewTicketService;

    /** 已发布单页；草稿 / 下线 / 其它站点一律 404。 */
    public PortalPageVO page(String siteKey, String pageKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        CmsPage page = pageMapper.selectOne(new LambdaQueryWrapper<CmsPage>()
                .eq(CmsPage::getSiteId, site.getSiteId())
                .eq(CmsPage::getPageKey, pageKey)
                .eq(CmsPage::getStatus, CmsPageService.STATUS_PUBLISHED));
        if (page == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.page.notfound");
        }
        return toPageVO(page);
    }

    /**
     * 启用导航及其可见导航项。
     *
     * <p>目标为栏目时校验栏目启用；目标为单页时校验单页已发布；失效目标
     * 以及隐藏项都不下发，避免门户出现死链。父项被过滤时子项提升为根。</p>
     */
    public PortalNavVO nav(String siteKey, String navKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        CmsNav nav = navMapper.selectOne(new LambdaQueryWrapper<CmsNav>()
                .eq(CmsNav::getSiteId, site.getSiteId())
                .eq(CmsNav::getNavKey, navKey)
                .eq(CmsNav::getStatus, CommonConstants.STATUS_NORMAL));
        if (nav == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.nav.notfound");
        }
        List<CmsNavItem> items = navItemMapper.selectList(new LambdaQueryWrapper<CmsNavItem>()
                .eq(CmsNavItem::getNavId, nav.getNavId())
                .eq(CmsNavItem::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(CmsNavItem::getVisible, CommonConstants.STATUS_NORMAL)
                .orderByAsc(CmsNavItem::getSort)
                .orderByAsc(CmsNavItem::getItemId));

        PortalNavVO vo = new PortalNavVO();
        vo.setNavId(nav.getNavId());
        vo.setNavKey(nav.getNavKey());
        vo.setNavName(nav.getNavName());
        vo.setPosition(nav.getPosition());
        vo.setItems(buildNavTree(site.getSiteId(), items));
        return vo;
    }

    /** 启用区块；正文清洗后返回。 */
    public PortalBlockVO block(String siteKey, String blockKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        CmsBlock block = blockMapper.selectOne(new LambdaQueryWrapper<CmsBlock>()
                .eq(CmsBlock::getSiteId, site.getSiteId())
                .eq(CmsBlock::getBlockKey, blockKey)
                .eq(CmsBlock::getStatus, CommonConstants.STATUS_NORMAL));
        if (block == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.block.notfound");
        }
        PortalBlockVO vo = new PortalBlockVO();
        vo.setBlockId(block.getBlockId());
        vo.setBlockKey(block.getBlockKey());
        vo.setBlockName(block.getBlockName());
        vo.setBlockType(block.getBlockType());
        vo.setContent(HtmlSanitizer.clean(block.getContent()));
        vo.setCover(block.getCover());
        vo.setLinkUrl(block.getLinkUrl());
        vo.setSort(block.getSort());
        return vo;
    }

    /** 轮播：启用 + 时间窗内，按 sort 升序。 */
    public List<PortalBannerVO> banners(String siteKey, String groupKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        String group = StringUtils.hasText(groupKey) ? groupKey.trim() : "home";
        LocalDateTime now = LocalDateTime.now();
        List<CmsBanner> banners = bannerMapper.selectList(new LambdaQueryWrapper<CmsBanner>()
                .eq(CmsBanner::getSiteId, site.getSiteId())
                .eq(CmsBanner::getBannerGroup, group)
                .eq(CmsBanner::getStatus, CommonConstants.STATUS_NORMAL)
                .and(w -> w.isNull(CmsBanner::getStartTime).or().le(CmsBanner::getStartTime, now))
                .and(w -> w.isNull(CmsBanner::getEndTime).or().ge(CmsBanner::getEndTime, now))
                .orderByAsc(CmsBanner::getSort)
                .orderByAsc(CmsBanner::getBannerId));
        List<PortalBannerVO> result = new ArrayList<>(banners.size());
        for (CmsBanner banner : banners) {
            PortalBannerVO vo = new PortalBannerVO();
            vo.setBannerId(banner.getBannerId());
            vo.setBannerGroup(banner.getBannerGroup());
            vo.setTitle(banner.getTitle());
            vo.setImage(banner.getImage());
            vo.setLinkUrl(banner.getLinkUrl());
            vo.setSort(banner.getSort());
            result.add(vo);
        }
        return result;
    }

    /** 草稿预览：消费一次性票据后返回文章或单页。 */
    public PortalPreviewVO preview(String ticket) {
        CmsPreviewTicket row = previewTicketService.consume(ticket);
        CmsSite site = siteService.mustGet(row.getSiteId());
        PortalPreviewVO vo = new PortalPreviewVO();
        vo.setTargetType(row.getTargetType());
        if (CmsPreviewTicketService.TARGET_ARTICLE.equals(row.getTargetType())) {
            CmsArticle article = articleMapper.selectById(row.getTargetId());
            if (article == null || !Objects.equals(site.getSiteId(), article.getSiteId())) {
                throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.article.notfound");
            }
            vo.setArticle(toPreviewArticle(article));
        } else if (CmsPreviewTicketService.TARGET_PAGE.equals(row.getTargetType())) {
            CmsPage page = pageMapper.selectById(row.getTargetId());
            if (page == null || !Objects.equals(site.getSiteId(), page.getSiteId())) {
                throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.page.notfound");
            }
            vo.setPage(toPageVO(page));
        } else {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.preview.target.missing");
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    private PortalPageVO toPageVO(CmsPage page) {
        PortalPageVO vo = new PortalPageVO();
        vo.setPageId(page.getPageId());
        vo.setPageKey(page.getPageKey());
        vo.setPageName(page.getPageName());
        vo.setTitle(page.getTitle());
        vo.setSummary(page.getSummary());
        vo.setCover(page.getCover());
        vo.setContent(HtmlSanitizer.clean(page.getContent()));
        vo.setTemplate(page.getTemplate());
        vo.setSeoTitle(page.getSeoTitle());
        vo.setSeoKeywords(page.getSeoKeywords());
        vo.setSeoDescription(page.getSeoDescription());
        vo.setPublishTime(page.getPublishTime());
        vo.setExt(extService.load("page", page.getPageId()));
        return vo;
    }

    /** 预览文章：不校验发布状态、不累加阅读量。 */
    private PortalArticleDetailVO toPreviewArticle(CmsArticle article) {
        PortalArticleDetailVO vo = new PortalArticleDetailVO();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSlug(article.getSlug());
        vo.setSummary(article.getSummary());
        vo.setCover(article.getCover());
        vo.setContent(HtmlSanitizer.clean(article.getContent()));
        vo.setAuthorName(article.getAuthorName());
        vo.setSource(article.getSource());
        vo.setSourceUrl(article.getSourceUrl());
        vo.setPublishTime(article.getPublishTime());
        vo.setViewCount(article.getViewCount() == null ? 0L : article.getViewCount());
        CmsCategory category = article.getCategoryId() == null
                ? null : categoryMapper.selectById(article.getCategoryId());
        if (category != null && Objects.equals(article.getSiteId(), category.getSiteId())) {
            vo.setCategory(CmsPortalService.toCategoryNode(category));
        }
        vo.setTags(loadTags(article.getSiteId(),
                articleTagMapper.selectTagIds(article.getArticleId())));
        vo.setExt(extService.load("article", article.getArticleId()));
        return vo;
    }

    private List<PortalNavItemVO> buildNavTree(Long siteId, List<CmsNavItem> items) {
        Map<Long, PortalNavItemVO> nodeMap = new LinkedHashMap<>();
        for (CmsNavItem item : items) {
            String url = resolveUrl(siteId, item);
            if (url == null) {
                continue;
            }
            PortalNavItemVO node = new PortalNavItemVO();
            node.setItemId(item.getItemId());
            node.setItemName(item.getItemName());
            node.setTargetType(item.getTargetType());
            node.setTargetId(item.getTargetId());
            node.setUrl(url);
            node.setIcon(item.getIcon());
            node.setSort(item.getSort());
            nodeMap.put(item.getItemId(), node);
        }
        List<PortalNavItemVO> roots = new ArrayList<>();
        for (CmsNavItem item : items) {
            PortalNavItemVO node = nodeMap.get(item.getItemId());
            if (node == null) {
                continue;
            }
            PortalNavItemVO parent = nodeMap.get(item.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        Comparator<PortalNavItemVO> comparator = Comparator
                .comparing((PortalNavItemVO item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(PortalNavItemVO::getItemId);
        roots.sort(comparator);
        for (PortalNavItemVO node : nodeMap.values()) {
            node.getChildren().sort(comparator);
        }
        return roots;
    }

    /** 目标不可用返回 null，调用方直接丢弃该导航项。 */
    private String resolveUrl(Long siteId, CmsNavItem item) {
        if (TARGET_CATEGORY.equals(item.getTargetType())) {
            CmsCategory category = item.getTargetId() == null
                    ? null : categoryMapper.selectById(item.getTargetId());
            if (category == null
                    || !Objects.equals(siteId, category.getSiteId())
                    || !CommonConstants.STATUS_NORMAL.equals(category.getStatus())) {
                return null;
            }
            return StringUtils.hasText(category.getPath())
                    ? category.getPath() : "/category/" + category.getCategoryKey();
        }
        if (TARGET_PAGE.equals(item.getTargetType())) {
            CmsPage page = item.getTargetId() == null
                    ? null : pageMapper.selectById(item.getTargetId());
            if (page == null
                    || !Objects.equals(siteId, page.getSiteId())
                    || !CmsPageService.STATUS_PUBLISHED.equals(page.getStatus())) {
                return null;
            }
            return "/page/" + page.getPageKey();
        }
        return StringUtils.hasText(item.getUrl()) ? item.getUrl() : null;
    }

    private List<PortalTagVO> loadTags(Long siteId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> Objects.equals(siteId, tag.getSiteId()))
                .filter(tag -> CommonConstants.STATUS_NORMAL.equals(tag.getStatus()))
                .sorted(Comparator.comparing(CmsTag::getTagId))
                .map(this::toTag)
                .collect(Collectors.toList());
    }

    private PortalTagVO toTag(CmsTag tag) {
        PortalTagVO vo = new PortalTagVO();
        vo.setTagId(tag.getTagId());
        vo.setTagKey(tag.getTagKey());
        vo.setTagName(tag.getTagName());
        return vo;
    }
}
