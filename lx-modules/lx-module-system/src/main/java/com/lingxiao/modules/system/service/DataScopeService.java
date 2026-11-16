package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysDeptClosure;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptClosureMapper;
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
 *   <li>{@code 3} 本部门：{@code dept_id = 当前用户部门}；</li>
 *   <li>{@code 4} 本部门及以下：用闭包表 {@code sys_dept_closure} 取整棵子树，含自己；</li>
 *   <li>{@code 5} 仅本人：{@code user_id = 当前用户}；</li>
 *   <li>{@code 2} 自定义：v1.0 没有 {@code sys_role_dept} 关联表，<b>降级为仅本人</b>
 *       并打 warn 日志；角色保存接口也拒绝写入 2。</li>
 * </ul>
 *
 * <p>多角色时取“最大范围”（1 &gt; 4 &gt; 3 &gt; 5），这是权限系统的常规语义：
 * 角色是授权叠加，不是取交集。</p>
 *
 * <p>兜底原则：拿不到登录上下文、用户没分配部门、角色列表为空时，一律按
 * “仅本人”处理，<b>失败必须收紧，不能放开</b>。</p>
 *
 * <p>当前作用点：用户列表、用户详情 / 修改 / 删除 / 重置密码 / 启停用，
 * 以及新增 / 修改用户时的部门归属校验。部门列表、角色列表暂不做过滤
 * （见 docs/21-build-log.md 遗留项）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

    /** 1 全部数据 */
    public static final String DATA_SCOPE_ALL = "1";
    /** 2 自定义数据（v1.0 未实现） */
    public static final String DATA_SCOPE_CUSTOM = "2";
    /** 3 本部门 */
    public static final String DATA_SCOPE_DEPT = "3";
    /** 4 本部门及以下 */
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4";
    /** 5 仅本人 */
    public static final String DATA_SCOPE_SELF = "5";

    private final SysRoleMapper roleMapper;
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
     * <p>非超管必须指定部门；{@code 3 / 4} 只能选范围内的部门，
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
        Long userId = currentUserId();
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
        if (deptId == null) {
            log.warn("用户 {} 未分配部门，数据范围降级为仅本人", userId);
            return Scope.self(userId);
        }
        if (scopes.contains(DATA_SCOPE_DEPT_AND_CHILD)) {
            List<Long> deptIds = deptClosureMapper.selectSubtree(deptId).stream()
                    .map(SysDeptClosure::getDescendantId)
                    .collect(Collectors.toList());
            if (deptIds.isEmpty()) {
                deptIds = Collections.singletonList(deptId);
            }
            return Scope.dept(deptIds);
        }
        if (scopes.contains(DATA_SCOPE_DEPT)) {
            return Scope.dept(Collections.singletonList(deptId));
        }
        if (scopes.contains(DATA_SCOPE_CUSTOM)) {
            log.warn("用户 {} 的角色包含自定义数据范围（v1.0 未实现），降级为仅本人", userId);
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

    /** 一次解析得到的数据范围快照。 */
    @Getter
    private static final class Scope {

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
