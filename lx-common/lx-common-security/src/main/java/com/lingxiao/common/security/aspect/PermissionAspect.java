package com.lingxiao.common.security.aspect;

import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.StpPlatformUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 平台域权限切面。
 *
 * <p>处理 {@link RequiresPermission} 注解：先校验登录，再校验权限码。</p>
 *
 * <ul>
 *   <li>未登录 → {@code NotLoginException} → 401 / code 1002；</li>
 *   <li>无权限 → {@code NotPermissionException} → 403 / code 1003。</li>
 * </ul>
 *
 * <p>优先级设在很高但不抢在鉴权异常处理之前：异常处理器是 {@code @RestControllerAdvice}
 * 的 {@code @Order(HIGHEST_PRECEDENCE)}，与本切面不冲突。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class PermissionAspect {

    @Around("@annotation(requiresPermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        // 1. 未登录直接 401，避免权限查询在无 Session 时抛 NPE
        StpPlatformUtil.checkLogin();
        // 2. 权限码由 StpInterfaceImpl 提供；超管 *:*:* 由 Sa-Token 通配匹配放行
        StpPlatformUtil.checkPermission(requiresPermission.value());
        return joinPoint.proceed();
    }
}
