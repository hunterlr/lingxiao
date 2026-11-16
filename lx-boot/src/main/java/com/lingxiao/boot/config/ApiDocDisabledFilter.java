package com.lingxiao.boot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境彻底关闭接口文档入口。
 *
 * <p>只关 {@code springdoc.api-docs.enabled} 时，{@code /v3/api-docs} 会 404，
 * 但 {@code /doc.html} 与 {@code /webjars/**} 是 Knife4j 打进 jar 的静态资源，
 * 仍会返回 200（页面打开后拿不到数据而已）。对生产来说这仍是多余的信息暴露面，
 * 所以这里在 {@code lx.api-doc.enabled=false} 时直接把文档相关路径拦成 404。</p>
 *
 * <p>为什么放在 lx-boot 而不是 common：这是启动模块的部署策略，
 * 业务模块与安全模块都不应该知道“文档有没有关”。</p>
 */
@Component
@ConditionalOnProperty(name = "lx.api-doc.enabled", havingValue = "false")
public class ApiDocDisabledFilter extends OncePerRequestFilter {

    /** 需要关闭的路径前缀：Knife4j 页面、Swagger UI、OpenAPI 描述、webjars 静态资源 */
    private static final List<String> BLOCKED_PREFIXES = Arrays.asList(
            "/doc.html",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        for (String prefix : BLOCKED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
