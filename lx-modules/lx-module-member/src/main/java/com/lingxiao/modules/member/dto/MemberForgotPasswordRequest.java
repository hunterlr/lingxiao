package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员找回密码 - 申请验证码。
 *
 * <p>无论账号是否存在都返回成功，避免被用来枚举会员账号。发送前要求图形
 * 验证码（场景 {@code reset}），与登录 / 注册分开计数，防止被刷短信。</p>
 */
@Data
public class MemberForgotPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.account.required}")
    @Size(max = 128, message = "{valid.member.account.max}")
    private String account;

    @Size(max = 64, message = "{valid.login.captchaKey.max}")
    private String captchaKey;

    @Size(max = 8, message = "{valid.login.captchaCode.max}")
    private String captchaCode;
}
