package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.TreeSelect;
import com.lingxiao.modules.system.entity.SysMenu;
import com.lingxiao.modules.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 菜单管理接口。
 *
 * <p>{@code /tree?onlyMenu=true} 给“父级菜单选择”用（只要目录与菜单），
 * 默认 {@code false} 给角色授权用（包含 F 按钮权限）。</p>
 */
@RestController
@RequestMapping("/api/admin/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @RequiresPermission("platform:system:menu:list")
    @GetMapping("/list")
    public R<List<SysMenu>> list(@RequestParam(required = false) String menuName,
                                 @RequestParam(required = false) String status) {
        return R.ok(menuService.list(menuName, status));
    }

    @RequiresPermission("platform:system:menu:list")
    @GetMapping("/tree")
    public R<List<TreeSelect>> tree(@RequestParam(defaultValue = "false") boolean onlyMenu) {
        return R.ok(menuService.tree(onlyMenu));
    }

    @RequiresPermission("platform:system:menu:list")
    @GetMapping("/{menuId}")
    public R<SysMenu> detail(@PathVariable Long menuId) {
        return R.ok(menuService.detail(menuId));
    }

    @RequiresPermission("platform:system:menu:add")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysMenu menu) {
        return R.ok(menuService.create(menu));
    }

    @RequiresPermission("platform:system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{menuId}")
    public R<Void> edit(@PathVariable Long menuId, @Valid @RequestBody SysMenu menu) {
        menuService.update(menuId, menu);
        return R.ok();
    }

    @RequiresPermission("platform:system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public R<Void> remove(@PathVariable Long menuId) {
        menuService.delete(menuId);
        return R.ok();
    }
}
