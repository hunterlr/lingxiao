package com.lingxiao.framework.module;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块开关配置。
 *
 * <p>YAML 写法：</p>
 * <pre>
 * lx:
 *   module:
 *     modules:
 *       demo:
 *         enabled: false
 *         name: 演示模块
 * </pre>
 *
 * <p>为什么用 Map 而不是给每个模块写一个字段：
 * 模块是可插拔的，基座不应该因为新增模块就改配置类。新增模块只需在 YAML 加一段。</p>
 */
@Data
@ConfigurationProperties(prefix = "lx.module")
public class LxModuleProperties {

    /** key = 模块编码，例如 demo / cms / member */
    private Map<String, ModuleSwitch> modules = new LinkedHashMap<>();

    /**
     * 多语言模块专用开关。
     *
     * <p>按 v1.4 约定使用 {@code lx.module.i18n.enabled}，不放进
     * {@link #modules}，避免和“模块编码即配置键”的旧写法混淆。</p>
     */
    private ModuleSwitch i18n = new ModuleSwitch();

    @Data
    public static class ModuleSwitch {
        /** 是否启用；默认 false，未配置的模块一律不加载 */
        private boolean enabled = false;
        /** 展示名，仅用于 /api/admin/modules */
        private String name;
        /** 说明，仅用于排查 */
        private String description;
    }
}
