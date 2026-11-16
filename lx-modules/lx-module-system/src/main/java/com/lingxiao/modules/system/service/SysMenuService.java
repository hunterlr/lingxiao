package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.dto.TreeSelect;
import com.lingxiao.modules.system.entity.SysMenu;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import com.lingxiao.modules.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理服务。
 *
 * <p>菜单既是“侧边栏导航”，也是“权限码的载体”：按钮（F）不进导航，
 * 只提供 {@code perms} 给后端鉴权与前端按钮控制。</p>
 *
 * <p>树结构只有两层以上都可能，构建方式统一为“先放节点，再挂父级”，
 * 父节点不在结果集时作为根返回（和 {@code AuthService} 的菜单树一致）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /** 平铺列表（菜单管理表格用）。 */
    public List<SysMenu> list(String menuName, String status) {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .like(StringUtils.hasText(menuName), SysMenu::getMenuName, menuName)
                .eq(StringUtils.hasText(status), SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum));
    }

    /**
     * 菜单树。
     *
     * @param onlyMenu true 只返回目录 + 菜单（侧边栏 / 父级选择）；false 返回全部含按钮（角色授权）
     */
    public List<TreeSelect> tree(boolean onlyMenu) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum);
        if (onlyMenu) {
            wrapper.in(SysMenu::getMenuType, "M", "C");
        }
        List<SysMenu> menus = menuMapper.selectList(wrapper);
        return buildTree(menus);
    }

    public SysMenu detail(Long menuId) {
        return mustGet(menuId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysMenu menu) {
        normalize(menu, null);
        checkMenuKeyUnique(menu.getMenuKey(), null);
        menu.setMenuId(null);
        menu.setCreateBy(LoginHelper.getUserId());
        menuMapper.insert(menu);
        return menu.getMenuId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long menuId, SysMenu menu) {
        SysMenu existing = mustGet(menuId);
        normalize(menu, menuId);
        if (!existing.getMenuKey().equals(menu.getMenuKey())) {
            checkMenuKeyUnique(menu.getMenuKey(), menuId);
        }
        // 不能把父级设为自己或自己的后代，否则树会成环
        if (menu.getParentId() != null && menu.getParentId() != 0L) {
            if (menu.getParentId().equals(menuId)
                    || collectDescendants(menuId).contains(menu.getParentId())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级菜单不能是自己或自己的子菜单");
            }
            mustGet(menu.getParentId());
        }

        existing.setMenuKey(menu.getMenuKey());
        existing.setParentId(menu.getParentId());
        existing.setMenuName(menu.getMenuName());
        existing.setMenuType(menu.getMenuType());
        existing.setPath(menu.getPath());
        existing.setComponent(menu.getComponent());
        existing.setPerms(menu.getPerms());
        existing.setIcon(menu.getIcon());
        existing.setOrderNum(menu.getOrderNum());
        existing.setVisible(menu.getVisible());
        existing.setStatus(menu.getStatus());
        existing.setModuleCode(menu.getModuleCode());
        existing.setRemark(menu.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        menuMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long menuId) {
        mustGet(menuId);
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, menuId));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCode.FAIL, "存在子菜单，请先删除子菜单");
        }
        roleMenuMapper.deleteByMenuId(menuId);
        menuMapper.hardDeleteById(menuId);
    }

    // ==================== 内部方法 ====================

    private SysMenu mustGet(Long menuId) {
        SysMenu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    private void checkMenuKeyUnique(String menuKey, Long excludeMenuId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getMenuKey, menuKey)
                .ne(excludeMenuId != null, SysMenu::getMenuId, excludeMenuId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "菜单标识已存在: " + menuKey);
        }
    }

    /** 补默认值并做类型相关清理。 */
    private void normalize(SysMenu menu, Long selfId) {
        menu.setParentId(menu.getParentId() == null ? 0L : menu.getParentId());
        menu.setOrderNum(menu.getOrderNum() == null ? 0 : menu.getOrderNum());
        menu.setVisible(defaultIfBlank(menu.getVisible(), "0"));
        menu.setStatus(defaultIfBlank(menu.getStatus(), CommonConstants.STATUS_NORMAL));
        menu.setModuleCode(defaultIfBlank(menu.getModuleCode(), "system"));
        if ("F".equals(menu.getMenuType())) {
            // 按钮不对应路由
            menu.setPath(null);
            menu.setComponent(null);
            menu.setIcon(null);
        }
        // 父级存在性在 create 时校验；update 已单独校验
        if (selfId == null && menu.getParentId() != 0L) {
            mustGet(menu.getParentId());
        }
    }

    /** 收集某菜单的全部后代 ID（用于防止父级成环）。 */
    private Set<Long> collectDescendants(Long menuId) {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .select(SysMenu::getMenuId, SysMenu::getParentId));
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (SysMenu menu : all) {
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(menu.getMenuId());
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>(childrenMap.getOrDefault(menuId, new ArrayList<>()));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenMap.getOrDefault(current, new ArrayList<>()));
            }
        }
        return result;
    }

    private List<TreeSelect> buildTree(List<SysMenu> menus) {
        Map<Long, TreeSelect> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            TreeSelect node = new TreeSelect(menu.getMenuId(), menu.getMenuName(),
                    menu.getParentId(), menu.getMenuType());
            node.setPerms(menu.getPerms());
            node.setOrderNum(menu.getOrderNum());
            nodeMap.put(menu.getMenuId(), node);
        }
        List<TreeSelect> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            TreeSelect node = nodeMap.get(menu.getMenuId());
            TreeSelect parent = nodeMap.get(menu.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
