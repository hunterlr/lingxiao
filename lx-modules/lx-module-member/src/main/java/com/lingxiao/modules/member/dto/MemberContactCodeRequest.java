package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员换绑手机号 / 邮箱 - 发送验证码。
 *
 * <p>验证码发送到“新联系方式”，而不是旧值。已设置密码的账号必须同时提交
 * 当前密码，避免会话被盗后直接改绑；第三方建号未设置密码时由服务端跳过
 * 密码校验，仍要求登录态与新联系方式验证码。</p>
 */
@Data
public class MemberContactCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** mobile / email */
    @NotBlank(message = "{valid.member.contact.type.required}")
    @Pattern(regexp = "(?i)mobile|email", message = "{valid.member.contact.type}")
    private String type;

    /** 待绑定的新手机号 / 邮箱 */
    @NotBlank(message = "{valid.member.contact.value.required}")
    @Size(max = 128, message = "{valid.member.contact.value.max}")
    private String value;

    /** 当前登录密码；password_set=1 时必填 */
    @Size(max = 64, message = "{valid.password.old.max}")
    private String password;
}
