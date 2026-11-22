package com.lingxiao.modules.system.security;

import com.lingxiao.common.core.module.ModuleProvider;
import com.lingxiao.modules.system.dto.MenuPermRow;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台域权限提供者测试。
 *
 * <p>模块关闭后权限码必须一起失效；没有模块注册表时不能误伤，
 * 应按“全部启用”返回。</p>
 */
@DisplayName("StpInterfaceImpl 权限与模块开关")
class StpInterfaceImplTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final ModuleProvider moduleProvider = mock(ModuleProvider.class);

    @Test
    @DisplayName("非 platform 域直接返回空，不查库")
    void otherLoginTypeReturnsEmpty() {
        StpInterfaceImpl impl = implWithProvider();
        assertThat(impl.getPermissionList("2", "member")).isEmpty();
        verify(menuMapper, never()).selectPermsByUserId(2L);
    }

    @Test
    @DisplayName("超级管理员固定 *:*:*，不受模块开关影响")
    void superAdminAlwaysHasAllPermissions() {
        StpInterfaceImpl impl = implWithProvider();
        assertThat(impl.getPermissionList("1", "platform")).containsExactly("*:*:*");
    }

    @Test
    @DisplayName("关闭模块的权限码被过滤，启用模块保留")
    void disabledModulePermissionsFiltered() {
        when(menuMapper.selectPermsByUserId(2L)).thenReturn(Arrays.asList(
                row("platform:system:user:list", "system"),
                row("platform:monitor:job:list", "job"),
                row("platform:tool:gen:list", "generator")));
        when(moduleProvider.isEnabled("system")).thenReturn(true);
        when(moduleProvider.isEnabled("job")).thenReturn(false);
        when(moduleProvider.isEnabled("generator")).thenReturn(false);

        List<String> permissions = implWithProvider().getPermissionList("2", "platform");

        assertThat(permissions).containsExactly("platform:system:user:list");
    }

    @Test
    @DisplayName("没有模块注册表时按全部启用处理")
    void missingProviderKeepsAllPermissions() {
        when(menuMapper.selectPermsByUserId(2L)).thenReturn(Arrays.asList(
                row("platform:system:user:list", "system"),
                row("platform:monitor:job:list", "job")));

        ObjectProvider<ModuleProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        List<String> permissions = new StpInterfaceImpl(menuMapper, provider)
                .getPermissionList("2", "platform");

        assertThat(permissions).containsExactly(
                "platform:system:user:list",
                "platform:monitor:job:list");
    }

    @Test
    @DisplayName("空权限码不进入结果")
    void blankPermsIgnored() {
        when(menuMapper.selectPermsByUserId(2L)).thenReturn(Collections.singletonList(
                row(null, "system")));
        when(moduleProvider.isEnabled("system")).thenReturn(true);

        assertThat(implWithProvider().getPermissionList("2", "platform")).isEmpty();
    }

    private StpInterfaceImpl implWithProvider() {
        ObjectProvider<ModuleProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(moduleProvider);
        return new StpInterfaceImpl(menuMapper, provider);
    }

    private static MenuPermRow row(String perms, String moduleCode) {
        MenuPermRow row = new MenuPermRow();
        row.setPerms(perms);
        row.setModuleCode(moduleCode);
        return row;
    }
}
