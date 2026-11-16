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
