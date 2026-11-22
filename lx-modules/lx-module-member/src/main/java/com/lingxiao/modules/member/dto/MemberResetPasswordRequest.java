package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员找回密码 - 使用验证码重置（会员端）。
 *
 * <p>验证码先由 {@code POST /api/app/auth/forgot-password} 申请，
 * 服务端只保存 BCrypt 哈希；这里提交明文验证码，校验通过后立即作废。</p>
 */
@Data
public class MemberResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.account.required}")
    @Size(max = 128, message = "{valid.member.account.max}")
    private String account;

    @NotBlank(message = "{valid.member.reset.code.required}")
    @Size(max = 16, message = "{valid.member.reset.code.max}")
    private String code;

    @NotBlank(message = "{valid.password.new.required}")
    @Size(min = 6, max = 64, message = "{valid.password.new.size}")
    private String newPassword;
}
