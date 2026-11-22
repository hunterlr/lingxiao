package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台新增会员。
 *
 * <p>后台建号用于“线下运营录入 / 客服代注册”，与会员自助注册共用同一张
 * {@code mem_user}，但不需要验证码与协议勾选。</p>
 */
@Data
public class MemberCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.username.required}")
    @Size(max = 64, message = "{valid.user.username.max}")
    private String username;

    @NotBlank(message = "{valid.login.password.required}")
    @Size(min = 6, max = 64, message = "{valid.password.size}")
    private String password;

    @NotBlank(message = "{valid.nickname.required}")
    @Size(max = 64, message = "{valid.nickname.max}")
    private String nickname;

    @Size(max = 32, message = "{valid.phone.max}")
    private String mobile;

    @Email(message = "{valid.email.format}")
    @Size(max = 128, message = "{valid.email.max}")
    private String email;

    @Size(max = 255, message = "{valid.avatar.max}")
    private String avatar;

    private String sex;

    private Long levelId;

    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
