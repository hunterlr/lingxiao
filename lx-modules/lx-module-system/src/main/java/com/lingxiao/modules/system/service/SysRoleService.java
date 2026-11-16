package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysRoleMenuMapper;
import com.lingxiao.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务。
 *
 * <p>角色是权限的载体：用户 → 角色 → 菜单 / 按钮。删除角色前必须确认没有用户占用，
 * 否则用户会“突然没有任何权限”，排查成本很高。</p>
 *
 * <p>内置角色 {@code role_id = 1}（admin）不允许删除、不允许停用、不允许改标识。</p>
 *
 * <p>数据范围只接受 1 / 3 / 4 / 5：{@code 2 自定义} 需要 {@code sys_role_dept}
 * 关联表，v1.0 未建设，保存时直接拒绝而不是“存进去但不生效”。
 * 非超管也不能把角色范围改成“全部数据”，否则拿到角色编辑权限即可横向提权。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DataScopeService dataScopeService;

    /** 允许写入数据库的数据范围；2 自定义 v1.0 未实现，故意不在列表里。 */
    private static final Set<String> SUPPORTED_DATA_SCOPES = new HashSet<>(Arrays.asList(
            DataScopeService.DATA_SCOPE_ALL,
            DataScopeService.DATA_SCOPE_DEPT,
            DataScopeService.DATA_SCOPE_DEPT_AND_CHILD,
            DataScopeService.DATA_SCOPE_SELF));

    public PageResult<SysRole> page(long pageNum, long pageSize, String roleName, String roleKey, String status) {
        Page<SysRole> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .like(StringUtils.hasText(roleKey), SysRole::getRoleKey, roleKey)
                .eq(StringUtils.hasText(status), SysRole::getStatus, status)
                .orderByAsc(SysRole::getSort)
                .orderByDesc(SysRole::getCreateTime);
        Page<SysRole> result = roleMapper.selectPage(page, wrapper);

        PageResult<SysRole> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 详情：角色基础信息 + 已授权菜单 ID。 */
    public SysRole detail(Long roleId) {
        SysRole role = mustGet(roleId);
        role.setMenuIds(roleMenuMapper.selectMenuIdsByRoleId(roleId));
        return role;
    }

    /** 下拉选项：只返回启用角色。 */
    public List<SysRole> options() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByAsc(SysRole::getSort));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role) {
        checkRoleKeyUnique(role.getRoleKey(), null);
        role.setRoleId(null);
        role.setSort(role.getSort() == null ? 0 : role.getSort());
        role.setDataScope(defaultIfBlank(role.getDataScope(), "1"));
        checkDataScope(role.getDataScope(), null);
        role.setStatus(defaultIfBlank(role.getStatus(), CommonConstants.STATUS_NORMAL));
        role.setCreateBy(LoginHelper.getUserId());
        roleMapper.insert(role);
        replaceMenus(role.getRoleId(), role.getMenuIds());
        return role.getRoleId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long roleId, SysRole role) {
        SysRole existing = mustGet(roleId);
        if (!existing.getRoleKey().equals(role.getRoleKey())) {
            if (CommonConstants.SUPER_ADMIN_ROLE_ID.equals(roleId)) {
                throw new ServiceException(ErrorCode.FAIL, "内置超级管理员角色不允许修改角色标识");
            }
            checkRoleKeyUnique(role.getRoleKey(), roleId);
        }

        String previousDataScope = existing.getDataScope();
        existing.setRoleKey(role.getRoleKey());
        existing.setRoleName(role.getRoleName());
        existing.setSort(role.getSort() == null ? existing.getSort() : role.getSort());
        existing.setDataScope(defaultIfBlank(role.getDataScope(), existing.getDataScope()));
        checkDataScope(existing.getDataScope(), previousDataScope);
        existing.setRemark(role.getRemark());
        if (CommonConstants.SUPER_ADMIN_ROLE_ID.equals(roleId)) {
            existing.setStatus(CommonConstants.STATUS_NORMAL);
        } else {
            existing.setStatus(defaultIfBlank(role.getStatus(), existing.getStatus()));
        }
        existing.setUpdateBy(LoginHelper.getUserId());
        roleMapper.updateById(existing);

        if (role.getMenuIds() != null) {
            replaceMenus(roleId, role.getMenuIds());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        if (CommonConstants.SUPER_ADMIN_ROLE_ID.equals(roleId)) {
            throw new ServiceException(ErrorCode.FAIL, "内置超级管理员角色不能删除");
        }
        mustGet(roleId);
        int userCount = userRoleMapper.countByRoleId(roleId);
        if (userCount > 0) {
            throw new ServiceException(ErrorCode.FAIL,
                    "该角色已分配给 " + userCount + " 个用户，请先解除关联再删除");
        }
        roleMenuMapper.deleteByRoleId(roleId);
        roleMapper.hardDeleteById(roleId);
    }

    // ==================== 内部方法 ====================

    private SysRole mustGet(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void checkRoleKeyUnique(String roleKey, Long excludeRoleId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeRoleId != null, SysRole::getRoleId, excludeRoleId));
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.FAIL, "角色标识已存在: " + roleKey);
        }
    }

    /**
     * 校验数据范围取值，并阻止非超管把范围提升为“全部数据”。
     *
     * @param dataScope         本次要保存的值
     * @param previousDataScope 修改前的值；新增传 {@code null}
     */
    private void checkDataScope(String dataScope, String previousDataScope) {
        if (!SUPPORTED_DATA_SCOPES.contains(dataScope)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "数据范围只支持 1 全部数据 / 3 本部门 / 4 本部门及以下 / 5 仅本人");
        }
        if (!DataScopeService.DATA_SCOPE_ALL.equals(dataScope)
                || DataScopeService.DATA_SCOPE_ALL.equals(previousDataScope)) {
            return;
        }
        if (!CommonConstants.SUPER_ADMIN_ID.equals(dataScopeService.currentUserId())) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "非超级管理员不能把角色数据范围设为全部数据");
        }
    }

    private void replaceMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null) {
            return;
        }
        roleMenuMapper.deleteByRoleId(roleId);
        List<Long> cleaned = menuIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!cleaned.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, cleaned);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
