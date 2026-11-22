package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsTag;
import com.lingxiao.modules.cms.mapper.CmsArticleTagMapper;
import com.lingxiao.modules.cms.mapper.CmsTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签服务测试。
 *
 * <p>标签删除没有外键兜底，必须由服务层拦住“仍被文章引用”的情况，
 * 否则关联表会留下指向不存在标签的孤儿行。</p>
 */
@DisplayName("CmsTagService 标签服务")
class CmsTagServiceTest {

    private CmsTagMapper tagMapper;
    private CmsArticleTagMapper articleTagMapper;
    private CmsTagService service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(CmsTagMapper.class);
        articleTagMapper = mock(CmsArticleTagMapper.class);
        CmsSiteService siteService = mock(CmsSiteService.class);
        service = new CmsTagService(tagMapper, articleTagMapper, siteService);
    }

    @Test
    @DisplayName("标签仍被文章引用：拒绝删除")
    void rejectsDeleteWhenUsedByArticles() {
        when(tagMapper.selectById(3L)).thenReturn(tag(3L, 1L, "java"));
        when(articleTagMapper.countByTagId(3L)).thenReturn(2);

        ServiceException error = catchThrowableOfType(() -> service.delete(3L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("2 篇文章");
        verify(tagMapper, never()).hardDeleteById(any());
    }

    @Test
    @DisplayName("标签未被引用：物理删除")
    void deletesUnusedTag() {
        when(tagMapper.selectById(3L)).thenReturn(tag(3L, 1L, "java"));
        when(articleTagMapper.countByTagId(3L)).thenReturn(0);

        service.delete(3L);

        verify(tagMapper).hardDeleteById(3L);
    }

    private CmsTag tag(Long id, Long siteId, String key) {
        CmsTag tag = new CmsTag();
        tag.setTagId(id);
        tag.setSiteId(siteId);
        tag.setTagKey(key);
        tag.setTagName(key);
        return tag;
    }
}
