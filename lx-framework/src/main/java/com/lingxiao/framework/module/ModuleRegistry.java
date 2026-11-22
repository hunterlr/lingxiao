package com.lingxiao.framework.module;

import com.lingxiao.common.core.module.ModuleProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 模块注册表。
 *
 * <p>v1.0 只根据配置判断模块是否启用；后续会读取每个模块 jar 里的
 * {@code module.yaml}，补上依赖关系、迁移目录、菜单 SQL、权限码文件。</p>
 *
 * <p>判定规则（顺序很重要）：</p>
 * <ol>
 *   <li>{@code system} / {@code file} 是基座模块，<b>永远启用</b>，
 *       即使配置文件里漏写也不会把整个后台的菜单清空；</li>
 *   <li>其它模块必须显式配置 {@code lx.module.modules.<code>.enabled=true}；</li>
 *   <li>没有配置的模块一律视为关闭，这样移除依赖后，库里残留的
 *       {@code cms} / {@code member} 菜单不会继续下发。</li>
 * </ol>
 *
 * <p>注意：注册表不负责实例化模块 Bean。模块自己用
 * {@code @ConditionalOnProperty} 控制装配，这样模块关闭时连类都不会被加载。</p>
 */
@Component
public class ModuleRegistry implements ModuleProvider {

    /** 基座模块：没有开关，始终启用。 */
    public static final Set<String> BASE_MODULES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("system", "file")));

    private final LxModuleProperties properties;

    public ModuleRegistry(LxModuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isEnabled(String moduleCode) {
        if (moduleCode == null || moduleCode.trim().isEmpty()) {
            // 旧数据 / 手工插入的菜单可能没有模块归属，不能因此把它藏起来
            return true;
        }
        String code = moduleCode.trim();
        if (BASE_MODULES.contains(code)) {
            return true;
        }
        LxModuleProperties.ModuleSwitch module = properties.getModules().get(code);
        return module != null && module.isEnabled();
    }

    /**
     * 模块列表：基座模块 + 配置里的模块，按编码排序。
     *
     * <p>基座模块也返回，前端拿到的是完整视图：{@code enabled=false} 的模块
     * 不注册路由，{@code system/file} 始终为 true。</p>
     */
    public List<ModuleInfo> list() {
        Map<String, ModuleInfo> result = new TreeMap<>();
        for (String code : BASE_MODULES) {
            result.put(code, new ModuleInfo(code, baseName(code), true, "基座模块，始终启用"));
        }
        properties.getModules().forEach((code, module) -> {
            boolean enabled = BASE_MODULES.contains(code) || module.isEnabled();
            result.put(code, new ModuleInfo(
                    code,
                    module.getName() == null ? code : module.getName(),
                    enabled,
                    module.getDescription()
            ));
        });
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }

    private String baseName(String code) {
        if ("system".equals(code)) {
            return "系统模块";
        }
        if ("file".equals(code)) {
            return "文件中心";
        }
        return code;
    }
}
