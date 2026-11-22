package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台重置会员密码。
 *
 * <p>与会员端的 {@link MemberResetPasswordRequest} 分开：后台不需要验证码，
 * 由有权限的运营直接设置初始密码；重置后该会员所有会话立即失效。</p>
 */
@Data
public class MemberAdminResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.password.new.required}")
    @Size(min = 6, max = 64, message = "{valid.password.new.size}")
    private String newPassword;
}
