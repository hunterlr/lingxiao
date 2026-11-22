package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员登录请求。
 *
 * <p>{@code account} 可以是用户名、手机号或邮箱；Service 按用户名 → 手机号 →
 * 邮箱的顺序查找，找到第一个即停止，避免一个值同时命中两个账号。</p>
 */
@Data
public class MemberLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.account.required}")
    @Size(max = 128, message = "{valid.member.account.max}")
    private String account;

    @NotBlank(message = "{valid.login.password.required}")
    @Size(max = 64, message = "{valid.login.password.max}")
    private String password;

    @Size(max = 64, message = "{valid.login.captchaKey.max}")
    private String captchaKey;

    @Size(max = 8, message = "{valid.login.captchaCode.max}")
    private String captchaCode;
}
