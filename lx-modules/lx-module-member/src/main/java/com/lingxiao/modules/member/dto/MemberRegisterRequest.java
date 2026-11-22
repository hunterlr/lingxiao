package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员注册请求。
 *
 * <p>手机号 / 邮箱是否必填由参数 {@code mem.register.mobileRequired} /
 * {@code mem.register.emailRequired} 决定，所以这里不加 {@code @NotBlank}，
 * 由 Service 按当前配置校验，避免“参数关了但注解还拦着”。</p>
 */
@Data
public class MemberRegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.username.required}")
    @Size(max = 64, message = "{valid.user.username.max}")
    private String username;

    @NotBlank(message = "{valid.login.password.required}")
    @Size(min = 6, max = 64, message = "{valid.password.size}")
    private String password;

    @Size(max = 64, message = "{valid.nickname.max}")
    private String nickname;

    @Size(max = 32, message = "{valid.phone.max}")
    private String mobile;

    @Email(message = "{valid.email.format}")
    @Size(max = 128, message = "{valid.email.max}")
    private String email;

    @Size(max = 64, message = "{valid.login.captchaKey.max}")
    private String captchaKey;

    @Size(max = 8, message = "{valid.login.captchaCode.max}")
    private String captchaCode;

    /** 是否勾选用户协议；必须为 true */
    private Boolean agreement;
}
