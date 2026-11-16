package com.lingxiao.admin.api.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 个人中心 - 修改密码。
 *
 * <p>必须校验原密码，防止 Token 被盗后直接改密锁死账号。
 * 二次确认只在前端做；后端拿到的是两个字段，不存明文、不落日志。</p>
 */
@Data
public class PasswordChangeRequest {

    @NotBlank(message = "{valid.password.old.required}")
    @Size(max = 64, message = "{valid.password.old.max}")
    private String oldPassword;

    @NotBlank(message = "{valid.password.new.required}")
    @Size(min = 6, max = 64, message = "{valid.password.new.size}")
    private String newPassword;
}
