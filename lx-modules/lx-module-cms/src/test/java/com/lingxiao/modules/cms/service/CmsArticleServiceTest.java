package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.CmsArticleRecycleVO;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @DisplayName("删除：进回收站，保留标签关联，不物理删除")
    void deleteMovesArticleToRecycleBin() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(articleMapper.softDeleteById(eq(5L), isNull(), anyString(), any())).thenReturn(1);

        service.delete(5L);

        verify(articleMapper).softDeleteById(eq(5L), isNull(), eq("system"), any());
        verify(articleTagMapper, never()).deleteByArticleId(anyLong());
        verify(articleMapper, never()).hardDeleteById(anyLong());
    }

    @Test
    @DisplayName("回收站列表：透传分页并保持站点 / 关键字条件")
    void recyclePagePassesFilters() {
        CmsArticleRecycleVO row = new CmsArticleRecycleVO();
        row.setArticleId(9L);
        Page<CmsArticleRecycleVO> dbPage = new Page<>(1, 20);
        dbPage.setTotal(1);
        dbPage.setRecords(Collections.singletonList(row));
        when(articleMapper.selectRecyclePage(any(), eq(3L), eq("%旧稿%"))).thenReturn(dbPage);

        CmsArticleRecycleVO result = service.recyclePage(1, 20, 3L, "  旧稿  ").getRecords().get(0);

        assertThat(result.getArticleId()).isEqualTo(9L);
        verify(articleMapper).selectRecyclePage(any(), eq(3L), eq("%旧稿%"));
    }

    @Test
    @DisplayName("恢复：回收站没有该文章时 404")
    void restoreMissingArticle() {
        when(articleMapper.selectDeletedById(404L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(() -> service.restore(404L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("回收站中不存在");
        verify(articleMapper, never()).restoreById(anyLong(), any(), any());
    }

    @Test
    @DisplayName("恢复：所属栏目已删除时拒绝，并提示直接彻底删除")
    void restoreRejectsMissingCategory() {
        CmsArticle deleted = new CmsArticle();
        deleted.setArticleId(5L);
        deleted.setSiteId(1L);
        deleted.setCategoryId(9L);
        when(articleMapper.selectDeletedById(5L)).thenReturn(deleted);
        when(categoryMapper.selectById(9L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(() -> service.restore(5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("所属栏目已删除");
        verify(articleMapper, never()).restoreById(anyLong(), any(), any());
    }

    @Test
    @DisplayName("恢复：slug 被其它文章占用时拒绝")
    void restoreRejectsSlugConflict() {
        CmsArticle deleted = new CmsArticle();
        deleted.setArticleId(5L);
        deleted.setSiteId(1L);
        deleted.setCategoryId(9L);
        deleted.setSlug("hello");
        when(articleMapper.selectDeletedById(5L)).thenReturn(deleted);
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 1L));
        when(articleMapper.countBySlugIncludingDeleted(1L, "hello", 5L)).thenReturn(1L);

        ServiceException error = catchThrowableOfType(() -> service.restore(5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("slug");
        verify(articleMapper, never()).restoreById(anyLong(), any(), any());
    }

    @Test
    @DisplayName("恢复：校验通过后回到草稿")
    void restoreMovesBackToDraft() {
        CmsArticle deleted = new CmsArticle();
        deleted.setArticleId(5L);
        deleted.setSiteId(1L);
        deleted.setCategoryId(9L);
        deleted.setSlug("hello");
        when(articleMapper.selectDeletedById(5L)).thenReturn(deleted);
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 1L));
        when(articleMapper.countBySlugIncludingDeleted(1L, "hello", 5L)).thenReturn(0L);
        when(articleMapper.restoreById(eq(5L), isNull(), any())).thenReturn(1);

        service.restore(5L);

        verify(articleMapper).restoreById(eq(5L), isNull(), any());
    }

    @Test
    @DisplayName("彻底删除：先清标签关联，再物理删除回收站记录")
    void purgeRemovesTagsThenArticle() {
        CmsArticle deleted = new CmsArticle();
        deleted.setArticleId(5L);
        when(articleMapper.selectDeletedById(5L)).thenReturn(deleted);
        when(articleMapper.hardDeleteDeletedById(5L)).thenReturn(1);

        service.purge(5L);

        InOrder order = inOrder(articleTagMapper, articleMapper);
        order.verify(articleTagMapper).deleteByArticleId(5L);
        order.verify(articleMapper).hardDeleteDeletedById(5L);
    }

    @Test
    @DisplayName("彻底删除：只允许删回收站记录")
    void purgeRejectsActiveArticle() {
        when(articleMapper.selectDeletedById(5L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(() -> service.purge(5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("回收站中不存在");
        verify(articleTagMapper, never()).deleteByArticleId(anyLong());
        verify(articleMapper, never()).hardDeleteDeletedById(anyLong());
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
        when(articleMapper.countBySlugIncludingDeleted(1L, "hello", null)).thenReturn(1L);

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

    /* ==================== 定时发布 ==================== */

    @Test
    @DisplayName("定时发布：时间早于当前时拒绝，不改状态")
    void scheduleRejectsPastTime() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(article);

        ServiceException error = catchThrowableOfType(
                () -> service.schedule(5L, LocalDateTime.now().minusMinutes(1)), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("晚于当前时间");
        verify(articleMapper, never()).scheduleById(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("定时发布：已发布文章必须先下线")
    void scheduleRejectsPublishedArticle() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setStatus(CmsArticleService.STATUS_PUBLISHED);
        when(articleMapper.selectById(5L)).thenReturn(article);

        ServiceException error = catchThrowableOfType(
                () -> service.schedule(5L, LocalDateTime.now().plusHours(1)), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("先下线");
        verify(articleMapper, never()).scheduleById(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("定时发布：草稿排期后写状态 3 与计划时间")
    void scheduleMarksArticleAsScheduled() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(articleMapper.scheduleById(eq(5L), any(), isNull(), any())).thenReturn(1);

        service.schedule(5L, LocalDateTime.now().plusHours(2));

        verify(articleMapper).scheduleById(eq(5L), any(), isNull(), any());
    }

    @Test
    @DisplayName("取消定时：非定时状态拒绝")
    void cancelScheduleRejectsDraft() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(article);

        ServiceException error = catchThrowableOfType(
                () -> service.cancelSchedule(5L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("只有定时待发布");
        verify(articleMapper, never()).cancelScheduleById(anyLong(), any(), any());
    }

    @Test
    @DisplayName("取消定时：定时待发布回到草稿")
    void cancelScheduleMovesBackToDraft() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setStatus(CmsArticleService.STATUS_SCHEDULED);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(articleMapper.cancelScheduleById(eq(5L), isNull(), any())).thenReturn(1);

        service.cancelSchedule(5L);

        verify(articleMapper).cancelScheduleById(eq(5L), isNull(), any());
    }

    @Test
    @DisplayName("到点扫描：只统计真正抢占成功的文章")
    void publishDueScheduledCountsClaimedRows() {
        when(articleMapper.selectDueScheduledIds(any(), eq(200)))
                .thenReturn(Arrays.asList(5L, 6L));
        when(articleMapper.publishScheduledById(eq(5L), any())).thenReturn(1);
        when(articleMapper.publishScheduledById(eq(6L), any())).thenReturn(0);

        int published = service.publishDueScheduled(200);

        assertThat(published).isEqualTo(1);
        verify(articleMapper).publishScheduledById(eq(5L), any());
        verify(articleMapper).publishScheduledById(eq(6L), any());
    }

    @Test
    @DisplayName("到点扫描：没有候选时不发 UPDATE")
    void publishDueScheduledWithoutCandidates() {
        when(articleMapper.selectDueScheduledIds(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertThat(service.publishDueScheduled(200)).isZero();
        verify(articleMapper, never()).publishScheduledById(anyLong(), any());
    }

    private CmsCategory category(Long id, Long siteId) {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(id);
        category.setSiteId(siteId);
        return category;
    }
}
