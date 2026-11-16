package com.lingxiao.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档元信息。
 *
 * <p>文档本身由 SpringDoc 扫描 Controller 自动生成，这里只补标题、说明与版本。
 * 配置类放在启动模块，common / modules 不依赖 swagger 注解，避免文档框架渗透到业务代码。</p>
 *
 * <p>关闭文档：{@code lx.api-doc.enabled=false}（同时建议把
 * {@code springdoc.api-docs.enabled} / {@code knife4j.production} 一起关掉）。</p>
 */
@Configuration
@ConditionalOnProperty(name = "lx.api-doc.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    @Value("${lx.api-doc.title:凌霄后台管理接口}")
    private String title;

    @Value("${lx.api-doc.description:平台后台域接口}")
    private String description;

    @Value("${lx.api-doc.version:1.0.0}")
    private String version;

    @Bean
    public OpenAPI lingxiaoOpenApi() {
        return new OpenAPI().info(new Info()
                .title(title)
                .description(description)
                .version(version));
    }
}
