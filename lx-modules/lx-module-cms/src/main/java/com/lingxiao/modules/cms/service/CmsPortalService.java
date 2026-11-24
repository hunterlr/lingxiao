package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.dto.ArticleTagRow;
import com.lingxiao.modules.cms.dto.portal.PortalArticleDetailVO;
import com.lingxiao.modules.cms.dto.portal.PortalArticleSummaryVO;
import com.lingxiao.modules.cms.dto.portal.PortalCategoryVO;
import com.lingxiao.modules.cms.dto.portal.PortalSiteVO;
import com.lingxiao.modules.cms.dto.portal.PortalTagVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryClosureMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import com.lingxiao.modules.cms.support.CmsTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CMS 门户服务。
 *
 * <p><b>只读、只出已发布内容。</b>门户接口不登录、不经过后台权限拦截，
 * 因此这里必须自己把住三件事：</p>
 * <ol>
 *   <li>站点必须启用；</li>
 *   <li>文章必须是已发布状态，且所属栏目仍启用；</li>
 *   <li>栏目导航只出“显示且启用”的节点，停用栏目整棵子树都不出。</li>
 * </ol>
 *
 * <p><b>字段白名单：</b>不直接返回实体，统一转成 {@code Portal*VO}，
 * 避免把 {@code createBy / status / remark / delFlag} 等后台字段带出去。</p>
 *
 * <p><b>正文清洗：</b>详情返回前再过一次 {@link HtmlSanitizer}，
 * 即便有人直接改库也能守住 XSS 底线；门户侧可以直接渲染。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPortalService {

    /** 门户列表页大小上限；公开接口不允许被一页拉空全站。 */
    private static final long MAX_PAGE_SIZE = 50;

    /** 关键字最大长度；避免超长 LIKE 拖慢公开接口。 */
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final CmsSiteService siteService;
    private final CmsCategoryMapper categoryMapper;
    private final CmsArticleMapper articleMapper;
    private final CmsTagMapper tagMapper;
    private final CmsArticleTagMapper articleTagMapper;
    private final CmsCategoryClosureMapper closureMapper;
    private final CmsContentExtService extService;

    /** 启用站点列表，供门户做站点切换。 */
    public List<PortalSiteVO> sites() {
        return siteService.listEnabled().stream().map(this::toSite).collect(Collectors.toList());
    }

    public PortalSiteVO site(String siteKey) {
        return toSite(siteService.detailByKey(siteKey));
    }

    /**
     * 门户栏目导航树。
     *
     * <p>规则：停用栏目整棵子树都不出现；隐藏栏目本身不出现，但它的
     * 可见子栏目会提升到最近的可见父级（没有则成为根）。这样“隐藏容器、
     * 保留子页面”不需要额外字段。</p>
     */
    public List<PortalCategoryVO> categories(String siteKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        List<CmsCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, site.getSiteId())
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getCategoryId));
        return buildVisibleTree(all);
    }

    public List<PortalTagVO> tags(String siteKey) {
        CmsSite site = siteService.detailByKey(siteKey);
        return tagMapper.selectList(new LambdaQueryWrapper<CmsTag>()
                        .eq(CmsTag::getSiteId, site.getSiteId())
                        .eq(CmsTag::getStatus, CommonConstants.STATUS_NORMAL)
                        .orderByAsc(CmsTag::getTagId))
                .stream().map(this::toTag).collect(Collectors.toList());
    }

    /**
     * 已发布文章分页。
     *
     * <p>支持栏目 ID / 栏目标识、标签 ID / 标签标识、关键字；按栏目筛选时
     * 包含该栏目的整棵子树。关键字只匹配标题与摘要，不扫正文。</p>
     */
    public PageResult<PortalArticleSummaryVO> articles(String siteKey, long pageNum, long pageSize,
                                                       Long categoryId, String categoryKey,
                                                       Long tagId, String tagKey, String keyword) {
        CmsSite site = siteService.detailByKey(siteKey);
        Long resolvedCategoryId = resolveCategoryId(site.getSiteId(), categoryId, categoryKey);
        Long resolvedTagId = resolveTagId(site.getSiteId(), tagId, tagKey);
        String normalizedKeyword = normalizeKeyword(keyword);

        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<CmsArticle> page = new Page<>(current, size);
        LambdaQueryWrapper<CmsArticle> wrapper = new LambdaQueryWrapper<CmsArticle>()
                .select(CmsArticle.class, field -> !"content".equals(field.getColumn()))
                .eq(CmsArticle::getSiteId, site.getSiteId())
                .eq(CmsArticle::getStatus, CmsArticleService.STATUS_PUBLISHED)
                // 栏目停用后，文章不再出现在门户；祖先栏目停用同样隐藏整棵子树。
                // 用 EXISTS / NOT EXISTS 子查询避免 JOIN 影响分页 count。
                .apply("EXISTS (SELECT 1 FROM cms_category c "
                        + "WHERE c.category_id = cms_article.category_id "
                        + "AND c.site_id = {0} AND c.status = '0')", site.getSiteId())
                .apply("NOT EXISTS (SELECT 1 FROM cms_category_closure cc "
                        + "JOIN cms_category ac ON ac.category_id = cc.ancestor_id "
                        + "WHERE cc.category_id = cms_article.category_id "
                        + "AND cc.depth > 0 AND ac.status = '1')")
                .and(normalizedKeyword != null, w -> w
                        .like(CmsArticle::getTitle, normalizedKeyword)
                        .or().like(CmsArticle::getSummary, normalizedKeyword));
        if (resolvedCategoryId != null) {
            wrapper.in(CmsArticle::getCategoryId, subtreeIds(resolvedCategoryId));
        }
        if (resolvedTagId != null) {
            wrapper.apply("EXISTS (SELECT 1 FROM cms_article_tag t "
                    + "WHERE t.article_id = cms_article.article_id AND t.tag_id = {0})", resolvedTagId);
        }
        wrapper.orderByDesc(CmsArticle::getSort)
                .orderByDesc(CmsArticle::getPublishTime)
                .orderByDesc(CmsArticle::getArticleId);

        Page<CmsArticle> result = articleMapper.selectPage(page, wrapper);
        List<PortalArticleSummaryVO> records = toSummaries(site.getSiteId(), result.getRecords());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 门户搜索：与列表同一套规则，显式暴露给前端做“搜索页”。 */
    public PageResult<PortalArticleSummaryVO> search(String siteKey, long pageNum, long pageSize,
                                                     String keyword) {
        return articles(siteKey, pageNum, pageSize, null, null, null, null, keyword);
    }

    /** 按 ID 取已发布文章；草稿 / 下线 / 其它站点一律按“不存在”处理。 */
    public PortalArticleDetailVO article(String siteKey, Long articleId) {
        CmsSite site = siteService.detailByKey(siteKey);
        CmsArticle article = articleId == null ? null : articleMapper.selectById(articleId);
        return toDetail(site, article);
    }

    /** 按 slug 取已发布文章；slug 站点内唯一。 */
    public PortalArticleDetailVO articleBySlug(String siteKey, String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "slug 不能为空");
        }
        CmsSite site = siteService.detailByKey(siteKey);
        CmsArticle article = articleMapper.selectOne(new LambdaQueryWrapper<CmsArticle>()
                .eq(CmsArticle::getSiteId, site.getSiteId())
                .eq(CmsArticle::getSlug, slug.trim())
                .eq(CmsArticle::getStatus, CmsArticleService.STATUS_PUBLISHED));
        return toDetail(site, article);
    }

    /**
     * 按 ID 批量取已发布文章摘要。
     *
     * <p>用于会员收藏这类“只有文章 ID”的场景：只查摘要字段（排除正文），
     * 不累加阅读量，并按请求顺序返回；不可见或已删除的 ID 直接省略。</p>
     */
    public List<PortalArticleSummaryVO> articleBatch(String siteKey, List<Long> ids) {
        CmsSite site = siteService.detailByKey(siteKey);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> normalized = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_PAGE_SIZE)
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<CmsArticle> articles = articleMapper.selectList(new LambdaQueryWrapper<CmsArticle>()
                .select(CmsArticle.class, field -> !"content".equals(field.getColumn()))
                .in(CmsArticle::getArticleId, normalized)
                .eq(CmsArticle::getSiteId, site.getSiteId())
                .eq(CmsArticle::getStatus, CmsArticleService.STATUS_PUBLISHED)
                .apply("EXISTS (SELECT 1 FROM cms_category c "
                        + "WHERE c.category_id = cms_article.category_id "
                        + "AND c.site_id = {0} AND c.status = '0')", site.getSiteId())
                .apply("NOT EXISTS (SELECT 1 FROM cms_category_closure cc "
                        + "JOIN cms_category ac ON ac.category_id = cc.ancestor_id "
                        + "WHERE cc.category_id = cms_article.category_id "
                        + "AND cc.depth > 0 AND ac.status = '1')"));
        Map<Long, PortalArticleSummaryVO> byId = toSummaries(site.getSiteId(), articles).stream()
                .collect(Collectors.toMap(PortalArticleSummaryVO::getArticleId, item -> item, (a, b) -> a));
        return normalized.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    private PortalArticleDetailVO toDetail(CmsSite site, CmsArticle article) {
        if (article == null
                || !Objects.equals(site.getSiteId(), article.getSiteId())
                || !CmsArticleService.STATUS_PUBLISHED.equals(article.getStatus())) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        CmsCategory category = categoryMapper.selectById(article.getCategoryId());
        if (category == null
                || !Objects.equals(site.getSiteId(), category.getSiteId())
                || !isCategoryVisible(category)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        articleMapper.increaseViewCount(article.getArticleId());

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
        vo.setViewCount((article.getViewCount() == null ? 0L : article.getViewCount()) + 1);
        vo.setCategory(toCategoryNode(category));
        vo.setTemplate(CmsTemplates.resolveDetailTemplate(category.getTemplate()));
        vo.setTags(loadTags(site.getSiteId(), articleTagMapper.selectTagIds(article.getArticleId())));
        vo.setExt(extService.load("article", article.getArticleId()));
        return vo;
    }

    private List<PortalArticleSummaryVO> toSummaries(Long siteId, List<CmsArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> articleIds = articles.stream()
                .map(CmsArticle::getArticleId).filter(Objects::nonNull).collect(Collectors.toList());

        Map<Long, CmsCategory> categoryMap = new HashMap<>();
        List<Long> categoryIds = articles.stream()
                .map(CmsArticle::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!categoryIds.isEmpty()) {
            for (CmsCategory category : categoryMapper.selectBatchIds(categoryIds)) {
                categoryMap.put(category.getCategoryId(), category);
            }
        }

        Map<Long, List<PortalTagVO>> tagMap = loadTagsByArticle(siteId, articleIds);

        List<PortalArticleSummaryVO> records = new ArrayList<>(articles.size());
        for (CmsArticle article : articles) {
            PortalArticleSummaryVO vo = new PortalArticleSummaryVO();
            vo.setArticleId(article.getArticleId());
            vo.setTitle(article.getTitle());
            vo.setSlug(article.getSlug());
            vo.setSummary(article.getSummary());
            vo.setCover(article.getCover());
            vo.setAuthorName(article.getAuthorName());
            vo.setSource(article.getSource());
            vo.setPublishTime(article.getPublishTime());
            vo.setViewCount(article.getViewCount() == null ? 0L : article.getViewCount());
            vo.setCategoryId(article.getCategoryId());
            CmsCategory category = categoryMap.get(article.getCategoryId());
            if (category != null) {
                vo.setCategoryKey(category.getCategoryKey());
                vo.setCategoryName(category.getCategoryName());
            }
            vo.setTags(tagMap.getOrDefault(article.getArticleId(), Collections.emptyList()));
            records.add(vo);
        }
        return records;
    }

    /** 一页文章一次查完标签关联，再一次性取标签，避免逐篇 N+1。 */
    private Map<Long, List<PortalTagVO>> loadTagsByArticle(Long siteId, List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleTagRow> rows = articleTagMapper.selectRowsByArticleIds(articleIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> tagIds = rows.stream()
                .map(ArticleTagRow::getTagId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, PortalTagVO> tagMap = loadTags(siteId, tagIds).stream()
                .collect(Collectors.toMap(PortalTagVO::getTagId, tag -> tag, (a, b) -> a));
        Map<Long, List<PortalTagVO>> result = new LinkedHashMap<>();
        for (ArticleTagRow row : rows) {
            PortalTagVO tag = tagMap.get(row.getTagId());
            if (tag != null) {
                result.computeIfAbsent(row.getArticleId(), key -> new ArrayList<>()).add(tag);
            }
        }
        return result;
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

    /** 栏目筛选：ID 优先，其次站点内唯一标识；同时校验栏目属于当前站点且启用。 */
    private Long resolveCategoryId(Long siteId, Long categoryId, String categoryKey) {
        if (categoryId == null && !StringUtils.hasText(categoryKey)) {
            return null;
        }
        CmsCategory category = categoryId != null
                ? categoryMapper.selectById(categoryId)
                : categoryMapper.selectOne(new LambdaQueryWrapper<CmsCategory>()
                        .eq(CmsCategory::getSiteId, siteId)
                        .eq(CmsCategory::getCategoryKey, categoryKey.trim()));
        if (category == null
                || !Objects.equals(siteId, category.getSiteId())
                || !isCategoryVisible(category)) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "栏目不存在或已停用");
        }
        return category.getCategoryId();
    }

    /**
     * 栏目可见性：自身启用，且所有祖先都启用。
     *
     * <p>闭包表 {@code cms_category_closure} 包含自身（depth=0），这里把自身
     * 排除后只看祖先，避免和直接状态判断重复查询。栏目表被手工改坏、闭包
     * 缺失时按“无祖先”处理，宁可多显示也不误删内容。</p>
     */
    private boolean isCategoryVisible(CmsCategory category) {
        if (category == null || !CommonConstants.STATUS_NORMAL.equals(category.getStatus())) {
            return false;
        }
        List<Long> ancestorIds = closureMapper.selectAncestorIds(category.getCategoryId());
        if (ancestorIds == null || ancestorIds.isEmpty()) {
            return true;
        }
        List<Long> parentIds = ancestorIds.stream()
                .filter(id -> !Objects.equals(id, category.getCategoryId()))
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return true;
        }
        for (CmsCategory parent : categoryMapper.selectBatchIds(parentIds)) {
            if (!CommonConstants.STATUS_NORMAL.equals(parent.getStatus())) {
                return false;
            }
        }
        return true;
    }

    /** 标签筛选：同样校验站点与启用状态，避免 A 站用 B 站标签。 */
    private Long resolveTagId(Long siteId, Long tagId, String tagKey) {
        if (tagId == null && !StringUtils.hasText(tagKey)) {
            return null;
        }
        CmsTag tag = tagId != null
                ? tagMapper.selectById(tagId)
                : tagMapper.selectOne(new LambdaQueryWrapper<CmsTag>()
                        .eq(CmsTag::getSiteId, siteId)
                        .eq(CmsTag::getTagKey, tagKey.trim()));
        if (tag == null
                || !Objects.equals(siteId, tag.getSiteId())
                || !CommonConstants.STATUS_NORMAL.equals(tag.getStatus())) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "标签不存在或已停用");
        }
        return tag.getTagId();
    }

    private List<Long> subtreeIds(Long categoryId) {
        List<Long> ids = closureMapper.selectSubtreeIds(categoryId);
        return ids == null || ids.isEmpty() ? Collections.singletonList(categoryId) : ids;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String value = keyword.trim();
        return value.length() > MAX_KEYWORD_LENGTH ? value.substring(0, MAX_KEYWORD_LENGTH) : value;
    }

    /**
     * 过滤停用 / 隐藏栏目并构建导航树。
     *
     * <p>停用栏目的后代全部剔除；隐藏栏目本身剔除，但可见子栏目保留，
     * 父级不在结果集中时自然成为根节点。</p>
     */
    static List<PortalCategoryVO> buildVisibleTree(List<CmsCategory> all) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CmsCategory> byId = new HashMap<>();
        for (CmsCategory category : all) {
            byId.put(category.getCategoryId(), category);
        }
        List<CmsCategory> visible = new ArrayList<>();
        for (CmsCategory category : all) {
            if (!CommonConstants.STATUS_NORMAL.equals(category.getStatus())
                    || !CommonConstants.STATUS_NORMAL.equals(category.getVisible())) {
                continue;
            }
            if (hasDisabledAncestor(category, byId)) {
                continue;
            }
            visible.add(category);
        }
        return buildTree(visible);
    }

    private static boolean hasDisabledAncestor(CmsCategory category, Map<Long, CmsCategory> byId) {
        Set<Long> visited = new HashSet<>();
        Long parentId = category.getParentId();
        while (parentId != null && parentId != 0L && visited.add(parentId)) {
            CmsCategory parent = byId.get(parentId);
            if (parent == null) {
                return false;
            }
            if (!CommonConstants.STATUS_NORMAL.equals(parent.getStatus())) {
                return true;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    static List<PortalCategoryVO> buildTree(List<CmsCategory> all) {
        Map<Long, PortalCategoryVO> nodeMap = new LinkedHashMap<>();
        for (CmsCategory category : all) {
            nodeMap.put(category.getCategoryId(), toCategoryNode(category));
        }
        List<PortalCategoryVO> roots = new ArrayList<>();
        for (CmsCategory category : all) {
            PortalCategoryVO node = nodeMap.get(category.getCategoryId());
            PortalCategoryVO parent = nodeMap.get(category.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        Comparator<PortalCategoryVO> comparator = Comparator
                .comparing((PortalCategoryVO item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(PortalCategoryVO::getCategoryId);
        roots.sort(comparator);
        for (PortalCategoryVO node : nodeMap.values()) {
            node.getChildren().sort(comparator);
        }
        return roots;
    }

    static PortalCategoryVO toCategoryNode(CmsCategory category) {
        PortalCategoryVO vo = new PortalCategoryVO();
        vo.setCategoryId(category.getCategoryId());
        vo.setParentId(category.getParentId());
        vo.setCategoryKey(category.getCategoryKey());
        vo.setCategoryName(category.getCategoryName());
        vo.setSlug(category.getSlug());
        vo.setPath(category.getPath());
        vo.setTemplate(category.getTemplate());
        vo.setIcon(category.getIcon());
        vo.setCover(category.getCover());
        vo.setSeoTitle(category.getSeoTitle());
        vo.setSeoKeywords(category.getSeoKeywords());
        vo.setSeoDescription(category.getSeoDescription());
        vo.setSort(category.getSort());
        return vo;
    }

    private PortalSiteVO toSite(CmsSite site) {
        PortalSiteVO vo = new PortalSiteVO();
        vo.setSiteKey(site.getSiteKey());
        vo.setSiteName(site.getSiteName());
        vo.setDomain(site.getDomain());
        vo.setLogo(site.getLogo());
        vo.setSeoTitle(site.getSeoTitle());
        vo.setSeoKeywords(site.getSeoKeywords());
        vo.setSeoDescription(site.getSeoDescription());
        vo.setTheme(site.getTheme());
        return vo;
    }

    private PortalTagVO toTag(CmsTag tag) {
        PortalTagVO vo = new PortalTagVO();
        vo.setTagId(tag.getTagId());
        vo.setTagKey(tag.getTagKey());
        vo.setTagName(tag.getTagName());
        return vo;
    }
}
