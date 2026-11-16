package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysRole;
import com.lingxiao.modules.system.service.SysRoleService;
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
 * 角色管理接口。
 *
 * <p>角色的创建 / 编辑请求里可以带 {@code menuIds}，服务端会先清空再重建授权，
 * 这就是“保存角色时一并授权菜单”的实现方式，不需要单独的授权接口。</p>
 */
@RestController
@RequestMapping("/api/admin/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @RequiresPermission("platform:system:role:list")
    @GetMapping("/list")
    public R<PageResult<SysRole>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String roleName,
                                       @RequestParam(required = false) String roleKey,
                                       @RequestParam(required = false) String status) {
        return R.ok(roleService.page(pageNum, pageSize, roleName, roleKey, status));
    }

    /** 下拉选项（启用角色），用户表单用。 */
    @RequiresPermission("platform:system:role:list")
    @GetMapping("/options")
    public R<List<SysRole>> options() {
        return R.ok(roleService.options());
    }

    @RequiresPermission("platform:system:role:list")
    @GetMapping("/{roleId}")
    public R<SysRole> detail(@PathVariable Long roleId) {
        return R.ok(roleService.detail(roleId));
    }

    @RequiresPermission("platform:system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysRole role) {
        return R.ok(roleService.create(role));
    }

    @RequiresPermission("platform:system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{roleId}")
    public R<Void> edit(@PathVariable Long roleId, @Valid @RequestBody SysRole role) {
        roleService.update(roleId, role);
        return R.ok();
    }

    @RequiresPermission("platform:system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleId}")
    public R<Void> remove(@PathVariable Long roleId) {
        roleService.delete(roleId);
        return R.ok();
    }
}
