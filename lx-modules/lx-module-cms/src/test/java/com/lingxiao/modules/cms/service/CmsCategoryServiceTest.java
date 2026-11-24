package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryClosureMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsModelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 栏目闭包与删除保护测试。
 *
 * <p>不连数据库：重点验证三件事——闭包按父链写对深度、
 * 跨站点挂父级被拒绝、有子栏目/文章时不能删除。</p>
 */
@DisplayName("CmsCategoryService 栏目树与闭包")
class CmsCategoryServiceTest {

    private CmsCategoryMapper categoryMapper;
    private CmsCategoryClosureMapper closureMapper;
    private CmsArticleMapper articleMapper;
    private CmsSiteService siteService;
    private CmsModelMapper modelMapper;
    private CmsCategoryService service;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CmsCategoryMapper.class);
        closureMapper = mock(CmsCategoryClosureMapper.class);
        articleMapper = mock(CmsArticleMapper.class);
        siteService = mock(CmsSiteService.class);
        modelMapper = mock(CmsModelMapper.class);
        service = new CmsCategoryService(categoryMapper, closureMapper, articleMapper,
                siteService, modelMapper);
    }

    @Test
    @DisplayName("闭包重建：自身 0、父 1、祖父 2")
    void rebuildsClosureWithDepths() {
        CmsCategory root = category(1L, 1L, 0L, "news");
        CmsCategory child = category(2L, 1L, 1L, "tech");
        CmsCategory grand = category(3L, 1L, 2L, "ai");
        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(root, child, grand));

        service.rebuildClosure(1L);

        verify(closureMapper).deleteBySite(1L);
        ArgumentCaptor<Long> categoryIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> ancestorIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> depths = ArgumentCaptor.forClass(Integer.class);
        verify(closureMapper, org.mockito.Mockito.times(6))
                .insertRow(categoryIds.capture(), ancestorIds.capture(), depths.capture());

        List<Long> categoryList = categoryIds.getAllValues();
        List<Long> ancestorList = ancestorIds.getAllValues();
        List<Integer> depthList = depths.getAllValues();
        assertThat(relation(categoryList, ancestorList, depthList, 3L, 3L)).isEqualTo(0);
        assertThat(relation(categoryList, ancestorList, depthList, 3L, 2L)).isEqualTo(1);
        assertThat(relation(categoryList, ancestorList, depthList, 3L, 1L)).isEqualTo(2);
        assertThat(relation(categoryList, ancestorList, depthList, 2L, 1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("父栏目属于其它站点：拒绝创建")
    void rejectsParentFromAnotherSite() {
        CmsCategory parent = category(9L, 2L, 0L, "other");
        when(categoryMapper.selectById(9L)).thenReturn(parent);

        CmsCategory request = new CmsCategory();
        request.setSiteId(1L);
        request.setParentId(9L);
        request.setCategoryKey("news");
        request.setCategoryName("新闻");

        ServiceException error = catchThrowableOfType(() -> service.create(request), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("不属于当前站点");
        verify(categoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("存在子栏目：拒绝删除")
    void rejectsDeleteWhenChildrenExist() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 1L, 0L, "news"));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        ServiceException error = catchThrowableOfType(() -> service.delete(1L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("下级栏目");
        verify(categoryMapper, never()).hardDeleteById(any());
        verify(closureMapper, never()).deleteByCategoryId(eq(1L));
    }

    @Test
    @DisplayName("存在文章：拒绝删除")
    void rejectsDeleteWhenArticlesExist() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 1L, 0L, "news"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.countByCategoryIncludingDeleted(1L)).thenReturn(3L);

        ServiceException error = catchThrowableOfType(() -> service.delete(1L), ServiceException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("3 篇文章");
    }

    private Integer relation(List<Long> categoryIds, List<Long> ancestorIds, List<Integer> depths,
                             Long categoryId, Long ancestorId) {
        for (int i = 0; i < categoryIds.size(); i++) {
            if (categoryIds.get(i).equals(categoryId) && ancestorIds.get(i).equals(ancestorId)) {
                return depths.get(i);
            }
        }
        return null;
    }

    private CmsCategory category(Long id, Long siteId, Long parentId, String key) {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(id);
        category.setSiteId(siteId);
        category.setParentId(parentId);
        category.setCategoryKey(key);
        category.setCategoryName(key);
        return category;
    }
}
