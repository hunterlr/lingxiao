package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.web.util.HtmlSanitizer;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * CMS 文章服务。
 *
 * <p><b>正文安全：</b>正文由 WangEditor 提交 HTML，服务端用
 * {@link HtmlSanitizer} 在写入与读取两侧都清洗（与公告一致），
 * 不依赖前端、也不依赖数据库。</p>
 *
 * <p><b>状态流转：</b>新建一律草稿；普通编辑只改内容，不改状态与发布时间；
 * 发布 / 下线走独立接口。这样“改个错别字”不会把已下线文章悄悄重新发布。</p>
 *
 * <p><b>列表不查正文：</b>{@code content} 是 longtext，列表页用不到；
 * 分页查询显式排除该列，避免一页几十篇正文把内存和网络拖慢。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsArticleService {

    /** 文章状态：草稿 / 已发布 / 已下线 */
    public static final String STATUS_DRAFT = "0";
    public static final String STATUS_PUBLISHED = "1";
    public static final String STATUS_OFFLINE = "2";

    private final CmsArticleMapper articleMapper;
    private final CmsArticleTagMapper articleTagMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsSiteService siteService;
    private final CmsCategoryService categoryService;

    public PageResult<CmsArticle> page(long pageNum, long pageSize, Long siteId, Long categoryId,
                                       Long tagId, String keyword, String status) {
        Page<CmsArticle> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<CmsArticle> wrapper = new LambdaQueryWrapper<CmsArticle>()
                .select(CmsArticle.class, field -> !"content".equals(field.getColumn()))
                .eq(siteId != null, CmsArticle::getSiteId, siteId)
                .eq(StringUtils.hasText(status), CmsArticle::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(CmsArticle::getTitle, keyword)
                        .or().like(CmsArticle::getSummary, keyword));
        if (categoryId != null) {
            // 选父栏目时把子栏目文章一并列出来
            wrapper.in(CmsArticle::getCategoryId, categoryService.subtreeIds(categoryId));
        }
        if (tagId != null) {
            // 用 EXISTS 子查询而不是 JOIN：分页 count 不会被一对多关联放大
            wrapper.apply("EXISTS (SELECT 1 FROM cms_article_tag t "
                    + "WHERE t.article_id = cms_article.article_id AND t.tag_id = {0})", tagId);
        }
        wrapper.orderByDesc(CmsArticle::getCreateTime);
        Page<CmsArticle> result = articleMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CmsArticle detail(Long articleId) {
        CmsArticle article = mustGet(articleId);
        article.setContent(HtmlSanitizer.clean(article.getContent()));
        article.setTagIds(articleTagMapper.selectTagIds(articleId));
        if (article.getTagIds() == null) {
            article.setTagIds(new ArrayList<>());
        }
        return article;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsArticle article) {
        siteService.mustGet(article.getSiteId());
        CmsCategory category = mustCategory(article.getCategoryId(), article.getSiteId());
        ensureSlugUnique(article.getSiteId(), article.getSlug(), null);

        article.setArticleId(null);
        article.setCategoryId(category.getCategoryId());
        article.setTitle(article.getTitle().trim());
        article.setContent(HtmlSanitizer.clean(article.getContent()));
        article.setStatus(STATUS_DRAFT);
        article.setPublishTime(null);
        article.setViewCount(0L);
        article.setSort(article.getSort() == null ? 0 : article.getSort());
        fillAuthor(article);
        article.setCreateBy(LoginHelper.getUserId());
        articleMapper.insert(article);

        replaceTags(article.getArticleId(), article.getSiteId(), article.getTagIds());
        return article.getArticleId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long articleId, CmsArticle article) {
        CmsArticle existing = mustGet(articleId);
        // 站点不允许通过编辑文章改动：跨站点移动内容应走“移动”接口，避免栏目 / 标签错配
        Long siteId = existing.getSiteId();
        CmsCategory category = mustCategory(article.getCategoryId(), siteId);
        ensureSlugUnique(siteId, article.getSlug(), articleId);

        existing.setCategoryId(category.getCategoryId());
        existing.setTitle(article.getTitle().trim());
        existing.setSlug(article.getSlug());
        existing.setSummary(article.getSummary());
        existing.setCover(article.getCover());
        existing.setContent(HtmlSanitizer.clean(article.getContent()));
        existing.setSource(article.getSource());
        existing.setSourceUrl(article.getSourceUrl());
        existing.setSort(article.getSort() == null ? existing.getSort() : article.getSort());
        existing.setRemark(article.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        articleMapper.updateById(existing);

        replaceTags(articleId, siteId, article.getTagIds());
    }

    /** 发布：草稿 / 已下线 → 已发布；首次发布写 publishTime，重新发布保留原时间。 */
    public void publish(Long articleId) {
        CmsArticle existing = mustGet(articleId);
        LambdaUpdateWrapper<CmsArticle> update = new LambdaUpdateWrapper<CmsArticle>()
                .eq(CmsArticle::getArticleId, articleId)
                .set(CmsArticle::getStatus, STATUS_PUBLISHED)
                .set(CmsArticle::getUpdateBy, LoginHelper.getUserId());
        if (existing.getPublishTime() == null) {
            update.set(CmsArticle::getPublishTime, LocalDateTime.now());
        }
        articleMapper.update(null, update);
    }

    /** 下线：已发布 → 已下线；草稿调用也允许，结果就是“不会出现在门户”。 */
    public void offline(Long articleId) {
        mustGet(articleId);
        articleMapper.update(null, new LambdaUpdateWrapper<CmsArticle>()
                .eq(CmsArticle::getArticleId, articleId)
                .set(CmsArticle::getStatus, STATUS_OFFLINE)
                .set(CmsArticle::getUpdateBy, LoginHelper.getUserId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long articleId) {
        mustGet(articleId);
        articleTagMapper.deleteByArticleId(articleId);
        articleMapper.hardDeleteById(articleId);
    }

    // ==================== 内部方法 ====================

    private CmsArticle mustGet(Long articleId) {
        CmsArticle article = articleId == null ? null : articleMapper.selectById(articleId);
        if (article == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private CmsCategory mustCategory(Long categoryId, Long siteId) {
        CmsCategory category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "栏目不存在");
        }
        if (!Objects.equals(category.getSiteId(), siteId)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "栏目不属于当前站点");
        }
        return category;
    }

    /** 首次创建时把当前登录人写成作者快照；后端已指定作者时不覆盖。 */
    private void fillAuthor(CmsArticle article) {
        if (article.getAuthorId() == null) {
            article.setAuthorId(LoginHelper.getUserId());
        }
        if (!StringUtils.hasText(article.getAuthorName())) {
            String nickname = LoginHelper.getNickname();
            article.setAuthorName(StringUtils.hasText(nickname) ? nickname : LoginHelper.getUsername());
        }
    }

    private void ensureSlugUnique(Long siteId, String slug, Long excludeId) {
        if (!StringUtils.hasText(slug)) {
            return;
        }
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<CmsArticle>()
                .eq(CmsArticle::getSiteId, siteId)
                .eq(CmsArticle::getSlug, slug.trim())
                .ne(excludeId != null, CmsArticle::getArticleId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章 slug 已存在：" + slug);
        }
    }

    /**
     * 替换文章标签：先删旧关联，再按去重后的新列表插入。
     * 标签必须属于同一站点，防止把 A 站标签挂到 B 站文章上。
     */
    private void replaceTags(Long articleId, Long siteId, List<Long> tagIds) {
        articleTagMapper.deleteByArticleId(articleId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                distinct.add(tagId);
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        List<CmsTag> tags = tagMapper.selectBatchIds(distinct);
        if (tags.size() != distinct.size()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "存在无效的标签");
        }
        for (CmsTag tag : tags) {
            if (!Objects.equals(tag.getSiteId(), siteId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "标签不属于当前站点");
            }
        }
        articleTagMapper.insertBatch(articleId, new ArrayList<>(distinct));
    }
}
