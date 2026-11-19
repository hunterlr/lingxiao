package com.lingxiao.modules.system.service;

import com.lingxiao.modules.system.entity.SysDeptClosure;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptClosureMapper;
import com.lingxiao.modules.system.mapper.SysRoleDeptMapper;
import com.lingxiao.modules.system.mapper.SysRoleMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据范围解析测试。
 *
 * <p>不连数据库、也不启动 Sa-Token：直接调用包内可见的 {@code resolveFor(userId)}，
 * 用 Mockito 固定角色 / 部门 / 用户数据，锁住四类边界：</p>
 * <ol>
 *   <li>自定义范围命中的部门集合；</li>
 *   <li>自定义范围为空时收紧为仅本人；</li>
 *   <li>超管直接放行，不查角色；</li>
 *   <li>多角色取最大范围（4 &gt; 3 &gt; 2 &gt; 5）。</li>
 * </ol>
 */
@DisplayName("DataScopeService 数据范围解析")
class DataScopeServiceTest {

    private static final Long NORMAL_USER_ID = 20L;

    private SysRoleMapper roleMapper;
    private SysRoleDeptMapper roleDeptMapper;
    private SysUserMapper userMapper;
    private SysDeptClosureMapper deptClosureMapper;
    private DataScopeService service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        userMapper = mock(SysUserMapper.class);
        deptClosureMapper = mock(SysDeptClosureMapper.class);
        service = new DataScopeService(roleMapper, roleDeptMapper, userMapper, deptClosureMapper);
    }

    @Test
    @DisplayName("自定义范围：返回勾选的部门集合（精确匹配）")
    void customScopeReturnsSelectedDepts() {
        when(roleMapper.selectDataScopesByUserId(NORMAL_USER_ID))
                .thenReturn(Collections.singletonList(DataScopeService.DATA_SCOPE_CUSTOM));
        when(roleDeptMapper.selectDeptIdsByUserId(NORMAL_USER_ID))
                .thenReturn(Arrays.asList(10L, 20L));

        DataScopeService.Scope scope = service.resolveFor(NORMAL_USER_ID);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.DEPT);
        assertThat(scope.getDeptIds()).containsExactly(10L, 20L);
        assertThat(scope.getUserId()).isNull();
    }

    @Test
    @DisplayName("自定义范围为空：收紧为仅本人，而不是放开")
    void emptyCustomScopeFallsBackToSelf() {
        when(roleMapper.selectDataScopesByUserId(NORMAL_USER_ID))
                .thenReturn(Collections.singletonList(DataScopeService.DATA_SCOPE_CUSTOM));
        when(roleDeptMapper.selectDeptIdsByUserId(NORMAL_USER_ID))
                .thenReturn(Collections.emptyList());

        DataScopeService.Scope scope = service.resolveFor(NORMAL_USER_ID);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.SELF);
        assertThat(scope.getUserId()).isEqualTo(NORMAL_USER_ID);
        assertThat(scope.getDeptIds()).isEmpty();
    }

    @Test
    @DisplayName("超级管理员：直接全部数据，不查角色与自定义部门")
    void superAdminGetsAllScope() {
        DataScopeService.Scope scope = service.resolveFor(1L);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.ALL);
        verify(roleMapper, never()).selectDataScopesByUserId(anyLong());
        verify(roleDeptMapper, never()).selectDeptIdsByUserId(anyLong());
    }

    @Test
    @DisplayName("多角色取最大：本部门及以下优先于自定义")
    void deptAndChildWinsOverCustom() {
        when(roleMapper.selectDataScopesByUserId(NORMAL_USER_ID))
                .thenReturn(Arrays.asList(DataScopeService.DATA_SCOPE_CUSTOM,
                        DataScopeService.DATA_SCOPE_DEPT_AND_CHILD));
        SysUser user = new SysUser();
        user.setUserId(NORMAL_USER_ID);
        user.setDeptId(5L);
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user);
        when(deptClosureMapper.selectSubtree(5L))
                .thenReturn(Arrays.asList(new SysDeptClosure(5L, 5L, 0),
                        new SysDeptClosure(5L, 6L, 1)));

        DataScopeService.Scope scope = service.resolveFor(NORMAL_USER_ID);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.DEPT);
        assertThat(scope.getDeptIds()).containsExactly(5L, 6L);
        verify(roleDeptMapper, never()).selectDeptIdsByUserId(anyLong());
    }

    @Test
    @DisplayName("多角色取最大：本部门优先于自定义")
    void deptWinsOverCustom() {
        when(roleMapper.selectDataScopesByUserId(NORMAL_USER_ID))
                .thenReturn(Arrays.asList(DataScopeService.DATA_SCOPE_CUSTOM,
                        DataScopeService.DATA_SCOPE_DEPT));
        SysUser user = new SysUser();
        user.setUserId(NORMAL_USER_ID);
        user.setDeptId(8L);
        when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user);

        DataScopeService.Scope scope = service.resolveFor(NORMAL_USER_ID);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.DEPT);
        assertThat(scope.getDeptIds()).containsExactly(8L);
    }

    @Test
    @DisplayName("只有仅本人角色：返回 SELF 并带上用户 ID")
    void selfScopeCarriesUserId() {
        when(roleMapper.selectDataScopesByUserId(NORMAL_USER_ID))
                .thenReturn(Collections.singletonList(DataScopeService.DATA_SCOPE_SELF));

        DataScopeService.Scope scope = service.resolveFor(NORMAL_USER_ID);

        assertThat(scope.getType()).isEqualTo(DataScopeService.ScopeType.SELF);
        assertThat(scope.getUserId()).isEqualTo(NORMAL_USER_ID);
    }
}
