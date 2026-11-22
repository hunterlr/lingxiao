package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.modules.member.dto.MemberInfoVO;
import com.lingxiao.modules.member.dto.MemberPasswordChangeRequest;
import com.lingxiao.modules.member.dto.MemberPasswordSetRequest;
import com.lingxiao.modules.member.dto.MemberProfileUpdateRequest;
import com.lingxiao.modules.member.service.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 会员个人中心（{@code /api/app/profile/**}）。
 *
 * <p>由会员域拦截器保护，未登录直接 401；会员只能看 / 改自己的资料，
 * 不接受 {@code memberId} 参数，避免越权改别人。</p>
 */
@RestController
@RequestMapping("/api/app/profile")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberProfileController {

    private final MemberUserService userService;

    @GetMapping
    public R<MemberInfoVO> info() {
        return R.ok(userService.getInfo());
    }

    @PutMapping
    public R<MemberInfoVO> updateProfile(@Valid @RequestBody MemberProfileUpdateRequest request) {
        return R.ok(userService.updateProfile(request));
    }

    /** 改密接口不记录请求参数，避免明文密码进操作日志。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody MemberPasswordChangeRequest request) {
        userService.changePassword(request);
        return R.ok();
    }

    /** 第三方建号首次设置密码；已设置密码的账号只能走改密接口。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password/set")
    public R<Void> setPassword(@Valid @RequestBody MemberPasswordSetRequest request) {
        userService.setPassword(request);
        return R.ok();
    }
}
