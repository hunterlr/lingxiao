package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsNav;
import com.lingxiao.modules.cms.entity.CmsNavItem;
import com.lingxiao.modules.cms.entity.CmsPage;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsNavItemMapper;
import com.lingxiao.modules.cms.mapper.CmsNavMapper;
import com.lingxiao.modules.cms.mapper.CmsPageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CMS 导航服务。
 *
 * <p>导航按站点隔离，一套导航（top / footer / mobile）下挂树形导航项。
 * 导航项目标支持栏目、单页、外链三种；目标存在性与同站点在服务层校验，
 * 数据库不建跨表外键。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsNavService {

    private static final String TARGET_CATEGORY = "category";
    private static final String TARGET_PAGE = "page";
    private static final String TARGET_LINK = "link";

    private final CmsNavMapper navMapper;
    private final CmsNavItemMapper itemMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsPageMapper pageMapper;
    private final CmsSiteService siteService;

    public List<CmsNav> list(Long siteId) {
        siteService.mustGet(siteId);
        return navMapper.selectList(new LambdaQueryWrapper<CmsNav>()
                .eq(CmsNav::getSiteId, siteId)
                .orderByAsc(CmsNav::getNavId));
    }

    public CmsNav detail(Long navId) {
        CmsNav nav = mustGet(navId);
        nav.setItems(items(navId));
        return nav;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsNav nav) {
        siteService.mustGet(nav.getSiteId());
        String navKey = nav.getNavKey().trim();
        ensureKeyUnique(nav.getSiteId(), navKey, null);
        nav.setNavId(null);
        nav.setNavKey(navKey);
        nav.setNavName(nav.getNavName().trim());
        nav.setPosition(defaultText(nav.getPosition(), "top"));
        nav.setStatus(defaultStatus(nav.getStatus()));
        nav.setCreateBy(currentUserId());
        navMapper.insert(nav);
        return nav.getNavId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long navId, CmsNav nav) {
        CmsNav existing = mustGet(navId);
        String navKey = nav.getNavKey().trim();
        ensureKeyUnique(existing.getSiteId(), navKey, navId);
        existing.setNavKey(navKey);
        existing.setNavName(nav.getNavName().trim());
        existing.setPosition(defaultText(nav.getPosition(), existing.getPosition()));
        existing.setStatus(defaultStatus(nav.getStatus()));
        existing.setRemark(nav.getRemark());
        existing.setUpdateBy(currentUserId());
        navMapper.updateById(existing);
    }

    /** 删除导航：导航项随导航一起清理。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long navId) {
        mustGet(navId);
        itemMapper.deleteByNavId(navId);
        navMapper.hardDeleteById(navId);
    }

    /* ==================== 导航项 ==================== */

    public List<CmsNavItem> items(Long navId) {
        mustGet(navId);
        List<CmsNavItem> all = itemMapper.selectList(new LambdaQueryWrapper<CmsNavItem>()
                .eq(CmsNavItem::getNavId, navId)
                .orderByAsc(CmsNavItem::getSort)
                .orderByAsc(CmsNavItem::getItemId));
        return buildTree(all);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addItem(Long navId, CmsNavItem item) {
        CmsNav nav = mustGet(navId);
        Long parentId = item.getParentId() == null ? 0L : item.getParentId();
        checkParent(navId, parentId, null);
        validateTarget(nav.getSiteId(), item);
        item.setItemId(null);
        item.setNavId(navId);
        item.setSiteId(nav.getSiteId());
        item.setParentId(parentId);
        item.setItemName(item.getItemName().trim());
        item.setVisible(defaultStatus(item.getVisible()));
        item.setStatus(defaultStatus(item.getStatus()));
        item.setSort(item.getSort() == null ? 0 : item.getSort());
        item.setCreateBy(currentUserId());
        itemMapper.insert(item);
        return item.getItemId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long navId, Long itemId, CmsNavItem item) {
        CmsNav nav = mustGet(navId);
        CmsNavItem existing = mustItem(navId, itemId);
        Long parentId = item.getParentId() == null ? 0L : item.getParentId();
        checkParent(navId, parentId, itemId);
        validateTarget(nav.getSiteId(), item);
        existing.setParentId(parentId);
        existing.setItemName(item.getItemName().trim());
        existing.setTargetType(item.getTargetType());
        existing.setTargetId(item.getTargetId());
        existing.setUrl(item.getUrl());
        existing.setIcon(item.getIcon());
        existing.setVisible(defaultStatus(item.getVisible()));
        existing.setStatus(defaultStatus(item.getStatus()));
        existing.setSort(item.getSort() == null ? existing.getSort() : item.getSort());
        existing.setRemark(item.getRemark());
        existing.setUpdateBy(currentUserId());
        itemMapper.updateById(existing);
    }

    /** 删除导航项：有子项时拒绝，避免“删父项把整棵子树悄悄带走”。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long navId, Long itemId) {
        mustItem(navId, itemId);
        Long children = itemMapper.selectCount(new LambdaQueryWrapper<CmsNavItem>()
                .eq(CmsNavItem::getNavId, navId)
                .eq(CmsNavItem::getParentId, itemId));
        if (children != null && children > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.item.haschildren");
        }
        itemMapper.hardDeleteById(itemId);
    }

    /* ==================== 内部方法 ==================== */

    public CmsNav mustGet(Long navId) {
        CmsNav nav = navId == null ? null : navMapper.selectById(navId);
        if (nav == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.nav.notfound");
        }
        return nav;
    }

    private CmsNavItem mustItem(Long navId, Long itemId) {
        CmsNavItem item = itemId == null ? null : itemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getNavId(), navId)) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "cms.nav.item.notfound");
        }
        return item;
    }

    private void ensureKeyUnique(Long siteId, String navKey, Long excludeId) {
        Long count = navMapper.selectCount(new LambdaQueryWrapper<CmsNav>()
                .eq(CmsNav::getSiteId, siteId)
                .eq(CmsNav::getNavKey, navKey)
                .ne(excludeId != null, CmsNav::getNavId, excludeId));
        if (count != null && count > 0) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.key.duplicate");
        }
    }

    /** 父项必须属于同一导航；更新时禁止把节点挂到自己的后代下形成环。 */
    private void checkParent(Long navId, Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (Objects.equals(parentId, selfId)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.parent.invalid");
        }
        CmsNavItem parent = itemMapper.selectById(parentId);
        if (parent == null || !Objects.equals(parent.getNavId(), navId)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.parent.invalid");
        }
        if (selfId != null) {
            Set<Long> visited = new HashSet<>();
            Long current = parentId;
            while (current != null && current != 0L && visited.add(current)) {
                if (Objects.equals(current, selfId)) {
                    throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.parent.invalid");
                }
                CmsNavItem node = itemMapper.selectById(current);
                current = node == null || node.getParentId() == null ? 0L : node.getParentId();
            }
        }
    }

    private void validateTarget(Long siteId, CmsNavItem item) {
        String targetType = item.getTargetType();
        if (!TARGET_CATEGORY.equals(targetType) && !TARGET_PAGE.equals(targetType)
                && !TARGET_LINK.equals(targetType)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.target.invalid");
        }
        if (TARGET_LINK.equals(targetType)) {
            if (!StringUtils.hasText(item.getUrl())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.url.required");
            }
            item.setTargetId(null);
            return;
        }
        if (item.getTargetId() == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.target.invalid");
        }
        if (TARGET_CATEGORY.equals(targetType)) {
            CmsCategory category = categoryMapper.selectById(item.getTargetId());
            if (category == null || !Objects.equals(category.getSiteId(), siteId)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.target.invalid");
            }
        } else {
            CmsPage page = pageMapper.selectById(item.getTargetId());
            if (page == null || !Objects.equals(page.getSiteId(), siteId)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.nav.target.invalid");
            }
        }
    }

    private List<CmsNavItem> buildTree(List<CmsNavItem> all) {
        Map<Long, CmsNavItem> nodeMap = new LinkedHashMap<>();
        for (CmsNavItem item : all) {
            item.setChildren(new ArrayList<>());
            nodeMap.put(item.getItemId(), item);
        }
        List<CmsNavItem> roots = new ArrayList<>();
        for (CmsNavItem item : all) {
            CmsNavItem parent = nodeMap.get(item.getParentId());
            if (parent == null || parent == item) {
                roots.add(item);
            } else {
                parent.getChildren().add(item);
            }
        }
        Comparator<CmsNavItem> comparator = Comparator
                .comparing((CmsNavItem item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(CmsNavItem::getItemId);
        roots.sort(comparator);
        for (CmsNavItem item : all) {
            item.getChildren().sort(comparator);
        }
        return roots;
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : CommonConstants.STATUS_NORMAL;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
