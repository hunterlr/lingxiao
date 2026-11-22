package com.lingxiao.modules.system.security;

import cn.dev33.satoken.stp.StpInterface;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.module.ModuleProvider;
import com.lingxiao.common.security.util.StpPlatformUtil;
import com.lingxiao.modules.system.dto.MenuPermRow;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平台域权限提供者。
 *
 * <p>Sa-Token 在执行 {@code checkPermission} / {@code checkRole} 时回调这里。
 * 关键约束：<b>只处理 platform 域</b>，其他域直接返回空，防止 Token 串域后
 * 拿到平台权限。</p>
 *
 * <p>模块关闭后，它的权限码也必须失效：这里按 {@code sys_menu.module_code}
 * 过滤，和菜单下发保持同一套规则。否则“菜单没了、权限还在”，
 * 角色授权页会出现看不见来源的权限码。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysMenuMapper menuMapper;

    /** 同 AuthService：没有模块注册表时按“全部启用”处理。 */
    private final ObjectProvider<ModuleProvider> moduleProvider;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!StpPlatformUtil.TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        Long userId = toUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return Collections.singletonList("*:*:*");
        }
        List<MenuPermRow> rows = menuMapper.selectPermsByUserId(userId);
        List<String> permissions = new ArrayList<>(rows.size());
        for (MenuPermRow row : rows) {
            if (row.getPerms() != null && isModuleEnabled(row.getModuleCode())) {
                permissions.add(row.getPerms());
            }
        }
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!StpPlatformUtil.TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        Long userId = toUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        return menuMapper.selectRoleKeysByUserId(userId);
    }

    private boolean isModuleEnabled(String moduleCode) {
        ModuleProvider provider = moduleProvider.getIfAvailable();
        return provider == null || provider.isEnabled(moduleCode);
    }

    private Long toUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
