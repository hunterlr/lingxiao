package com.lingxiao.common.security.config;

import com.lingxiao.common.security.util.StpPlatformUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import cn.dev33.satoken.interceptor.SaInterceptor;

/**
 * 平台后台鉴权入口。
 *
 * <p>只拦 {@code /api/admin/**}，并且放行登录、验证码、应用名片、demo 四个路径：</p>
 * <ul>
 *   <li>{@code /api/admin/auth/**}：登录、登出、当前用户信息；</li>
 *   <li>{@code /api/admin/auth/captcha}：验证码图片；</li>
 *   <li>{@code /api/admin/app/profile}：品牌名与版本，登录页要用；</li>
 *   <li>{@code /api/admin/demo/**}：仅用于验证“可选模块关闭后接口 404”，生产不打包。</li>
 * </ul>
 *
 * <p>后续会员域 {@code /api/app/**} 会注册另一套拦截器，绝不复用本拦截器。</p>
 *
 * <p><b>接口文档为什么不用在这里放行：</b>Knife4j 的入口是 {@code /doc.html}，
 * OpenAPI 描述是 {@code /v3/api-docs/**}，静态资源是 {@code /webjars/**}，
 * 它们都不在 {@code /api/admin/**} 下，本拦截器从路径上就拦不到。
 * 如果以后把文档路径挪进 {@code /api/admin}，必须在这里显式 exclude，
 * 否则 Swagger UI 会拿着空 Token 收到 401。</p>
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    // 只对真正匹配到 Controller 方法的请求做登录校验。
                    // 模块关闭（如生产关掉代码生成器）或路径根本不存在时，
                    // DispatcherServlet 传进来的是 ResourceHttpRequestHandler / BasicErrorController，
                    // 这类请求没有 Controller，也就没有地方处理 NotLoginException，
                    // 抛出去只会变成 500。直接放行，让 Spring 走正常的 404。
                    if (!(handler instanceof HandlerMethod)) {
                        return;
                    }
                    StpPlatformUtil.checkLogin();
                }))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        "/api/admin/auth/captcha",
                        "/api/admin/app/profile",
                        "/api/admin/demo/**"
                );
    }
}
