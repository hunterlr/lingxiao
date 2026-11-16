package com.lingxiao.framework.module;

import com.lingxiao.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模块列表接口。
 *
 * <p>前端启动时调用它决定注册哪些路由；关闭的模块不返回，前端也不应显示菜单。</p>
 *
 * <p>安全：路径在 {@code /api/admin/**} 下，会被平台域拦截器要求登录。</p>
 */
@RestController
@RequestMapping("/api/admin/modules")
public class ModuleController {

    private final ModuleRegistry moduleRegistry;

    public ModuleController(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    @GetMapping
    public R<List<ModuleInfo>> list() {
        return R.ok(moduleRegistry.list());
    }
}
