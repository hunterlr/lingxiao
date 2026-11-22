package com.lingxiao.framework.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块注册表测试。
 *
 * <p>三条规则必须锁死：基座模块永远启用；显式关闭的模块不下发；
 * 没有配置的模块按关闭处理（移除依赖后不留悬空菜单）。</p>
 */
@DisplayName("ModuleRegistry 模块开关")
class ModuleRegistryTest {

    private static ModuleRegistry registryOf(Map<String, Boolean> modules) {
        LxModuleProperties properties = new LxModuleProperties();
        Map<String, LxModuleProperties.ModuleSwitch> map = new LinkedHashMap<>();
        modules.forEach((code, enabled) -> {
            LxModuleProperties.ModuleSwitch item = new LxModuleProperties.ModuleSwitch();
            item.setEnabled(enabled);
            item.setName(code + "-name");
            map.put(code, item);
        });
        properties.setModules(map);
        return new ModuleRegistry(properties);
    }

    @Test
    @DisplayName("基座模块始终启用，即使配置里没写或显式关闭")
    void baseModulesAlwaysEnabled() {
        ModuleRegistry registry = registryOf(new LinkedHashMap<>());
        assertThat(registry.isEnabled("system")).isTrue();
        assertThat(registry.isEnabled("file")).isTrue();

        ModuleRegistry disabled = registryOf(single("system", false));
        assertThat(disabled.isEnabled("system")).isTrue();
        assertThat(disabled.isEnabled("file")).isTrue();
    }

    @Test
    @DisplayName("配置为 true 才启用；false 与未配置都关闭")
    void optionalModulesFollowSwitch() {
        ModuleRegistry registry = registryOf(new LinkedHashMap<String, Boolean>() {{
            put("job", true);
            put("generator", false);
        }});
        assertThat(registry.isEnabled("job")).isTrue();
        assertThat(registry.isEnabled("generator")).isFalse();
        assertThat(registry.isEnabled("cms")).isFalse();
    }

    @Test
    @DisplayName("module_code 为空按启用处理，兼容没有模块归属的旧数据")
    void blankModuleCodeIsEnabled() {
        ModuleRegistry registry = registryOf(new LinkedHashMap<>());
        assertThat(registry.isEnabled(null)).isTrue();
        assertThat(registry.isEnabled("")).isTrue();
        assertThat(registry.isEnabled("   ")).isTrue();
    }

    @Test
    @DisplayName("模块列表包含基座 + 配置项，按编码排序")
    void listContainsBaseAndConfiguredModules() {
        ModuleRegistry registry = registryOf(new LinkedHashMap<String, Boolean>() {{
            put("generator", true);
            put("demo", false);
        }});
        List<ModuleInfo> modules = registry.list();
        assertThat(modules).extracting(ModuleInfo::getCode)
                .containsExactly("demo", "file", "generator", "system");
        assertThat(modules).filteredOn(item -> "system".equals(item.getCode()))
                .allMatch(ModuleInfo::isEnabled);
        assertThat(modules).filteredOn(item -> "demo".equals(item.getCode()))
                .noneMatch(ModuleInfo::isEnabled);
    }

    @Test
    @DisplayName("基座模块在列表里也强制 enabled=true，与 isEnabled 保持一致")
    void baseModulesEnabledInList() {
        ModuleRegistry registry = registryOf(single("system", false));
        assertThat(registry.list()).filteredOn(item -> "system".equals(item.getCode()))
                .allMatch(ModuleInfo::isEnabled);
    }

    private static Map<String, Boolean> single(String code, boolean enabled) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put(code, enabled);
        return map;
    }
}
