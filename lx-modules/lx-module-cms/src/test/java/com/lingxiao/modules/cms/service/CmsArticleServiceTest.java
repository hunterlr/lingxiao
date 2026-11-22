package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.CmsArticleRecycleVO;
import com.lingxiao.modules.cms.dto.CmsArticleVersionVO;
import com.lingxiao.modules.cms.entity.CmsArticle;
import com.lingxiao.modules.cms.entity.CmsArticleVersion;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsArticleVersionMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private CmsArticleVersionMapper versionMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsTagMapper tagMapper;
    private CmsSiteService siteService;
    private CmsCategoryService categoryService;
    private CmsArticleService service;

    @BeforeEach
    void setUp() {
        articleMapper = mock(CmsArticleMapper.class);
        articleTagMapper = mock(CmsArticleTagMapper.class);
        versionMapper = mock(CmsArticleVersionMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        tagMapper = mock(CmsTagMapper.class);
        siteService = mock(CmsSiteService.class);
        categoryService = mock(CmsCategoryService.class);
        service = new CmsArticleService(articleMapper, articleTagMapper, versionMapper,
                categoryMapper, tagMapper, siteService, categoryService);
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

        CmsArticle restored = new CmsArticle();
        restored.setArticleId(5L);
        restored.setSiteId(1L);
        restored.setTitle("恢复文章");
        restored.setSlug("hello");
        restored.setStatus(CmsArticleService.STATUS_DRAFT);
        when(articleMapper.selectById(5L)).thenReturn(restored);
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.emptyList());
        when(versionMapper.selectLatestHash(5L)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(5L)).thenReturn(0);
        when(versionMapper.insert(any())).thenReturn(1);

        service.restore(5L);

        verify(articleMapper).restoreById(eq(5L), isNull(), any());
        ArgumentCaptor<CmsArticleVersion> captor =
                ArgumentCaptor.forClass(CmsArticleVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(CmsArticleService.VERSION_RESTORE);
    }

    @Test
    @DisplayName("彻底删除：先清标签关联，再物理删除回收站记录")
    void purgeRemovesTagsThenArticle() {
        CmsArticle deleted = new CmsArticle();
        deleted.setArticleId(5L);
        when(articleMapper.selectDeletedById(5L)).thenReturn(deleted);
        when(articleMapper.hardDeleteDeletedById(5L)).thenReturn(1);

        service.purge(5L);

        InOrder order = inOrder(articleTagMapper, versionMapper, articleMapper);
        order.verify(articleTagMapper).deleteByArticleId(5L);
        order.verify(versionMapper).deleteByArticleId(5L);
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
    @DisplayName("到点扫描：只统计真正抢占成功的文章，成功才写发布快照")
    void publishDueScheduledCountsClaimedRows() {
        when(articleMapper.selectDueScheduledIds(any(), eq(200)))
                .thenReturn(Arrays.asList(5L, 6L));
        when(articleMapper.publishScheduledById(eq(5L), any())).thenReturn(1);
        when(articleMapper.publishScheduledById(eq(6L), any())).thenReturn(0);

        CmsArticle published = new CmsArticle();
        published.setArticleId(5L);
        published.setSiteId(1L);
        published.setTitle("到点文章");
        published.setStatus(CmsArticleService.STATUS_PUBLISHED);
        when(articleMapper.selectById(5L)).thenReturn(published);
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.emptyList());
        when(versionMapper.selectLatestHash(5L)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(5L)).thenReturn(0);
        when(versionMapper.insert(any())).thenReturn(1);

        int publishedCount = service.publishDueScheduled(200);

        assertThat(publishedCount).isEqualTo(1);
        verify(articleMapper).publishScheduledById(eq(5L), any());
        verify(articleMapper).publishScheduledById(eq(6L), any());
        ArgumentCaptor<CmsArticleVersion> captor =
                ArgumentCaptor.forClass(CmsArticleVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(CmsArticleService.VERSION_PUBLISH);
        assertThat(captor.getValue().getStatus()).isEqualTo(CmsArticleService.STATUS_PUBLISHED);
    }

    @Test
    @DisplayName("到点扫描：没有候选时不发 UPDATE")
    void publishDueScheduledWithoutCandidates() {
        when(articleMapper.selectDueScheduledIds(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertThat(service.publishDueScheduled(200)).isZero();
        verify(articleMapper, never()).publishScheduledById(anyLong(), any());
    }

    /* ==================== 内容版本 ==================== */

    @Test
    @DisplayName("版本列表：校验文章存在后透传分页")
    void versionPagePassesThrough() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        CmsArticleVersionVO row = new CmsArticleVersionVO();
        row.setVersionId(50L);
        row.setVersionNo(3);
        Page<CmsArticleVersionVO> dbPage = new Page<>(1, 20);
        dbPage.setTotal(1);
        dbPage.setRecords(Collections.singletonList(row));
        when(versionMapper.selectVersionPage(any(), eq(5L))).thenReturn(dbPage);

        CmsArticleVersionVO result = service.versionPage(5L, 1, 20).getRecords().get(0);

        assertThat(result.getVersionNo()).isEqualTo(3);
        verify(versionMapper).selectVersionPage(any(), eq(5L));
    }

    @Test
    @DisplayName("版本详情：读取侧清洗正文并回填标签")
    void versionDetailSanitizesContent() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        CmsArticleVersion version = new CmsArticleVersion();
        version.setVersionId(50L);
        version.setArticleId(5L);
        version.setContent("<p>old</p><script>x</script>");
        version.setTagIds("7,8");
        when(versionMapper.selectById(50L)).thenReturn(version);

        CmsArticleVersion detail = service.versionDetail(5L, 50L);

        assertThat(detail.getContent()).contains("<p>old</p>").doesNotContain("<script");
        assertThat(detail.getTagIdList()).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("版本详情：版本不属于该文章时按不存在处理")
    void versionDetailRejectsAnotherArticle() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        CmsArticleVersion version = new CmsArticleVersion();
        version.setVersionId(50L);
        version.setArticleId(6L);
        when(versionMapper.selectById(50L)).thenReturn(version);

        ServiceException error = catchThrowableOfType(
                () -> service.versionDetail(5L, 50L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("版本不存在");
    }

    @Test
    @DisplayName("回滚：版本不存在时 404")
    void rollbackMissingVersion() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        when(versionMapper.selectById(50L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(
                () -> service.rollback(5L, 50L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("版本不存在");
        verify(articleMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("回滚：版本中的栏目已删除时拒绝")
    void rollbackRejectsMissingCategory() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setSiteId(1L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        CmsArticleVersion version = new CmsArticleVersion();
        version.setVersionId(50L);
        version.setArticleId(5L);
        version.setCategoryId(8L);
        when(versionMapper.selectById(50L)).thenReturn(version);
        when(categoryMapper.selectById(8L)).thenReturn(null);

        ServiceException error = catchThrowableOfType(
                () -> service.rollback(5L, 50L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("栏目已删除");
        verify(articleMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("回滚：slug 被其它文章占用时拒绝")
    void rollbackRejectsSlugConflict() {
        CmsArticle article = new CmsArticle();
        article.setArticleId(5L);
        article.setSiteId(1L);
        when(articleMapper.selectById(5L)).thenReturn(article);
        CmsArticleVersion version = new CmsArticleVersion();
        version.setVersionId(50L);
        version.setArticleId(5L);
        version.setCategoryId(8L);
        version.setSlug("old-slug");
        when(versionMapper.selectById(50L)).thenReturn(version);
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, 1L));
        when(articleMapper.countBySlugIncludingDeleted(1L, "old-slug", 5L)).thenReturn(1L);

        ServiceException error = catchThrowableOfType(
                () -> service.rollback(5L, 50L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("slug 已存在");
        verify(articleMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("回滚：覆盖内容、回到草稿并写一条 rollback 快照")
    void rollbackAppliesVersionAndSnapshots() {
        CmsArticle current = new CmsArticle();
        current.setArticleId(5L);
        current.setSiteId(1L);
        current.setCategoryId(9L);
        current.setTitle("当前标题");
        current.setSlug("current");
        current.setStatus(CmsArticleService.STATUS_PUBLISHED);
        when(articleMapper.selectById(5L)).thenReturn(current);

        CmsArticleVersion version = new CmsArticleVersion();
        version.setVersionId(50L);
        version.setArticleId(5L);
        version.setCategoryId(8L);
        version.setTitle("旧标题");
        version.setSlug("old-slug");
        version.setSummary("旧摘要");
        version.setContent("<p>旧正文</p><script>x</script>");
        version.setSource("旧来源");
        version.setTagIds("7,8");
        when(versionMapper.selectById(50L)).thenReturn(version);
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, 1L));
        when(articleMapper.countBySlugIncludingDeleted(1L, "old-slug", 5L)).thenReturn(0L);
        when(articleMapper.updateById(any())).thenReturn(1);
        when(tagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(tag(7L, 1L), tag(8L, 1L)));
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Arrays.asList(8L, 7L));
        when(versionMapper.selectLatestHash(5L)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(5L)).thenReturn(3);
        when(versionMapper.insert(any())).thenReturn(1);

        service.rollback(5L, 50L);

        ArgumentCaptor<CmsArticle> articleCaptor = ArgumentCaptor.forClass(CmsArticle.class);
        verify(articleMapper).updateById(articleCaptor.capture());
        CmsArticle saved = articleCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CmsArticleService.STATUS_DRAFT);
        assertThat(saved.getTitle()).isEqualTo("旧标题");
        assertThat(saved.getSlug()).isEqualTo("old-slug");
        assertThat(saved.getContent()).contains("旧正文").doesNotContain("<script");

        ArgumentCaptor<CmsArticleVersion> versionCaptor =
                ArgumentCaptor.forClass(CmsArticleVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getChangeType()).isEqualTo(CmsArticleService.VERSION_ROLLBACK);
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(4);
    }

    @Test
    @DisplayName("编辑：内容变化后写 edit 快照并清洗正文")
    void updateCreatesEditSnapshot() {
        CmsArticle existing = new CmsArticle();
        existing.setArticleId(5L);
        existing.setSiteId(1L);
        existing.setCategoryId(9L);
        existing.setTitle("旧标题");
        existing.setSlug("slug");
        existing.setStatus(CmsArticleService.STATUS_DRAFT);
        existing.setContent("<p>v1</p>");
        when(articleMapper.selectById(5L)).thenReturn(existing);
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 1L));
        when(articleMapper.countBySlugIncludingDeleted(1L, "slug", 5L)).thenReturn(0L);
        when(articleMapper.updateById(any())).thenReturn(1);
        when(tagMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(tag(7L, 1L)));
        when(articleTagMapper.selectTagIds(5L)).thenReturn(Collections.singletonList(7L));
        when(versionMapper.selectLatestHash(5L)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(5L)).thenReturn(0);
        when(versionMapper.insert(any())).thenReturn(1);

        CmsArticle request = new CmsArticle();
        request.setCategoryId(9L);
        request.setTitle("新标题");
        request.setSlug("slug");
        request.setContent("<p>v2</p><script>x</script>");
        request.setTagIds(Collections.singletonList(7L));

        service.update(5L, request);

        ArgumentCaptor<CmsArticleVersion> captor =
                ArgumentCaptor.forClass(CmsArticleVersion.class);
        verify(versionMapper).insert(captor.capture());
        CmsArticleVersion snapshot = captor.getValue();
        assertThat(snapshot.getChangeType()).isEqualTo(CmsArticleService.VERSION_EDIT);
        assertThat(snapshot.getVersionNo()).isEqualTo(1);
        assertThat(snapshot.getTitle()).isEqualTo("新标题");
        assertThat(snapshot.getContent()).contains("v2").doesNotContain("<script");
        assertThat(snapshot.getTagIds()).isEqualTo("7");
    }

    private CmsCategory category(Long id, Long siteId) {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(id);
        category.setSiteId(siteId);
        return category;
    }

    private CmsTag tag(Long id, Long siteId) {
        CmsTag tag = new CmsTag();
        tag.setTagId(id);
        tag.setSiteId(siteId);
        return tag;
    }
}
