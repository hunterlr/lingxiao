package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.security.util.StpMemberUtil;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 会员 Token 签发与会话控制。
 *
 * <p>把 Sa-Token 的静态调用集中在这里，业务 Service 只依赖本类：
 * 单元测试可以 mock 掉，不需要启动 Sa-Token 上下文。</p>
 *
 * <p>会话策略由 {@code mem.account.multiDeviceLogin} 控制，默认 false：
 * 新登录先注销该会员的全部旧 Token，再发新 Token，避免同一账号在
 * 多台设备上无限堆积会话。改成 true 后允许多端并存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberTokenIssuer {

    public static final String MULTI_DEVICE_CONFIG_KEY = "mem.account.multiDeviceLogin";

    private final ConfigProvider configProvider;

    public MemberLoginResponse issue(MemUser user, String levelName) {
        if (!configProvider.getBooleanValue(MULTI_DEVICE_CONFIG_KEY, false)) {
            StpMemberUtil.logoutByUserId(user.getMemberId());
        }
        StpMemberUtil.login(user.getMemberId());
        MemberLoginHelper.setLoginUser(user.getMemberId(), user.getUsername(),
                user.getNickname(), user.getLevelId());
        MemberLoginHelper.setLoginContext(ServletUtils.getClientIp(),
                truncate(ServletUtils.getUserAgent(), 200));

        MemberLoginResponse response = new MemberLoginResponse();
        response.setTokenName(StpMemberUtil.getTokenName());
        response.setTokenValue(StpMemberUtil.getTokenValue());
        response.setTokenTimeout(StpMemberUtil.getTokenTimeout());
        response.setMemberId(user.getMemberId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setLevelId(user.getLevelId());
        response.setLevelName(levelName);
        response.setPoints(user.getPoints());
        response.setGrowth(user.getGrowth());
        return response;
    }

    /** 注销指定会员的全部会话；停用、重置密码、删除时调用。 */
    public void logoutAll(Long memberId) {
        if (memberId == null) {
            return;
        }
        StpMemberUtil.logoutByUserId(memberId);
    }

    public void logoutCurrent() {
        StpMemberUtil.logout();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
