package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.member.dto.MemberAdminResetPasswordRequest;
import com.lingxiao.modules.member.dto.MemberCreateRequest;
import com.lingxiao.modules.member.dto.MemberStatusRequest;
import com.lingxiao.modules.member.dto.MemberUpdateRequest;
import com.lingxiao.modules.member.entity.MemLevel;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.service.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 后台会员管理（{@code /api/admin/member/**}）。
 *
 * <p>由平台域拦截器保护，权限码统一 {@code platform:member:user:*}；
 * 会员端 Token 访问这些接口会因为域不同直接 401。</p>
 *
 * <p>删除是逻辑删除（{@code del_flag=1}）：账号从列表、登录查询与唯一性校验中消失，
 * 用户名 / 手机号 / 邮箱可以重新注册；历史业务数据仍指向原 {@code memberId}。</p>
 */
@RestController
@RequestMapping("/api/admin/member")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberAdminController {

    private final MemberUserService userService;

    @RequiresPermission("platform:member:user:list")
    @GetMapping("/list")
    public R<PageResult<MemUser>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long levelId) {
        return R.ok(userService.page(pageNum, pageSize, keyword, status, levelId));
    }

    @RequiresPermission("platform:member:user:query")
    @GetMapping("/{memberId}")
    public R<MemUser> detail(@PathVariable Long memberId) {
        return R.ok(userService.get(memberId));
    }

    @RequiresPermission("platform:member:user:list")
    @GetMapping("/level-options")
    public R<List<MemLevel>> levelOptions() {
        return R.ok(userService.levelOptions());
    }

    @RequiresPermission("platform:member:user:add")
    @Log(title = "会员管理", businessType = BusinessType.INSERT, saveRequestData = false)
    @PostMapping
    public R<Long> add(@Valid @RequestBody MemberCreateRequest request) {
        return R.ok(userService.create(request));
    }

    @RequiresPermission("platform:member:user:edit")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{memberId}")
    public R<Void> edit(@PathVariable Long memberId, @Valid @RequestBody MemberUpdateRequest request) {
        userService.update(memberId, request);
        return R.ok();
    }

    @RequiresPermission("platform:member:user:status")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{memberId}/status")
    public R<Void> changeStatus(@PathVariable Long memberId, @Valid @RequestBody MemberStatusRequest request) {
        userService.changeStatus(memberId, request.getStatus());
        return R.ok();
    }

    @RequiresPermission("platform:member:user:resetPwd")
    @Log(title = "会员管理", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{memberId}/password")
    public R<Void> resetPassword(@PathVariable Long memberId,
                                 @Valid @RequestBody MemberAdminResetPasswordRequest request) {
        userService.resetPassword(memberId, request.getNewPassword());
        return R.ok();
    }

    @RequiresPermission("platform:member:user:remove")
    @Log(title = "会员管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{memberId}")
    public R<Void> remove(@PathVariable Long memberId) {
        userService.delete(memberId);
        return R.ok();
    }
}
