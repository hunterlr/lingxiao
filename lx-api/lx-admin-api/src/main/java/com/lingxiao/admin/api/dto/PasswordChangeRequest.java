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

    @NotBlank(message = "原密码不能为空")
    @Size(max = 64, message = "原密码不能超过 64 个字符")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度必须在 6~64 之间")
    private String newPassword;
}
