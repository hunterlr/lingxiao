package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.mapper.SysDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysRoleMenuMapper;
import com.lingxiao.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
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
 * <p>数据范围接受 1 / 2 / 3 / 4 / 5：{@code 2 自定义} 的部门集合存在
 * {@code sys_role_dept}，保存时随 {@code deptIds} 一起先清后插；{@code null}
 * 表示“不修改”。切回其他范围时会清理历史部门关联，避免隐藏的旧范围下次生效。
 * 非超管也不能把角色范围改成“全部数据”，否则拿到角色编辑权限即可横向提权。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final DataScopeService dataScopeService;

    /** 允许写入数据库的数据范围。 */
    private static final Set<String> SUPPORTED_DATA_SCOPES = new HashSet<>(Arrays.asList(
            DataScopeService.DATA_SCOPE_ALL,
            DataScopeService.DATA_SCOPE_CUSTOM,
            DataScopeService.DATA_SCOPE_DEPT,
            DataScopeService.DATA_SCOPE_DEPT_AND_CHILD,
            DataScopeService.DATA_SCOPE_SELF));

    public PageResult<SysRole> page(long pageNum, long pageSize, String roleName, String roleKey, String status) {
        Page<SysRole> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .like(StringUtils.hasText(roleKey), SysRole::getRoleKey, roleKey)
                .eq(StringUtils.hasText(status), SysRole::getStatus, status);
        dataScopeService.applyRoleScope(wrapper);
        wrapper.orderByAsc(SysRole::getSort).orderByDesc(SysRole::getCreateTime);
        Page<SysRole> result = roleMapper.selectPage(page, wrapper);

        PageResult<SysRole> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 详情：角色基础信息 + 已授权菜单 ID + 自定义数据范围的部门 ID。 */
    public SysRole detail(Long roleId) {
        SysRole role = mustGet(roleId);
        role.setMenuIds(roleMenuMapper.selectMenuIdsByRoleId(roleId));
        role.setDeptIds(roleDeptMapper.selectDeptIdsByRoleId(roleId));
        return role;
    }

    /** 下拉选项：只返回启用角色；非全部数据范围账号只看“可授权”角色。 */
    public List<SysRole> options() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, CommonConstants.STATUS_NORMAL);
        dataScopeService.applyRoleScope(wrapper);
        wrapper.orderByAsc(SysRole::getSort);
        return roleMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role) {
        checkRoleKeyUnique(role.getRoleKey(), null);
        role.setRoleId(null);
        role.setSort(role.getSort() == null ? 0 : role.getSort());
        role.setDataScope(defaultIfBlank(role.getDataScope(), "1"));
        checkDataScope(role.getDataScope(), null);
        List<Long> deptIds = cleanIds(role.getDeptIds());
        if (DataScopeService.DATA_SCOPE_CUSTOM.equals(role.getDataScope())) {
            checkCustomDepts(deptIds);
        }
        role.setStatus(defaultIfBlank(role.getStatus(), CommonConstants.STATUS_NORMAL));
        role.setCreateBy(LoginHelper.getUserId());
        roleMapper.insert(role);
        replaceMenus(role.getRoleId(), role.getMenuIds());
        if (DataScopeService.DATA_SCOPE_CUSTOM.equals(role.getDataScope())) {
            replaceDepts(role.getRoleId(), deptIds);
        }
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

        // 先把自定义部门校验完再写库：非法请求不留下半成品的角色更新
        boolean customScope = DataScopeService.DATA_SCOPE_CUSTOM.equals(existing.getDataScope());
        List<Long> deptIdsToReplace = null;
        if (customScope) {
            if (role.getDeptIds() != null) {
                deptIdsToReplace = cleanIds(role.getDeptIds());
                checkCustomDepts(deptIdsToReplace);
            } else if (roleDeptMapper.selectDeptIdsByRoleId(roleId).isEmpty()) {
                // 从 3/4/5 切到 2 却没带部门：拒绝，避免“保存成功但看不到数据”
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "自定义数据范围必须选择至少一个部门");
            }
        }

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
        if (customScope) {
            if (deptIdsToReplace != null) {
                replaceDepts(roleId, deptIdsToReplace);
            }
        } else {
            // 切回 1/3/4/5 时清掉历史自定义部门，防止下次切回 2 时旧范围悄悄生效
            roleDeptMapper.deleteByRoleId(roleId);
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
        roleDeptMapper.deleteByRoleId(roleId);
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
                    "数据范围只支持 1 全部数据 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人");
        }
        if (!DataScopeService.DATA_SCOPE_ALL.equals(dataScope)
                || DataScopeService.DATA_SCOPE_ALL.equals(previousDataScope)) {
            return;
        }
        if (!CommonConstants.SUPER_ADMIN_ID.equals(dataScopeService.currentUserId())) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "非超级管理员不能把角色数据范围设为全部数据");
        }
    }

    /**
     * 校验自定义部门集合。
     *
     * <p>三点必须同时成立：</p>
     * <ol>
     *   <li>至少勾一个部门——空集合会被数据范围收紧成“仅本人”，
     *       与其“保存成功但看不到数据”，不如直接拒绝；</li>
     *   <li>部门必须真实存在（逻辑删除的部门查不到）；</li>
     *   <li>不能勾到自己的数据范围之外，否则拿到角色编辑权限就能横向扩权。</li>
     * </ol>
     */
    private void checkCustomDepts(List<Long> deptIds) {
        if (deptIds.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "自定义数据范围必须选择至少一个部门");
        }
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .in(SysDept::getDeptId, deptIds));
        if (count == null || count.intValue() != deptIds.size()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "所选部门不存在或已删除");
        }
        List<Long> visible = dataScopeService.visibleDeptIds();
        if (visible != null && !visible.containsAll(deptIds)) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "不能选择自己数据范围之外的部门");
        }
    }

    private void replaceMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null) {
            return;
        }
        roleMenuMapper.deleteByRoleId(roleId);
        List<Long> cleaned = cleanIds(menuIds);
        if (!cleaned.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, cleaned);
        }
    }

    /** 自定义部门同样“先清后插”，保证保存结果与界面勾选完全一致。 */
    private void replaceDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.deleteByRoleId(roleId);
        List<Long> cleaned = cleanIds(deptIds);
        if (!cleaned.isEmpty()) {
            roleDeptMapper.insertBatch(roleId, cleaned);
        }
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
