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
 * 会员功能总开关。
 *
 * <p>模块装配仍由 {@code lx.module.modules.member.enabled} 在启动期决定；
 * 这里读运行期参数 {@code mem.enabled}，关闭后会员端与后台会员管理接口都返回
 * “会员功能已关闭”，不需要重启。菜单由 {@code AuthService} 同步裁剪。</p>
 *
 * <p>为什么拦截器而不是每个 Controller 里判断：会员接口会持续增加，
 * 漏掉一个就会出现“开关关了但某个接口还能用”的半开状态。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberEnabledInterceptor implements HandlerInterceptor {

    /** 运行期总开关参数键。 */
    public static final String SWITCH_KEY = "mem.enabled";

    private final ConfigProvider configProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!configProvider.getBooleanValue(SWITCH_KEY, true)) {
            throw ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.disabled");
        }
        return true;
    }
}
