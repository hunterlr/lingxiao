package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.entity.SysDeptClosure;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptClosureMapper;
import com.lingxiao.modules.system.mapper.SysRoleDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据范围（{@code sys_role.data_scope}）解析与过滤服务。
 *
 * <p>解决的是“同一个后台账号，能看到哪些数据”的问题。只做行级过滤，
 * 与菜单 / 按钮权限（{@code @RequiresPermission}）是两个维度：有“用户列表”
 * 权限的人，可能只能看到本部门的人。</p>
 *
 * <p>取值与策略：</p>
 * <ul>
 *   <li>{@code 1} 全部数据：不加任何条件（等同超管）；</li>
 *   <li>{@code 2} 自定义：{@code sys_role_dept} 中勾选的部门集合（精确匹配，不含下级）；</li>
 *   <li>{@code 3} 本部门：{@code dept_id = 当前用户部门}；</li>
 *   <li>{@code 4} 本部门及以下：用闭包表 {@code sys_dept_closure} 取整棵子树，含自己；</li>
 *   <li>{@code 5} 仅本人：{@code user_id = 当前用户}。</li>
 * </ul>
 *
 * <p>多角色时取“最大范围”（1 &gt; 4 &gt; 3 &gt; 2 &gt; 5），这是权限系统的常规语义：
 * 角色是授权叠加，不是取交集。自定义范围在多个角色之间取并集。</p>
 *
 * <p>兜底原则：拿不到登录上下文、用户没分配部门、自定义范围没勾任何部门、
 * 角色列表为空时，一律按“仅本人”处理，<b>失败必须收紧，不能放开</b>。</p>
 *
 * <p>作用点：用户列表 / 详情 / 写操作、部门列表 / 详情 / 写操作、
 * 角色列表与下拉选项（非全部范围只展示“可授权”的角色）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    /** 1 全部数据 */
    public static final String DATA_SCOPE_ALL = "1";
    /** 2 自定义数据 */
    public static final String DATA_SCOPE_CUSTOM = "2";
    /** 3 本部门 */
    public static final String DATA_SCOPE_DEPT = "3";
    /** 4 本部门及以下 */
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4";
    /** 5 仅本人 */
    public static final String DATA_SCOPE_SELF = "5";

    private final SysRoleMapper roleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserMapper userMapper;
    private final SysDeptClosureMapper deptClosureMapper;

    /**
     * 给用户查询追加数据范围条件。
     *
     * <p>只“追加”条件，不覆盖调用方已有的筛选：调用方按部门筛、
     * 这里再按数据范围筛，两个条件同时生效（交集），不会互相绕过。</p>
     */
    public void applyUserScope(LambdaQueryWrapper<SysUser> wrapper) {
        Scope scope = resolve();
        switch (scope.getType()) {
            case ALL:
                return;
            case DEPT:
                if (scope.getDeptIds().isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(SysUser::getDeptId, scope.getDeptIds());
                }
                return;
            case SELF:
            default:
                if (scope.getUserId() == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.eq(SysUser::getUserId, scope.getUserId());
                }
        }
    }

    /**
     * 给部门查询追加数据范围条件。
     *
     * <p>与用户列表同一口径：自定义范围只返回勾选的部门（精确集合）；
     * 仅本人返回自己所在部门；全部数据不过滤。父部门不在结果集时，
     * 部门树会把可见子节点直接提升为根节点，不会整块消失。</p>
     */
    public void applyDeptScope(LambdaQueryWrapper<SysDept> wrapper) {
        Scope scope = resolve();
        switch (scope.getType()) {
            case ALL:
                return;
            case DEPT:
                if (scope.getDeptIds().isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(SysDept::getDeptId, scope.getDeptIds());
                }
                return;
            case SELF:
            default:
                Long ownDeptId = currentDeptId(scope.getUserId());
                if (ownDeptId == null) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.eq(SysDept::getDeptId, ownDeptId);
                }
        }
    }

    /**
     * 给角色列表 / 下拉补“可授权”过滤。
     *
     * <p>数据范围不是“全部”的账号，只能看到自己能授出去的角色：
     * 隐藏内置超管角色和 {@code data_scope=1} 的角色，与
     * {@code SysUserService.assertAssignableRoles} 的规则保持一致。
     * 否则低权限管理员能在列表里看到超管角色并尝试编辑。</p>
     */
    public void applyRoleScope(LambdaQueryWrapper<SysRole> wrapper) {
        if (resolve().getType() == ScopeType.ALL) {
            return;
        }
        wrapper.ne(SysRole::getRoleId, CommonConstants.SUPER_ADMIN_ROLE_ID)
                .and(w -> w.ne(SysRole::getDataScope, DATA_SCOPE_ALL)
                        .or().isNull(SysRole::getDataScope));
    }

    /**
     * 当前账号可见的部门 ID；全部数据范围返回 {@code null} 表示“不限制”。
     *
     * <p>用于“保存自定义数据范围时，不能把部门勾到自己的数据范围之外”，
     * 否则拿到角色编辑权限即可横向扩大可见范围。</p>
     */
    public List<Long> visibleDeptIds() {
        Scope scope = resolve();
        switch (scope.getType()) {
            case ALL:
                return null;
            case DEPT:
                return scope.getDeptIds();
            case SELF:
            default:
                Long ownDeptId = currentDeptId(scope.getUserId());
                return ownDeptId == null
                        ? Collections.emptyList()
                        : Collections.singletonList(ownDeptId);
        }
    }

    /**
     * 断言目标用户在当前账号的数据范围内，越界抛 {@link ErrorCode#FORBIDDEN}。
     *
     * <p>用于详情、修改、删除、重置密码、启停用等“按 ID 直查”的入口。
     * 只做列表过滤是不够的：知道 ID 就能直接调接口绕过列表。</p>
     */
    public void assertUserVisible(SysUser user) {
        Scope scope = resolve();
        switch (scope.getType()) {
            case ALL:
                return;
            case DEPT:
                if (user.getDeptId() != null && scope.getDeptIds().contains(user.getDeptId())) {
                    return;
                }
                break;
            case SELF:
            default:
                if (scope.getUserId() != null && scope.getUserId().equals(user.getUserId())) {
                    return;
                }
        }
        throw new ServiceException(ErrorCode.FORBIDDEN, "没有权限操作该用户");
    }

    /**
     * 断言可以把用户放到目标部门。
     *
     * <p>用于新增 / 修改用户：防止把用户建到别人的部门，从而“造”出一条自己
     * 看不到、但脱离原部门管理的数据；也防止把用户塞进更高的部门节点。</p>
     *
     * <p>非超管必须指定部门；{@code 2 / 3 / 4} 只能选范围内的部门，
     * {@code 5} 只能选自己所在部门。</p>
     */
    public void assertDeptVisible(Long deptId) {
        Scope scope = resolve();
        if (scope.getType() == ScopeType.ALL) {
            return;
        }
        if (deptId == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "必须为用户指定所属部门");
        }
        if (scope.getType() == ScopeType.DEPT && scope.getDeptIds().contains(deptId)) {
            return;
        }
        if (scope.getType() == ScopeType.SELF) {
            Long ownDeptId = currentDeptId(scope.getUserId());
            if (ownDeptId != null && ownDeptId.equals(deptId)) {
                return;
            }
        }
        throw new ServiceException(ErrorCode.FORBIDDEN, "没有权限把用户分配到该系统外部门");
    }

    /**
     * 断言部门在当前账号的数据范围内（部门详情 / 修改 / 删除 / 选择上级）。
     *
     * <p>与用户接口同理：只过滤列表挡不住“知道 ID 直接调接口”。
     * 自定义范围是精确集合，不含下级；在这类部门下新建子部门后，
     * 新部门不会自动进入范围，因此部门树维护建议交给全部数据范围管理员。</p>
     */
    public void assertDeptManageable(Long deptId) {
        Scope scope = resolve();
        if (scope.getType() == ScopeType.ALL) {
            return;
        }
        if (deptId != null) {
            if (scope.getType() == ScopeType.DEPT && scope.getDeptIds().contains(deptId)) {
                return;
            }
            if (scope.getType() == ScopeType.SELF) {
                Long ownDeptId = currentDeptId(scope.getUserId());
                if (ownDeptId != null && ownDeptId.equals(deptId)) {
                    return;
                }
            }
        }
        throw new ServiceException(ErrorCode.FORBIDDEN, "没有权限操作该部门");
    }

    /**
     * 断言当前账号可以创建 / 移动到根部门（{@code parent_id = 0}）。
     *
     * <p>非全部数据范围账号看不到根层，允许其创建根部门会出现
     * “建完自己就看不见”的孤儿数据，直接拒绝。</p>
     */
    public void assertRootDeptAllowed() {
        if (resolve().getType() != ScopeType.ALL) {
            throw new ServiceException(ErrorCode.FORBIDDEN,
                    "只有全部数据范围的管理员可以创建或移动到根部门");
        }
    }

    /** 当前登录用户 ID；无请求上下文（定时任务等）返回 null。 */
    public Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部方法 ====================

    private Scope resolve() {
        return resolveFor(currentUserId());
    }

    /**
     * 解析指定用户的数据范围。
     *
     * <p>包内可见：单测直接传入 userId，避免为了测这段逻辑去模拟
     * Sa-Token 的静态登录上下文。生产入口统一走无参 {@link #resolve()}。</p>
     */
    Scope resolveFor(Long userId) {
        if (userId == null) {
            log.debug("无登录上下文，数据范围按仅本人（空）处理");
            return Scope.self(null);
        }
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return Scope.all();
        }

        List<String> scopes = roleMapper.selectDataScopesByUserId(userId);
        if (scopes == null || scopes.isEmpty()) {
            return Scope.self(userId);
        }
        if (scopes.contains(DATA_SCOPE_ALL)) {
            return Scope.all();
        }

        Long deptId = currentDeptId(userId);
        // 4 > 3：本部门及以下覆盖面更大，多个角色时优先
        if (deptId != null && scopes.contains(DATA_SCOPE_DEPT_AND_CHILD)) {
            List<Long> deptIds = deptClosureMapper.selectSubtree(deptId).stream()
                    .map(SysDeptClosure::getDescendantId)
                    .collect(Collectors.toList());
            if (deptIds.isEmpty()) {
                deptIds = Collections.singletonList(deptId);
            }
            return Scope.dept(deptIds);
        }
        if (deptId != null && scopes.contains(DATA_SCOPE_DEPT)) {
            return Scope.dept(Collections.singletonList(deptId));
        }
        // 2：多角色在 SQL 里已经并集去重；空集合必须收紧，不能当作“不限制”
        if (scopes.contains(DATA_SCOPE_CUSTOM)) {
            List<Long> customDeptIds = roleDeptMapper.selectDeptIdsByUserId(userId);
            if (customDeptIds != null && !customDeptIds.isEmpty()) {
                return Scope.dept(customDeptIds);
            }
            log.warn("用户 {} 的自定义数据范围没有勾选任何部门，降级为仅本人", userId);
        }
        if (deptId == null && (scopes.contains(DATA_SCOPE_DEPT)
                || scopes.contains(DATA_SCOPE_DEPT_AND_CHILD))) {
            log.warn("用户 {} 未分配部门，数据范围降级为仅本人", userId);
        }
        return Scope.self(userId);
    }

    private Long currentDeptId(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    /** 数据范围类型。 */
    public enum ScopeType {
        /** 全部数据 */
        ALL,
        /** 指定部门集合 */
        DEPT,
        /** 仅本人 */
        SELF
    }

    /** 一次解析得到的数据范围快照；包内可见，供单测断言。 */
    @Getter
    static final class Scope {

        private final ScopeType type;
        private final Long userId;
        private final List<Long> deptIds;

        private Scope(ScopeType type, Long userId, List<Long> deptIds) {
            this.type = type;
            this.userId = userId;
            this.deptIds = deptIds;
        }

        private static Scope all() {
            return new Scope(ScopeType.ALL, null, Collections.emptyList());
        }

        private static Scope dept(List<Long> deptIds) {
            return new Scope(ScopeType.DEPT, null, deptIds);
        }

        private static Scope self(Long userId) {
            return new Scope(ScopeType.SELF, userId, Collections.emptyList());
        }
    }
}
