package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.portal.PortalArticleDetailVO;
import com.lingxiao.modules.cms.dto.portal.PortalCategoryVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryClosureMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户服务测试。
 *
 * <p>重点锁住公开接口的三条底线：草稿 / 下线 / 其它站点按不存在处理；
 * 停用栏目下的文章不可见；详情返回清洗后的正文并累加阅读量。
 * 列表 SQL 的条件（只出已发布、栏目子树、标签 EXISTS）由真实接口验收覆盖。</p>
 */
@DisplayName("CmsPortalService 门户服务")
class CmsPortalServiceTest {

    private CmsSiteService siteService;
    private CmsCategoryMapper categoryMapper;
    private CmsArticleMapper articleMapper;
    private CmsTagMapper tagMapper;
    private CmsArticleTagMapper articleTagMapper;
    private CmsCategoryClosureMapper closureMapper;
    private CmsContentExtService extService;
    private CmsPortalService service;

    @BeforeEach
    void setUp() {
        siteService = mock(CmsSiteService.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        articleMapper = mock(CmsArticleMapper.class);
        tagMapper = mock(CmsTagMapper.class);
        articleTagMapper = mock(CmsArticleTagMapper.class);
        closureMapper = mock(CmsCategoryClosureMapper.class);
        extService = mock(CmsContentExtService.class);
        service = new CmsPortalService(siteService, categoryMapper, articleMapper,
                tagMapper, articleTagMapper, closureMapper, extService);
        when(siteService.detailByKey("main")).thenReturn(site(1L, "main"));
    }

    @Test
    @DisplayName("详情：草稿按不存在处理，不累加阅读量")
    void rejectsDraftArticle() {
        when(articleMapper.selectById(5L)).thenReturn(article(5L, 1L, 9L, CmsArticleService.STATUS_DRAFT));

        ServiceException error = catchThrowableOfType(
                () -> service.article("main", 5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("文章不存在");
        verify(articleMapper, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("详情：其它站点的文章按不存在处理")
    void rejectsArticleFromAnotherSite() {
        when(articleMapper.selectById(5L))
                .thenReturn(article(5L, 2L, 9L, CmsArticleService.STATUS_PUBLISHED));

        ServiceException error = catchThrowableOfType(
                () -> service.article("main", 5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("文章不存在");
        verify(articleMapper, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("详情：所属栏目停用后文章不可见")
    void rejectsArticleUnderDisabledCategory() {
        when(articleMapper.selectById(5L))
                .thenReturn(article(5L, 1L, 9L, CmsArticleService.STATUS_PUBLISHED));
        CmsCategory category = category(9L, 1L, 0L, "news", "1", "0");
        when(categoryMapper.selectById(9L)).thenReturn(category);

        ServiceException error = catchThrowableOfType(
                () -> service.article("main", 5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("文章不存在");
        verify(articleMapper, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("详情：祖先栏目停用后文章不可见")
    void rejectsArticleUnderDisabledAncestor() {
        when(articleMapper.selectById(5L))
                .thenReturn(article(5L, 1L, 9L, CmsArticleService.STATUS_PUBLISHED));
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 1L, 8L, "child", "0", "0"));
        when(closureMapper.selectAncestorIds(9L)).thenReturn(Arrays.asList(9L, 8L));
        when(categoryMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(
                category(8L, 1L, 0L, "disabled-parent", "1", "0")));

        ServiceException error = catchThrowableOfType(
                () -> service.article("main", 5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("文章不存在");
        verify(articleMapper, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("详情：正文清洗、标签过滤、阅读量 +1")
    void detailSanitizesContentAndCountsView() {
        CmsArticle article = article(5L, 1L, 9L, CmsArticleService.STATUS_PUBLISHED);
        article.setContent("<p>正文</p><script>alert(1)</script>");
        article.setViewCount(7L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 1L, 0L, "news", "0", "0"));
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Arrays.asList(3L, 4L, 5L));
        when(tagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                tag(3L, 1L, "java", "0"),
                tag(4L, 1L, "disabled", "1"),
                tag(5L, 2L, "other-site", "0")));

        PortalArticleDetailVO detail = service.article("main", 5L);

        assertThat(detail.getContent()).contains("<p>正文</p>").doesNotContain("<script");
        assertThat(detail.getViewCount()).isEqualTo(8L);
        assertThat(detail.getCategory().getCategoryName()).isEqualTo("news");
        assertThat(detail.getTags()).extracting("tagId").containsExactly(3L);
        verify(articleMapper).increaseViewCount(5L);
    }

    @Test
    @DisplayName("导航树：停用栏目整棵子树剔除，隐藏栏目的可见子栏目提升为根")
    void visibleTreePrunesDisabledAndHiddenNodes() {
        List<CmsCategory> all = Arrays.asList(
                category(1L, 1L, 0L, "root", "0", "0", 1),
                category(2L, 1L, 1L, "hidden", "0", "1", 2),
                category(3L, 1L, 2L, "child-of-hidden", "0", "0", 3),
                category(4L, 1L, 1L, "disabled", "1", "0", 4),
                category(5L, 1L, 4L, "child-of-disabled", "0", "0", 5),
                category(6L, 1L, 0L, "other-root", "0", "0", 0),
                category(7L, 1L, 1L, "visible-child", "0", "0", 2));

        List<PortalCategoryVO> tree = CmsPortalService.buildVisibleTree(all);

        assertThat(tree).extracting("categoryId").containsExactly(6L, 1L, 3L);
        assertThat(tree.get(1).getChildren()).extracting("categoryId").containsExactly(7L);
        List<Long> flat = tree.stream()
                .flatMap(node -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(node.getCategoryId()),
                        node.getChildren().stream().map(PortalCategoryVO::getCategoryId)))
                .collect(Collectors.toList());
        assertThat(flat).doesNotContain(2L, 4L, 5L);
    }

    @Test
    @DisplayName("slug 详情：空 slug 直接拒绝")
    void rejectsBlankSlug() {
        ServiceException error = catchThrowableOfType(
                () -> service.articleBySlug("main", "  "), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        verify(siteService, never()).detailByKey(any());
    }

    @Test
    @DisplayName("站点列表：只映射白名单字段")
    void mapsSiteFields() {
        CmsSite site = site(1L, "main");
        site.setSiteName("主站");
        site.setDomain("www.example.com");
        site.setSeoTitle("标题");
        site.setRemark("内部备注");
        when(siteService.listEnabled()).thenReturn(Collections.singletonList(site));

        assertThat(service.sites()).hasSize(1);
        assertThat(service.sites().get(0).getSiteKey()).isEqualTo("main");
        assertThat(service.sites().get(0).getSiteName()).isEqualTo("主站");
        assertThat(service.sites().get(0).getDomain()).isEqualTo("www.example.com");
    }

    private CmsSite site(Long id, String key) {
        CmsSite site = new CmsSite();
        site.setSiteId(id);
        site.setSiteKey(key);
        site.setSiteName(key);
        site.setStatus("0");
        return site;
    }

    private CmsArticle article(Long id, Long siteId, Long categoryId, String status) {
        CmsArticle article = new CmsArticle();
        article.setArticleId(id);
        article.setSiteId(siteId);
        article.setCategoryId(categoryId);
        article.setTitle("标题");
        article.setStatus(status);
        return article;
    }

    private CmsCategory category(Long id, Long siteId, Long parentId, String name,
                                 String status, String visible) {
        return category(id, siteId, parentId, name, status, visible, 0);
    }

    private CmsCategory category(Long id, Long siteId, Long parentId, String name,
                                 String status, String visible, int sort) {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(id);
        category.setSiteId(siteId);
        category.setParentId(parentId);
        category.setCategoryKey(name);
        category.setCategoryName(name);
        category.setStatus(status);
        category.setVisible(visible);
        category.setSort(sort);
        return category;
    }

    private CmsTag tag(Long id, Long siteId, String key, String status) {
        CmsTag tag = new CmsTag();
        tag.setTagId(id);
        tag.setSiteId(siteId);
        tag.setTagKey(key);
        tag.setTagName(key);
        tag.setStatus(status);
        return tag;
    }
}
