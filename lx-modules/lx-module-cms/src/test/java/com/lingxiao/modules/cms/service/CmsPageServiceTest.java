package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单页服务测试：草稿强制、正文清洗、key 唯一、导航引用保护。
 */
@DisplayName("CmsPageService 单页")
class CmsPageServiceTest {

    private CmsPageMapper pageMapper;
    private CmsNavItemMapper navItemMapper;
    private CmsModelService modelService;
    private CmsContentExtService extService;
    private CmsSiteService siteService;
    private CmsPageService service;

    @BeforeEach
    void setUp() {
        pageMapper = mock(CmsPageMapper.class);
        navItemMapper = mock(CmsNavItemMapper.class);
        modelService = mock(CmsModelService.class);
        extService = mock(CmsContentExtService.class);
        siteService = mock(CmsSiteService.class);
        service = new CmsPageService(pageMapper, navItemMapper, modelService, extService, siteService);

        CmsModel pageModel = new CmsModel();
        pageModel.setModelId(30L);
        pageModel.setSiteId(1L);
        pageModel.setModelType(CmsModelService.TYPE_PAGE);
        when(modelService.resolveForPage(1L)).thenReturn(pageModel);
        when(modelService.validateExt(any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyMap());
    }

    @Test
    @DisplayName("新建：强制草稿、清洗正文、绑定 page 模型")
    void createForcesDraftAndSanitizes() {
        when(pageMapper.selectCount(any())).thenReturn(0L);
        when(pageMapper.insert(any())).thenAnswer(invocation -> {
            CmsPage page = invocation.getArgument(0);
            page.setPageId(50L);
            return 1;
        });
        CmsPage request = new CmsPage();
        request.setSiteId(1L);
        request.setPageKey(" about ");
        request.setPageName("关于我们");
        request.setContent("<p>ok</p><script>x</script>");
        request.setStatus(CmsPageService.STATUS_PUBLISHED);

        Long pageId = service.create(request);

        assertThat(pageId).isEqualTo(50L);
        assertThat(request.getPageKey()).isEqualTo("about");
        assertThat(request.getStatus()).isEqualTo(CmsPageService.STATUS_DRAFT);
        assertThat(request.getContent()).contains("<p>ok</p>").doesNotContain("<script");
        assertThat(request.getModelId()).isEqualTo(30L);
        verify(extService).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("新建：pageKey 站点内唯一")
    void rejectsDuplicatePageKey() {
        when(pageMapper.selectCount(any())).thenReturn(1L);
        CmsPage request = new CmsPage();
        request.setSiteId(1L);
        request.setPageKey("about");
        request.setPageName("关于我们");

        ServiceException error = catchThrowableOfType(
                () -> service.create(request), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.page.key.duplicate");
        verify(pageMapper, never()).insert(any());
    }

    @Test
    @DisplayName("删除：被导航项引用时拒绝")
    void rejectsDeleteWhenReferencedByNav() {
        when(pageMapper.selectById(50L)).thenReturn(page(50L, CmsPageService.STATUS_PUBLISHED));
        when(navItemMapper.countByTarget("page", 50L)).thenReturn(2L);

        ServiceException error = catchThrowableOfType(
                () -> service.delete(50L), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.page.referenced");
        verify(pageMapper, never()).hardDeleteById(50L);
    }

    @Test
    @DisplayName("详情：回填扩展字段并清洗正文")
    void detailLoadsExtAndSanitizes() {
        CmsPage page = page(50L, CmsPageService.STATUS_DRAFT);
        page.setContent("<p>ok</p><script>x</script>");
        when(pageMapper.selectById(50L)).thenReturn(page);
        when(extService.load("page", 50L)).thenReturn(Collections.singletonMap("contact", "hi"));

        CmsPage detail = service.detail(50L);

        assertThat(detail.getContent()).contains("<p>ok</p>").doesNotContain("<script");
        assertThat(detail.getExt()).containsEntry("contact", "hi");
    }

    private CmsPage page(Long id, String status) {
        CmsPage page = new CmsPage();
        page.setPageId(id);
        page.setSiteId(1L);
        page.setPageKey("about");
        page.setStatus(status);
        return page;
    }
}
