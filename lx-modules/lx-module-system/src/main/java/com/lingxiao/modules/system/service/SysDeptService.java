package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.dto.TreeSelect;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.entity.SysDeptClosure;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptClosureMapper;
import com.lingxiao.modules.system.mapper.SysDeptMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 部门管理服务。
 *
 * <p>树结构由 {@code parent_id} 表达，但层级查询依赖闭包表 {@code sys_dept_closure}，
 * 这样“本部门及以下”只需一条 SQL，不递归、不用方言函数。</p>
 *
 * <p><b>闭包表维护三个时机：</b></p>
 * <ol>
 *   <li>新增：插入自己，并根据父级的祖先链补全；</li>
 *   <li>移动（改 parent_id）：先取整棵子树，删除指向子树的所有链接，再按新父级重建；</li>
 *   <li>删除：先确认没有子部门与用户，再清理该节点的全部闭包行。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysDeptClosureMapper deptClosureMapper;
    private final SysUserMapper userMapper;

    /** 平铺列表（表格 / 导出用）。 */
    public List<SysDept> list(String deptName, String status) {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .like(StringUtils.hasText(deptName), SysDept::getDeptName, deptName)
                .eq(StringUtils.hasText(status), SysDept::getStatus, status)
                .orderByAsc(SysDept::getParentId)
                .orderByAsc(SysDept::getOrderNum));
    }

    /** 部门树（下拉选择用）。 */
    public List<TreeSelect> tree() {
        return buildTree(list(null, null));
    }

    public SysDept detail(Long deptId) {
        return mustGet(deptId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDept dept) {
        Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
        if (parentId != 0L) {
            mustGet(parentId);
        }
        dept.setDeptId(null);
        dept.setParentId(parentId);
        dept.setOrderNum(dept.getOrderNum() == null ? 0 : dept.getOrderNum());
        dept.setStatus(defaultIfBlank(dept.getStatus(), CommonConstants.STATUS_NORMAL));
        dept.setCreateBy(LoginHelper.getUserId());
        deptMapper.insert(dept);

        insertClosureForNew(dept.getDeptId(), parentId);
        return dept.getDeptId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long deptId, SysDept dept) {
        SysDept existing = mustGet(deptId);
        Long newParentId = dept.getParentId() == null ? 0L : dept.getParentId();

        if (newParentId != 0L) {
            if (newParentId.equals(deptId)) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级部门不能是自己");
            }
            // 新父级的祖先链里如果包含自己，说明会成环
            boolean cycle = deptClosureMapper.selectAncestors(newParentId).stream()
                    .anyMatch(closure -> closure.getAncestorId().equals(deptId));
            if (cycle) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "上级部门不能是自己的下级");
            }
            mustGet(newParentId);
        }

        boolean parentChanged = !Objects.equals(existing.getParentId(), newParentId);

        existing.setParentId(newParentId);
        existing.setDeptName(dept.getDeptName());
        existing.setOrderNum(dept.getOrderNum() == null ? existing.getOrderNum() : dept.getOrderNum());
        existing.setLeader(dept.getLeader());
        existing.setPhone(dept.getPhone());
        existing.setEmail(dept.getEmail());
        existing.setStatus(defaultIfBlank(dept.getStatus(), existing.getStatus()));
        existing.setRemark(dept.getRemark());
        existing.setUpdateBy(LoginHelper.getUserId());
        deptMapper.updateById(existing);

        if (parentChanged) {
            rebuildClosure(deptId, newParentId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long deptId) {
        mustGet(deptId);
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, deptId));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCode.FAIL, "存在下级部门，请先删除下级部门");
        }
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, deptId));
        if (userCount != null && userCount > 0) {
            throw new ServiceException(ErrorCode.FAIL, "部门下还有 " + userCount + " 个用户，请先调整用户部门");
        }
        deptClosureMapper.deleteByNode(deptId);
        deptMapper.hardDeleteById(deptId);
    }

    // ==================== 闭包表维护 ====================

    /** 新增部门：自己 + 父级祖先链。 */
    private void insertClosureForNew(Long deptId, Long parentId) {
        deptClosureMapper.insertOne(new SysDeptClosure(deptId, deptId, 0));
        if (parentId == null || parentId == 0L) {
            return;
        }
        List<SysDeptClosure> ancestors = deptClosureMapper.selectAncestors(parentId);
        if (ancestors.isEmpty()) {
            // 兜底：历史数据缺少闭包行时，至少保证父子关系正确
            deptClosureMapper.insertOne(new SysDeptClosure(parentId, deptId, 1));
            return;
        }
        for (SysDeptClosure ancestor : ancestors) {
            deptClosureMapper.insertOne(new SysDeptClosure(
                    ancestor.getAncestorId(), deptId, ancestor.getDepth() + 1));
        }
    }

    /**
     * 移动部门后重建闭包。
     *
     * <p><b>关键点：子树“内部”的层级关系不能丢。</b>早期实现是把子树涉及的所有行全删，
     * 再按新父级重建，结果移动到根节点时只补了被移动节点自己的 (self,0)，
     * 子节点与它之间的链接被一起删掉——闭包表看起来“空了”，
     * 后续的成环校验、按部门查子树都会失真。</p>
     *
     * <p>现在的步骤：</p>
     * <ol>
     *   <li>取出以被移动节点为根的子树快照（含各自相对深度）；</li>
     *   <li>先保存“祖先也在这棵子树里”的内部行，删掉涉及该子树的所有行；</li>
     *   <li>把内部行原样写回（把自己 → 自己、以及子树内部父子关系还原）；</li>
     *   <li>若挂到新父级，则用新父级的祖先链 × 子树每个节点，按
     *       {@code 新祖先深度 + 1 + 相对深度} 补外部链接。</li>
     * </ol>
     *
     * <p>调用前 Service 已排除“新父级是自己的后代”，所以不会写出自相矛盾的链接。</p>
     */
    private void rebuildClosure(Long deptId, Long newParentId) {
        List<SysDeptClosure> snapshot = deptClosureMapper.selectSubtree(deptId);
        if (snapshot.isEmpty()) {
            // 兜底：历史数据缺行时至少保证自己 → 自己
            snapshot = new ArrayList<>();
            snapshot.add(new SysDeptClosure(deptId, deptId, 0));
        }

        // 每个后代相对于被移动节点的深度
        Map<Long, Integer> relativeDepth = new LinkedHashMap<>();
        for (SysDeptClosure row : snapshot) {
            relativeDepth.put(row.getDescendantId(), row.getDepth());
        }
        // 内部行：祖先也在子树内（含 self 行），必须先留存再删
        List<SysDeptClosure> internalRows = new ArrayList<>();
        for (SysDeptClosure row : snapshot) {
            if (relativeDepth.containsKey(row.getAncestorId())) {
                internalRows.add(row);
            }
        }

        for (Long node : relativeDepth.keySet()) {
            deptClosureMapper.deleteByDescendant(node);
        }
        for (SysDeptClosure row : internalRows) {
            deptClosureMapper.insertOne(row);
        }

        if (newParentId == null || newParentId == 0L) {
            return;
        }

        List<SysDeptClosure> ancestors = deptClosureMapper.selectAncestors(newParentId);
        if (ancestors.isEmpty()) {
            // 兜底：新父级缺闭包行，只补直接父子关系
            deptClosureMapper.insertOne(new SysDeptClosure(newParentId, deptId, 1));
            return;
        }
        for (SysDeptClosure ancestor : ancestors) {
            for (Map.Entry<Long, Integer> node : relativeDepth.entrySet()) {
                deptClosureMapper.insertOne(new SysDeptClosure(
                        ancestor.getAncestorId(),
                        node.getKey(),
                        ancestor.getDepth() + 1 + node.getValue()));
            }
        }
    }

    // ==================== 内部方法 ====================

    private SysDept mustGet(Long deptId) {
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return dept;
    }

    private List<TreeSelect> buildTree(List<SysDept> depts) {
        Map<Long, TreeSelect> nodeMap = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            TreeSelect node = new TreeSelect(dept.getDeptId(), dept.getDeptName(),
                    dept.getParentId(), "D");
            node.setOrderNum(dept.getOrderNum());
            nodeMap.put(dept.getDeptId(), node);
        }
        List<TreeSelect> roots = new ArrayList<>();
        for (SysDept dept : depts) {
            TreeSelect node = nodeMap.get(dept.getDeptId());
            TreeSelect parent = nodeMap.get(dept.getParentId());
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
