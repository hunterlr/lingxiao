package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/** 第三方建号首次设置登录密码；已设置密码的账号必须走修改密码。 */
@Data
public class MemberPasswordSetRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.login.password.required}")
    @Size(min = 6, max = 64, message = "{valid.password.size}")
    private String newPassword;
}
