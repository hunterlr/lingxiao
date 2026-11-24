package com.lingxiao.modules.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.cms.entity.CmsCategory;
import com.lingxiao.modules.cms.entity.CmsModel;
import com.lingxiao.modules.cms.mapper.CmsArticleMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryClosureMapper;
import com.lingxiao.modules.cms.mapper.CmsCategoryMapper;
import com.lingxiao.modules.cms.mapper.CmsModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CMS 栏目服务。
 *
 * <p>树结构由 {@code parent_id} 表达；{@code cms_category_closure} 只用于
 * “取子树 / 判断祖先”查询，<b>每次变更后按站点全量重建</b>。栏目数量在
 * 几十到几百量级，全量重算比增量维护简单可靠，也不会出现历史脏数据导致的
 * 断裂闭包。</p>
 *
 * <p>闭包表不参与业务判断：栏目能否删除、文章挂在哪，仍然看
 * {@code parent_id} 与 {@code cms_article.category_id}，闭包只是加速查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsCategoryService {

    private final CmsCategoryMapper categoryMapper;
    private final CmsCategoryClosureMapper closureMapper;
    private final CmsArticleMapper articleMapper;
    private final CmsSiteService siteService;
    private final CmsModelMapper modelMapper;

    /** 站点下的栏目树（含停用栏目，供后台管理）。 */
    public List<CmsCategory> tree(Long siteId, String keyword, String status) {
        siteService.mustGet(siteId);
        LambdaQueryWrapper<CmsCategory> wrapper = new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, siteId)
                .like(StringUtils.hasText(keyword), CmsCategory::getCategoryName, keyword)
                .eq(StringUtils.hasText(status), CmsCategory::getStatus, status)
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getCategoryId);
        List<CmsCategory> all = categoryMapper.selectList(wrapper);
        return buildTree(all);
    }

    /** 平铺列表；下拉 / 关联选择用，前端自己拼层级。 */
    public List<CmsCategory> list(Long siteId) {
        siteService.mustGet(siteId);
        return categoryMapper.selectList(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, siteId)
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getCategoryId));
    }

    public CmsCategory detail(Long categoryId) {
        return mustGet(categoryId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CmsCategory category) {
        Long siteId = category.getSiteId();
        siteService.mustGet(siteId);
        Long parentId = category.getParentId() == null ? 0L : category.getParentId();
        if (parentId != 0L) {
            CmsCategory parent = mustGet(parentId);
            if (!Objects.equals(parent.getSiteId(), siteId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级栏目不属于当前站点");
            }
        }
        category.setCategoryId(null);
        category.setParentId(parentId);
        category.setCategoryKey(category.getCategoryKey().trim());
        category.setCategoryName(category.getCategoryName().trim());
        category.setModelId(validateModel(siteId, category.getModelId()));
        ensureKeyUnique(siteId, category.getCategoryKey(), null);
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        category.setVisible(defaultIfBlank(category.getVisible(), CommonConstants.STATUS_NORMAL));
        category.setStatus(defaultIfBlank(category.getStatus(), CommonConstants.STATUS_NORMAL));
        categoryMapper.insert(category);

        rebuildClosure(siteId);
        return category.getCategoryId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long categoryId, CmsCategory category) {
        CmsCategory existing = mustGet(categoryId);
        Long siteId = existing.getSiteId();
        Long newParentId = category.getParentId() == null ? 0L : category.getParentId();

        if (newParentId != 0L) {
            if (newParentId.equals(categoryId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级栏目不能是自己");
            }
            CmsCategory parent = mustGet(newParentId);
            if (!Objects.equals(parent.getSiteId(), siteId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级栏目不属于当前站点");
            }
            if (isDescendant(categoryId, newParentId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级栏目不能是自己的下级");
            }
        }

        String categoryKey = category.getCategoryKey().trim();
        ensureKeyUnique(siteId, categoryKey, categoryId);
        existing.setParentId(newParentId);
        existing.setCategoryKey(categoryKey);
        existing.setCategoryName(category.getCategoryName().trim());
        // 未传模型时保留原绑定；清空绑定需显式传 0（回退内置文章模型）
        existing.setModelId(category.getModelId() == null
                ? existing.getModelId() : validateModel(siteId, category.getModelId()));
        existing.setSlug(category.getSlug());
        existing.setPath(category.getPath());
        existing.setTemplate(category.getTemplate());
        existing.setIcon(category.getIcon());
        existing.setCover(category.getCover());
        existing.setSeoTitle(category.getSeoTitle());
        existing.setSeoKeywords(category.getSeoKeywords());
        existing.setSeoDescription(category.getSeoDescription());
        existing.setSort(category.getSort() == null ? existing.getSort() : category.getSort());
        existing.setVisible(defaultIfBlank(category.getVisible(), existing.getVisible()));
        existing.setStatus(defaultIfBlank(category.getStatus(), existing.getStatus()));
        existing.setRemark(category.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        categoryMapper.updateById(existing);

        rebuildClosure(siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        CmsCategory existing = mustGet(categoryId);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getParentId, categoryId));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "存在下级栏目，请先删除下级栏目");
        }
        Long articleCount = articleMapper.countByCategoryIncludingDeleted(categoryId);
        if (articleCount != null && articleCount > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "栏目下还有 " + articleCount + " 篇文章（含回收站），请先移走、恢复或彻底删除");
        }
        categoryMapper.hardDeleteById(categoryId);
        closureMapper.deleteByCategoryId(categoryId);
        rebuildClosure(existing.getSiteId());
    }

    /** 某栏目自身 + 全部后代 ID；文章按栏目筛选（含子栏目）时用。 */
    public List<Long> subtreeIds(Long categoryId) {
        List<Long> ids = closureMapper.selectSubtreeIds(categoryId);
        if (ids.isEmpty()) {
            // 兜底：闭包缺失时至少返回自己，不能让筛选变成全站
            ids = new ArrayList<>();
            ids.add(categoryId);
        }
        return ids;
    }

    /**
     * 按站点全量重建闭包：先清空，再按父链逐级向上写
     * {@code depth=0(自身) / 1(父) / 2(祖父)...}。
     *
     * <p>父链异常（父不存在、成环）时停止向上，保留已写出的部分，
     * 不让一条脏数据把整次重建打断。</p>
     */
    public void rebuildClosure(Long siteId) {
        closureMapper.deleteBySite(siteId);
        List<CmsCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, siteId));
        Map<Long, Long> parentMap = new HashMap<>();
        for (CmsCategory category : all) {
            parentMap.put(category.getCategoryId(),
                    category.getParentId() == null ? 0L : category.getParentId());
        }
        for (CmsCategory category : all) {
            Set<Long> visited = new HashSet<>();
            Long current = category.getCategoryId();
            int depth = 0;
            while (current != null && current != 0L && visited.add(current)) {
                closureMapper.insertRow(category.getCategoryId(), current, depth);
                current = parentMap.get(current);
                depth++;
            }
        }
    }

    // ==================== 内部方法 ====================

    /** 栏目只能绑定同站点的 article / gallery 模型；0 表示清空绑定回退文章模型。 */
    private Long validateModel(Long siteId, Long modelId) {
        if (modelId == null) {
            return null;
        }
        if (modelId == 0L) {
            return null;
        }
        CmsModel model = modelMapper.selectById(modelId);
        if (model == null || !Objects.equals(model.getSiteId(), siteId)
                || "page".equals(model.getModelType())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "cms.model.invalid");
        }
        return modelId;
    }

    private CmsCategory mustGet(Long categoryId) {
        CmsCategory category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "栏目不存在");
        }
        return category;
    }

    /** 用内存父链判断 candidate 是否为 categoryId 的后代，避开递归 SQL。 */
    private boolean isDescendant(Long categoryId, Long candidateId) {
        Long current = candidateId;
        Set<Long> visited = new HashSet<>();
        while (current != null && current != 0L && visited.add(current)) {
            if (current.equals(categoryId)) {
                return true;
            }
            CmsCategory node = categoryMapper.selectById(current);
            current = node == null || node.getParentId() == null ? 0L : node.getParentId();
        }
        return false;
    }

    private void ensureKeyUnique(Long siteId, String categoryKey, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getSiteId, siteId)
                .eq(CmsCategory::getCategoryKey, categoryKey)
                .ne(excludeId != null, CmsCategory::getCategoryId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "栏目标识已存在：" + categoryKey);
        }
    }

    private List<CmsCategory> buildTree(List<CmsCategory> all) {
        Map<Long, CmsCategory> nodeMap = new LinkedHashMap<>();
        for (CmsCategory category : all) {
            category.setChildren(new ArrayList<>());
            nodeMap.put(category.getCategoryId(), category);
        }
        List<CmsCategory> roots = new ArrayList<>();
        for (CmsCategory category : all) {
            CmsCategory parent = nodeMap.get(category.getParentId());
            if (parent == null || parent == category) {
                roots.add(category);
            } else {
                parent.getChildren().add(category);
            }
        }
        Comparator<CmsCategory> comparator = Comparator
                .comparing((CmsCategory c) -> c.getSort() == null ? 0 : c.getSort())
                .thenComparing(CmsCategory::getCategoryId);
        roots.sort(comparator);
        for (CmsCategory category : all) {
            category.getChildren().sort(comparator);
        }
        return roots;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
