package com.lingxiao.modules.member.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.interceptor.SaInterceptor;
import com.lingxiao.common.security.util.StpMemberUtil;
import com.lingxiao.modules.member.security.MemberCommentEnabledInterceptor;
import com.lingxiao.modules.member.security.MemberEnabledInterceptor;
import com.lingxiao.modules.member.security.MemberMessageEnabledInterceptor;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberSaTokenConfigure implements WebMvcConfigurer {

    /** 会员 Token 有效期：7 天 */
    private static final long MEMBER_TOKEN_TIMEOUT = 7 * 24 * 60 * 60L;

    /** 不活跃失效：2 小时 */
    private static final long MEMBER_ACTIVE_TIMEOUT = 2 * 60 * 60L;

    private final MemberEnabledInterceptor memberEnabledInterceptor;
    private final MemberCommentEnabledInterceptor memberCommentEnabledInterceptor;
    private final MemberMessageEnabledInterceptor memberMessageEnabledInterceptor;

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
        // 先判总开关，再判登录：关闭时不需要用户先登录也能得到明确提示。
        // 后台会员管理接口同样受开关控制；菜单由 AuthService 同步隐藏。
        registry.addInterceptor(memberEnabledInterceptor)
                .addPathPatterns("/api/app/**", "/api/admin/member/**");

        // 评论开关只作用于门户评论接口；后台审核在开关关闭时仍可用。
        // 必须在登录校验之前，匿名读评论关闭时也能直接得到明确提示。
        registry.addInterceptor(memberCommentEnabledInterceptor)
                .addPathPatterns("/api/app/comments/**", "/api/app/public/comments/**");

        // 消息中心开关只作用于门户消息接口；后台消息管理在关闭时仍可用。
        // 登录校验在它之后，未登录访问关闭的功能也能拿到明确的“已关闭”提示。
        registry.addInterceptor(memberMessageEnabledInterceptor)
                .addPathPatterns("/api/app/messages/**");

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
                        "/api/app/auth/reset-password",
                        // 第三方登录：入口列表、授权地址、平台回调与 ticket 换 Token
                        // 都必须匿名可访问；绑定列表 / 解绑仍在下方受登录保护。
                        // authorize-url 的 mode=bind 由 Service 自己校验会员登录态。
                        "/api/app/auth/oauth/providers",
                        "/api/app/auth/oauth/authorize-url",
                        "/api/app/auth/oauth/callback/**",
                        "/api/app/auth/oauth/exchange",
                        "/api/app/auth/oauth/mini-login",
                        // 公开只读统计：匿名可看收藏 / 点赞数，带 Token 时附带个人状态。
                        // 写操作仍在 /api/app/interactions/** 下，不在白名单里。
                        "/api/app/public/**"
                );
    }
}
