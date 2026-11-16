package com.lingxiao.modules.system.security;

import cn.dev33.satoken.stp.StpInterface;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.security.util.StpPlatformUtil;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 平台域权限提供者。
 *
 * <p>Sa-Token 在执行 {@code checkPermission} / {@code checkRole} 时回调这里。
 * 关键约束：<b>只处理 platform 域</b>，其他域直接返回空，防止 Token 串域后
 * 拿到平台权限。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysMenuMapper menuMapper;

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
        return menuMapper.selectPermsByUserId(userId);
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
