package com.lingxiao.framework.module;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模块注册表。
 *
 * <p>v1.0 只根据配置判断模块是否启用；后续会读取每个模块 jar 里的
 * {@code module.yaml}，补上依赖关系、迁移目录、菜单 SQL、权限码文件。</p>
 *
 * <p>注意：注册表不负责实例化模块 Bean。模块自己用
 * {@code @ConditionalOnProperty} 控制装配，这样模块关闭时连类都不会被加载。</p>
 */
@Component
public class ModuleRegistry {

    private final LxModuleProperties properties;

    public ModuleRegistry(LxModuleProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled(String moduleCode) {
        LxModuleProperties.ModuleSwitch module = properties.getModules().get(moduleCode);
        return module != null && module.isEnabled();
    }

    public List<ModuleInfo> list() {
        List<ModuleInfo> result = new ArrayList<>();
        properties.getModules().forEach((code, module) -> result.add(new ModuleInfo(
                code,
                module.getName() == null ? code : module.getName(),
                module.isEnabled(),
                module.getDescription()
        )));
        result.sort((a, b) -> a.getCode().compareTo(b.getCode()));
        return Collections.unmodifiableList(result);
    }
}
