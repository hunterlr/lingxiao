package com.lingxiao.common.log.annotation;

import com.lingxiao.common.log.enums.BusinessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>用法：加在 Controller 的写操作方法上，例如：</p>
 * <pre>
 * &#64;Log(title = "用户管理", businessType = BusinessType.INSERT)
 * public R&lt;Long&gt; add(&#64;RequestBody SysUser user) { ... }
 * </pre>
 *
 * <p>只记录写操作。查询接口不记，避免日志表膨胀。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 模块名，例如“用户管理” */
    String title();

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数（涉及密码 / 密钥的接口必须设为 false） */
    boolean saveRequestData() default true;
}
