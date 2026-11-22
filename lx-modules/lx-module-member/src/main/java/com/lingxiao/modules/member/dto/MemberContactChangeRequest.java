package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员换绑手机号 / 邮箱 - 校验验证码并落库。
 *
 * <p>{@code value} 必须与发码时的 {@code newValue} 一致；验证码一次性使用，
 * 换绑成功后旧联系方式立即失效，不能再用旧值登录或找回密码。</p>
 */
@Data
public class MemberContactChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** mobile / email */
    @NotBlank(message = "{valid.member.contact.type.required}")
    @Pattern(regexp = "(?i)mobile|email", message = "{valid.member.contact.type}")
    private String type;

    /** 待绑定的新手机号 / 邮箱 */
    @NotBlank(message = "{valid.member.contact.value.required}")
    @Size(max = 128, message = "{valid.member.contact.value.max}")
    private String value;

    @NotBlank(message = "{valid.member.contact.code.required}")
    @Size(max = 16, message = "{valid.member.contact.code.max}")
    private String code;
}
