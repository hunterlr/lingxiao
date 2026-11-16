package com.lingxiao.framework.config;

import com.lingxiao.framework.module.LxModuleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 框架自动装配入口。
 *
 * <p>只做配置绑定，不扫描业务包。业务 Bean 由 {@code lx-boot} 的
 * {@code @SpringBootApplication(scanBasePackages = "com.lingxiao")} 统一扫描。</p>
 */
@Configuration
@EnableConfigurationProperties(LxModuleProperties.class)
public class LxFrameworkAutoConfiguration {
}
