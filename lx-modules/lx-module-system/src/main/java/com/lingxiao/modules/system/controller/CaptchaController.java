package com.lingxiao.modules.system.controller;

import com.lingxiao.admin.api.dto.CaptchaResponse;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.system.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码接口。
 *
 * <p>放在 {@code /api/admin/auth/captcha}，由安全配置放行，不需要登录。</p>
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public R<CaptchaResponse> captcha() {
        return R.ok(captchaService.create());
    }
}
