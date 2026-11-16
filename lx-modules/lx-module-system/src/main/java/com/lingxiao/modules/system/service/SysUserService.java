package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.entity.SysDeptClosure;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptClosureMapper;
import com.lingxiao.modules.system.mapper.SysDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import com.lingxiao.modules.system.mapper.SysUserPostMapper;
import com.lingxiao.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务。
 *
 * <p>职责：后台用户的增删改查、重置密码、启停用、角色 / 岗位授权。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>密码只接收明文入参，落库前 BCrypt 加密；任何响应都不返回密码；</li>
 *   <li>内置超管（{@code user_id = 1}）不能被停用 / 删除；</li>
 *   <li>不允许删除或停用自己（防止把自己锁在系统外）。</li>
 * </ul>
 *
 * <p>数据范围：列表 / 详情 / 写操作统一由 {@link DataScopeService} 过滤与断言，
 * 不能只过滤列表——按 ID 直调接口同样要拦截。非超管不得授予
 * {@code role_id = 1} 或 {@code data_scope = 1} 的角色，防止横向提权。</p>
 *
 * <p>删除采用物理删除：逻辑删除会与 {@code uk_user_username} 唯一索引冲突，
 * 导致“删掉 test 后无法再建 test”。删前清理 {@code sys_user_role} / {@code sys_user_post}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysDeptClosureMapper deptClosureMapper;
    private final DataScopeService dataScopeService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    /**
     * 分页查询用户。
     *
     * @param deptId        部门 ID；配合 {@code includeSubDept} 使用闭包表查整棵子树
     * @param includeSubDept 是否包含下级部门（默认 false，精确匹配部门）
     */
    public PageResult<SysUser> page(long pageNum, long pageSize, String username, String nickname,
                                    String status, Long deptId, Boolean includeSubDept) {
        Page<SysUser> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(nickname), SysUser::getNickname, nickname)
                .eq(StringUtils.hasText(status), SysUser::getStatus, status);

        if (deptId != null) {
            if (Boolean.TRUE.equals(includeSubDept)) {
                List<Long> deptIds = deptClosureMapper.selectSubtree(deptId).stream()
                        .map(SysDeptClosure::getDescendantId)
                        .collect(Collectors.toList());
                if (deptIds.isEmpty()) {
                    deptIds = Collections.singletonList(deptId);
                }
                wrapper.in(SysUser::getDeptId, deptIds);
            } else {
                wrapper.eq(SysUser::getDeptId, deptId);
            }
        }
        // 数据范围是“在调用方筛选之上再收窄”，不能覆盖部门 / 状态等条件
        dataScopeService.applyUserScope(wrapper);
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        fillDeptNames(result.getRecords());

        PageResult<SysUser> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 详情：基础资料 + 角色 ID + 岗位 ID + 部门名；越界直接 403。 */
    public SysUser detail(Long userId) {
        SysUser user = mustGet(userId);
        dataScopeService.assertUserVisible(user);
        user.setRoleIds(userRoleMapper.selectRoleIdsByUserId(userId));
        user.setPostIds(userPostMapper.selectPostIdsByUserId(userId));
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            user.setDeptName(dept == null ? null : dept.getDeptName());
        }
        return user;
    }

    /**
     * 新增用户。
     *
     * <p>密码必填（6~64），落库前 BCrypt；用户名唯一。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUser user) {
        if (!StringUtils.hasText(user.getPassword())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "新增用户必须设置密码");
        }
        passwordPolicy.validate(user.getPassword());
        checkUsernameUnique(user.getUsername(), null);
        checkDeptExists(user.getDeptId());
        dataScopeService.assertDeptVisible(user.getDeptId());
        assertAssignableRoles(null, user.getRoleIds());

        user.setUserId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(defaultIfBlank(user.getStatus(), CommonConstants.STATUS_NORMAL));
        user.setSex(defaultIfBlank(user.getSex(), "2"));
        user.setCreateBy(LoginHelper.getUserId());
        userMapper.insert(user);

        replaceRoles(user.getUserId(), user.getRoleIds());
        replacePosts(user.getUserId(), user.getPostIds());
        return user.getUserId();
    }

    /**
     * 修改用户。
     *
     * <p>不处理密码（重置密码走独立接口），{@code roleIds / postIds} 为 null 时不改授权。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, SysUser user) {
        SysUser existing = mustGet(userId);
        dataScopeService.assertUserVisible(existing);
        if (!existing.getUsername().equals(user.getUsername())) {
            checkUsernameUnique(user.getUsername(), userId);
        }
        checkDeptExists(user.getDeptId());
        dataScopeService.assertDeptVisible(user.getDeptId());
        assertAssignableRoles(userId, user.getRoleIds());

        existing.setDeptId(user.getDeptId());
        existing.setUsername(user.getUsername());
        existing.setNickname(user.getNickname());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        existing.setSex(defaultIfBlank(user.getSex(), existing.getSex()));
        existing.setRemark(user.getRemark());

        // 超管永远保持启用
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            existing.setStatus(CommonConstants.STATUS_NORMAL);
        } else {
            existing.setStatus(defaultIfBlank(user.getStatus(), existing.getStatus()));
        }
        existing.setUpdateBy(LoginHelper.getUserId());
        userMapper.updateById(existing);

        if (user.getRoleIds() != null) {
            replaceRoles(userId, user.getRoleIds());
        }
        if (user.getPostIds() != null) {
            replacePosts(userId, user.getPostIds());
        }
    }

    /**
     * 重置密码（管理员操作，不校验原密码）。
     *
     * <p>同时清空失败计数与锁定时间：管理员重置密码通常就是在帮被锁定的用户恢复，
     * 重置完还要等 10 分钟才能登录会显得系统“坏了”。</p>
     */
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = mustGet(userId);
        dataScopeService.assertUserVisible(user);
        passwordPolicy.validate(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword))
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockTime, null)
                .set(SysUser::getUpdateBy, LoginHelper.getUserId()));
        log.info("重置用户密码: userId={}, operator={}", userId, LoginHelper.getUsername());
    }

    /** 手动解锁：清零失败次数与锁定时间；未锁定时也允许调用，结果幂等。 */
    public void unlock(Long userId) {
        SysUser user = mustGet(userId);
        dataScopeService.assertUserVisible(user);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockTime, null)
                .set(SysUser::getUpdateBy, LoginHelper.getUserId()));
        log.info("手动解锁用户: userId={}, operator={}", userId, LoginHelper.getUsername());
    }

    /** 启用 / 停用。 */
    public void changeStatus(Long userId, String status) {
        if (!CommonConstants.STATUS_NORMAL.equals(status) && !CommonConstants.STATUS_DISABLED.equals(status)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "状态值只能是 0 或 1");
        }
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId) && !CommonConstants.STATUS_NORMAL.equals(status)) {
            throw new ServiceException(ErrorCode.FAIL, "内置超级管理员不能停用");
        }
        if (CommonConstants.STATUS_DISABLED.equals(status) && userId.equals(LoginHelper.getUserId())) {
            throw new ServiceException(ErrorCode.FAIL, "不能停用当前登录账号");
        }
        SysUser user = mustGet(userId);
        dataScopeService.assertUserVisible(user);
        user.setStatus(status);
        user.setUpdateBy(LoginHelper.getUserId());
        userMapper.updateById(user);
    }

    /** 删除用户（物理删除，级联清理角色 / 岗位关联）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId) {
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            throw new ServiceException(ErrorCode.FAIL, "内置超级管理员不能删除");
        }
        if (userId.equals(LoginHelper.getUserId())) {
            throw new ServiceException(ErrorCode.FAIL, "不能删除当前登录账号");
        }
        SysUser user = mustGet(userId);
        dataScopeService.assertUserVisible(user);
        userRoleMapper.deleteByUserId(userId);
        userPostMapper.deleteByUserId(userId);
        userMapper.hardDeleteById(userId);
    }

    // ==================== 内部方法 ====================

    private SysUser mustGet(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void checkUsernameUnique(String username, Long excludeUserId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(excludeUserId != null, SysUser::getUserId, excludeUserId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "用户名已存在: " + username);
        }
    }

    private void checkDeptExists(Long deptId) {
        if (deptId != null && deptMapper.selectById(deptId) == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "所选部门不存在");
        }
    }

    /**
     * 非超管不能授予内置管理员角色，也不能授予数据范围为“全部”的角色。
     *
     * <p>只检查“本次新增”的角色：编辑用户时前端会回传已有角色，
     * 如果目标用户本来就持有高权限角色，不应该连改手机号都被拒绝；
     * 真正的提权动作是把高权限角色新加进来。</p>
     */
    private void assertAssignableRoles(Long userId, List<Long> roleIds) {
        if (CommonConstants.SUPER_ADMIN_ID.equals(dataScopeService.currentUserId())) {
            return;
        }
        List<Long> assigned = distinct(roleIds);
        if (assigned.isEmpty()) {
            return;
        }
        List<Long> existing = userId == null
                ? Collections.emptyList()
                : userRoleMapper.selectRoleIdsByUserId(userId);
        List<Long> added = assigned.stream()
                .filter(roleId -> !existing.contains(roleId))
                .collect(Collectors.toList());
        if (added.isEmpty()) {
            return;
        }
        if (added.contains(CommonConstants.SUPER_ADMIN_ROLE_ID)) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "不能授予内置超级管理员角色");
        }
        List<SysRole> roles = roleMapper.selectBatchIds(added);
        for (SysRole role : roles) {
            if (DataScopeService.DATA_SCOPE_ALL.equals(role.getDataScope())) {
                throw new ServiceException(ErrorCode.FORBIDDEN,
                        "不能授予数据范围为“全部”的角色: " + role.getRoleName());
            }
        }
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        List<Long> cleaned = distinct(roleIds);
        if (!cleaned.isEmpty()) {
            userRoleMapper.insertBatch(userId, cleaned);
        }
    }

    private void replacePosts(Long userId, List<Long> postIds) {
        userPostMapper.deleteByUserId(userId);
        List<Long> cleaned = distinct(postIds);
        if (!cleaned.isEmpty()) {
            userPostMapper.insertBatch(userId, cleaned);
        }
    }

    private List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /** 列表补部门名，避免前端为每行再查一次部门。 */
    private void fillDeptNames(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return;
        }
        Map<Long, String> deptNameMap = deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getDeptId, SysDept::getDeptName, (a, b) -> a));
        users.forEach(user -> user.setDeptName(deptNameMap.get(user.getDeptId())));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
