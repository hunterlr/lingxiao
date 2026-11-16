package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.TreeSelect;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.service.SysDeptService;
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
 * 部门管理接口。
 *
 * <p>部门树由闭包表支撑：移动部门（改 {@code parentId}）时服务端会重建子树闭包，
 * 前端不需要也不能自己维护层级。</p>
 */
@RestController
@RequestMapping("/api/admin/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @RequiresPermission("platform:system:dept:list")
    @GetMapping("/list")
    public R<List<SysDept>> list(@RequestParam(required = false) String deptName,
                                 @RequestParam(required = false) String status) {
        return R.ok(deptService.list(deptName, status));
    }

    @RequiresPermission("platform:system:dept:list")
    @GetMapping("/tree")
    public R<List<TreeSelect>> tree() {
        return R.ok(deptService.tree());
    }

    @RequiresPermission("platform:system:dept:list")
    @GetMapping("/{deptId}")
    public R<SysDept> detail(@PathVariable Long deptId) {
        return R.ok(deptService.detail(deptId));
    }

    @RequiresPermission("platform:system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysDept dept) {
        return R.ok(deptService.create(dept));
    }

    @RequiresPermission("platform:system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{deptId}")
    public R<Void> edit(@PathVariable Long deptId, @Valid @RequestBody SysDept dept) {
        deptService.update(deptId, dept);
        return R.ok();
    }

    @RequiresPermission("platform:system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public R<Void> remove(@PathVariable Long deptId) {
        deptService.delete(deptId);
        return R.ok();
    }
}
