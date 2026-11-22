package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章服务测试。
 *
 * <p>创建 / 更新里的审计字段依赖 Sa-Token 登录上下文，单元测试不伪造登录态；
 * 这里覆盖不依赖上下文的边界：读取侧清洗、删除顺序、跨站点栏目与 slug 冲突。
 * “新建强制草稿、写入侧清洗”由真实接口验收覆盖。</p>
 */
@DisplayName("CmsArticleService 文章服务")
class CmsArticleServiceTest {

    private CmsArticleMapper articleMapper;
    private CmsArticleTagMapper articleTagMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsTagMapper tagMapper;
    private CmsSiteService siteService;
    private CmsCategoryService categoryService;
    private CmsArticleService service;

    @BeforeEach
    void setUp() {
        articleMapper = mock(CmsArticleMapper.class);
        articleTagMapper = mock(CmsArticleTagMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        tagMapper = mock(CmsTagMapper.class);
        siteService = mock(CmsSiteService.class);
        categoryService = mock(CmsCategoryService.class);
        service = new CmsArticleService(articleMapper, articleTagMapper, categoryMapper,
                tagMapper, siteService, categoryService);
    }

    @Test
    @DisplayName("读取详情：历史脏 HTML 也清洗，并回填标签")
    void detailSanitizesContentAndLoadsTags() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setContent("<p>ok</p><script>alert(1)</script>");
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Arrays.asList(7L, 8L));

        CmsArticle detail = service.detail(5L);

        assertThat(detail.getContent()).contains("<p>ok</p>").doesNotContain("<script");
        assertThat(detail.getTagIds()).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("删除：先删标签关联，再删正文")
    void deleteRemovesTagRelationsFirst() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);

        service.delete(5L);

        InOrder order = inOrder(articleTagMapper, articleMapper);
        order.verify(articleTagMapper).deleteByArticleId(5L);
        order.verify(articleMapper).hardDeleteById(5L);
    }

    @Test
    @DisplayName("新建：栏目不属于当前站点时拒绝，不落库")
    void rejectsCategoryFromAnotherSite() {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(2L);
        when(categoryMapper.selectById(9L)).thenReturn(category);

        CmsArticle request = new CmsArticle();
        request.setSiteId(1L);
        request.setCategoryId(9L);
        request.setTitle("标题");

        ServiceException error = catchThrowableOfType(() -> service.create(request), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("栏目不属于当前站点");
        verify(articleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新建：slug 站点内重复时拒绝")
    void rejectsDuplicateSlug() {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(articleMapper.selectCount(any())).thenReturn(1L);

        CmsArticle request = new CmsArticle();
        request.setSiteId(1L);
        request.setCategoryId(9L);
        request.setTitle("标题");
        request.setSlug("hello");

        ServiceException error = catchThrowableOfType(() -> service.create(request), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("slug 已存在");
        verify(articleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("文章不存在：详情返回 404 语义异常")
    void detailMissingArticle() {
        when(articleMapper.selectById(404L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(() -> service.detail(404L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("文章不存在");
    }
}
