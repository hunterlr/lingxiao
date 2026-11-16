package com.lingxiao.admin.api.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>验证码字段<b>不在这里强制必填</b>：平台可以在参数设置里关闭验证码
 * （{@code sys.account.captchaEnabled}），此时登录页不发送验证码。
 * “开启时才校验”的逻辑放在 {@code CaptchaService.verify}，
 * 用条件校验而不是给 DTO 加一个永远为 true 的开关。</p>
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过 128")
    private String password;

    /** 验证码标识；关闭验证码时为空 */
    @Size(max = 64, message = "验证码标识长度不正确")
    private String captchaKey;

    /** 验证码；关闭验证码时为空 */
    @Size(max = 8, message = "验证码长度不正确")
    private String captchaCode;
}
