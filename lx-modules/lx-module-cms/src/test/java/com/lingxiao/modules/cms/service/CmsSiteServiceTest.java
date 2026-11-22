package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsSite;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsSiteMapper;
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
 * 站点服务删除保护测试。
 *
 * <p>站点是内容根节点。栏目或文章还在时删除站点，会让 cms_* 出现
 * 无法归属的孤儿数据，因此必须在服务层拦截。</p>
 */
@DisplayName("CmsSiteService 站点服务")
class CmsSiteServiceTest {

    private CmsSiteMapper siteMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsArticleMapper articleMapper;
    private CmsSiteService service;

    @BeforeEach
    void setUp() {
        siteMapper = mock(CmsSiteMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        articleMapper = mock(CmsArticleMapper.class);
        service = new CmsSiteService(siteMapper, categoryMapper, articleMapper);
    }

    @Test
    @DisplayName("站点下还有栏目：拒绝删除")
    void rejectsDeleteWhenCategoriesExist() {
        when(siteMapper.selectById(1L)).thenReturn(site(1L, "main"));
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        ServiceException error = catchThrowableOfType(() -> service.delete(1L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("还有栏目");
        verify(siteMapper, never()).hardDeleteById(any());
    }

    @Test
    @DisplayName("站点下还有文章：拒绝删除")
    void rejectsDeleteWhenArticlesExist() {
        when(siteMapper.selectById(1L)).thenReturn(site(1L, "main"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.selectCount(any())).thenReturn(5L);

        ServiceException error = catchThrowableOfType(() -> service.delete(1L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("还有文章");
        verify(siteMapper, never()).hardDeleteById(any());
    }

    @Test
    @DisplayName("站点没有内容：物理删除")
    void deletesEmptySite() {
        when(siteMapper.selectById(1L)).thenReturn(site(1L, "main"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(siteMapper).hardDeleteById(1L);
    }

    private CmsSite site(Long id, String key) {
        CmsSite site = new CmsSite();
        site.setSiteId(id);
        site.setSiteKey(key);
        site.setSiteName(key);
        return site;
    }
}
