package com.lingxiao.modules.cms.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsNav;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsNavMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导航服务测试：外链必填、跨站点目标拒绝、父级成环拒绝、树排序。
 */
@DisplayName("CmsNavService 导航")
class CmsNavServiceTest {

    private CmsNavMapper navMapper;
    private CmsNavItemMapper itemMapper;
    private CmsCategoryMapper categoryMapper;
    private CmsPageMapper pageMapper;
    private CmsNavService service;

    @BeforeEach
    void setUp() {
        navMapper = mock(CmsNavMapper.class);
        itemMapper = mock(CmsNavItemMapper.class);
        categoryMapper = mock(CmsCategoryMapper.class);
        pageMapper = mock(CmsPageMapper.class);
        service = new CmsNavService(navMapper, itemMapper, categoryMapper,
                pageMapper, mock(CmsSiteService.class));
        when(navMapper.selectById(1L)).thenReturn(nav(1L, 1L));
    }

    @Test
    @DisplayName("外链导航项：地址必填")
    void rejectsLinkWithoutUrl() {
        CmsNavItem item = item(null, "官网", "link");
        item.setUrl(" ");

        ServiceException error = catchThrowableOfType(
                () -> service.addItem(1L, item), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.nav.url.required");
        verify(itemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("栏目目标：跨站点拒绝")
    void rejectsCategoryFromAnotherSite() {
        CmsCategory category = new CmsCategory();
        category.setCategoryId(9L);
        category.setSiteId(2L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        CmsNavItem item = item(null, "新闻", "category");
        item.setTargetId(9L);

        ServiceException error = catchThrowableOfType(
                () -> service.addItem(1L, item), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.nav.target.invalid");
    }

    @Test
    @DisplayName("父级调整：不能挂到自己的后代下")
    void rejectsParentCycle() {
        CmsNavItem parent = item(10L, "父", "link");
        parent.setUrl("/a");
        parent.setParentId(0L);
        CmsNavItem child = item(11L, "子", "link");
        child.setUrl("/b");
        child.setParentId(10L);
        when(itemMapper.selectById(10L)).thenReturn(parent);
        when(itemMapper.selectById(11L)).thenReturn(child);

        CmsNavItem request = item(10L, "父", "link");
        request.setUrl("/a");
        request.setParentId(11L);

        ServiceException error = catchThrowableOfType(
                () -> service.updateItem(1L, 10L, request), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.nav.parent.invalid");
    }

    @Test
    @DisplayName("删除导航项：有子项时拒绝")
    void rejectsDeleteWithChildren() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "父", "link"));
        when(itemMapper.selectCount(any())).thenReturn(1L);

        ServiceException error = catchThrowableOfType(
                () -> service.deleteItem(1L, 10L), ServiceException.class);

        assertThat(error.getMessageKey()).isEqualTo("cms.nav.item.haschildren");
        verify(itemMapper, never()).hardDeleteById(10L);
    }

    @Test
    @DisplayName("导航项树：按 sort 升序，子项挂到父级")
    void buildsSortedTree() {
        CmsNavItem rootB = item(2L, "B", "link");
        rootB.setParentId(0L);
        rootB.setSort(2);
        CmsNavItem rootA = item(1L, "A", "link");
        rootA.setParentId(0L);
        rootA.setSort(1);
        CmsNavItem child = item(3L, "A-1", "link");
        child.setParentId(1L);
        child.setSort(1);
        when(itemMapper.selectList(any())).thenReturn(Arrays.asList(rootB, rootA, child));

        List<CmsNavItem> tree = service.items(1L);

        assertThat(tree).extracting(CmsNavItem::getItemName).containsExactly("A", "B");
        assertThat(tree.get(0).getChildren()).extracting(CmsNavItem::getItemName)
                .containsExactly("A-1");
    }

    private CmsNav nav(Long id, Long siteId) {
        CmsNav nav = new CmsNav();
        nav.setNavId(id);
        nav.setSiteId(siteId);
        nav.setNavKey("main");
        nav.setNavName("主导航");
        nav.setStatus("0");
        return nav;
    }

    private CmsNavItem item(Long id, String name, String targetType) {
        CmsNavItem item = new CmsNavItem();
        item.setItemId(id);
        item.setNavId(1L);
        item.setSiteId(1L);
        item.setItemName(name);
        item.setTargetType(targetType);
        item.setVisible("0");
        item.setStatus("0");
        item.setSort(0);
        return item;
    }
}
