package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.mapper.SysDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysRoleMenuMapper;
import com.lingxiao.modules.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色保存的数据范围校验。
 *
 * <p>只测“不该写进去”的分支：自定义范围勾了空集合必须拒绝，
 * 否则保存成功但数据范围会被收紧成仅本人，属于静默失败。
 * 正常写入链路要落库，交给接口联调脚本验证。</p>
 */
@DisplayName("SysRoleService 自定义数据范围")
class SysRoleServiceTest {

    private SysRoleMapper roleMapper;
    private SysRoleDeptMapper roleDeptMapper;
    private SysRoleService service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        service = new SysRoleService(
                roleMapper,
                mock(SysRoleMenuMapper.class),
                roleDeptMapper,
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                mock(DataScopeService.class));
    }

    @Test
    @DisplayName("新增自定义范围但没有勾部门：拒绝保存")
    void rejectsEmptyCustomDeptsOnCreate() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        SysRole role = new SysRole();
        role.setRoleKey("custom_reader");
        role.setRoleName("自定义范围");
        role.setDataScope(DataScopeService.DATA_SCOPE_CUSTOM);
        role.setDeptIds(Collections.emptyList());

        ServiceException ex = catchThrowableOfType(() -> service.create(role), ServiceException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("至少一个部门");
        verify(roleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("删除部门关联后再次保存空集合：仍然拒绝")
    void rejectsEmptyCustomDeptsOnUpdate() {
        SysRole existing = new SysRole();
        existing.setRoleId(9L);
        existing.setRoleKey("custom_reader");
        existing.setRoleName("自定义范围");
        existing.setDataScope(DataScopeService.DATA_SCOPE_CUSTOM);
        when(roleMapper.selectById(9L)).thenReturn(existing);

        SysRole update = new SysRole();
        update.setRoleKey("custom_reader");
        update.setRoleName("自定义范围");
        update.setDataScope(DataScopeService.DATA_SCOPE_CUSTOM);
        update.setDeptIds(Collections.emptyList());

        ServiceException ex = catchThrowableOfType(() -> service.update(9L, update), ServiceException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("至少一个部门");
        verify(roleDeptMapper, never()).deleteByRoleId(9L);
    }
}
