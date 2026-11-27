package com.lingxiao.modules.member.security;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 消息中心运行期开关。
 *
 * <p>只拦门户消息接口；后台消息管理不受影响，关闭后仍可查看与清理历史消息。
 * 与会员总开关独立：会员总开关关闭时，消息接口会先被
 * {@link MemberEnabledInterceptor} 拦住。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberMessageEnabledInterceptor implements HandlerInterceptor {

    /** 消息中心运行期开关参数键。 */
    public static final String SWITCH_KEY = "mem.message.enabled";

    private final ConfigProvider configProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!configProvider.getBooleanValue(SWITCH_KEY, true)) {
            throw ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.messageDisabled");
        }
        return true;
    }
}