package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsBanner;
import com.lingxiao.modules.cms.entity.CmsBlock;
import com.lingxiao.modules.cms.mapper.CmsBannerMapper;
import com.lingxiao.modules.cms.mapper.CmsBlockMapper;
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
 * 区块与轮播测试：HTML 清洗、起止时间校验。
 */
@DisplayName("CmsBlockService / CmsBannerService")
class CmsBlockBannerServiceTest {

    private CmsBlockMapper blockMapper;
    private CmsBannerMapper bannerMapper;
    private CmsBlockService blockService;
    private CmsBannerService bannerService;

    @BeforeEach
    void setUp() {
        blockMapper = mock(CmsBlockMapper.class);
        bannerMapper = mock(CmsBannerMapper.class);
        CmsSiteService siteService = mock(CmsSiteService.class);
        blockService = new CmsBlockService(blockMapper, siteService);
        bannerService = new CmsBannerService(bannerMapper, siteService);
    }

    @Test
    @DisplayName("区块：html 正文写入前清洗")
    void blockSanitizesHtml() {
        when(blockMapper.selectCount(any())).thenReturn(0L);
        when(blockMapper.insert(any())).thenAnswer(invocation -> {
            CmsBlock block = invocation.getArgument(0);
            block.setBlockId(1L);
            return 1;
        });
        CmsBlock request = new CmsBlock();
        request.setSiteId(1L);
        request.setBlockKey("home-hero");
        request.setBlockName("首页推荐");
        request.setBlockType("html");
        request.setContent("<p>推荐</p><script>alert(1)</script>");

        blockService.create(request);

        assertThat(request.getContent()).contains("<p>推荐</p>").doesNotContain("<script");
    }

    @Test
    @DisplayName("区块：类型非法拒绝")
    void blockRejectsUnknownType() {
        CmsBlock request = new CmsBlock();
        request.setSiteId(1L);
        request.setBlockKey("home-hero");
        request.setBlockName("首页推荐");
        request.setBlockType("video");

        ServiceException error = catchThrowableOfType(
                () -> blockService.create(request), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.block.type.invalid");
        verify(blockMapper, never()).insert(any());
    }

    @Test
    @DisplayName("轮播：结束时间必须晚于开始时间")
    void bannerRejectsInvalidTimeRange() {
        CmsBanner request = new CmsBanner();
        request.setSiteId(1L);
        request.setBannerGroup("home");
        LocalDateTime start = LocalDateTime.now();
        request.setStartTime(start);
        request.setEndTime(start.minusMinutes(1));

        ServiceException error = catchThrowableOfType(
                () -> bannerService.create(request), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.banner.time.invalid");
        verify(bannerMapper, never()).insert(any());
    }
}
