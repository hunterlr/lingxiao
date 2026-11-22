package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.member.dto.MemberCaptchaResponse;
import com.lingxiao.modules.member.dto.MemberForgotPasswordRequest;
import com.lingxiao.modules.member.dto.MemberLoginRequest;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.dto.MemberRegisterRequest;
import com.lingxiao.modules.member.dto.MemberResetPasswordRequest;
import com.lingxiao.modules.member.service.MemberCaptchaService;
import com.lingxiao.modules.member.service.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 会员端认证接口（{@code /api/app/auth/**}）。
 *
 * <p>与后台 {@code /api/admin/auth/**} 完全分离：Token 名不同、拦截器不同、
 * 账号表不同。门户前端只调这里，不调任何 {@code /api/admin/**}。</p>
 *
 * <p>白名单：验证码、注册、登录、找回密码、重置密码；登出必须已登录。</p>
 */
@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberAuthController {

    private final MemberUserService userService;
    private final MemberCaptchaService captchaService;

    /** 图形验证码；{@code scene} 取 login / register / reset。 */
    @GetMapping("/captcha")
    public R<MemberCaptchaResponse> captcha(@RequestParam(required = false, defaultValue = "login") String scene) {
        return R.ok(captchaService.create(scene));
    }

    @PostMapping("/register")
    public R<MemberLoginResponse> register(@Valid @RequestBody MemberRegisterRequest request) {
        return R.ok(userService.register(request));
    }

    @PostMapping("/login")
    public R<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return R.ok(userService.login(request));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout();
        return R.ok();
    }

    /** 申请找回密码验证码；账号不存在也返回成功，避免枚举会员。 */
    @PostMapping("/forgot-password")
    public R<Void> forgotPassword(@Valid @RequestBody MemberForgotPasswordRequest request) {
        userService.forgotPassword(request);
        return R.ok();
    }

    @PostMapping("/reset-password")
    public R<Void> resetPassword(@Valid @RequestBody MemberResetPasswordRequest request) {
        userService.resetPassword(request);
        return R.ok();
    }
}
