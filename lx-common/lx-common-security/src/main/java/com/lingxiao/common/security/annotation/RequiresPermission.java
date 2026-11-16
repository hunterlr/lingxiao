package com.lingxiao.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台后台权限校验注解。
 *
 * <p>用法：</p>
 * <pre>
 * &#64;RequiresPermission("platform:system:user:add")
 * &#64;PostMapping
 * public R&lt;Long&gt; add(...) { ... }
 * </pre>
 *
 * <p>为什么不用 Sa-Token 自带的 {@code @SaCheckPermission}：
 * 它的 {@code type} 默认是 {@code login} 域，后台必须每个方法都写
 * {@code type = "platform"}，漏写就会校验错域。本注解把域固定为平台域，
 * 少一个出错点。</p>
 *
 * <p>校验逻辑见 {@code PermissionAspect}：未登录 401，无权限 403。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** 权限码，例如 {@code platform:system:user:list}；支持 Sa-Token 通配符匹配 */
    String value();
}
