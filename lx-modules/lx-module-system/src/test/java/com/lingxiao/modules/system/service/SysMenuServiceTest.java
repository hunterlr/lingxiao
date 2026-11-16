package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.system.dto.TreeSelect;
import com.lingxiao.modules.system.entity.SysMenu;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import com.lingxiao.modules.system.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 菜单树构建测试。
 *
 * <p>不连数据库：Mapper 用 Mockito 返回固定列表，只验证“平铺 → 树”的算法。
 * 重点覆盖两个历史坑：多级嵌套不能丢节点；父节点不在结果集时要作为根返回，
 * 而不是整棵子树消失（权限收紧但菜单不能凭空少一块）。</p>
 */
@DisplayName("SysMenuService 菜单树")
class SysMenuServiceTest {

    private SysMenuMapper menuMapper;
    private SysMenuService service;

    @BeforeEach
    void setUp() {
        menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        service = new SysMenuService(menuMapper, roleMenuMapper);
    }

    @Test
    @DisplayName("两层树：目录下挂菜单，节点字段完整复制")
    void buildsTwoLevelTree() {
        SysMenu directory = menu(1L, 0L, "系统管理", "M");
        directory.setOrderNum(1);
        SysMenu child = menu(2L, 1L, "用户管理", "C");
        child.setPerms("platform:system:user:list");
        child.setOrderNum(2);
        SysMenu anotherRoot = menu(3L, 0L, "文件中心", "C");
        when(menuMapper.selectList(any())).thenReturn(Arrays.asList(directory, child, anotherRoot));

        List<TreeSelect> roots = service.tree(true);

        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).getId()).isEqualTo(1L);
        assertThat(roots.get(0).getLabel()).isEqualTo("系统管理");
        assertThat(roots.get(0).getType()).isEqualTo("M");
        assertThat(roots.get(0).getChildren()).hasSize(1);
        assertThat(roots.get(0).getChildren().get(0).getId()).isEqualTo(2L);
        assertThat(roots.get(0).getChildren().get(0).getPerms())
                .isEqualTo("platform:system:user:list");
        assertThat(roots.get(0).getChildren().get(0).getOrderNum()).isEqualTo(2);
        assertThat(roots.get(1).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("父节点缺失的节点作为根返回，不丢数据")
    void orphanBecomesRoot() {
        SysMenu orphan = menu(10L, 999L, "孤儿菜单", "C");
        when(menuMapper.selectList(any())).thenReturn(Collections.singletonList(orphan));

        List<TreeSelect> roots = service.tree(false);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getId()).isEqualTo(10L);
        assertThat(roots.get(0).getParentId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("空列表返回空树，不抛异常")
    void emptyList() {
        when(menuMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(service.tree(true)).isEmpty();
    }

    @Test
    @DisplayName("详情不存在：抛 1004，而不是返回 null 让前端白屏")
    void detailNotFound() {
        when(menuMapper.selectById(99L)).thenReturn(null);

        ServiceException e = catchThrowableOfType(() -> service.detail(99L), ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1004);
        assertThat(e.getMessage()).contains("菜单");
    }

    private static SysMenu menu(Long id, Long parentId, String name, String type) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        return menu;
    }
}
