package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台编辑会员。
 *
 * <p>用户名不可改：它是登录标识，改了会让会员“账号突然不存在”。
 * 密码不在编辑表单里改，走独立的重置密码接口。</p>
 */
@Data
public class MemberUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

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
