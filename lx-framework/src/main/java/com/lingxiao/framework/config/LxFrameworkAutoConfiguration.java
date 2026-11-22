package com.lingxiao.framework.config;

import com.lingxiao.framework.module.LxModuleProperties;
import com.lingxiao.framework.module.ModuleMigrationLocations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

/**
 * 框架自动装配入口。
 *
 * <p>只做配置绑定与基础设施装配，不扫描业务包。业务 Bean 由 {@code lx-boot} 的
 * {@code @SpringBootApplication(scanBasePackages = "com.lingxiao")} 统一扫描。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LxModuleProperties.class)
public class LxFrameworkAutoConfiguration {

    /**
     * 按模块开关裁剪 Flyway 迁移目录。
     *
     * <p>关闭的模块不扫描它的 {@code db/migration/<code>}，避免“模块关了还在建表”。
     * 已执行过的迁移记录保留在 {@code flyway_schema_history}，重新开启后不会重复执行。</p>
     */
    @Bean
    public FlywayConfigurationCustomizer lxFlywayLocationsCustomizer(LxModuleProperties properties) {
        return configuration -> {
            List<String> locations = ModuleMigrationLocations.build(properties, this::migrationDirectoryExists);
            if (!locations.isEmpty()) {
                configuration.locations(locations.toArray(new String[0]));
            }
            log.info("Flyway 迁移目录（按模块开关裁剪）: {}", locations);
        };
    }

    private boolean migrationDirectoryExists(String moduleCode) {
        return new ClassPathResource("db/migration/" + moduleCode).exists();
    }
}
