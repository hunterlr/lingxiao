package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.PasswordResetRequest;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.service.SysUserService;
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

/**
 * 用户管理接口。
 *
 * <p>权限码约定：{@code platform:system:user:list|add|edit|remove|resetPwd|unlock}。
 * 每个方法都单独标权限，不靠“类级拦截 + 方法名推断”，避免新增接口忘记授权。</p>
 *
 * <p>停用 / 启用单独一个接口（{@code PUT /{userId}/status}），不复用编辑接口：
 * 编辑要提交整份资料，而列表页的开关只想改一个字段，混在一起容易误覆盖其他字段。</p>
 */
@RestController
@RequestMapping("/api/admin/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @RequiresPermission("platform:system:user:list")
    @GetMapping("/list")
    public R<PageResult<SysUser>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String username,
                                       @RequestParam(required = false) String nickname,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long deptId,
                                       @RequestParam(required = false) Boolean includeSubDept) {
        return R.ok(userService.page(pageNum, pageSize, username, nickname, status, deptId, includeSubDept));
    }

    @RequiresPermission("platform:system:user:list")
    @GetMapping("/{userId}")
    public R<SysUser> detail(@PathVariable Long userId) {
        return R.ok(userService.detail(userId));
    }

    @RequiresPermission("platform:system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysUser user) {
        return R.ok(userService.create(user));
    }

    @RequiresPermission("platform:system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}")
    public R<Void> edit(@PathVariable Long userId, @Valid @RequestBody SysUser user) {
        userService.update(userId, user);
        return R.ok();
    }

    @RequiresPermission("platform:system:user:resetPwd")
    @Log(title = "重置用户密码", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{userId}/password")
    public R<Void> resetPassword(@PathVariable Long userId,
                                 @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(userId, request.getNewPassword());
        return R.ok();
    }

    @RequiresPermission("platform:system:user:unlock")
    @Log(title = "解锁用户", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/unlock")
    public R<Void> unlock(@PathVariable Long userId) {
        userService.unlock(userId);
        return R.ok();
    }

    @RequiresPermission("platform:system:user:edit")
    @Log(title = "用户状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/status")
    public R<Void> changeStatus(@PathVariable Long userId, @RequestParam String status) {
        userService.changeStatus(userId, status);
        return R.ok();
    }

    @RequiresPermission("platform:system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userId}")
    public R<Void> remove(@PathVariable Long userId) {
        userService.delete(userId);
        return R.ok();
    }
}
