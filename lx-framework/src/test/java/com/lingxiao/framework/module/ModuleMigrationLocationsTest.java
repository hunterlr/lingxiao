package com.lingxiao.framework.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移目录拼装测试。
 *
 * <p>重点：关闭的模块不能扫描迁移目录；目录不存在的模块要跳过，
 * 否则 Flyway 会因为 “Unable to resolve location” 启动失败。</p>
 */
@DisplayName("ModuleMigrationLocations 迁移目录")
class ModuleMigrationLocationsTest {

    @Test
    @DisplayName("基座模块永远参与，顺序固定 system → file")
    void baseModulesAlwaysIncluded() {
        List<String> locations = ModuleMigrationLocations.build(properties(), code -> true);
        assertThat(locations).containsExactly(
                "classpath:db/migration/system",
                "classpath:db/migration/file");
    }

    @Test
    @DisplayName("启用模块按编码排序；关闭模块不参与")
    void enabledModulesSortedAndDisabledSkipped() {
        List<String> locations = ModuleMigrationLocations.build(
                properties("job", "true", "generator", "false", "cms", "true"), code -> true);
        assertThat(locations).containsExactly(
                "classpath:db/migration/system",
                "classpath:db/migration/file",
                "classpath:db/migration/cms",
                "classpath:db/migration/job");
    }

    @Test
    @DisplayName("目录不存在时跳过，避免 Flyway 解析不到 location")
    void missingDirectoriesSkipped() {
        List<String> locations = ModuleMigrationLocations.build(
                properties("job", "true", "cms", "true"),
                code -> !"cms".equals(code));
        assertThat(locations).containsExactly(
                "classpath:db/migration/system",
                "classpath:db/migration/file",
                "classpath:db/migration/job");
    }

    @Test
    @DisplayName("i18n 开关打开时扫描 i18n 目录，关闭时不扫描")
    void i18nLocationFollowsDedicatedSwitch() {
        LxModuleProperties enabled = properties();
        enabled.getI18n().setEnabled(true);
        assertThat(ModuleMigrationLocations.build(enabled, code -> true))
                .containsExactly(
                        "classpath:db/migration/system",
                        "classpath:db/migration/file",
                        "classpath:db/migration/i18n");

        LxModuleProperties disabled = properties();
        disabled.getI18n().setEnabled(false);
        assertThat(ModuleMigrationLocations.build(disabled, code -> true))
                .doesNotContain("classpath:db/migration/i18n");
    }

    private static LxModuleProperties properties(String... codesAndFlags) {
        LxModuleProperties properties = new LxModuleProperties();
        Map<String, LxModuleProperties.ModuleSwitch> modules = new LinkedHashMap<>();
        for (int i = 0; i < codesAndFlags.length; i += 2) {
            LxModuleProperties.ModuleSwitch item = new LxModuleProperties.ModuleSwitch();
            item.setEnabled(Boolean.parseBoolean(codesAndFlags[i + 1]));
            modules.put(codesAndFlags[i], item);
        }
        properties.setModules(modules);
        return properties;
    }
}
