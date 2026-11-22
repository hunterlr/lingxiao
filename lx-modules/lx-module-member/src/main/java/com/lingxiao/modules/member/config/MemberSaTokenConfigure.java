package com.lingxiao.modules.member.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.interceptor.SaInterceptor;
import com.lingxiao.common.security.util.StpMemberUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;

/**
 * 会员域拦截器与 Token 配置。
 *
 * <p>只拦 {@code /api/app/**}，与平台域拦截器 {@code /api/admin/**} 完全分开：
 * 平台 Token 访问会员接口会 401，会员 Token 访问后台接口也会 401。</p>
 *
 * <p><b>Token 配置：</b>复制全局 Sa-Token 配置（拿到超时、存储、Cookie 等默认值），
 * 再覆盖成本域需要的值：Token 名 {@code lx-member-token}、7 天有效期、
 * 2 小时不活跃失效、允许并发但每次登录发新 Token。复制而不是直接改全局，
 * 避免影响平台后台会话。</p>
 *
 * <p>白名单只放注册 / 登录 / 验证码 / 找回密码；其它会员接口必须登录。</p>
 */
@Configuration
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberSaTokenConfigure implements WebMvcConfigurer {

    /** 会员 Token 有效期：7 天 */
    private static final long MEMBER_TOKEN_TIMEOUT = 7 * 24 * 60 * 60L;

    /** 不活跃失效：2 小时 */
    private static final long MEMBER_ACTIVE_TIMEOUT = 2 * 60 * 60L;

    @PostConstruct
    public void initTokenConfig() {
        SaTokenConfig config = new SaTokenConfig();
        BeanUtils.copyProperties(SaManager.getConfig(), config);
        config.setTokenName(StpMemberUtil.TOKEN_NAME);
        config.setTimeout(MEMBER_TOKEN_TIMEOUT);
        config.setActiveTimeout(MEMBER_ACTIVE_TIMEOUT);
        // 与平台域一致：存储层允许并发，是否踢旧会话由参数 mem.account.multiDeviceLogin 控制
        config.setIsConcurrent(true);
        config.setIsShare(false);
        config.setIsReadCookie(false);
        config.setIsReadHeader(true);
        config.setIsReadBody(false);
        config.setIsWriteHeader(false);
        config.setTokenStyle("uuid");
        StpMemberUtil.setConfig(config);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // 只对匹配到 Controller 的请求做登录校验；会员模块关闭时
                    // 路径不存在，交给 Spring 正常 404，避免被包装成 500
                    if (!(handler instanceof HandlerMethod)) {
                        return;
                    }
                    StpMemberUtil.checkLogin();
                }))
                .addPathPatterns("/api/app/**")
                .excludePathPatterns(
                        "/api/app/auth/captcha",
                        "/api/app/auth/register",
                        "/api/app/auth/login",
                        "/api/app/auth/forgot-password",
                        "/api/app/auth/reset-password"
                );
    }
}
