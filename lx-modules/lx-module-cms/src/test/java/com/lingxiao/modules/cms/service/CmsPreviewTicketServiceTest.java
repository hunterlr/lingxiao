package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.dto.CmsPreviewTicketVO;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.entity.CmsPreviewTicket;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import com.lingxiao.modules.cms.mapper.CmsPreviewTicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预览票据测试：目标校验、一次性消费、过期 / 已使用拒绝。
 */
@DisplayName("CmsPreviewTicketService 预览票据")
class CmsPreviewTicketServiceTest {

    private CmsPreviewTicketMapper ticketMapper;
    private CmsArticleMapper articleMapper;
    private CmsPageMapper pageMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsPreviewTicketService service;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(CmsPreviewTicketMapper.class);
        articleMapper = mock(CmsArticleMapper.class);
        pageMapper = mock(CmsPageMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        service = new CmsPreviewTicketService(ticketMapper, articleMapper, pageMapper, categoryMapper);
    }

    @Test
    @DisplayName("生成：单页存在时返回票据与过期时间")
    void issuesTicketForPage() {
        CmsPage page = new CmsPage();
        page.setPageId(50L);
        page.setSiteId(1L);
        when(pageMapper.selectById(50L)).thenReturn(page);
        when(ticketMapper.insert(any())).thenReturn(1);

        CmsPreviewTicketVO ticket = service.issue(CmsPreviewTicketService.TARGET_PAGE, 50L);

        assertThat(ticket.getTicket()).isNotBlank();
        assertThat(ticket.getExpireTime()).isAfter(LocalDateTime.now());
        verify(ticketMapper).insert(any());
    }

    @Test
    @DisplayName("生成：文章不存在时 404")
    void rejectsMissingArticle() {
        ServiceException error = catchThrowableOfType(
                () -> service.issue(CmsPreviewTicketService.TARGET_ARTICLE, 5L),
                ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.article.notfound");
        verify(ticketMapper, never()).insert(any());
    }

    @Test
    @DisplayName("生成：栏目模板预览票据携带模板覆盖")
    void issuesTicketForCategoryWithTemplate() {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(1L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(ticketMapper.insert(any())).thenAnswer(invocation -> {
            CmsPreviewTicket row = invocation.getArgument(0);
            assertThat(row.getTemplate()).isEqualTo("list.news");
            assertThat(row.getTargetType()).isEqualTo(CmsPreviewTicketService.TARGET_CATEGORY);
            return 1;
        });

        CmsPreviewTicketVO ticket = service.issue(
                CmsPreviewTicketService.TARGET_CATEGORY, 9L, "list.news");

        assertThat(ticket.getTicket()).isNotBlank();
        verify(ticketMapper).insert(any());
    }

    @Test
    @DisplayName("生成：预览模板与目标类型不匹配时拒绝")
    void rejectsTemplateMismatch() {
        CmsPage page = new CmsPage();
        page.setPageId(50L);
        page.setSiteId(1L);
        when(pageMapper.selectById(50L)).thenReturn(page);

        ServiceException error = catchThrowableOfType(
                () -> service.issue(CmsPreviewTicketService.TARGET_PAGE, 50L, "list.news"),
                ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.preview.template.invalid");
        verify(ticketMapper, never()).insert(any());
    }

    @Test
    @DisplayName("消费：票据无效 / 过期 / 已使用时拒绝")
    void rejectsUnusableTicket() {
        when(ticketMapper.consume("abc")).thenReturn(0);

        ServiceException error = catchThrowableOfType(
                () -> service.consume("abc"), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.preview.ticket.invalid");
    }

    @Test
    @DisplayName("消费：原子抢占成功后返回绑定目标")
    void consumesTicketOnce() {
        when(ticketMapper.consume("abc")).thenReturn(1);
        CmsPreviewTicket row = new CmsPreviewTicket();
        row.setTicket("abc");
        row.setSiteId(1L);
        row.setTargetType(CmsPreviewTicketService.TARGET_PAGE);
        row.setTargetId(50L);
        row.setUsed("1");
        when(ticketMapper.selectOne(any())).thenReturn(row);

        CmsPreviewTicket result = service.consume(" abc ");

        assertThat(result.getTargetId()).isEqualTo(50L);
        verify(ticketMapper).consume("abc");
    }
}
